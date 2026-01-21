package ru.zkir.urbaneye3d;


import com.jogamp.opengl.GL2;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.TMSLayer;

import ru.zkir.customtms.MapRenderer;
import ru.zkir.customtms.TileCache;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point3D;
import ru.zkir.urbaneye3d.utils.Point2D;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.openstreetmap.josm.data.coor.EastNorth;
import static java.lang.Math.round;

/**
 * This class is responsible for rendering the ground plane with the current JOSM imagery layer.
 */
public class GroundPlane implements TileCache.CacheUpdateListener {

    //private static final double PLANE_SIZE_GRADS = 0.01;
    private static final double PLANE_SIZE_METERS = 1000;
    private static final int EXPECTED_TEXTURE_SIZE_PIXELS = 1024;

    private Mesh mesh;
    private Texture texture;
    private BufferedImage composedImage;
    private final Object imageLock = new Object();
    private Renderer3D renderer;

    // State tracking to avoid unnecessary updates
    private Layer lastLayer;
    private LatLon lastCenter;
    private Bounds lastBounds;
    private long lastUpdateTime = 0;
    private static final long UPDATE_THROTTLE_MS = 25;//250; // Update at most 4 times per second
    private boolean isWorkerRunning = false;

    private final MapRenderer tmsRenderer = new MapRenderer();

    public GroundPlane(){
        tmsRenderer.addCacheUpdateListener(this); //subscribe itself for tile cache update events
    }

    public void setRenderer(Renderer3D renderer) {
        this.renderer = renderer;
    }

    public void update() {
        update(false);
    }
    public void update(boolean force) {
        long now = System.currentTimeMillis();
        if (now - lastUpdateTime < UPDATE_THROTTLE_MS) {
            return; // Throttle updates
        }


        if (!MainApplication.isDisplayingMapView() || isWorkerRunning) {
            return;
        }

        final MapView mv = MainApplication.getMap().mapView;
        TMSLayer currentImageryLayer = getTopmostImageryLayer(mv);

        try {
            if (currentImageryLayer != null) {
                tmsRenderer.setCurrentImagery(currentImageryLayer.getInfo());
            }
        }catch (IllegalArgumentException e){
            //current imagery layer is not compatible with our rendering engine
            UrbanEye3dPlugin.debugMsg("GroundPlane: Layer '" + currentImageryLayer.getName() + "' is ignored");
            UrbanEye3dPlugin.debugMsg(e.getMessage());
            currentImageryLayer=null;
        }

        if (currentImageryLayer == null) {
            if (lastLayer != null) { // Only log if the state changes
                UrbanEye3dPlugin.debugMsg("GroundPlane: No visible imagery layer found. Clearing ground plane.");
                clearGlObjects();
                lastLayer = null;
            }
            return;
        }

        if (lastLayer == null) { // Log when a layer is found for the first time
            UrbanEye3dPlugin.debugMsg("GroundPlane: Found imagery layer: " + currentImageryLayer.getName());
        }

        final EastNorth centerEN = mv.getCenter();
        final LatLon center = mv.getProjection().eastNorth2latlon(centerEN);

        if (!force && currentImageryLayer == lastLayer && Objects.equals(center, lastCenter)) {
            return;
        }
        lastUpdateTime = now;
        UrbanEye3dPlugin.debugMsg("GroundPlane: State change detected. Launching worker.");
        lastLayer = currentImageryLayer;
        lastCenter = center;


        new LayerCaptureWorker(currentImageryLayer).execute();
    }

    private class LayerCaptureWorker extends SwingWorker<BufferedImage, Void> {
        private final TMSLayer layer;

        LayerCaptureWorker(TMSLayer layer) {
            this.layer = layer;
            isWorkerRunning = true;
        }

        @Override
        protected BufferedImage doInBackground() throws Exception {

            UrbanEye3dPlugin.debugMsg("LayerCaptureWorker: Starting custom image capture.");

            var bounds = getGroundPlaneBounds();


            // Find the optimal zoom level dynamically instead of hardcoding it
            int optimalZoomLevel = 18; // Default
            double minDiff = Double.MAX_VALUE;
            double idealResolution = PLANE_SIZE_METERS / EXPECTED_TEXTURE_SIZE_PIXELS;
            for (int zl = 15; zl <= 22; zl++) {
                double res = (Math.cos(lastCenter.lat() * Math.PI/180) * 2 * Math.PI * 6378137) / (256 * Math.pow(2, zl));
                double diff = Math.abs(res - idealResolution);
                if (diff < minDiff) {
                    minDiff = diff;
                    optimalZoomLevel = zl;
                }
            }
            int zoomLevel = optimalZoomLevel;
            UrbanEye3dPlugin.debugMsg("Optimal zoom level found: " + zoomLevel);

            //meters per pixel for the given zoom
            double ground_resolution = (Math.cos(lastCenter.lat() * Math.PI/180) * 2 * Math.PI * 6378137) / (256 * Math.pow(2, zoomLevel));
            UrbanEye3dPlugin.debugMsg("ground_resolution: " + ground_resolution);
            int textureSizePixels = Math.toIntExact(round(PLANE_SIZE_METERS / ground_resolution));
            UrbanEye3dPlugin.debugMsg("Texture size: " + textureSizePixels);

            BufferedImage image = tmsRenderer.renderMap(zoomLevel, bounds, textureSizePixels, textureSizePixels);

            UrbanEye3dPlugin.debugMsg("LayerCaptureWorker: Image capture complete.");
            return image;
        }

