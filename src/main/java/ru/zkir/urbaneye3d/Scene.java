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

import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_ROOF_THICKNESS;

public class Scene {
    //default values
    final double DEFAULT_LEVELS_NUMBER=2;
    final double DEFAULT_LEVEL_HEIGHT=3;
    final boolean INHERIT_HEIGHT_FROM_PARENT=false;
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

            if (primitive.hasKey("building") && ! primitive.get("building").equals("no") && !  getTagStr("building:part", primitive, "").equals("base") ) {
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
            Double stepHeight = getTagD("step:height", primitive, parent);

            Double layer = getTagD("layer", primitive, 0);
            String location = getTagStr("location", primitive, "");

            if ((layer<0) || (location.equals("underground"))){
                // we ignore such underground buildings/parts for now.
                continue;
            }

            if (roofShape.isEmpty()){
                roofShape="flat";
            }
            if (roofShape.equals("cone")){
                roofShape="pyramidal";
            }

            if (roofShape.equals("equal_hipped")){
                roofShape="hipped";
            }

            if (roofShape.equals("crosspitched")) {
                roofShape = "cross_gabled";
            }

            if (roofShape.equals("skillion") && getTagStr(source_key, primitive, "").equals("steps")) {
                roofShape="steps";
            }

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
                    if (roofLevels==0 && !roofShape.equals("flat")){
                        // roof:levels=0 does not mean that the roof is flat,
                        // especially in case it is explicitly specified that it does not.
                        // it rather means that roof does not constitute habitable level
                        roofHeight =  DEFAULT_LEVEL_HEIGHT * 0.5 ;
                    }else {
                        roofHeight = roofLevels * DEFAULT_LEVEL_HEIGHT;
                    }
                } else {
                    if (roofShape.equals("flat")) {
                        roofHeight = 0.;
                    } else {
                        //here we infer roof height from it's shape, because, say, gabled roof usually have some height.
                        //F4 map also does that.
                        roofHeight = 1.0 * DEFAULT_LEVEL_HEIGHT;
                    }
                }
            }

            //default values for height. Tags order: height, building:levels+roof:levels, default height or parent height
            if (height==null) {
                if(INHERIT_HEIGHT_FROM_PARENT) {
                    if (source_key.equals("building") && levels == null) {
                        levels = DEFAULT_LEVELS_NUMBER;
                    }
                    if (levels != null) {
                        height = levels * DEFAULT_LEVEL_HEIGHT;
                        height += roofHeight; //roof:levels are not included into levels, so we can do this increment
                    } else {
                        //This is a very controversial feature. There are a lot of building parts without height,
                        //which are not rendered in any 3D renderer. So they can look strange.
                        height = buildingHeights.get(parent);
                        if (height == null) {
                            //this situation is possible in 2 cases:
                            // * Building part is orphan
                            // * Spatial containment check failed
                            height = 0.0;
                            //System.out.println("Height could not be determined for "+ primitive.getPrimitiveId()+ " (" + source_key+")");
                        }
                    }
                }else{
                    //buildings and buildings parts are processed uniformly:
                    //Tags order: height, building:levels, default_level.
                    if (levels == null) {
                        levels = DEFAULT_LEVELS_NUMBER;
                    }
                    height = levels * DEFAULT_LEVEL_HEIGHT;
                    height += roofHeight; //roof:levels are not included into levels, so we can do this increment
                }
            }

            if(height<minHeight){
                // this it not a defined behaviour, so we can do anything.
                // disappearing buildings are not nice, so let's limit height.
                height=minHeight;
            }

            buildingHeights.put(primitive, height);

            // we have the proper support for building:part=roof now,
            // in such case walls and bottom are extruded downwards, for certain roof shapes
            //TODO: encapsulate logic of minHeight adjustment into RenderableBuildingElement() constructor
            if (primitive.get(source_key).equals("roof")){
                minHeight = height - roofHeight - DEFAULT_ROOF_THICKNESS;
            }

            if (partParents.containsValue(primitive)){
                continue; //we just skip building if it is a parent for some building parts.
            }

            if (height > 0) {
                String wallColor = getTagStr("building:colour", primitive, parent);
                String roofColor = getTagStr("roof:colour", primitive, parent);

                String roofDirection = getTagStr("roof:direction", primitive, parent);
                String roofOrientation = getTagStr("roof:orientation", primitive, parent);

                //TODO: probably we need to create material, and inherit diffuse colour from xxx:colour tag
                var roofMaterial = Materials.fromString(getTagStr("roof:material",     primitive, parent));
                var wallMaterial = Materials.fromString(getTagStr("building:material", primitive, parent));

                // get default values if material is specified
                if (roofMaterial!=null && roofColor.isEmpty()){
                    roofColor = roofMaterial.defaultColour;
                }
                if (wallMaterial!=null && wallColor.isEmpty()){
                    wallColor = wallMaterial.defaultColour;
                }

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
                            renderableElements.add(new RenderableBuildingElement(primitive.getPrimitiveId(), primitiveOrigin, partContour, height, minHeight, roofHeight, wallColor, roofColor, roofShape, roofDirection, roofOrientation, primitive.get(source_key), stepHeight));
                        }
                    } else {
                        // Single outer ring, or multiple outer rings with inner rings, or a Way
                        mainContour.toLocalCoords(primitiveOrigin);
                        mainContour.removeRedundantNodes();
                        renderableElements.add(new RenderableBuildingElement(primitive.getPrimitiveId(), primitiveOrigin, mainContour, height, minHeight, roofHeight, wallColor, roofColor, roofShape, roofDirection, roofOrientation, primitive.get(source_key), stepHeight));
                    }
                }
            }
        }

        for (OsmPrimitive primitive : dataSet.allPrimitives()) {
            if (primitive instanceof Way && primitive.hasKey("barrier")) {
                String barrierType = primitive.get("barrier");
                double width;
                double height;
                String color =  getTagStr("colour", primitive, "");
                //unlike buildings, for barrier just colour=* and material=* tags are used.
                var material = Materials.fromString(getTagStr("material", primitive, (String) null));

                // get default values if material is specified
                if (material!=null && color.isEmpty()){
                    color = material.defaultColour;
                }
                if (color.isEmpty()){
                    if(barrierType.equals("hedge")){
                        color = "#308030";
                    }else {
                        color = "lightgray";
                    }
                }

                switch (barrierType) {
                    case "wall":
                        width = getTagD("width", primitive, 0.25);
                        height = getTagD("height",primitive, 1.5);
                        break;
                    case "hedge":
                        width = getTagD("width", primitive, 0.5);
                        height = getTagD("height", primitive, 1.5);
                        break;
                    case "fence":
                        width = getTagD("width", primitive, 0.1);
                        height = getTagD("height", primitive,  1.5);
                        break;
                    case "city_wall":
                        width = getTagD("width", primitive, 1.0);
                        height = getTagD("height", primitive, 5.0);
                        break;
                    default:
                        continue; // Skip unsupported barrier types
                }
                Double minHeight = getTagD("min_height", primitive, 0);


                Contour contour;
                LatLon origin = primitive.getBBox().getCenter();
                if ("yes".equals(primitive.get("area"))) {
                    contour = new Contour(primitive, null);
                    contour.toLocalCoords(origin);
                } else {
                    if (width > 0) {
                        contour = new Contour((Way)primitive, width, origin);
                    } else {
                        continue;
                    }
                }

                if (contour != null && !contour.outerRings.isEmpty()) {
                    contour.removeRedundantNodes();
                    renderableElements.add(new RenderableBuildingElement(primitive.getPrimitiveId(), origin, contour, height, minHeight, 0, color, color, "flat", "", "", null, null));
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
    private @NotNull String getTagStr(String key, OsmPrimitive primitive, String defaultValue ){
        String value = primitive.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value;
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

    private Double getTagD(String key, OsmPrimitive primitive, double defaultValue) {
        String value = primitive.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.split(" ")[0]);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
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