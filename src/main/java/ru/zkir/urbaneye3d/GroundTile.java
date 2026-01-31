package ru.zkir.urbaneye3d;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.gui.layer.TMSLayer;
import ru.zkir.customtms.MapRenderer;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

    private final AtomicBoolean isLoading = new AtomicBoolean(false);
    private final AtomicBoolean hasImageData = new AtomicBoolean(false);
    private final AtomicBoolean hasGlTexture = new AtomicBoolean(false);

    private TMSLayer imageryLayer;
    private static final int TILE_TEXTURE_SIZE_PIXELS = 2048;

    public GroundTile(GroundTileXY coord, double tileLonSizeDeg, double tileLatSizeDeg) {
        this.coord = Objects.requireNonNull(coord);

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

    public void setImageryLayer(TMSLayer layer) {
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
        if ((!forced && hasImageData.get() )  || imageryLayer == null) {
            return;
        }
        if (isLoading.get()){
            //UrbanEye3dPlugin.debugMsg ("Denied request for redraw");
            return;
        }

        //UrbanEye3dPlugin.debugMsg("loadTextureAsync");
        isLoading.set(true);
        //new TileCaptureWorker(tmsRenderer, imageryLayer, this.bounds).execute();
        BufferedImage result = null;
        try {
            int textureWidth  = (int) Math.ceil(TILE_TEXTURE_SIZE_PIXELS * cos(toRadians(bounds.getCenter().lat())));
            int textureHeight = TILE_TEXTURE_SIZE_PIXELS;
            tmsRenderer.setCurrentImagery(imageryLayer.getInfo());
            int zoomLevel = calculateOptimalZoomLevel(this.bounds, textureWidth);
            result = tmsRenderer.renderMap(zoomLevel, this.bounds, textureWidth, textureHeight);

        } catch (Exception e) {
            debugMsg("TileCaptureWorker for " + coord + ": Failed to capture image: " + e.getMessage());
        }
        try {
            if (result != null) {
                synchronized (imageDataLock) {
                    imageData = result;
                }
                hasImageData.set(true);
                //since we have a new image, texture should be invalidated
                hasGlTexture.set(false);
            }
        } catch (Exception e) {
            debugMsg("TileCaptureWorker for " + coord + ": Error in done(): " + e.getMessage());
        } finally {
            isLoading.set(false);
        }

    }
   /*
    private class TileCaptureWorker extends SwingWorker<BufferedImage, Void> {
        private final MapRenderer tmsRenderer;
        private final TMSLayer layer;
        private final Bounds captureBounds;

        TileCaptureWorker(MapRenderer tmsRenderer, TMSLayer layer, Bounds captureBounds) {
            this.tmsRenderer = tmsRenderer;
            this.layer = layer;
            this.captureBounds = captureBounds;
        }

        @Override
        protected BufferedImage doInBackground() {
            try {
                tmsRenderer.setCurrentImagery(layer.getInfo());
                int zoomLevel = calculateOptimalZoomLevel(captureBounds);
                return tmsRenderer.renderMap(zoomLevel, captureBounds, TILE_TEXTURE_SIZE_PIXELS, TILE_TEXTURE_SIZE_PIXELS);
            } catch (Exception e) {
                debugMsg("TileCaptureWorker for " + coord + ": Failed to capture image: " + e.getMessage());
                return null;
            }
        }

        @Override
        protected void done() {
            try {
                BufferedImage result = get();
                if (result != null) {
                    synchronized (imageDataLock) {
                        imageData = result;
                    }
                    hasImageData.set(true);
                    //since we have a new image, texture should be invalidated
                    hasGlTexture.set(false);
                }
            } catch (Exception e) {
                debugMsg("TileCaptureWorker for " + coord + ": Error in done(): " + e.getMessage());
            } finally {
                isLoading.set(false);
            }
            UrbanEye3dPlugin.debugMsg("TileCaptureWorker completed for " + captureBounds);
        }
    }
    */

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
                // debugMsg("GroundTile " + coord + ": GL Texture uploaded.");
            }
        }
    }

    public void render(GL2 gl) {
        if (!isReadyToRender()) {
            if (hasImageData()) {
                uploadToGl(gl);
            } else {
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
        isLoading.set(false);
    }

    public boolean isReadyToRender() {
        return hasGlTexture.get();
    }
    public boolean hasImageData() { return hasImageData.get(); }
}
