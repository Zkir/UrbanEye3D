package ru.zkir.urbaneye3d;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.osm.DataSet;
import ru.zkir.customtms.MapRenderer;
import ru.zkir.customtms.TileCache;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Math.cos;
import static java.lang.Math.toRadians;

public class GroundPlane implements TileCache.CacheUpdateListener {

    public enum ImageryType {
        TMS,
        MapCSS
    }
    public static class Layer2dInfo{
        private final ImageryType type;
        private final ImageryInfo imageryInfo;
        private final String dataSetName;

        public Layer2dInfo(String dataSetName){
            this.type = ImageryType.MapCSS;
            this.imageryInfo = null;
            if(dataSetName!=null) {
                this.dataSetName = dataSetName;
            }else{
                throw new RuntimeException("Dataset name must be specified");
            }
        }
        public Layer2dInfo(ImageryInfo imageryInfo){
            this.type = ImageryType.TMS;
            this.imageryInfo = imageryInfo;
            this.dataSetName = "";
        }

        public ImageryInfo getImageryInfo() {
            if(type==ImageryType.TMS){
                return this.imageryInfo;
            }else{
                return null;
            }
        }

        public ImageryType getType(){
            return this.type;
        }

        public boolean equals (Layer2dInfo other){
            if (other==null) {return false;}
            return this.type==other.type && this.imageryInfo==other.imageryInfo && this.dataSetName.equals(other.dataSetName);
        }
        public static boolean equal(Layer2dInfo one, Layer2dInfo another){
            boolean result = true;
            if (one!=another){
                if (one!=null){
                    result = one.equals(another);
                }else {
                    result = false;
                }
            }
            return result;
        }
        public String toString(){
            if (this.imageryInfo!=null){
                return "Layer2dInfo[" + this.type.toString() + ", " + this.imageryInfo.getId() + "]";
            }else {
                return "Layer2dInfo[" +this.type.toString() + ", null" + "]"  ;
            }

        }

    }

