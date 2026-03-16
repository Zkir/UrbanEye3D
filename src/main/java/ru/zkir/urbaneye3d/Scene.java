package ru.zkir.urbaneye3d;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.gui.MainApplication;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.Point2D;

import java.util.*;


public class Scene {
    /** The list of scene "elements" that should be rendered.
    * renderable element can be either a building or a building part. */
    final List<RenderableBuildingElement> renderableElements = new ArrayList<>();

    public void updateSelection(Collection<PrimitiveId> selectedPrimitivesIds) {
        for (RenderableBuildingElement element : renderableElements) {
            element.isSelected = selectedPrimitivesIds.contains(element.primitiveId);
        }
    }

    /** ground plane represents earth surface with projected satellite image.
     *  Currently, it's separated from other scene objects    */
    final GroundPlane groundPlane = new GroundPlane();

    public GroundPlane getGroundPlane() {
        return groundPlane;
    }

    /**
     * This method analyzes dataset and creates 3D objects
     * It also updates ground plane tiles, which may be or may be not dataset dependent,
     * depending on presence of imagery layer
     */
    public void updateData(DataSet dataSet, ImageryInfo tmsLayer) {
        renderableElements.clear();
        if (dataSet == null){
            return;
        }

        if (MainApplication.isDisplayingMapView()) {
            //TODO: it's a dirty hack.
            // If the main map window is not visible, we cannot neither obtain map center nor active satellite layer
            var visibleAreaCenter = Renderer3D.getCameraPosition();
            this.groundPlane.update(visibleAreaCenter, tmsLayer);
        }

        // A map to cache the expensive-to-create Contour objects for each primitive.
        HashMap<OsmPrimitive, Contour> primitiveContours = new HashMap<>();

        //preliminary list of building parts. Needed to check buildings
        ArrayList<OsmPrimitive> buildings = new ArrayList<>();
        ArrayList<OsmPrimitive> buildingParts = new ArrayList<>();
        HashMap<OsmPrimitive, OsmPrimitive> partParents = new HashMap<>();
        ArrayList<OsmPrimitive> manmades = new ArrayList<>();

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
                primitiveContours.put(primitive, new Contour(primitive));
            }
        }

        for (OsmPrimitive primitive : dataSet.allPrimitives()) {
            if (primitive instanceof  Node || !isPrimitiveComplete(primitive)) {
                continue;
            }

            if (primitive.hasKey("building") && ! primitive.get("building").equals("no") && !(primitive.hasKey("building:part") && ! primitive.get("building:part").equals("no"))   ) {
                // Create and cache the contour for the building, if not already present.
                if (!primitiveContours.containsKey(primitive)) {
                    primitiveContours.put(primitive, new Contour(primitive));
                }
                Contour buildingContour = primitiveContours.get(primitive);

                var containedParts = findContainedParts(primitive, buildingContour, buildingParts, primitiveContours);
                for(OsmPrimitive part: containedParts){
                    //for buildings, we need to support parent-child relationship with parts.
                    partParents.put(part, primitive);
                }

                buildings.add(primitive); //building which have parts are suppressed later.

            }else if(primitive.hasKey("man_made")){
                Contour manmadeContour = new Contour(primitive);
                primitiveContours.put(primitive, manmadeContour);
                if (findContainedParts(primitive, manmadeContour, buildingParts, primitiveContours).isEmpty() ) {
                    // for man-mades logic is different a bit.
                    //man-made cannot be parent and is just suppressed, if there are parts inside (see gh #36)
                    manmades.add(primitive);
                }
            }
        }
        ArrayList<OsmPrimitive> allCandidates = new ArrayList<>();
        allCandidates.addAll(buildings);
        allCandidates.addAll(buildingParts);

        for (OsmPrimitive primitive : allCandidates) {
            if (partParents.containsValue(primitive)){
                continue; //we just skip building if it is a parent for some building parts.
            }

            OsmPrimitive parent = partParents.get(primitive);
            Contour mainContour = primitiveContours.get(primitive);
            LatLon primitiveOrigin = primitive.getBBox().getCenter();
            Map<String, String> parentTags=null;
            if (parent!=null){
                parentTags=parent.getInterestingTags();
            }

            if (primitive instanceof Relation && mainContour.outerRings.size() > 1 && mainContour.innerRings.isEmpty()) {
                // Split multipolygon with multiple outer rings and no inner rings
                for (ArrayList<Point2D> outerRing : mainContour.outerRings) {
                    //TODO: this is not exactly correct. primitiveOrigin should be adjusted also (like blender ORIGIN_TO_GEOMETRY)
                    Contour partContour = new Contour(outerRing, mainContour.mode);

                    var element = RenderableBuildingElement.createBuildingOrPart(primitive, primitiveOrigin, partContour, primitive.getInterestingTags(), parentTags);
                    if (element != null) {
                        renderableElements.add(element);
                        element.isSelected = primitive.isSelected();
                    }
                }
            } else {
                // Single outer ring, or multiple outer rings with inner rings, or a Way
                var element = RenderableBuildingElement.createBuildingOrPart(primitive, primitiveOrigin, mainContour, primitive.getInterestingTags(), parentTags);
                if (element != null) {
                    renderableElements.add(element);
                    element.isSelected = primitive.isSelected();
                }
            }
        }

        /*
        * Barriers
        */
        for (OsmPrimitive primitive : dataSet.allPrimitives()) {
            if (isBuildingOrPart(primitive)){
                continue;
            }
            if (primitive instanceof Way && primitive.hasKey("barrier")) {
                var element = RenderableBuildingElement.createBarrier(primitive);
                if (element != null){
                    renderableElements.add(element);
                }
            }
        }

        /*
         * Experimental feature: man_made.
         */
        for (OsmPrimitive primitive : manmades) {
            if (!isPrimitiveComplete(primitive)){
                continue;
            }

            Contour contour = primitiveContours.get(primitive);

            var element = RenderableBuildingElement.createManMade(primitive, contour);
            if (element != null){
                renderableElements.add(element);
            }

        }
    }

    private List<OsmPrimitive> findContainedParts (OsmPrimitive primitive, Contour buildingContour, List<OsmPrimitive> buildingParts, Map<OsmPrimitive, Contour> primitiveContours) {
        List<OsmPrimitive> containedParts = new ArrayList<>();
        for (OsmPrimitive part: buildingParts ){
            // First, a quick BBox check. It is much cheaper and will filter out most of the candidates.
            if (primitive.getBBox().bounds(part.getBBox())) {
                // If BBoxes intersect, then perform a more expensive contour check.
                Contour partContour = primitiveContours.get(part);
                // spatial check requires original contour, before simplification.
                if (buildingContour.contains(partContour)) {
                    //there is a building part for this building. goodbye!
                    containedParts.add(part);
                }
            }
        }
        return containedParts;
    }

    /**
     * primitive can be considered building or building part in case it has appropriate tags
     * or is a member of a Building relation
     * This actually means that man_made=something can be a parent for parts even without building tag,
     * if it has the *outline* role.
     */
    private boolean isBuildingOrPart(OsmPrimitive primitive){
        return ( (primitive.hasKey("building") && !primitive.get("building").equals("no")) ||
                 (primitive.hasKey("building:part") && !primitive.get("building:part").equals("no")) ||
                  isBuildingRelationMember(primitive));
    }

    private boolean isBuildingRelationMember(OsmPrimitive primitive) {
        boolean member_of_building_relation = false;
        for (var r: primitive.getReferrers()) {
            if( "building".equals(r.get("type"))){
                member_of_building_relation =true;
            }
        }
        return member_of_building_relation;
    }


    private boolean isPrimitiveComplete(OsmPrimitive primitive) {
        if(primitive.isDeleted()){
            //sometimes a deleted relation appears in the list of active objects
            //see github issue #37
            return false;
        }
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
}