package ru.zkir.josm.plugins.z3dviewer;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
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
import org.openstreetmap.josm.gui.layer.LayerManager;
import org.openstreetmap.josm.gui.layer.MainLayerManager;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.NavigatableComponent;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.coor.LatLon;
import ru.zkir.josm.plugins.z3dviewer.utils.Point2D;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class Z3dViewerDialog extends ToggleDialog
                             implements DataSetListener, NavigatableComponent.ZoomChangeListener,
                                        LayerManager.LayerChangeListener, MainLayerManager.ActiveLayerChangeListener
{
    private final Renderer3D renderer3D;
    private final List<RenderableBuildingElement> buildings = new ArrayList<>();
    private OsmDataLayer listenedLayer;

    public Z3dViewerDialog(Z3dViewerPlugin plugin) {
        super("3D Viewer", "z3dviewer", "3D Viewer", null, 150, true); //path for icon not required, it is picked up by JOSM automatically.
        renderer3D = new Renderer3D(buildings);
        add(renderer3D, BorderLayout.CENTER);

        // Register the action so the shortcut works, but don't create a menu item
        new ToggleWireframeAction(renderer3D);

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


    private void updateData() {
        buildings.clear();
        OsmDataLayer editLayer = MainApplication.getLayerManager().getEditLayer();
        if (editLayer != null) {
            DataSet dataSet = editLayer.getDataSet();
            LatLon center = MainApplication.getMap().mapView.getProjection().eastNorth2latlon(MainApplication.getMap().mapView.getCenter());
            for (OsmPrimitive primitive : dataSet.allPrimitives()) {
                if (primitive instanceof  Node ) {
                    continue;
                }

                if (primitive.hasKey("building:part") && ! primitive.get("building:part").equals("no") ) {
                    if (primitive instanceof Way) {
                        if (((Way) primitive).getNodesCount() < 3) continue;
                    }

                    String heightStr = primitive.get("height");
                    String minHeightStr = primitive.get("min_height");
                    String roofHeightStr = primitive.get("roof:height");
                    double height = 0.0;
                    double minHeight = 0.0;
                    double roofHeight = 0.0;
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
                    if (roofHeightStr != null) {
                        try {
                            roofHeight = Double.parseDouble(roofHeightStr.split(" ")[0]);
                        } catch (NumberFormatException e) {
                            // Ignore
                        }
                    }

                    // this is a dirty hack.
                    // since we do not have proper support for building:part=roof,
                    // we just set zero height for walls. It's better than nothing obviously.
                    //TODO: for gabled and profiled building:part=roof requires completely different mesher:
                    //walls and bottom are not created, but roof polygons are extruded downwards slightly!
                    if (primitive.get("building:part").equals("roof")){
                        minHeight = height - roofHeight;
                    }

                    if (height > 0) {
                        String color = primitive.get("building:colour");
                        String roofColor = primitive.get("roof:colour");
                        String roofShape = primitive.get("roof:shape");
                        String roofDirection = primitive.get("roof:direction");
                        String roofOrientation = primitive.get("roof:orientation");

                        LatLon primitiveOrigin = primitive.getBBox().getCenter();
                        RenderableBuildingElement.Contour mainContour = null;

                        if (primitive instanceof Way) {
                            mainContour = new RenderableBuildingElement.Contour((Way) primitive, primitiveOrigin);
                        } else if (primitive instanceof Relation) {
                            mainContour = new RenderableBuildingElement.Contour((Relation) primitive, primitiveOrigin);
                        }

                        if (mainContour != null && !mainContour.outerRings.isEmpty()) {
                            if (primitive instanceof Relation && mainContour.outerRings.size() > 1 && mainContour.innerRings.isEmpty()) {
                                // Split multipolygon with multiple outer rings and no inner rings
                                for (ArrayList<Point2D> outerRing : mainContour.outerRings) {
                                    //TODO: this is not exactly correct. primitiveOrigin should be adjusted also (like blender ORIGIN_TO_GEOMETRY)
                                    //to make things worse, we use local coords already, so if origin is moved, coordinates of the outerRing should be recalculated.
                                    buildings.add(new RenderableBuildingElement(primitiveOrigin, new RenderableBuildingElement.Contour(outerRing), height, minHeight, roofHeight, color, roofColor, roofShape, roofDirection, roofOrientation));
                                }
                            } else {
                                // Single outer ring, or multiple outer rings with inner rings, or a Way
                                buildings.add(new RenderableBuildingElement(primitiveOrigin, mainContour, height, minHeight, roofHeight, color, roofColor, roofShape, roofDirection, roofOrientation));
                            }
                        }
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