package ru.zkir.josm.plugins.z3dviewer;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
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
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerAddEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerChangeListener;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerOrderChangeEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerRemoveEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

import java.util.ArrayList;
import java.util.List;

public class Z3dViewerDialog extends ToggleDialog implements DataSetListener, LayerChangeListener, MainLayerManager.ActiveLayerChangeListener {
    private final Renderer3D renderer3D;
    private final List<Building> buildings = new ArrayList<>();

    public static class Building {
        public final Way way;
        public final double height;

        public Building(Way way, double height) {
            this.way = way;
            this.height = height;
        }
    }

    public Z3dViewerDialog(Z3dViewerPlugin plugin) {
        super("3D Viewer", "up", "3D Viewer", null, 150, true);
        renderer3D = new Renderer3D(buildings);
        add(renderer3D);

        MainApplication.getLayerManager().addLayerChangeListener(this);
        MainApplication.getLayerManager().addActiveLayerChangeListener(this);

        if (MainApplication.getLayerManager().getActiveDataLayer() != null) {
            MainApplication.getLayerManager().getActiveDataLayer().getDataSet().addDataSetListener(this);
        }
        updateData();
    }

    private void updateData() {
        buildings.clear();
        OsmDataLayer editLayer = MainApplication.getLayerManager().getEditLayer();
        if (editLayer != null) {
            DataSet dataSet = editLayer.getDataSet();
            for (OsmPrimitive primitive : dataSet.allPrimitives()) {
                if (primitive instanceof Way && primitive.hasKey("building")) {
                    String heightStr = primitive.get("height");
                    double height = 0.0;
                    if (heightStr != null) {
                        try {
                            height = Double.parseDouble(heightStr.split(" ")[0]);
                        } catch (NumberFormatException e) {
                            // Ignore
                        }
                    }
                    if (height > 0) {
                        buildings.add(new Building((Way) primitive, height));
                    }
                }
            }
        }
        renderer3D.repaint();
    }

    // --- LayerChangeListener ---
    @Override
    public void layerAdded(LayerAddEvent e) {
        if (e.getAddedLayer() instanceof OsmDataLayer) {
            ((OsmDataLayer) e.getAddedLayer()).getDataSet().addDataSetListener(this);
        }
        updateData();
    }

    @Override
    public void layerRemoving(LayerRemoveEvent e) {
        if (e.getRemovedLayer() instanceof OsmDataLayer) {
            ((OsmDataLayer) e.getRemovedLayer()).getDataSet().removeDataSetListener(this);
        }
        updateData();
    }
    
    @Override
    public void layerOrderChanged(LayerOrderChangeEvent e) {
        updateData();
    }

    // --- ActiveLayerChangeListener ---
    @Override
    public void activeOrEditLayerChanged(MainLayerManager.ActiveLayerChangeEvent e) {
        Layer oldLayer = e.getPreviousActiveLayer();
        Layer newLayer = e.getSource().getActiveLayer();
        if (oldLayer instanceof OsmDataLayer) {
            ((OsmDataLayer) oldLayer).getDataSet().removeDataSetListener(this);
        }
        if (newLayer instanceof OsmDataLayer) {
            ((OsmDataLayer) newLayer).getDataSet().addDataSetListener(this);
        }
        updateData();
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
        updateData();
    }
}