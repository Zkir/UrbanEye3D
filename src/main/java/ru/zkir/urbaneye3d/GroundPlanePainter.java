package ru.zkir.urbaneye3d;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import com.drew.lang.annotations.NotNull;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.gui.MapViewState;
import org.openstreetmap.josm.gui.layer.*;
import org.openstreetmap.josm.gui.layer.imagery.TileCoordinateConverter;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.tools.JosmRuntimeException;
import org.openstreetmap.josm.tools.bugreport.BugReport;

public class GroundPlanePainter {
    private static int TEXTURE_SIZE_PIXELS; //this has be a static constant, otherwise MapView use it before initialization

    /**
     * A LayerManager that holds a single layer. Used to safely initialize a VirtualMapView
     * without causing side-effects like listener registration.
     */

    private static class DummyLayerManager extends MainLayerManager {
        private final transient Layer layer;

        DummyLayerManager(Layer layer) {
            this.layer = layer;
        }

        @Override public java.util.List<Layer> getLayers() {
            return layer != null ? java.util.List.of(layer) : java.util.List.of();
        }

        @Override public List<Layer> getVisibleLayersInZOrder() {
            return getLayers();
        }

        @Override public void addLayer(Layer layer) { /* no-op */ }
        @Override public void removeLayer(Layer layer) { /* no-op */ }
        @Override public void moveLayer(Layer layer, int to) { /* no-op */ }
        @Override public void addLayerChangeListener(LayerChangeListener listener) { /* no-op */ }
        @Override public void addAndFireLayerChangeListener(LayerChangeListener listener) { /* no-op */ }
        @Override public void removeLayerChangeListener(LayerChangeListener listener) { /* no-op */ }
        @Override public void addActiveLayerChangeListener(ActiveLayerChangeListener listener) { /* no-op */ }
        @Override public void removeActiveLayerChangeListener(ActiveLayerChangeListener listener) { /* no-op */ }
    }


    //TODO:deconstruct this class and get rid of ancestor: MapView, because MapView is really a JPanel, not map painter.
    private static class VirtualMapView extends MapView {
        private final ProjectionBounds customBounds;
        private final double customScale;

        VirtualMapView(Layer layerToPaint, Bounds bounds) {

            super(new DummyLayerManager(layerToPaint), null);

            this.customBounds = new ProjectionBounds(
                    getProjection().latlon2eastNorth(bounds.getMin()),
                    getProjection().latlon2eastNorth(bounds.getMax())
            );
            // Calculate a scale that fits the desired meter size into the desired pixel texture size
            // We must not use EastNorth for calculations. So we will use greatCircleDistance instead
            LatLon bottomLeft = bounds.getMin();
            LatLon bottomRight = new LatLon(bounds.getMin().lat(), bounds.getMax().lon());
            double widthInMeters = bottomLeft.greatCircleDistance(bottomRight);
            double proj_coeff = 1 / Math.cos(bounds.getCenter().lat() * Math.PI/180); //We still need to adjust by cos(lat) again
            //UrbanEye3dPlugin.debugMsg("proj_coeff: " + proj_coeff);
            this.customScale =  proj_coeff * widthInMeters / TEXTURE_SIZE_PIXELS;
            //UrbanEye3dPlugin.debugMsg("widthInMeters: " + widthInMeters + " customScale: " + customScale);

            // Manually register the layer using reflection, since the DummyLayerManager doesn't fire events
            // and the LayerAddEvent constructor is not public.
            try {
                Constructor<LayerManager.LayerAddEvent> constructor = LayerManager.LayerAddEvent.class.getDeclaredConstructor(LayerManager.class, Layer.class, boolean.class);
                constructor.setAccessible(true);
                LayerManager.LayerAddEvent event = constructor.newInstance(this.getLayerManager(), layerToPaint, false);
                this.layerAdded(event);
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                UrbanEye3dPlugin.debugMsg("Failed to register layer in VirtualMapView using reflection: " + e.getMessage());
            }
        }

        //TODO:deconstruct this method

        public void paintLayer(TMSLayer layer, Graphics2D g) {
            try {
                //MapViewPaintable.LayerPainter painter = (MapViewPaintable.LayerPainter)this.registeredLayers.get(layer);
                var painter = layer.attachToMapView(new MapViewPaintable.MapViewEvent(this, false));
                //MapViewPaintable.LayerPainter painter = new AbstractMapViewPaintable.CompatibilityModeLayerPainter();
                //UrbanEye3dPlugin.debugMsg("painter" + painter);

                MapViewState.MapViewRectangle clipBounds = this.getState().getViewArea(g.getClipBounds());
                MapViewGraphics paintGraphics = new MapViewGraphics(this, g, clipBounds);
                float opacity = (float)layer.getOpacity();
                if (opacity < 1.0F) {
                    g.setComposite(AlphaComposite.getInstance(3, opacity));
                }

                painter.paint(paintGraphics);
                g.setPaintMode();
            } catch (IllegalArgumentException | IllegalStateException | JosmRuntimeException t) {
                BugReport.intercept(t).put("layer", layer).warn();
            }
        }