        @Override
        protected void done() {
            try {
                BufferedImage result = get();
                if (result != null) {
                    UrbanEye3dPlugin.debugMsg("LayerCaptureWorker: Worker finished. Scheduling GL update.");
                    synchronized (imageLock) {
                        composedImage = result;
                    }
                    if (renderer != null) {
                        renderer.invoke(false, glAutoDrawable -> {
                            updateGlObjects(glAutoDrawable.getGL().getGL2());
                            return true;
                        });
                        renderer.repaint();
                    }
                }
            } catch (Exception e) {
                UrbanEye3dPlugin.debugMsg("LayerCaptureWorker: Worker failed. " + e.getMessage());
            } finally {
                isWorkerRunning = false;
            }

        }
    }


    private Bounds getGroundPlaneBounds(){
        LatLon center = MainApplication.getMap().mapView.getRealBounds().getCenter();
        //3D window has the same center, but different bounds
        //LatLon min = new LatLon(center.lat() - PLANE_SIZE_GRADS/2.0, center.lon() - PLANE_SIZE_GRADS/2.0);
        //LatLon max = new LatLon(center.lat() + PLANE_SIZE_GRADS/2.0, center.lon() + PLANE_SIZE_GRADS/2.0);
        // * Math.cos(Math.toRadians(center.lat())) * GRAD_LENGTH_M


        LatLon min = new LatLon(center.lat() - PLANE_SIZE_METERS/2.0/Contour.GRAD_LENGTH_M, center.lon() - PLANE_SIZE_METERS/2.0/Contour.GRAD_LENGTH_M/Math.cos(Math.toRadians(center.lat())));
        LatLon max = new LatLon(center.lat() + PLANE_SIZE_METERS/2.0/Contour.GRAD_LENGTH_M, center.lon() + PLANE_SIZE_METERS/2.0/Contour.GRAD_LENGTH_M/Math.cos(Math.toRadians(center.lat())));
        return new Bounds(min, max);
    }

    private void updateGlObjects(GL2 gl) {
        synchronized (imageLock) {
            if (texture != null) {
                texture.destroy(gl);
            }
            if (composedImage != null) {
                try {
                    //javax.imageio.ImageIO.write(composedImage, "png", new java.io.File("debug.png"));
                }catch (Exception e){
                    UrbanEye3dPlugin.debugMsg(e.getMessage());
                }

                texture = AWTTextureIO.newTexture(gl.getGLProfile(), composedImage, false);

            }
        }

        if (lastCenter != null) {
            // Create a PLANE_SIZE_GRADSxPLANE_SIZE_GRADS plane
            LatLon center = MainApplication.getMap().mapView.getRealBounds().getCenter();
            lastBounds = getGroundPlaneBounds();
            UrbanEye3dPlugin.debugMsg("lastBounds: "+ lastBounds);

            var min = lastBounds.getMin();
            var max = lastBounds.getMax();
            Point2D v1 = Contour.getLocalCoords(new Point2D(min.lon(), min.lat()), center);
            Point2D v2 = Contour.getLocalCoords(new Point2D(max.lon(), min.lat()), center);
            Point2D v3 = Contour.getLocalCoords(new Point2D(max.lon(), max.lat()), center);
            Point2D v4 = Contour.getLocalCoords(new Point2D(min.lon(), max.lat()), center);


            mesh = new Mesh();
            mesh.verts = new ArrayList<>(List.of(new Point3D(v1), new Point3D(v2), new Point3D(v3), new Point3D( v4)));
            mesh.bottomFaces.add(new int[]{0, 1, 2, 3});
        }
    }

    private void clearGlObjects() {
        if (renderer != null) {
            renderer.invoke(false, glAutoDrawable -> {
                GL2 gl = glAutoDrawable.getGL().getGL2();
                if (texture != null) {
                    texture.destroy(gl);
                    texture = null;
                }
                mesh = null;
                return true;
            });
        }
    }

    private TMSLayer getTopmostImageryLayer(MapView mv) {
        List<Layer> visibleLayers = mv.getLayerManager().getLayers();
        for (Layer layer : visibleLayers) {
            if ((layer instanceof TMSLayer) && layer.isVisible() ) {
                return (TMSLayer)layer;
            }
        }
        return null;
    }

    public void render(GL2 gl) {
        if (mesh == null || texture == null) {
            return;
        }

        gl.glEnable(GL2.GL_TEXTURE_2D);
        texture.bind(gl);
        gl.glColor4d(1.0, 1.0, 1.0, 1.0);

        gl.glBegin(GL2.GL_QUADS);
        gl.glTexCoord2d(0, 1); // Flipped Y
        gl.glVertex3d(mesh.verts.get(0).x, mesh.verts.get(0).y, mesh.verts.get(0).z);
        gl.glTexCoord2d(1, 1); // Flipped Y
        gl.glVertex3d(mesh.verts.get(1).x, mesh.verts.get(1).y, mesh.verts.get(1).z);
        gl.glTexCoord2d(1, 0); // Flipped Y
        gl.glVertex3d(mesh.verts.get(2).x, mesh.verts.get(2).y, mesh.verts.get(2).z);
        gl.glTexCoord2d(0, 0); // Flipped Y
        gl.glVertex3d(mesh.verts.get(3).x, mesh.verts.get(3).y, mesh.verts.get(3).z);
        gl.glEnd();

        gl.glDisable(GL2.GL_TEXTURE_2D);
    }
    @Override
    public void tileCacheUpdated(TileCache.TileCacheUpdateEvent event) {
        // This event is fired by imagery layers when new tiles are loaded.
        // We just need to update the ground plane, not the whole scene.
        this.update(true);
        //renderer.repaint(); -- hopefully it will repaint by itself
    }


}
