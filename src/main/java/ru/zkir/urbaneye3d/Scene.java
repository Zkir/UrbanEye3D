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

    /** ground plane represents earth surface with projected satellite image.
     *  Currently, it's separated from other scene objects    */
    final GroundPlane groundPlane = new GroundPlane();

    public GroundPlane getGroundPlane() {
        return groundPlane;
    }

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

            if (primitive.hasKey("building") && ! primitive.get("building").equals("no") && !  RenderableBuildingElement.getTagStr("building:part", primitive, "").equals("base") ) {
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

            if (primitive.hasKey("building") || primitive.hasKey("building:part") ) {

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

                        var element = RenderableBuildingElement.createBuildingOrPart(primitive.getPrimitiveId(), primitiveOrigin, partContour, primitive.getInterestingTags(), parentTags);
                        if (element != null) {
                            renderableElements.add(element);
                        }
                    }
                } else {
                    // Single outer ring, or multiple outer rings with inner rings, or a Way
                    var element = RenderableBuildingElement.createBuildingOrPart(primitive.getPrimitiveId(), primitiveOrigin, mainContour, primitive.getInterestingTags(), parentTags);
                    if (element != null) {
                        renderableElements.add(element);
                    }
                }
            }
        }

        for (OsmPrimitive primitive : dataSet.allPrimitives()) {
            if (primitive instanceof Way && primitive.hasKey("barrier")) {
                var element = RenderableBuildingElement.createBarrier(primitive);
                if (element != null){
                    renderableElements.add(element);
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
}