package ru.zkir.urbaneye3d;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSelectionListener;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.PrimitiveId;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.event.*;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.NavigatableComponent;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.dialogs.relation.DownloadRelationMemberTask;
import org.openstreetmap.josm.gui.layer.*;
import org.openstreetmap.josm.spi.preferences.Config;
import ru.zkir.urbaneye3d.josmactions.ResetCameraAction;
import ru.zkir.urbaneye3d.josmactions.ToggleFakeAOAction;
import ru.zkir.urbaneye3d.josmactions.ToggleSatelliteAction;
import ru.zkir.urbaneye3d.josmactions.ToggleWireframeAction;

import javax.swing.*;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.openstreetmap.josm.tools.I18n.tr;

public class DialogWindow3D extends ToggleDialog
                             implements DataSetListener, NavigatableComponent.ZoomChangeListener,
                                        LayerManager.LayerChangeListener, MainLayerManager.ActiveLayerChangeListener,
                                        PropertyChangeListener, DataSelectionListener
{
    private final Renderer3D renderer3D;
    private final Scene scene3d = new Scene();
    private OsmDataLayer listenedLayer;
    private boolean isDestroyed = false;
    private final ExecutorService sceneUpdateExecutor = Executors.newSingleThreadExecutor();
    private Future<?> pendingSceneUpdate;

    //private long lastDataChangedTimestamp = 0; //TODO: remove when no longer needed

    public Renderer3D getRenderer3D() {
        return renderer3D;
    }

    public DialogWindow3D(UrbanEye3dPlugin plugin) {
        super(tr("Urban Eye 3D"), "urbaneye3d", tr("Urban Eye 3D"), null, 250, true, UrbanEye3dPreferences.class); //path for the icon is not required, JOSM picks it up by  automatically.
        renderer3D = new Renderer3D(scene3d);
        scene3d.getGroundPlane().setRenderer(renderer3D);
        createLayout(renderer3D, false, null);

        // Register the action so the shortcut works, but don't create a menu item
        new ToggleWireframeAction(renderer3D);
        new ToggleFakeAOAction(renderer3D);
        new ResetCameraAction(renderer3D);
        new ToggleSatelliteAction(this);

        renderer3D.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    renderer3D.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    renderer3D.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                renderer3D.setCursor(Cursor.getDefaultCursor());
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 1) {
                    RenderableElement picked = renderer3D.getPickedElement(e.getX(), e.getY());
                    if (picked != null && picked.primitiveId != null) {
                        if (listenedLayer != null) {
                            OsmPrimitive primitive = listenedLayer.getDataSet().getPrimitiveById(picked.primitiveId);
                            if (primitive != null) {
                                listenedLayer.getDataSet().setSelected(primitive);
                            }
                        }
                    }
                }
            }
        });

        renderer3D.setFocusable(true);
        renderer3D.requestFocusInWindow();

        NavigatableComponent.addZoomChangeListener(this);
        MainApplication.getLayerManager().addLayerChangeListener(this);
        MainApplication.getLayerManager().addActiveLayerChangeListener(this);

        //it's highly unlikely for layers to exist on this stage, just to be on a safe side
        // we also add listeners to layers when they are added.
        for (Layer layer : MainApplication.getLayerManager().getLayers()) {
            layer.addPropertyChangeListener(this);
        }

        updateListenedLayer();
        requestSceneUpdate(null);
    }

    @Override
    public String helpTopic() {
        return "/Plugin/UrbanEye3D";
    }

    @Override
    public void destroy() {
        if(isDestroyed){
            return;
        }
        sceneUpdateExecutor.shutdownNow();
        NavigatableComponent.removeZoomChangeListener(this);
        MainApplication.getLayerManager().removeLayerChangeListener(this);
        MainApplication.getLayerManager().removeActiveLayerChangeListener(this);
        for (Layer layer : MainApplication.getLayerManager().getLayers()) {
            layer.removePropertyChangeListener(this);
        }
        updateListenedLayer(null);
        isDestroyed=true;
        super.destroy();
    }

    private void updateListenedLayer() {
        updateListenedLayer(MainApplication.getLayerManager().getEditLayer());
    }

    private void updateListenedLayer(OsmDataLayer newLayer) {
        if (listenedLayer != null) {
            listenedLayer.getDataSet().removeDataSetListener(this);
            listenedLayer.getDataSet().removeSelectionListener(this);
        }
        listenedLayer = newLayer;
        if (listenedLayer != null) {
            listenedLayer.getDataSet().addDataSetListener(this);
            listenedLayer.getDataSet().addSelectionListener(this);
        }
    }


    /**
     * This function is called when the window is:
     * Turned on / turned off, via menu
     * Collapsed / expanded
     * Docked / undocked
     */
    @Override
    public void stateChanged(){
        // When the window state changes (visibility, collapsed, docked/undocked),
        // related GL context is recreated, so all loaded textures becomes invalid.
        // That's why we need to clear all existing tiles and force re-creation
        // of ground plane and its textures in the new GL context.
        scene3d.groundPlane.clearAllTiles();
        requestSceneUpdate(null);
    }

    public void requestSceneUpdate(Bounds dirtyBounds) {
        if (!this.isUpdateRequired() ){
            return;
        }
        //update scene elements: buildings, etc.
        if (pendingSceneUpdate != null && !pendingSceneUpdate.isDone()) {
            // We want scene to be updated following changes in the dataset,
            //  that's why we do cancel(false).
            pendingSceneUpdate.cancel(false);
        }

        final DataSet dataSet = (listenedLayer != null) ? listenedLayer.getDataSet() : null;
        pendingSceneUpdate = sceneUpdateExecutor.submit(() -> {
            final Scene.SceneUpdate update = scene3d.calculateUpdate(dataSet);
            SwingUtilities.invokeLater(() -> {
                scene3d.applyUpdate(update);
                renderer3D.repaint();
            });
        });

        //update ground tiles
        if (MainApplication.isDisplayingMapView()) {
            var layer2Dinfo = getTopmostImageryLayer();
            //TODO: it's a dirty hack.
            // If the main map window is not visible, we cannot neither obtain map center nor active satellite layer
            var visibleAreaCenter = Renderer3D.getCameraPosition();
            //if 2d layer is generated one, it depends on Dataset.
            //Since we recalculate buildings, we should also update 2d layer
            boolean forcedUpdate = (layer2Dinfo != null && layer2Dinfo.getType() == GroundPlane.ImageryType.MapCSS);
            scene3d.groundPlane.update(visibleAreaCenter, layer2Dinfo, dataSet, forcedUpdate, dirtyBounds);
        }
    }

    private boolean isUpdateRequired() {
        return (!this.isCollapsed || !this.isDocked) && this.isVisible();
    }

    /**
     * Calculates area which is affected by the given primitives.
     * Should be called from change events
     * @param primitives list of changed primitives
     * @return geographical bounds that are affected
     */
    private Bounds calculateDirtyBounds(List<? extends OsmPrimitive> primitives){
        var bbox = new BBox();
        for (var primitive: primitives ){
            bbox.add(primitive.getBBox());
        }
        return new Bounds(bbox.getMinLat(), bbox.getMinLon(), bbox.getMaxLat(), bbox.getMaxLon());
    }


    // --- DataSetListener ---
    @Override
    public void dataChanged(DataChangedEvent event) {
        requestSceneUpdate(null);
        downloadIncompleteMultipolygons();
    }

    @Override
    public void primitivesAdded(PrimitivesAddedEvent event) {
        requestSceneUpdate(calculateDirtyBounds(event.getPrimitives()));
    }

    @Override
    public void primitivesRemoved(PrimitivesRemovedEvent event) {
        requestSceneUpdate(calculateDirtyBounds(event.getPrimitives()));
    }

    @Override
    public void tagsChanged(TagsChangedEvent event) {
        requestSceneUpdate(calculateDirtyBounds(event.getPrimitives()));
    }

    @Override
    public void nodeMoved(NodeMovedEvent event) {
        //TODO: remove this measurement when no longer needed
        /*if (lastDataChangedTimestamp != 0) {
            long currentTime = System.nanoTime();
            long elapsed = (currentTime - lastDataChangedTimestamp) / 1_000_000; // Milliseconds
            UrbanEye3dPlugin.debugMsg("Time since last dataChanged event: " + elapsed + " ms");
        }
        lastDataChangedTimestamp = System.nanoTime();*/

        requestSceneUpdate(calculateDirtyBounds(event.getPrimitives()) );
    }

    @Override
    public void wayNodesChanged(WayNodesChangedEvent event) {
        requestSceneUpdate(calculateDirtyBounds(event.getPrimitives()));
    }

    @Override
    public void relationMembersChanged(RelationMembersChangedEvent event) {
        requestSceneUpdate(calculateDirtyBounds(event.getPrimitives()));
    }

    @Override
    public void otherDatasetChange(AbstractDatasetChangedEvent event) {
        requestSceneUpdate(null);
    }

    /**
     *  This event is triggered for both moving and panning.
     *  We need to process it, because our camera always look to the center of the screen.  */
    @Override
    public void zoomChanged() {
        if (isUpdateRequired()) {
            /*
            * Note the following: Building models do not depend on pan and zoom,
            * but the ground plane currently does.
            * */
            var layer2dInfo = getTopmostImageryLayer();
            LatLon visibleAreaCenter = Renderer3D.getCameraPosition();

            scene3d.getGroundPlane().update(visibleAreaCenter, layer2dInfo, listenedLayer != null ? listenedLayer.getDataSet() : null, false, null);
            renderer3D.repaint();
        }
    }

    @Override
    public void layerAdded(LayerManager.LayerAddEvent e) {
        e.getAddedLayer().addPropertyChangeListener(this);
        updateListenedLayer();
        requestSceneUpdate(null);
    }

    @Override
    public void layerRemoving(LayerManager.LayerRemoveEvent e) {
        e.getRemovedLayer().removePropertyChangeListener(this);
        if (e.getRemovedLayer() == listenedLayer) {
            updateListenedLayer(null);
        }
        requestSceneUpdate(null);
    }

    @Override
    public void layerOrderChanged(LayerManager.LayerOrderChangeEvent e) {
        requestSceneUpdate(null);
    }

    @Override
    public void activeOrEditLayerChanged(MainLayerManager.ActiveLayerChangeEvent e) {
        boolean editLayerChanged = listenedLayer != MainApplication.getLayerManager().getEditLayer();
        updateListenedLayer();
        requestSceneUpdate(null);
        if (editLayerChanged) {
            downloadIncompleteMultipolygons();
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        //TODO: it still not clear do we need all layer types here or just TMSLayer
        if (evt.getSource() instanceof Layer) {
            if (Layer.VISIBLE_PROP.equals(evt.getPropertyName())) {
                //recreated ground plane geometries and texture
                if(isUpdateRequired()) {
                    var tmsLayer = getTopmostImageryLayer();
                    LatLon visibleAreaCenter = Renderer3D.getCameraPosition();
                    scene3d.getGroundPlane().update(visibleAreaCenter, tmsLayer, listenedLayer != null ? listenedLayer.getDataSet() : null, false, null);
                    //this is some kind of back magic, otherwise repaint does not work.
                    SwingUtilities.invokeLater(() -> this.getRenderer3D().repaint());
                }
            }
        }
    }

    public void toggleSatelliteImagery() {
        boolean useSatellite = Config.getPref().getBoolean("urbaneye3d.ground-plane.use-satellite", true);
        Config.getPref().putBoolean("urbaneye3d.ground-plane.use-satellite", !useSatellite);
        requestSceneUpdate(null);
    }

    private GroundPlane.Layer2dInfo getTopmostImageryLayer() {
        boolean useSatellite = Config.getPref().getBoolean("urbaneye3d.ground-plane.use-satellite", true);
        if (useSatellite && MainApplication.getMap() != null) {
            MapView mv = MainApplication.getMap().mapView;
            for (Layer layer : mv.getLayerManager().getLayers()) {
                if (layer instanceof TMSLayer && layer.isVisible()) {
                    TMSLayer tmsLayer = (TMSLayer) layer;
                    try {
                        return new GroundPlane.Layer2dInfo(tmsLayer.getInfo());
                    } catch (IllegalArgumentException e) {
                        // Skip incompatible layers
                    }
                }
            }
        }

        if (listenedLayer != null) {
            String datasetName = listenedLayer.getDataSet().getName();
            return new GroundPlane.Layer2dInfo(datasetName);
        } else {
            return null;
        }

    }

    /**
     *  This method downloads incomplete multipolygons and incomplete building relation members
     */
    private void downloadIncompleteMultipolygons() {

        boolean downloadWholeMultipolygons = Config.getPref().getBoolean("urbaneye3d.download-incomplete.enabled", false);
        if (!downloadWholeMultipolygons) {
            return;
        }

        if (listenedLayer == null || listenedLayer.getDataSet() == null) {
            return;
        }

        if (isDestroyed){
            throw new RuntimeException("this instance is already destroyed, it cannot download anything");
            //NOTE: if you get this error, probably listeners are not properly freed in destroy method
        }

        DataSet dataSet = listenedLayer.getDataSet();
        Set<Relation> incompleteMultipolygons = new HashSet<>();
        Set<OsmPrimitive> primitivesToDownload = new HashSet<>();

        for (Relation relation : dataSet.getRelations()) {
            // Check if it's a multipolygon relation or building relation and has incomplete members

            if (relation.hasIncompleteMembers() &&
                    (relation.hasTag("type", "multipolygon") || relation.hasTag("type", "building")) && !relation.hasKey("place")) {
                incompleteMultipolygons.add(relation);
                primitivesToDownload.addAll(relation.getIncompleteMembers());
            }
        }

        if (!primitivesToDownload.isEmpty()) {
            UrbanEye3dPlugin.debugMsg("Downloading " + primitivesToDownload.size() + " incomplete members for " + incompleteMultipolygons.size() + " multipolygons.");
            DownloadRelationMemberTask downloadTask = new DownloadRelationMemberTask(
                    incompleteMultipolygons,
                    primitivesToDownload,
                    listenedLayer
            );
            MainApplication.worker.submit(downloadTask);
        }
    }


    @Override
    public void selectionChanged(SelectionChangeEvent event) {
		Collection<PrimitiveId> selectedPrimitiveIds = event.getSelection().stream()
                .map(OsmPrimitive::getPrimitiveId)
                .collect(Collectors.toList());
        scene3d.updateSelection(selectedPrimitiveIds);
		
        if(isUpdateRequired()) {
			//TODO: since josm has special styles for selected objects, we need to redraw ground plane when selection changes.
            //  in future we should get rid of that.
            var tmsLayer = getTopmostImageryLayer();
            LatLon visibleAreaCenter = Renderer3D.getCameraPosition();
            List<OsmPrimitive> added = new ArrayList<>(event.getAdded());
            List<OsmPrimitive> removed = new ArrayList<>(event.getRemoved());
            List<OsmPrimitive> modifiedPrimitives = new ArrayList<>();
            modifiedPrimitives.addAll(added);
            modifiedPrimitives.addAll(removed);

            scene3d.getGroundPlane().update(visibleAreaCenter, tmsLayer, listenedLayer != null ? listenedLayer.getDataSet() : null, true, calculateDirtyBounds(modifiedPrimitives));
            //this is some kind of back magic, otherwise repaint does not work.
            SwingUtilities.invokeLater(() -> this.getRenderer3D().repaint());
        }
    }
}