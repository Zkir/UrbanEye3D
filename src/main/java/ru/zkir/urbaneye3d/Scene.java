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
        HashMap<OsmPrimitive, Double> buildingHeights = new HashMap<>();


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

            if (primitive.hasKey("building") && ! primitive.get("building").equals("no") && !  getTagStr("building:part", primitive, null).equals("base") ) {
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
                        //TODO: bug: proper spatial check requires original contour, before simplification.
                        if (buildingContour.contains(partContour)) {
                            //there is a building part for this building. goodbye!
                            partParents.put(part, primitive);
                        }
                    }
                }
                buildings.add(primitive);
            }
        }
        ArrayList<OsmPrimitive> allCandidates = new ArrayList<>();
        allCandidates.addAll(buildings);
        allCandidates.addAll(buildingParts);

        for (OsmPrimitive primitive : allCandidates) {

            String source_key="";
            if (primitive.hasKey("building")) {
                source_key = "building";
            } else if (primitive.hasKey("building:part")  ) {
                source_key="building:part";
            } else {
                //UrbanEye3dPlugin.debugMsg("Primitive "+ primitive.getPrimitiveId() + " is neither building nor building part");
                continue;
            }

            if (primitive instanceof Way) {
                if (((Way) primitive).getNodesCount() < 3) continue;
            }
            OsmPrimitive parent = partParents.get(primitive);

            Double height =  getTagD("height", primitive, parent);
            if ( height==null ) {
                height = getTagD("building:height", primitive, parent);
            }
            Double levels = getTagD("building:levels", primitive, parent);
            Double minHeight = getTagD("min_height", primitive, parent);
            Double minLevel = getTagD("building:min_level", primitive, parent);
            Double roofHeight = getTagD("roof:height", primitive, parent);
            Double roofLevels =  getTagD("roof:levels", primitive, parent);
            String roofShape = getTagStr("roof:shape", primitive, parent);
            if (roofShape.isEmpty()){
                roofShape="flat";
            }

            final double DEFAULT_LEVELS_NUMBER=2;
            final double DEFAULT_LEVEL_HEIGHT=3;

            //default values for minHeight. Tags order: min_height, minLevel
            if (minHeight ==null){
                if (minLevel!=null) {
                    minHeight = minLevel * DEFAULT_LEVEL_HEIGHT;
                }else{
                    minHeight=0.0;
                }
            }

            //default value for roof:height
            if (roofHeight == null ) {
                if (roofLevels != null) {
                    roofHeight = roofLevels * DEFAULT_LEVEL_HEIGHT;
                } else {
                    if (roofShape.equals("flat")) {
                        roofHeight = 0.;
                    } else {
                        roofHeight = 1.0 * DEFAULT_LEVEL_HEIGHT;
                    }
                }
            }
            //default values for height. Tags order: height, building:levels+roof:levels, default height or parent height
            if (height==null) {
                if (source_key.equals("building") && levels == null) {
                    levels = DEFAULT_LEVELS_NUMBER;
                }
                if (levels != null) {
                    height = levels * DEFAULT_LEVEL_HEIGHT;
                    height += roofHeight; //roof:levels are not included into levels, so we can do this increment
                }else{
                    //This is a very controversial feature. There are a lot of building parts without height,
                    //which are not rendered in any 3D renderer. So they can look strange.
                    height = buildingHeights.get(parent);
                    if (height==null){
                        //this situation is possible in 2 cases:
                        // * Building part is orphan
                        // * Spatial containment check failed
                        height=0.0;
                        //System.out.println("Height could not be determined for "+ primitive.getPrimitiveId()+ " (" + source_key+")");
                    }
                }
            }

            if(height<minHeight){
                // this it not a defined behaviour, so we can do anything.
                // disappearing buildings are not nice, so let's limit height.
                height=minHeight;
            }

            buildingHeights.put(primitive, height);

            // this is a dirty hack.
            // since we do not have proper support for building:part=roof,
            // we just set zero height for walls. It's better than nothing obviously.
            //TODO: for gabled and profiled building:part=roof requires completely different mesher:
            //walls and bottom are not created, but roof polygons are extruded downwards slightly!
            if (primitive.get(source_key).equals("roof")){
                minHeight = height - roofHeight;
            }

            if (partParents.containsValue(primitive)){
                continue; //we just skip building if it is a parent for some building parts.
            }

            if (height > 0) {
                String color = getTagStr("building:colour", primitive, parent);
                String roofColor = getTagStr("roof:colour", primitive, parent);

                String roofDirection = getTagStr("roof:direction", primitive, parent);
                String roofOrientation = getTagStr("roof:orientation", primitive, parent);

                LatLon primitiveOrigin = primitive.getBBox().getCenter();
                Contour mainContour = primitiveContours.get(primitive);

                if (mainContour != null && !mainContour.outerRings.isEmpty()) {
                    if (primitive instanceof Relation && mainContour.outerRings.size() > 1 && mainContour.innerRings.isEmpty()) {
                        // Split multipolygon with multiple outer rings and no inner rings
                        for (ArrayList<Point2D> outerRing : mainContour.outerRings) {
                            //TODO: this is not exactly correct. primitiveOrigin should be adjusted also (like blender ORIGIN_TO_GEOMETRY)
                            Contour partContour = new Contour(outerRing);
                            partContour.toLocalCoords(primitiveOrigin); //TODO: recalculate origin
                            partContour.removeRedundantNodes();
                            renderableElements.add(new RenderableBuildingElement(primitive.getPrimitiveId(),  primitiveOrigin, partContour, height, minHeight, roofHeight, color, roofColor, roofShape, roofDirection, roofOrientation));
                        }
                    } else {
                        // Single outer ring, or multiple outer rings with inner rings, or a Way
                        mainContour.toLocalCoords(primitiveOrigin);
                        mainContour.removeRedundantNodes();
                        renderableElements.add(new RenderableBuildingElement(primitive.getPrimitiveId(), primitiveOrigin, mainContour, height, minHeight, roofHeight, color, roofColor, roofShape, roofDirection, roofOrientation));
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

    private @NotNull String getTagStr(String key, OsmPrimitive primitive, OsmPrimitive parent ){

        String value=primitive.get(key);
        if ((value==null) && parent!=null && inheritableKeys.contains(key)){
            value=parent.get(key);
        }

        if (value==null){
            value="";
        }
        return value;
    }

    //we need to get a floating point value from an osm tag
    // if tag is missing or cannot be parsed, the return value is null,
    // to let it possible to fallback to defaults.
    private Double getTagD(String key, OsmPrimitive primitive, OsmPrimitive parent ){
        Double result;
        String tag_str = getTagStr(key, primitive, parent);

        if (tag_str.isEmpty()){
            return null;
        }

        try {
            result = Double.parseDouble(tag_str.split(" ")[0]);
        } catch (NumberFormatException e) {
            result = null;
        }
        return result;

    }
}