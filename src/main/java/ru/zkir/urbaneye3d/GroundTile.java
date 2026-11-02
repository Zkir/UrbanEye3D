package ru.zkir.urbaneye3d;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import ru.zkir.customtms.MapRenderer;
import ru.zkir.urbaneye3d.mapcssproxy.MapCssProxy;
import ru.zkir.urbaneye3d.utils.*;
import javax.swing.SwingUtilities;
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

/**
 * A single tile of the ground plane: a quad mesh plus the raster image painted on it.
 * <p>
 * This class knows nothing about OpenGL. It renders the image (from TMS imagery or from
 * OSM data via MapCSS) into a {@link BufferedImage} and reports it as ready.
 * {@link TextureManager} is the only owner of GL textures: it picks the image up by
 * {@link #getTextureKey()} / {@link #getImage()} and uploads it when the tile is drawn.
 * <p>
 * The contract between the two classes is the image version: every time the tile produces a
 * new image, {@link #getImageVersion()} changes, and the texture manager re-uploads the texture.
 */
public class GroundTile {

    /**
     * A simple class to represent the coordinates of a ground tile in a grid.
     */
    public static final class GroundTileXY {
        public final int x;
        public final int y;

        public GroundTileXY(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
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
    private volatile BufferedImage imageData;
    private final Object imageDataLock = new Object();

    private final AtomicBoolean hasImageData = new AtomicBoolean(false);

    /**
     * Incremented each time a new image becomes available, so that the texture manager
     * can tell its GL texture has gone stale without comparing the pixels.
     */
    private volatile int imageVersion = 0;

    private volatile GroundPlane.Layer2dInfo imageryLayer;
    private static final int TILE_TEXTURE_SIZE_PIXELS = 2048;
    private static final double MAPCSS_SCALE = 1; // meters per pixel, for MapCSS. Should correlate with TILE_TEXTURE_SIZE_PIXELS

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

    public GroundTile(GroundTileXY coord, double tileLonSizeDeg, double tileLatSizeDeg, GroundPlane groundPlane) {
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
            // Pixels of one imagery layer are never valid for another one, and the texture key
            // changes as well, so the old image and the old GL texture are simply dropped.
            invalidateImageData();
        }
    }

    GroundPlane.Layer2dInfo getImageryLayer() {
        return imageryLayer;
    }

    /**
     * Tells the texture manager how expensive it is to paint this tile again:
     * a TMS image is just a composition of already cached imagery tiles,
     * while a MapCSS image has to be rendered from OSM data all over again.
     */
    boolean isPaintedByMapCss() {
        GroundPlane.Layer2dInfo layer = this.imageryLayer;
        return layer != null && layer.getType() == MapCSS;
    }

    private int calculateOptimalZoomLevel(Bounds tileBounds, int tileTextureSizePixels) {
        double tileWidthMeters = FlatEarth.getLocalCoords(tileBounds.getCenter().lat(), tileBounds.getMax().lon(), tileBounds.getCenter()).x -
                FlatEarth.getLocalCoords(tileBounds.getCenter().lat(), tileBounds.getMin().lon(), tileBounds.getCenter()).x;

        double idealResolution = tileWidthMeters / tileTextureSizePixels;
        int optimalZoomLevel = 18;
        double minDiff = Double.MAX_VALUE;

        for (int zl = 15; zl <= 22; zl++) {
            double res = (cos(toRadians(tileBounds.getCenter().lat())) * FlatEarth.EQUATOR_LENGTH_M) / (256 * Math.pow(2, zl));
            double diff = Math.abs(res - idealResolution);
            if (diff < minDiff) {
                minDiff = diff;
                optimalZoomLevel = zl;
            }
        }
        return optimalZoomLevel;
    }