        @Override
        public ProjectionBounds getProjectionBounds() {
            //UrbanEye3dPlugin.debugMsg("getProjectionBounds "+customBounds);
            return customBounds;
        }

        @Override
        public double getScale() {
            //UrbanEye3dPlugin.debugMsg("getScale " + customScale + " " + super.getScale());
            return customScale;
        }

        @Override
        public int getWidth() {
            //UrbanEye3dPlugin.debugMsg("getWidth " + TEXTURE_SIZE_PIXELS);
            return TEXTURE_SIZE_PIXELS;
        }

        @Override
        public int getHeight() {
            //UrbanEye3dPlugin.debugMsg("getWidth " + TEXTURE_SIZE_PIXELS);
            return TEXTURE_SIZE_PIXELS;
        }

        @Override @NotNull
        public Rectangle getBounds(){
            //UrbanEye3dPlugin.debugMsg("getBounds " + new Rectangle(0,0, TEXTURE_SIZE_PIXELS, TEXTURE_SIZE_PIXELS)+ " " + super.getBounds());
            return new Rectangle(0,0, TEXTURE_SIZE_PIXELS, TEXTURE_SIZE_PIXELS);
        }

        @Override
        public EastNorth getCenter(){
            //UrbanEye3dPlugin.debugMsg("getCenter " +this.customBounds.getCenter());
            return this.customBounds.getCenter();
        }
    }


    public BufferedImage renderMapToImage(TMSLayer layer, Bounds bounds, int textureSizePixels) {
        TEXTURE_SIZE_PIXELS = textureSizePixels;

        VirtualMapView virtualMapView = new VirtualMapView(layer, bounds);
        //UrbanEye3dPlugin.debugMsg("getMetersPerUnit: " + virtualMapView.getProjection().getMetersPerUnit());

        BufferedImage image = new BufferedImage(textureSizePixels, textureSizePixels, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.GRAY);
        g2d.fillRect(0, 0, textureSizePixels, textureSizePixels);
        g2d.setClip(0, 0, textureSizePixels, textureSizePixels); // this is necessary, otherwise layer paint fails


        if (layer instanceof AbstractTileSourceLayer) {
            AbstractTileSourceLayer imageryLayer = (AbstractTileSourceLayer) layer;
            Field converterField = null;
            Object originalConverter = null;
            try {
                // Get protected getTileSource method and invoke it
                Method getTileSourceMethod = AbstractTileSourceLayer.class.getDeclaredMethod("getTileSource");
                getTileSourceMethod.setAccessible(true);
                Object tileSource = getTileSourceMethod.invoke(imageryLayer);

                // Create a new converter that uses our virtual map view
                TileCoordinateConverter virtualConverter = new TileCoordinateConverter(
                        virtualMapView,
                        (org.openstreetmap.gui.jmapviewer.interfaces.TileSource) tileSource,
                        imageryLayer.getDisplaySettings()
                );

                // Use reflection to access the private coordinateConverter field
                converterField = AbstractTileSourceLayer.class.getDeclaredField("coordinateConverter");
                converterField.setAccessible(true);

                // Swap it
                originalConverter = converterField.get(imageryLayer);
                converterField.set(imageryLayer, virtualConverter);

                // Run the paint action with the swapped converter
                virtualMapView.paintLayer(layer, g2d);

            } catch (NoSuchFieldException | IllegalAccessException | ClassCastException | NoSuchMethodException |
                     InvocationTargetException e) {
                UrbanEye3dPlugin.debugMsg("Failed to swap CoordinateConverter via reflection: " + e.getMessage());
                // If reflection fails, fall back to the original (likely incorrect) paint method
                virtualMapView.paintLayer(layer, g2d);
            } finally {
                // IMPORTANT: Swap it back in all cases to not break the main JOSM map view
                if (converterField != null && originalConverter != null) {
                    try {
                        converterField.set(imageryLayer, originalConverter);
                    } catch (IllegalAccessException e) {
                        UrbanEye3dPlugin.debugMsg("Failed to restore original CoordinateConverter: " + e.getMessage());
                    }
                }
            }
        } else {
            // Fallback for non-standard imagery layers, though unlikely to work well
            virtualMapView.paintLayer(layer, g2d);
        }

        g2d.dispose();
        return image;
    }
}
