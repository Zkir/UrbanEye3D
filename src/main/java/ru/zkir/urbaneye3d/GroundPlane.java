package ru.zkir.urbaneye3d;

import com.jogamp.opengl.GL2;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.gui.MainApplication; //TODO: to be removed
import org.openstreetmap.josm.gui.MapView; //TODO: to be removed
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.TMSLayer;
import ru.zkir.customtms.MapRenderer;
import ru.zkir.customtms.TileCache;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Math.cos;
import static java.lang.Math.toRadians;

public class GroundPlane implements TileCache.CacheUpdateListener {

    private final Map<GroundTile.GroundTileXY, GroundTile> activeTiles = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 200;
    private final Map<GroundTile.GroundTileXY, GroundTile> tileCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<GroundTile.GroundTileXY, GroundTile> eldest) {
            if (size() > MAX_CACHE_SIZE) {
                // This part requires a GL context to destroy the texture.
                // We'll queue it for destruction on the renderer thread.
                if (renderer != null) {
                    renderer.invoke(false, glAutoDrawable -> {
                        eldest.getValue().destroy(glAutoDrawable.getGL().getGL2());
                        return true;
                    });
                }
                return true;
            }
            return false;
        }
    };

    private Renderer3D renderer;
    private ImageryInfo currentImageryLayer;
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

    public void update(LatLon visibleAreaCenter, ImageryInfo newImageryLayer) {

        if (!Objects.equals(currentImageryLayer, newImageryLayer)) {
            currentImageryLayer = newImageryLayer;
            // When layer changes, clear everything. Simpler than trying to update.
            clearAllTiles();
        }

        if (currentImageryLayer == null) {
            if (!activeTiles.isEmpty() || !tileCache.isEmpty()) {
                clearAllTiles();
            }
            return;
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
            if (activeTiles.containsKey(coord)) continue;

            GroundTile tile = tileCache.remove(coord);
            if (tile != null) {
                activeTiles.put(coord, tile);
            } else {
                GroundTile newTile = new GroundTile(coord, TILE_SIZE_DEG, TILE_SIZE_DEG, renderer);
                newTile.setImageryLayer(currentImageryLayer);
                newTile.loadTextureAsync(tmsRenderer, false);
                activeTiles.put(coord, newTile);
            }
        }
        renderer.repaint();
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
        if (renderer != null) {
            renderer.invoke(false, glAutoDrawable -> {
                GL2 gl = glAutoDrawable.getGL().getGL2();
                for (GroundTile tile : activeTiles.values()) {
                    tile.destroy(gl);
                }
                for (GroundTile tile : tileCache.values()) {
                    tile.destroy(gl);
                }
                return true;
            });
        }
        activeTiles.clear();
        tileCache.clear();
    }

    private Bounds getVisibleArea(LatLon center){
        final double N = 0.75;
        double dLat = TILE_SIZE_DEG*N;
        double dLon = TILE_SIZE_DEG*N / cos(toRadians(center.lat()));
        return new Bounds( center.lat() - dLat, center.lon()  - dLon,
                center.lat() + dLat, center.lon() + dLon);

    }

    ImageryInfo getTopmostImageryLayer() {
        MapView mv = MainApplication.getMap().mapView;
        for (Layer layer : mv.getLayerManager().getLayers()) {
            if (layer instanceof TMSLayer && layer.isVisible()) {
                TMSLayer tmsLayer = (TMSLayer) layer;
                try {
                    tmsRenderer.setCurrentImagery(tmsLayer.getInfo());
                    return tmsLayer.getInfo();
                } catch (IllegalArgumentException e) {
                    // Skip incompatible layers
                }
            }
        }
        return null;
    }

    /** we need to update the textures of the ground tiles, within given bounds */
    private void updateGroundTileTextures(Bounds bounds){
        int i = 0;
        for(var tile: getAllTiles()){
            //we need to update only ground tiles related to downloaded tms tiles
            if(bounds.intersects( tile.bounds)) {
                tile.loadTextureAsync(this.tmsRenderer, true);
                i++;
            }
        }
        if (i==0){
            UrbanEye3dPlugin.debugMsg("No ground tiles found for " + bounds + "!");
            //for(var tile: getAllTiles()){
            //    UrbanEye3dPlugin.debugMsg( "  "+tile.bounds );
            //}
        }

    }

    /** This is our response to the update of the original TMS tiles */
    @Override
    public void tileCacheUpdated(TileCache.TileCacheUpdateEvent event) {
        this.updateGroundTileTextures( tmsRenderer.getTileBounds(event.getZoom(), event.getX(), event.getY()) );
        if (renderer != null) {
            SwingUtilities.invokeLater(() -> {
                renderer.repaint();
            });
        }
    }

    public int getPendingTileUpdateRequests() {
        return tmsRenderer.getPendingRequestsCount();
    }
}

