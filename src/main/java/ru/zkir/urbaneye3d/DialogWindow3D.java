package ru.zkir.urbaneye3d;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSelectionListener;
import org.openstreetmap.josm.data.osm.event.AbstractDatasetChangedEvent;
import org.openstreetmap.josm.data.osm.event.DataChangedEvent;
import org.openstreetmap.josm.data.osm.event.DataSetListener;
import org.openstreetmap.josm.data.osm.event.NodeMovedEvent;
import org.openstreetmap.josm.data.osm.event.PrimitivesAddedEvent;
import org.openstreetmap.josm.data.osm.event.PrimitivesRemovedEvent;
import org.openstreetmap.josm.data.osm.event.RelationMembersChangedEvent;
import org.openstreetmap.josm.data.osm.event.TagsChangedEvent;
import org.openstreetmap.josm.data.osm.event.WayNodesChangedEvent;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.layer.*;
import org.openstreetmap.josm.gui.NavigatableComponent;
import org.openstreetmap.josm.spi.preferences.Config;
import ru.zkir.urbaneye3d.josmactions.ResetCameraAction;
import ru.zkir.urbaneye3d.josmactions.ToggleFakeAOAction;
import ru.zkir.urbaneye3d.josmactions.ToggleWireframeAction;
import org.openstreetmap.josm.data.osm.DataSelectionListener;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.PrimitiveId;


import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.gui.dialogs.relation.DownloadRelationMemberTask;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.Set;
import java.util.Collection;
import java.util.stream.Collectors;

public class DialogWindow3D extends ToggleDialog
                             implements DataSetListener, NavigatableComponent.ZoomChangeListener,
                                        LayerManager.LayerChangeListener, MainLayerManager.ActiveLayerChangeListener,
                                        PropertyChangeListener, DataSelectionListener
{
    private final Renderer3D renderer3D;
    private final Scene scene3d = new Scene();
    private OsmDataLayer listenedLayer;
    private boolean isDestroyed = false;

    public Renderer3D getRenderer3D() {
        return renderer3D;
    }

    public DialogWindow3D(UrbanEye3dPlugin plugin) {
        super("Urban Eye 3D", "urbaneye3d", "Urban Eye 3D", null, 250, true, UrbanEye3dPreferences.class); //path for the icon is not required, JOSM picks it up by  automatically.
        renderer3D = new Renderer3D(scene3d);
        scene3d.getGroundPlane().setRenderer(renderer3D);
        createLayout(renderer3D, false, null);

        // Register the action so the shortcut works, but don't create a menu item
        new ToggleWireframeAction(renderer3D);
        new ToggleFakeAOAction(renderer3D);
        new ResetCameraAction(renderer3D);

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
        updateData();
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
        updateData();
    }


    public void updateData() {
        if (!this.isUpdateRequired() ){
            //it seems that if 3d window is minimized or closed this is not necessary to update data.
            return;
        }

        if (listenedLayer != null) {
            scene3d.updateData(listenedLayer.getDataSet(), getTopmostImageryLayer());
        } else {
            scene3d.updateData(null, getTopmostImageryLayer());
        }
        renderer3D.repaint();
    }

    private boolean isUpdateRequired() {
        return (!this.isCollapsed || !this.isDocked) && this.isVisible();
    }


    // --- DataSetListener ---
    @Override
    public void dataChanged(DataChangedEvent event) {
        updateData();
        downloadIncompleteMultipolygons();
    }

    @Override
    public void primitivesAdded(PrimitivesAddedEvent event) {
        updateData();
    }

    @Override
    public void primitivesRemoved(PrimitivesRemovedEvent event) {
        updateData();
    }

    @Override
    public void tagsChanged(TagsChangedEvent event) {
        updateData();
    }

    @Override
    public void nodeMoved(NodeMovedEvent event) {
       //System.out.println("Event: nodeMoved");
        updateData();

    }

    @Override
    public void wayNodesChanged(WayNodesChangedEvent event) {
        updateData();
    }

    @Override
    public void relationMembersChanged(RelationMembersChangedEvent event) {
        updateData();
    }

    @Override
    public void otherDatasetChange(AbstractDatasetChangedEvent event) {
        //System.out.println("Event: otherDatasetChange");
        updateData();
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

            scene3d.getGroundPlane().update(visibleAreaCenter, layer2dInfo, listenedLayer != null ? listenedLayer.getDataSet() : null, false);
            renderer3D.repaint();
        }
    }

    @Override
    public void layerAdded(LayerManager.LayerAddEvent e) {
        e.getAddedLayer().addPropertyChangeListener(this);
        updateListenedLayer();
        updateData();
    }

    @Override
    public void layerRemoving(LayerManager.LayerRemoveEvent e) {
        e.getRemovedLayer().removePropertyChangeListener(this);
        if (e.getRemovedLayer() == listenedLayer) {
            updateListenedLayer(null);
        }
        updateData();
    }

    @Override
    public void layerOrderChanged(LayerManager.LayerOrderChangeEvent e) {
        updateData();
    }

    @Override
    public void activeOrEditLayerChanged(MainLayerManager.ActiveLayerChangeEvent e) {
        boolean editLayerChanged = listenedLayer != MainApplication.getLayerManager().getEditLayer();
        updateListenedLayer();
        updateData();
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
                    scene3d.getGroundPlane().update(visibleAreaCenter, tmsLayer, listenedLayer != null ? listenedLayer.getDataSet() : null, false);
                    //this is some kind of back magic, otherwise repaint does not work.
                    SwingUtilities.invokeLater(() -> this.getRenderer3D().repaint());
                }
            }
        }
    }

    private GroundPlane.Layer2dInfo getTopmostImageryLayer() {
        if (MainApplication.getMap()!=null) {
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

        if (listenedLayer!=null){
            String datasetName = listenedLayer.getDataSet().getName();
            return new GroundPlane.Layer2dInfo(datasetName);
        }else{
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
            scene3d.getGroundPlane().update(visibleAreaCenter, tmsLayer, listenedLayer != null ? listenedLayer.getDataSet() : null, true);
            //this is some kind of back magic, otherwise repaint does not work.
            SwingUtilities.invokeLater(() -> this.getRenderer3D().repaint());
        }
    }
}