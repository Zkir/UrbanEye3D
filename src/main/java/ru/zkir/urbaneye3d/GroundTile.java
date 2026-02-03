package ru.zkir.urbaneye3d;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import ru.zkir.customtms.MapRenderer;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Math.cos;
import static java.lang.Math.toRadians;
import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.debugMsg;

public class GroundTile {
    /**  A simple class to represent the coordinates of a ground tile in a grid.  */
    public static final class GroundTileXY {
        public final int x;
        public final int y;

        public GroundTileXY(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GroundTileXY tileCoord = (GroundTileXY) o;
            return x == tileCoord.x && y == tileCoord.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }

        @Override
        public String toString() {
            return "TileCoord{" +
                    "x=" + x +
                    ", y=" + y +
                    '}';
        }
    }

    public final GroundTileXY coord;
    public final Bounds bounds;
    private Mesh mesh;
    private Texture texture;
    private volatile BufferedImage imageData;
    private final Object imageDataLock = new Object();

    private final AtomicBoolean hasImageData = new AtomicBoolean(false);
    private final AtomicBoolean hasGlTexture = new AtomicBoolean(false);

    private ImageryInfo imageryLayer;
    private static final int TILE_TEXTURE_SIZE_PIXELS = 2048;
    private static final int THREAD_POOL_SIZE = 4;

    private static final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    private static final Map<String, Future<?>> pendingRequests = new ConcurrentHashMap<>();
    private final Renderer3D renderer;

    public GroundTile(GroundTileXY coord, double tileLonSizeDeg, double tileLatSizeDeg, Renderer3D renderer) {
        this.coord = Objects.requireNonNull(coord);
        this.renderer = renderer;

        double minLon = coord.x * tileLonSizeDeg;
        double maxLon = (coord.x + 1) * tileLonSizeDeg;
        double minLat = coord.y * tileLatSizeDeg;
        double maxLat = (coord.y + 1) * tileLatSizeDeg;
        this.bounds = new Bounds(minLat, minLon, maxLat, maxLon);

        createLocalMesh();
    }

    private void createLocalMesh() {
        // Create a mesh in local coordinates, centered around (0,0)
        LatLon center = this.bounds.getCenter();
        Point2D v1_local = Contour.getLocalCoords(new Point2D(bounds.getMin().lon(), bounds.getMin().lat()), center);
        Point2D v2_local = Contour.getLocalCoords(new Point2D(bounds.getMax().lon(), bounds.getMin().lat()), center);
        Point2D v3_local = Contour.getLocalCoords(new Point2D(bounds.getMax().lon(), bounds.getMax().lat()), center);
        Point2D v4_local = Contour.getLocalCoords(new Point2D(bounds.getMin().lon(), bounds.getMax().lat()), center);

        mesh = new Mesh();
        mesh.verts = new ArrayList<>(List.of(
                new Point3D(v1_local),
                new Point3D(v2_local),
                new Point3D(v3_local),
                new Point3D(v4_local)
        ));
        mesh.bottomFaces.add(new int[]{0, 1, 2, 3});
    }

    public void setImageryLayer(ImageryInfo layer) {
        if (!Objects.equals(this.imageryLayer, layer)) {
            this.imageryLayer = layer;
            if (hasImageData.get()) {
                hasImageData.set(false);
                hasGlTexture.set(false);
                //if (hasGlTexture.get()) {
                //    // Texture is now stale, but we need a GL context to destroy it.
                //   // This will be handled during the next render pass.
                //}
            }
        }
    }
    private int calculateOptimalZoomLevel(Bounds tileBounds, int tileTextureSizePixels) {
        double tileWidthMeters = Contour.getLocalCoords(new Point2D(tileBounds.getMax().lon(), tileBounds.getCenter().lat()), tileBounds.getCenter()).x -
                Contour.getLocalCoords(new Point2D(tileBounds.getMin().lon(), tileBounds.getCenter().lat()), tileBounds.getCenter()).x;

        double idealResolution = tileWidthMeters / tileTextureSizePixels;
        int optimalZoomLevel = 18;
        double minDiff = Double.MAX_VALUE;

        for (int zl = 15; zl <= 22; zl++) {
            double res = (cos(tileBounds.getCenter().lat() * Math.PI/180) * 2 * Math.PI * 6378137) / (256 * Math.pow(2, zl));
            double diff = Math.abs(res - idealResolution);
            if (diff < minDiff) {
                minDiff = diff;
                optimalZoomLevel = zl;
            }
        }
        return optimalZoomLevel;
    }
    public void loadTextureAsync(MapRenderer tmsRenderer, boolean forced) {
        String key = "#" + this.coord.x + "/" + this.coord.y;

        Runnable task = () -> {
            try {
                loadTexture(tmsRenderer, forced);
            } finally {
                pendingRequests.remove(key);
            }
        };

        Future<?> newTask = executorService.submit(task);
        Future<?> oldTask = pendingRequests.put(key, newTask);
        if (oldTask != null) {
            oldTask.cancel(false);
        }
    }

