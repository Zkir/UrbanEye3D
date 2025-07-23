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
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.NavigatableComponent;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.coor.EastNorth;


import java.util.ArrayList;
import java.util.List;

public class Z3dViewerDialog extends ToggleDialog implements DataSetListener, NavigatableComponent.ZoomChangeListener {
    private final Renderer3D renderer3D;
    private final List<RenderableBuildingElement> buildings = new ArrayList<>();
    private OsmDataLayer listenedLayer;

    public Z3dViewerDialog(Z3dViewerPlugin plugin) {
        super("3D Viewer", "up", "3D Viewer", null, 150, true);
        renderer3D = new Renderer3D(buildings);
        add(renderer3D);

        NavigatableComponent.addZoomChangeListener(this);

        updateListenedLayer();
        updateData();
    }

    @Override
    public void destroy() {
        NavigatableComponent.removeZoomChangeListener(this);
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



    private void updateData() {
        buildings.clear();
        OsmDataLayer editLayer = MainApplication.getLayerManager().getEditLayer();
        if (editLayer != null) {
            DataSet dataSet = editLayer.getDataSet();
            EastNorth center = MainApplication.getMap().mapView.getCenter();
            for (OsmPrimitive primitive : dataSet.allPrimitives()) {
                if (primitive instanceof Way && primitive.hasKey("building:part")) {
                    if ( ((Way)primitive).getNodesCount() < 3)
                        continue;

                    String heightStr = primitive.get("height");
                    String minHeightStr = primitive.get("min_height");
                    double height = 0.0;
                    double minHeight = 0.0;
                    if (heightStr != null) {
                        try {
                            height = Double.parseDouble(heightStr.split(" ")[0]);
                        } catch (NumberFormatException e) {
                            // Ignore
                        }
                    }
                    if (minHeightStr != null) {
                        try {
                            minHeight = Double.parseDouble(minHeightStr.split(" ")[0]);
                        } catch (NumberFormatException e) {
                            // Ignore
                        }
                    }
                    if (height > 0) {
                        String color = primitive.get("building:colour");
                        String roofColor = primitive.get("roof:colour");

                        RenderableBuildingElement.Contour contour = new RenderableBuildingElement.Contour((Way) primitive, center );
                        buildings.add(new RenderableBuildingElement(contour, height, minHeight, color, roofColor));
                    }
                }
            }
        }
        renderer3D.repaint();
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

    @Override
    public void zoomChanged() {
        updateData();
    }
}