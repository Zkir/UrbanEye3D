package ru.zkir.urbaneye3d;

import com.drew.lang.annotations.NotNull;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.*;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.Point2D;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class Scene {
    //the list of elements that should be rendered.
    //renderable element can be either a building or a building part.
    final List<RenderableBuildingElement> renderableElements = new ArrayList<>();

    final static List<String> inheritableKeys = Arrays.asList("building:colour", "building:material", "roof:colour", "roof:material");

    public void updateData(DataSet dataSet) {
        renderableElements.clear();
        if (dataSet == null){
            return;
        }

        // A map to cache the expensive-to-create Contour objects for each primitive.
        HashMap<OsmPrimitive, Contour> primitiveContours = new HashMap<>();

        //preliminary list of building parts. Needed to check buildings
        ArrayList<OsmPrimitive> buildings = new ArrayList<>();
        ArrayList<OsmPrimitive> buildingParts = new ArrayList<>();
        HashMap<OsmPrimitive, OsmPrimitive> partParents = new HashMap<>();

        //We need to do very interesting thing.
        // we need to collect both buildings and building parts.
        //building parts are rendered all
        // buildings -- only if they do not contain building parts.

        for (OsmPrimitive primitive : dataSet.allPrimitives()) {
            if (primitive instanceof Node || !isPrimitiveComplete(primitive)) {
                continue;
            }

            if (primitive.hasKey("building:part") && ! primitive.get("building:part").equals("no") ) {
                buildingParts.add(primitive);
                // Create and cache the contour for the building part.
                primitiveContours.put(primitive, new Contour(primitive, null));
            }
        }

        for (OsmPrimitive primitive : dataSet.allPrimitives()) {
            if (primitive instanceof  Node || !isPrimitiveComplete(primitive)) {
                continue;
            }

            if (primitive.hasKey("building") && ! primitive.get("building").equals("no") && !  getTag("building:part", primitive, null).equals("base") ) {
                boolean include_element = true;
                // Create and cache the contour for the building, if not already present.
                if (!primitiveContours.containsKey(primitive)) {
                    primitiveContours.put(primitive, new Contour(primitive, null )); //primitive.getBBox().getCenter()
                }
                Contour buildingContour = primitiveContours.get(primitive);

                for (OsmPrimitive part: buildingParts ){
                    // First, a quick BBox check. It is much cheaper and will filter out most of the candidates.
                    if (primitive.getBBox().bounds(part.getBBox())) {
                        // If BBoxes intersect, then perform a more expensive contour check.
                        Contour partContour = primitiveContours.get(part);
                        if (buildingContour.contains(partContour)) {
                            //there is a building part for this building. goodbye!
                            include_element = false;
                            partParents.put(part, primitive);
                        }
                    }
                }
                if (include_element) {
                    buildings.add(primitive);
                }
            }
        }
        ArrayList<OsmPrimitive> allCandidates = new ArrayList<>();
        allCandidates.addAll(buildings);
        allCandidates.addAll(buildingParts);

        for (OsmPrimitive primitive : allCandidates) {

            String source_key="";
            if (primitive.hasKey("building:part")  ) {
                source_key="building:part";
            }

            if (primitive.hasKey("building")) {
                source_key = "building";
            }

            if (primitive instanceof Way) {
                if (((Way) primitive).getNodesCount() < 3) continue;
            }
            OsmPrimitive parent = partParents.get(primitive);

            String heightStr =  getTag("height", primitive, parent);
            if ( heightStr.isEmpty()) {
                heightStr = getTag("building:height", primitive, parent);
            }

            String minHeightStr = getTag("min_height", primitive, parent);
            String roofHeightStr = getTag("roof:height", primitive, parent);
            double height = 0.0;
            double minHeight = 0.0;
            double roofHeight = 0.0;

            try {
                height = Double.parseDouble(heightStr.split(" ")[0]);
            } catch (NumberFormatException e) {
                // Ignore
            }

            try {
                minHeight = Double.parseDouble(minHeightStr.split(" ")[0]);
            } catch (NumberFormatException e) {
                // Ignore
            }

            try {
                roofHeight = Double.parseDouble(roofHeightStr.split(" ")[0]);
            } catch (NumberFormatException e) {
                // Ignore
            }

            String levelsStr = getTag("building:levels", primitive, parent);
            double levels = 0.0;

            try {
                levels = Double.parseDouble(levelsStr.split(" ")[0]);
            } catch (NumberFormatException e) {
                // Ignore
            }

            String roofShape = getTag("roof:shape", primitive, parent);
            final double DEFAULT_LEVELS_NUMBER=2;
            final double DEFAULT_LEVEL_HEIGHT=3;

            if (height==0) {
                if (source_key.equals("building")) {
                    if (levels == 0) {
                        levels = DEFAULT_LEVELS_NUMBER;
                    }
                    height = levels * DEFAULT_LEVEL_HEIGHT + 2; //some bonus for roof and basement!
                }
                else{
                    //for building parts there is no default height.
                    //if neither height nor levels are specified, building part is not rendered.
                    height = levels * DEFAULT_LEVEL_HEIGHT; // no bonus!
                }
                if (!roofShape.equals("flat")){
                    height += 3 ; //levels tag does not include roof
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
                String color = getTag("building:colour", primitive, parent);
                String roofColor = getTag("roof:colour", primitive, parent);

                String roofDirection = getTag("roof:direction", primitive, parent);
                String roofOrientation = getTag("roof:orientation", primitive, parent);

                Contour mainContour = primitiveContours.get(primitive);
                //LatLon primitiveOrigin = primitive.getBBox().getCenter();
                LatLon primitiveOrigin = mainContour.getCentroid();
                System.out.println(primitiveOrigin.lat()+" "+primitiveOrigin.lon());


                if (mainContour != null && !mainContour.outerRings.isEmpty()) {
                    if (primitive instanceof Relation && mainContour.outerRings.size() > 1 && mainContour.innerRings.isEmpty()) {
                        // Split multipolygon with multiple outer rings and no inner rings
                        for (ArrayList<Point2D> outerRing : mainContour.outerRings) {
                            //TODO: this is not exactly correct. primitiveOrigin should be adjusted also (like blender ORIGIN_TO_GEOMETRY)
                            Contour partContour = new Contour(outerRing);
                            partContour.toLocalCoords(primitiveOrigin); //TODO: recalculate origin
                            renderableElements.add(new RenderableBuildingElement(primitiveOrigin, partContour, height, minHeight, roofHeight, color, roofColor, roofShape, roofDirection, roofOrientation));
                        }
                    } else {
                        // Single outer ring, or multiple outer rings with inner rings, or a Way
                        mainContour.toLocalCoords(primitiveOrigin);
                        renderableElements.add(new RenderableBuildingElement(primitiveOrigin, mainContour, height, minHeight, roofHeight, color, roofColor, roofShape, roofDirection, roofOrientation));
                    }
                }
            }
        }
    }

    private boolean isPrimitiveComplete(OsmPrimitive primitive) {
        boolean isComplete=true;
        if (primitive instanceof Relation){
            Relation rel = (Relation)primitive;
            if (!rel.getIncompleteMembers().isEmpty()) {
                isComplete=false;
            }
        }else if (primitive instanceof Way){
            Way way = (Way) primitive;
            if(!way.isClosed()){
                isComplete=false;
            }

        }

        return isComplete;
    }

    private @NotNull String getTag(String key, OsmPrimitive primitive, OsmPrimitive parent ){

        String value=primitive.get(key);
        if ((value==null) && parent!=null && inheritableKeys.contains(key)){
            value=parent.get(key);
        }

        if (value==null){
            value="";
        }
        return value;
    }
}