    public void loadTexture(MapRenderer tmsRenderer, boolean forced) {
        if ((!forced && hasImageData.get()) || imageryLayer == null) {
            return;
        }

        BufferedImage result = null;
        try {
            int textureWidth = (int) Math.ceil(TILE_TEXTURE_SIZE_PIXELS * cos(toRadians(bounds.getCenter().lat())));
            int textureHeight = TILE_TEXTURE_SIZE_PIXELS;
            tmsRenderer.setCurrentImagery(imageryLayer);
            int zoomLevel = calculateOptimalZoomLevel(this.bounds, textureWidth);
            if (renderer.isNpotSupported()) {
                result = tmsRenderer.renderMap(zoomLevel, this.bounds);
            }else{
                //It seems that for modern hardware texture size should not be a power of 2
                //but we can still resize the image, if we would like to.
                result = tmsRenderer.renderMap(zoomLevel, this.bounds, getPOT(textureWidth), getPOT(textureHeight));
            }


        } catch (Exception e) {
            debugMsg("TileCaptureWorker for " + coord + ": Failed to capture image: " + e.getMessage());
        }

        BufferedImage finalResult = result;
        SwingUtilities.invokeLater(() -> {
            if (finalResult != null) {
                synchronized (imageDataLock) {
                    imageData = finalResult;
                }
                hasImageData.set(true);
                hasGlTexture.set(false);
                if (renderer != null) {
                    renderer.repaint();
                }
            }
        });
    }
    private int getPOT(int x){
        int result = Integer.highestOneBit(x);
        return result;
    }


    public void uploadToGl(GL2 gl) {
        if (!hasImageData.get()) return;

        synchronized (imageDataLock) {
            if (imageData != null) {
                // First, destroy the old texture if it exists
                if (texture != null) {
                    texture.destroy(gl);
                    texture = null;
                }
                // Now, create the new texture from the available image data
                texture = AWTTextureIO.newTexture(gl.getGLProfile(), imageData, false);
                imageData = null; // Free up system memory
                hasGlTexture.set(true);
            }
        }
    }

    public void render(GL2 gl) {
        if (!isReadyToRender()) {
            if (hasImageData()) {
                uploadToGl(gl);
            } else {
                renderAsEmptyTile(gl);
                return;
            }
        }
        
        texture.bind(gl);
        gl.glEnable(GL2.GL_TEXTURE_2D);
        gl.glColor4d(1.0, 1.0, 1.0, 1.0);

        gl.glBegin(GL2.GL_QUADS);
        gl.glTexCoord2d(0, 1);
        gl.glVertex3d(mesh.verts.get(0).x, mesh.verts.get(0).y, 0);
        gl.glTexCoord2d(1, 1);
        gl.glVertex3d(mesh.verts.get(1).x, mesh.verts.get(1).y, 0);
        gl.glTexCoord2d(1, 0);
        gl.glVertex3d(mesh.verts.get(2).x, mesh.verts.get(2).y, 0);
        gl.glTexCoord2d(0, 0);
        gl.glVertex3d(mesh.verts.get(3).x, mesh.verts.get(3).y, 0);
        gl.glEnd();

        gl.glDisable(GL2.GL_TEXTURE_2D);
    }
    private void renderAsEmptyTile(GL2 gl){
        gl.glBegin(GL2.GL_LINE_LOOP);
        gl.glColor3f(0.0f, 0.0f, 0.0f);
        for (int index : mesh.bottomFaces.get(0)) {
            Point3D p = mesh.verts.get(index);
            gl.glVertex3d(p.x, p.y, p.z);
        }
        gl.glEnd();
    };

    public void destroy(GL2 gl) {
        if (texture != null) {
            texture.destroy(gl);
            texture = null;
        }
        synchronized (imageDataLock) {
            imageData = null;
        }
        hasGlTexture.set(false);
        hasImageData.set(false);
    }

    public boolean isReadyToRender() {
        return hasGlTexture.get();
    }
    public boolean hasImageData() { return hasImageData.get(); }
    
    public BufferedImage getImage(){
        return this.imageData;
    }
}
