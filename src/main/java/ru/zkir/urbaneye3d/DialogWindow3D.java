package ru.zkir.urbaneye3d;

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
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.layer.LayerManager;
import org.openstreetmap.josm.gui.layer.MainLayerManager;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.NavigatableComponent;
import ru.zkir.urbaneye3d.josmactions.ResetCameraAction;
import ru.zkir.urbaneye3d.josmactions.SwitchRenderEngineAction;
import ru.zkir.urbaneye3d.josmactions.ToggleFakeAOAction;
import ru.zkir.urbaneye3d.josmactions.ToggleWireframeAction;

import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class DialogWindow3D extends ToggleDialog
                             implements DataSetListener, NavigatableComponent.ZoomChangeListener,
                                        LayerManager.LayerChangeListener, MainLayerManager.ActiveLayerChangeListener
{
    private final Renderer3D renderer3D;
    private final Scene scene3d = new Scene();
    private OsmDataLayer listenedLayer;
    private Boolean updatableState = null;

    public Renderer3D getRenderer3D() {
        return renderer3D;
    }

    public DialogWindow3D(UrbanEye3dPlugin plugin) {
        super("Urban Eye 3D", "urbaneye3d", "Urban Eye 3D", null, 250, true, UrbanEye3dPreferences.class); //path for the icon is not required, JOSM picks it up by  automatically.
        renderer3D = new Renderer3D(scene3d);
        createLayout(renderer3D, false, null);

        // Register the action so the shortcut works, but don't create a menu item
        new ToggleWireframeAction(renderer3D);
        new ToggleFakeAOAction(renderer3D);
        new ResetCameraAction(renderer3D);
        new SwitchRenderEngineAction(this);

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

        updateListenedLayer();
        updateData();

    }

    @Override
    public String helpTopic() {
        return "/Plugin/UrbanEye3D";
    }

    @Override
    public void destroy() {
        updateListenedLayer(null);
        super.destroy();
    }

    private void updateListenedLayer() {
        updateListenedLayer(MainApplication.getLayerManager().getEditLayer());
    }

    private void updateListenedLayer(OsmDataLayer newLayer) {
        if (listenedLayer != null) {
            listenedLayer.getDataSet().removeDataSetListener(this);
        }
        listenedLayer = newLayer;
        if (listenedLayer != null) {
            listenedLayer.getDataSet().addDataSetListener(this);
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
        // we need to track when our window becomes visible.
        // when it becomes visible, data is updated.
        if (this.updatableState == null || this.updatableState != this.isUpdateRequired()){
            updateData();
            this.updatableState = this.isUpdateRequired();
        }
    }


    public void updateData() {

        if (!this.isUpdateRequired() ){
            //it seems that if 3d window is minimized or closed this is not necessary to update data.
            return;
        }

        if (listenedLayer != null) {
            scene3d.updateData(listenedLayer.getDataSet());
        } else {
            scene3d.updateData(null);
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

    @Override
    public void zoomChanged() {
        //this event is triggered for both moving and panning
        // we need to process this, because our camera always look to the center of the screen.
        renderer3D.repaint();
    }

    @Override
    public void layerAdded(LayerManager.LayerAddEvent e) {
        updateListenedLayer();
        updateData();
    }

    @Override
    public void layerRemoving(LayerManager.LayerRemoveEvent e) {
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
        updateListenedLayer();
        updateData();
    }
}