    private final Map<GroundTile.GroundTileXY, GroundTile> activeTiles = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 20;
    private final Map<GroundTile.GroundTileXY, GroundTile> tileCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<GroundTile.GroundTileXY, GroundTile> eldest) {
            if (size() > MAX_CACHE_SIZE) {
                GroundTile evictedTile = eldest.getValue();
                evictedTile.cancelLoadRequest();
                // This part requires a GL context to destroy the texture.
                // We'll queue it for destruction on the renderer thread.
                requestTileGLTextureDestruction(eldest.getValue());

                return true;
            }
            return false;
        }
    };

    private Renderer3D renderer;
    private Layer2dInfo currentImageryLayer;
    private final MapRenderer tmsRenderer = new MapRenderer();

    private static final double TILE_SIZE_DEG = 0.01;

    public GroundPlane() {
        tmsRenderer.addCacheUpdateListener(this);
    }

    public void setRenderer(Renderer3D renderer) {
        this.renderer = renderer;
    }

    public Collection<GroundTile> getActiveTiles() {
        return activeTiles.values();
    }

    /** Returns alls the tiles, both active and cached*/
    public Collection<GroundTile> getAllTiles(){
        var allTiles = new ArrayList<GroundTile>();
        allTiles.addAll(activeTiles.values());
        allTiles.addAll(tileCache.values());
        return allTiles;
    }

    public void update(LatLon visibleAreaCenter, Layer2dInfo newImageryLayer, DataSet dataSet, boolean forced) {
        tmsRenderer.cancelAllPendingRequests();

        if (newImageryLayer == null) {
            currentImageryLayer = null;
            if (!activeTiles.isEmpty() || !tileCache.isEmpty()) {
                clearAllTiles();
            }
            return;
        }

        if (! Layer2dInfo.equal(currentImageryLayer, newImageryLayer)) {
            currentImageryLayer = newImageryLayer;
            tmsRenderer.setCurrentImagery(newImageryLayer.getImageryInfo());
            // When layer changes, clear everything. Simpler than trying to update.
            this.cancelAllPendingRequests();
            this.clearCachedTiles();
            forced = true;
        }

        //We need to determine the visible area. For now just a constant.
        // TODO: link with cut-off distance.
        Bounds viewBounds = getVisibleArea(visibleAreaCenter);

        Set<GroundTile.GroundTileXY> requiredCoords = calculateRequiredTiles(viewBounds);

        // Deactivate tiles that are no longer visible, moving them to cache
        Set<GroundTile.GroundTileXY> toDeactivate = new HashSet<>(activeTiles.keySet());
        toDeactivate.removeAll(requiredCoords);
        for (GroundTile.GroundTileXY coord : toDeactivate) {
            GroundTile tile = activeTiles.remove(coord);
            if (tile != null) {
                tileCache.put(coord, tile);
            }
        }

        // Activate or create newly required tiles
        for (GroundTile.GroundTileXY coord : requiredCoords) {
            GroundTile tile = activeTiles.get(coord);

            if (tile == null) {
                // Not active, try cache
                tile = tileCache.remove(coord);
                if (tile == null) {
                    // Not in cache either, create new
                    tile = new GroundTile(coord, TILE_SIZE_DEG, TILE_SIZE_DEG, this, dataSet);
                }
                activeTiles.put(coord, tile);
            }

            // Unconditionally update the dataset and imagery info for the tile
            //tile.setDataSet(dataSet);
            tile.setImageryLayer(currentImageryLayer);

            if (forced || !tile.hasImageData()) {
                tile.loadTextureAsync(tmsRenderer, dataSet, forced);
            }
        }
        raiseUpdatedEvent();
    }

    private Set<GroundTile.GroundTileXY> calculateRequiredTiles(Bounds viewBounds) {
        Set<GroundTile.GroundTileXY> required = new HashSet<>();
        LatLon min = viewBounds.getMin();
        LatLon max = viewBounds.getMax();

        int minTileX = (int) Math.floor(min.lon() / TILE_SIZE_DEG);
        int maxTileX = (int) Math.floor(max.lon() / TILE_SIZE_DEG);
        int minTileY = (int) Math.floor(min.lat() / TILE_SIZE_DEG);
        int maxTileY = (int) Math.floor(max.lat() / TILE_SIZE_DEG);

        for (int x = minTileX; x <= maxTileX; x++) {
            for (int y = minTileY; y <= maxTileY; y++) {
                required.add(new GroundTile.GroundTileXY(x, y));
            }
        }
        return required;
    }
    
    public void clearAllTiles() {
        cancelAllPendingRequests();
        clearActiveTiles();
        clearCachedTiles();
    }

    private void cancelAllPendingRequests(){
        tmsRenderer.cancelAllPendingRequests();
        GroundTile.cancelAllPendingRequests();
    }

    private void clearCachedTiles(){
        for (GroundTile tile : tileCache.values()) {
            requestTileGLTextureDestruction(tile);
        }
        tileCache.clear();
    }

    private void clearActiveTiles(){
        for (GroundTile tile : activeTiles.values()) {
            requestTileGLTextureDestruction(tile);
        }
        activeTiles.clear();
    }


    private Bounds getVisibleArea(LatLon center){
        final double N = 0.75;
        double dLat = TILE_SIZE_DEG*N;
        double dLon = TILE_SIZE_DEG*N / cos(toRadians(center.lat()));
        return new Bounds( center.lat() - dLat, center.lon()  - dLon,
                center.lat() + dLat, center.lon() + dLon);

    }

    /** we need to update the textures of the ground tiles, within given bounds */
    private void updateGroundTileTextures(Bounds bounds ){
        int i = 0;
        for(var tile: getAllTiles()){
            //we need to update only ground tiles related to downloaded tms tiles
            if(bounds.intersects( tile.bounds)) {
                //dataset here can be null, because this operation is TMS specific
                tile.loadTextureAsync(this.tmsRenderer, null,  true);
                i++;
            }
        }
        if (System.getProperty("urbaneye3d.unittest") == null) {
            if (i == 0) {
                UrbanEye3dPlugin.debugMsg("No ground tiles found for " + bounds + "!");
            }
        }

    }

    /** This is our response to the update of the original TMS tiles */
    @Override
    public void tileCacheUpdated(TileCache.TileCacheUpdateEvent event) {
        this.updateGroundTileTextures( tmsRenderer.getTileBounds(event.getZoom(), event.getX(), event.getY()) );
        raiseUpdatedEvent();
    }

    /**
     * This method returns the number of pending TMS tiles download requests.
     * */
    public int getPendingTileUpdateRequests() {
        return tmsRenderer.getPendingRequestsCount();
    }

    /**
     * This method returns the number of pending ground tiles paint requests.
     * */
    public int getPendingGroundTilePaintRequests() {
        return GroundTile.getPendingRequestsCount();
    }

    /**
     * Should be called when ground plane i.e. any tile is updated and related renderer should be repainted
     * */
    void raiseUpdatedEvent(){
        if (renderer!=null){
            renderer.repaint();
        }
    }

    /**
    * when tile is deleted, it's GL texture should be disposed, to save video memory.
     */
    void requestTileGLTextureDestruction(GroundTile tile) {
        if (renderer != null) {
            renderer.invoke(false, glAutoDrawable -> {
                tile.destroy(glAutoDrawable.getGL().getGL2());
                return true;
            });
        }
    }

    /** we have to ask GL context about npot texture support */
    boolean isNpotSupported() {
        boolean npotSupported=true;
        if (renderer != null){
            npotSupported = renderer.isNpotSupported();
        }
        return npotSupported;
    }
}

