package ru.zkir.urbaneye3d;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import ru.zkir.urbaneye3d.utils.Point2D;

import java.util.ArrayList;
import java.util.List;

public class Scene {
    //the list of elements that should be rendered.
    //renderable element can be either a building or a building part.
    final List<RenderableBuildingElement> renderableElements = new ArrayList<>();

    public void updateData() {
        renderableElements.clear();

        //preliminary list of building parts. Needed to check buildings
        ArrayList<BBox> buildingParts = new ArrayList<>();

        OsmDataLayer editLayer = MainApplication.getLayerManager().getEditLayer();
        if (editLayer != null) {
            DataSet dataSet = editLayer.getDataSet();

            //We need to do very interesting thing.
            // we need to collect both buildings and building parts.
            //building parts are rendered all
            // buildings -- only if they do not contain building parts.

            for (OsmPrimitive primitive : dataSet.allPrimitives()) {
                if (primitive instanceof Node) {
                    continue;
                }
                if (primitive.hasKey("building:part") && ! primitive.get("building:part").equals("no") ) {
                    buildingParts.add(primitive.getBBox());
                }
            }

            for (OsmPrimitive primitive : dataSet.allPrimitives()) {
                if (primitive instanceof  Node ) {
                    continue;
                }
                boolean include_element = false;
                String source_key="";
                if (primitive.hasKey("building:part") && ! primitive.get("building:part").equals("no") ) {
                    source_key="building:part";
                    include_element = true;

                }

                if (primitive.hasKey("building") && ! primitive.get("building").equals("no") ) {
                    source_key = "building";
                    include_element = true;
                    for (BBox part: buildingParts ){ //TODO: implement more efficient geospatial check using r-tree.
                        if (primitive.getBBox().bounds(part)) {
                            //there are building parts for this building. goodbye!
                            include_element = false;
                            break;
                        }
                    }
                }

                if (include_element){
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

                    String levelsStr = primitive.get("building:levels");
                    double levels = 0.0;
                    if (levelsStr != null) {
                        try {
                            levels = Double.parseDouble(levelsStr.split(" ")[0]);
                        } catch (NumberFormatException e) {
                            // Ignore
                        }
                    }
                    if (source_key.equals("building")){
                        if ((height==0) && (levels==0)) {
                            final double DEFAULT_LEVELS=2;
                            levels = DEFAULT_LEVELS;
                        }
                        if ((height==0) && (levels!=0)) {
                            height= levels*3 + 2;
                        }

                    }

                    // this is a dirty hack.
                    // since we do not have proper support for building:part=roof,
                    // we just set zero height for walls. It's better than nothing obviously.
                    //TODO: for gabled and profiled building:part=roof requires completely different mesher:
                    //walls and bottom are not created, but roof polygons are extruded downwards slightly!
                    if (primitive.get(source_key).equals("roof")){
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
                                    renderableElements.add(new RenderableBuildingElement(primitiveOrigin, new RenderableBuildingElement.Contour(outerRing), height, minHeight, roofHeight, color, roofColor, roofShape, roofDirection, roofOrientation));
                                }
                            } else {
                                // Single outer ring, or multiple outer rings with inner rings, or a Way
                                renderableElements.add(new RenderableBuildingElement(primitiveOrigin, mainContour, height, minHeight, roofHeight, color, roofColor, roofShape, roofDirection, roofOrientation));
                            }
                        }
                    }
                }
            }
        }
    }
}
