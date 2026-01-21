package ru.zkir.urbaneye3d;

import org.openstreetmap.gui.jmapviewer.tilesources.TMSTileSource;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
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
import org.openstreetmap.josm.gui.layer.*;
import org.openstreetmap.josm.gui.NavigatableComponent;
import ru.zkir.urbaneye3d.josmactions.ResetCameraAction;
import ru.zkir.urbaneye3d.josmactions.ToggleFakeAOAction;
import ru.zkir.urbaneye3d.josmactions.ToggleWireframeAction;

import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class DialogWindow3D extends ToggleDialog
                             implements DataSetListener, NavigatableComponent.ZoomChangeListener,
                                        LayerManager.LayerChangeListener, MainLayerManager.ActiveLayerChangeListener,
                                        PropertyChangeListener
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
        NavigatableComponent.removeZoomChangeListener(this);
        for (Layer layer : MainApplication.getLayerManager().getLayers()) {
            layer.removePropertyChangeListener(this);
        }
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
            scene3d.getGroundPlane().update();
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
        updateListenedLayer();
        updateData();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        //TODO: it still not clear do we need all layer types here or just TMSLayer
        if (evt.getSource() instanceof Layer) {
            if (Layer.VISIBLE_PROP.equals(evt.getPropertyName())) {
                scene3d.getGroundPlane().update(); //this should recreate texture for ground plane
                renderer3D.repaint();

                if (evt.getSource() instanceof TMSLayer) {
                    TMSLayer l = (TMSLayer) evt.getSource();
                    UrbanEye3dPlugin.debugMsg("LayerInfo: " + l.getInfo());
                    UrbanEye3dPlugin.debugMsg("      id: " + l.getInfo().getId());
                    UrbanEye3dPlugin.debugMsg("    name: " + l.getInfo().getName());
                    UrbanEye3dPlugin.debugMsg("     url: " + l.getInfo().getUrl());
                    UrbanEye3dPlugin.debugMsg(" ext url: " + l.getInfo().getExtendedUrl());
                    UrbanEye3dPlugin.debugMsg("    type: " + l.getInfo().getImageryType());

                    try {
                        Method getTileSourceMethod = AbstractTileSourceLayer.class.getDeclaredMethod("getTileSource");
                        getTileSourceMethod.setAccessible(true);
                        TMSTileSource tileSource = (TMSTileSource) getTileSourceMethod.invoke(l);
                        UrbanEye3dPlugin.debugMsg("    source: " + tileSource.getBaseUrl());
                        UrbanEye3dPlugin.debugMsg("    source: " + tileSource.getTileUrl(10,1,1));
                    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                        UrbanEye3dPlugin.debugMsg("    source: (failed to get via reflection: " + e.getMessage() + ")");
                    } catch (Exception e) {
                        UrbanEye3dPlugin.debugMsg(e.getMessage());
                    }
                }
            }
        }
    }
}