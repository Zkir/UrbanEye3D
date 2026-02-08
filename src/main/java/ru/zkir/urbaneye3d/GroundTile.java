package ru.zkir.urbaneye3d;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import ru.zkir.customtms.MapRenderer;
import ru.zkir.urbaneye3d.mapcssproxy.MapCssProxy;
import ru.zkir.urbaneye3d.utils.*;

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
import static ru.zkir.urbaneye3d.GroundPlane.ImageryType.MapCSS;
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

    private GroundPlane.Layer2dInfo imageryLayer;
    private static final int TILE_TEXTURE_SIZE_PIXELS = 2048;
    private static final int THREAD_POOL_SIZE = 4;

    private static final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    private static final Map<String, Future<?>> pendingRequests = new ConcurrentHashMap<>();
    private final GroundPlane groundPlane;
    private final DataSet dataSet;

    public static int getPendingRequestsCount() {
        return pendingRequests.size();
    }

    public static void cancelAllPendingRequests() {
        for (Future<?> future : pendingRequests.values()) {
            future.cancel(true);
        }
        pendingRequests.clear();
    }

    public void cancelLoadRequest() {
        String key = "#" + this.coord.x + "/" + this.coord.y;
        Future<?> future = pendingRequests.get(key);
        if (future != null) {
            future.cancel(true);
            pendingRequests.remove(key);
        }
    }

    public GroundTile(GroundTileXY coord, double tileLonSizeDeg, double tileLatSizeDeg, GroundPlane groundPlane, DataSet dataSet) {
        this.coord = Objects.requireNonNull(coord);
        this.groundPlane = groundPlane;
        this.dataSet = dataSet;

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
        Point2D v1_local = FlatEarth.getLocalCoords(bounds.getMin().lat(), bounds.getMin().lon(), center);
        Point2D v2_local = FlatEarth.getLocalCoords(bounds.getMin().lat(), bounds.getMax().lon(), center);
        Point2D v3_local = FlatEarth.getLocalCoords(bounds.getMax().lat(), bounds.getMax().lon(), center);
        Point2D v4_local = FlatEarth.getLocalCoords(bounds.getMax().lat(), bounds.getMin().lon(), center);

        mesh = new Mesh();
        mesh.verts = new ArrayList<>(List.of(
                new Point3D(v1_local),
                new Point3D(v2_local),
                new Point3D(v3_local),
                new Point3D(v4_local)
        ));
        mesh.bottomFaces.add(new int[]{0, 1, 2, 3});
    }

    public void setImageryLayer(GroundPlane.Layer2dInfo layer) {
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
        double tileWidthMeters = FlatEarth.getLocalCoords(tileBounds.getCenter().lat(), tileBounds.getMax().lon(), tileBounds.getCenter()).x -
                FlatEarth.getLocalCoords(tileBounds.getCenter().lat() , tileBounds.getMin().lon(),  tileBounds.getCenter()).x;

        double idealResolution = tileWidthMeters / tileTextureSizePixels;
        int optimalZoomLevel = 18;
        double minDiff = Double.MAX_VALUE;

        for (int zl = 15; zl <= 22; zl++) {
            double res = (cos(toRadians(tileBounds.getCenter().lat()) ) * FlatEarth.EQUATOR_LENGTH_M) / (256 * Math.pow(2, zl));
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
                if (this.imageryLayer.getType() == MapCSS ){
                    if (dataSet!=null) {
                        loadMapCSSTexture(forced);
                    }
                }else{
                    loadTMSTexture(tmsRenderer, forced);
                }

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

    public void loadTMSTexture(MapRenderer tmsRenderer, boolean forced) {
        if ((!forced && hasImageData.get()) || imageryLayer == null) {
            return;
        }

        BufferedImage result = null;
        try {
            int textureWidth = (int) Math.ceil(TILE_TEXTURE_SIZE_PIXELS * cos(toRadians(bounds.getCenter().lat())));
            int textureHeight = TILE_TEXTURE_SIZE_PIXELS;
            tmsRenderer.setCurrentImagery(imageryLayer.getImageryInfo() );
            int zoomLevel = calculateOptimalZoomLevel(this.bounds, textureWidth);
            boolean npotSupported=groundPlane.isNpotSupported();

            if (npotSupported) {
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
                this.groundPlane.raiseUpdatedEvent();

            }
        });
    }
    public void loadMapCSSTexture(boolean forced) {
        if ((!forced && hasImageData.get()) || dataSet == null) {
            return;
        }
        BufferedImage result=null;

         String styleUrl = "resource://mapcss-styles/urbaneye2d.mapcss";
        //String styleUrl ="d:\\UrbanEye3D\\src\\main\\resources\\mapcss-styles\\urbaneye2d.mapcss";

        try {
            var mapCssProxy = new MapCssProxy();

            boolean npotSupported = groundPlane.isNpotSupported();

            if (npotSupported) {
                result =  mapCssProxy.render(dataSet, bounds, 1, styleUrl);
            } else {
                throw new RuntimeException("Support for NPOT textures is not implemented yet!");
            }

        } catch (Exception e) {
            debugMsg("MapCSSRenderer for " + coord + ": Failed to render MapCSS: " + e.getMessage());
        }

        BufferedImage finalResult = result;
        SwingUtilities.invokeLater(() -> {
            if (finalResult != null) {
                synchronized (imageDataLock) {
                    imageData = finalResult;
                }
                hasImageData.set(true);
                hasGlTexture.set(false);
                this.groundPlane.raiseUpdatedEvent();
            }
        });
    }

    private int getPOT(int x){
        return Integer.highestOneBit(x);
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
    public static int getPendingGroundTilePaintRequests() {
        return pendingRequests.size();
    }

}
