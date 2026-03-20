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

    //TODO: strange bug in JOSM: MapCSS painter partially uses main thread, so background threads fights with each other
    //  we have to decrease number of threads.
    private static final int THREAD_POOL_SIZE = 1;

    private static final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    private static final Map<String, Future<?>> pendingRequests = new ConcurrentHashMap<>();
    private final GroundPlane groundPlane;

    public static int getPendingRequestsCount() {
        return pendingRequests.size();
    }

    public static void cancelAllPendingRequests() {
        for (Future<?> future : pendingRequests.values()) {
            // TODO: (JOSM-fix-ticket) Once JOSM's GuiHelper.runInEDTAndWait is fixed to not log InterruptedException
            // as a SEVERE error (or InterruptedException is re-thrown), change this back to 'true'
            // to allow for aggressive cancellation of running tasks and better performance.
            future.cancel(false); // Prevents task from starting, but doesn't interrupt running tasks.
        }
        pendingRequests.clear();
    }

    public void cancelLoadRequest() {
        String key = "#" + this.coord.x + "/" + this.coord.y;
        Future<?> future = pendingRequests.get(key);
        if (future != null) {
            // TODO: (JOSM-fix-ticket) Once JOSM's GuiHelper.runInEDTAndWait is fixed to not log InterruptedException
            // as a SEVERE error (or InterruptedException is re-thrown), change this back to 'true'
            // to allow for aggressive cancellation of running tasks and better performance.
            future.cancel(false); // Prevents task from starting, but doesn't interrupt running tasks.
            pendingRequests.remove(key);
        }
    }

    public GroundTile(GroundTileXY coord, double tileLonSizeDeg, double tileLatSizeDeg, GroundPlane groundPlane, DataSet dataSet) {
        this.coord = Objects.requireNonNull(coord);
        this.groundPlane = groundPlane;

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

        mesh = new Mesh(null, null, null);
        mesh.addVertex(new Point3D(v1_local));
        mesh.addUV(0, 1);
        mesh.addVertex(new Point3D(v2_local));
        mesh.addUV(1, 1);
        mesh.addVertex(new Point3D(v3_local));
        mesh.addUV(1, 0);
        mesh.addVertex(new Point3D(v4_local));
        mesh.addUV(0, 0);
        mesh.addFace(new int[]{0, 1, 2, 3}, new int[]{0, 1, 2, 3});

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

    public void loadTextureAsync(MapRenderer tmsRenderer, DataSet dataSet, boolean forced ) {
        String key = "#" + this.coord.x + "/" + this.coord.y;

        Runnable task = () -> {
            try {
                if (this.imageryLayer.getType() == MapCSS ){
                    if (dataSet!=null) {
                        loadMapCSSTexture(dataSet, forced);
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
    public void loadMapCSSTexture(DataSet dataSet, boolean forced) {
        if ((!forced && hasImageData.get()) || dataSet == null) {
            return;
        }
        BufferedImage result=null;
        UrbanEye3dPlugin.debugMsg("loadMapCSSTexture()"); //TODO remove when no longer necessary.

        var styles= new ArrayList<String>();
        styles.add("resource://mapcss-styles/urbaneye2d.general.mapcss");
        styles.add("resource://mapcss-styles/urbaneye2d.roads.mapcss");

        try {
            var mapCssProxy = new MapCssProxy();

            boolean npotSupported = groundPlane.isNpotSupported();

            if (npotSupported) {
                result =  mapCssProxy.render(dataSet, bounds, 1, styles);
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

    public Texture render(GL2 gl) {
        if (!isReadyToRender()) {
            if (hasImageData()) {
                uploadToGl(gl);
            } else {
                return null;
            }
        }
        return this.texture;
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

    public Mesh getMesh() {
        return this.mesh;

    }

}