    public void loadTextureAsync(MapRenderer tmsRenderer, DataSet dataSet, boolean forced) {
        String key = "#" + this.coord.x + "/" + this.coord.y;

        Runnable task = () -> {
            try {
                if (this.imageryLayer.getType() == MapCSS) {
                    if (dataSet != null) {
                        Thread.sleep(250); //hack to make JOSM UI more responsive
                        loadMapCSSTexture(dataSet, forced);
                    }
                } else {
                    loadTMSTexture(tmsRenderer, forced);
                }

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
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

    /**
     * Renders the TMS imagery covering this tile into a BufferedImage.
     * The image is always created at its natural (may be non-power-of-two) resolution:
     * resizing for old GPUs that cannot handle NPOT textures is done by the texture manager.
     */
    public void loadTMSTexture(MapRenderer tmsRenderer, boolean forced) {
        if ((!forced && hasImageData.get()) || imageryLayer == null) {
            return;
        }

        BufferedImage result = null;
        try {
            int textureWidth = (int) Math.ceil(TILE_TEXTURE_SIZE_PIXELS * cos(toRadians(bounds.getCenter().lat())));
            int textureHeight = TILE_TEXTURE_SIZE_PIXELS;
            tmsRenderer.setCurrentImagery(imageryLayer.getImageryInfo());
            int zoomLevel = calculateOptimalZoomLevel(this.bounds, textureWidth);

            result = tmsRenderer.renderMap(zoomLevel, this.bounds);

        } catch (Exception e) {
            debugMsg("TileCaptureWorker for " + coord + ": Failed to capture image: " + e.getMessage());
        }

        BufferedImage finalResult = result;
        SwingUtilities.invokeLater(() -> {
            if (finalResult != null) {
                setImageData(finalResult);
                this.groundPlane.raiseUpdatedEvent();
            }
        });
    }

    public void loadMapCSSTexture(DataSet dataSet, boolean forced) {
        if ((!forced && hasImageData.get()) || dataSet == null) {
            return;
        }
        BufferedImage result = null;

        var styles = new ArrayList<String>();
        styles.add("resource://mapcss-styles/urbaneye2d.general.mapcss");
        styles.add("resource://mapcss-styles/urbaneye2d.roads.mapcss");

        try {
            var mapCssProxy = new MapCssProxy();
            result = mapCssProxy.render(dataSet, bounds, MAPCSS_SCALE, styles);

        } catch (Exception e) {
            debugMsg("MapCSSRenderer for " + coord + ": Failed to render MapCSS: " + e.getMessage());
        }

        BufferedImage finalResult = result;

        SwingUtilities.invokeLater(() -> {
            if (finalResult != null) {
                setImageData(finalResult);
                this.groundPlane.raiseUpdatedEvent();
            }
        });
    }

    /**
     * The key under which the GL texture of this tile lives in {@link TextureManager}.
     * The imagery layer is part of it, so tiles of different layers never share a texture.
     */
    public String getTextureKey() {
        GroundPlane.Layer2dInfo layer = this.imageryLayer;
        String layerKey;
        if (layer == null) {
            layerKey = "no-layer";
        } else if (layer.getType() == MapCSS) {
            layerKey = "mapcss:" + layer.getDataSetName();
        } else {
            layerKey = "tms:" + layer.getImageryInfo().getId();
        }
        return "ground:" + layerKey + ":#" + coord.x + "/" + coord.y;
    }

    /**
     * Stores a freshly painted image and marks it as a new version, which tells the
     * texture manager that the GL texture of this tile must be recreated.
     * Package-private on purpose: it is the entry point used by the TMS/MapCSS loaders,
     * and by tests that want to feed a tile without running the whole rendering machinery.
     */
    void setImageData(BufferedImage image) {
        synchronized (imageDataLock) {
            imageData = image;
        }
        imageVersion++;
        hasImageData.set(true);
    }

    /**
     * Drops the raster image, without touching the GL texture (which its owner may still hold).
     * Called by the texture manager after the image has been uploaded to video memory,
     * and by the ground plane when a tile is thrown away.
     */
    public void clearImageData() {
        synchronized (imageDataLock) {
            imageData = null;
        }
        hasImageData.set(false);
    }

    /**
     * Drops the raster image and marks the version as changed, so an already uploaded
     * GL texture is considered stale by the texture manager.
     */
    public void invalidateImageData() {
        clearImageData();
        imageVersion++;
    }

    /** Frees everything this tile owns. The GL texture is disposed by {@link TextureManager}. */
    public void destroy() {
        clearImageData();
    }

    public BufferedImage getImage() {
        synchronized (imageDataLock) {
            return imageData;
        }
    }

    public boolean hasImageData() {
        return hasImageData.get();
    }

    public int getImageVersion() {
        return imageVersion;
    }

    public static int getPendingGroundTilePaintRequests() {
        return pendingRequests.size();
    }

    public Mesh getMesh() {
        return this.mesh;

    }

}
