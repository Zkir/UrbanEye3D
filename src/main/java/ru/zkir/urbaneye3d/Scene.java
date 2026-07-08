package ru.zkir.urbaneye3d;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.spi.preferences.Config;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

import ru.zkir.urbaneye3d.utils.*;

import java.util.*;
import java.util.stream.Collectors;

import java.util.concurrent.CopyOnWriteArrayList;

import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_TREE_HEIGHT;


public class Scene {
    /** The list of scene "elements" that should be rendered.
    * renderable element can be either a building or a building part. */
    final List<RenderableElement> renderableElements = new CopyOnWriteArrayList<>();
    private int objectCount = 0;
    private int faceCount = 0;

    public int getObjectCount() {
        return objectCount;
    }

    public int getFaceCount() {
        return faceCount;
    }

    public static class SceneUpdate {
        final List<RenderableElement> elementsToRemove;
        final List<RenderableElement> elementsToAdd;
        final Set<PrimitiveId> idsToRemove;
        final boolean isFullUpdate;

        public SceneUpdate(List<RenderableElement> elementsToRemove, List<RenderableElement> elementsToAdd, Set<PrimitiveId> idsToRemove, boolean isFullUpdate) {
            this.elementsToRemove = elementsToRemove;
            this.elementsToAdd = elementsToAdd;
            this.idsToRemove = idsToRemove;
            this.isFullUpdate = isFullUpdate;
        }
    }

    public void updateSelection(Collection<PrimitiveId> selectedPrimitivesIds) {
        for (RenderableElement element : renderableElements) {
            element.isSelected = selectedPrimitivesIds.contains(element.primitiveId);
        }
    }

    /** ground plane represents earth surface with projected satellite image.
     *  Currently, it's separated from other scene objects    */
    final GroundPlane groundPlane = new GroundPlane();

    public GroundPlane getGroundPlane() {
        return groundPlane;
    }

    public void applyUpdate(SceneUpdate update) {
        if (update == null) {
            return;
        }

        if (update.isFullUpdate) {
            renderableElements.clear();
            faceCount = 0;
        } else {
            for (RenderableElement element : update.elementsToRemove) {
                renderableElements.remove(element);
                if (element.getMesh() != null && element.getMesh().faces != null) {
                    faceCount -= element.getMesh().faces.size();
                }
            }
            // Additional safety removal by ID
            if (update.idsToRemove != null) {
                renderableElements.removeIf(element -> {
                    if (update.idsToRemove.contains(element.primitiveId)) {
                        if (element.getMesh() != null && element.getMesh().faces != null) {
                            faceCount -= element.getMesh().faces.size();
                        }
                        return true;
                    }
                    return false;
                });
            }
        }

        renderableElements.addAll(update.elementsToAdd);
        objectCount = renderableElements.size();
        for (RenderableElement element : update.elementsToAdd) {
            if (element.getMesh() != null && element.getMesh().faces != null) {
                faceCount += element.getMesh().faces.size();
            }
        }
    }

    public SceneUpdate calculateUpdate(DataSet dataSet, org.openstreetmap.josm.data.Bounds dirtyBounds, Set<PrimitiveId> modifiedIds) {
        long startTime = System.currentTimeMillis();
        List<RenderableElement> newElements = new ArrayList<>();
        List<RenderableElement> elementsToRemove = new ArrayList<>();
        boolean isFullUpdate = (dirtyBounds == null);

        if (dataSet == null) {
            return new SceneUpdate(elementsToRemove, newElements, modifiedIds, isFullUpdate);
        }

        if (!isFullUpdate) {
            UrbanEye3dPlugin.debugMsg(String.format("Partial update. Dirty bounds: %s", dirtyBounds.toString()));
            for (RenderableElement element : renderableElements) {
                OsmPrimitive primitive = dataSet.getPrimitiveById(element.primitiveId);
                // Remove if primitive is gone, deleted, intersects dirty bounds, OR is explicitly in modifiedIds
                if (primitive == null || primitive.isDeleted() || 
                    (modifiedIds != null && modifiedIds.contains(element.primitiveId)) ||
                    (primitive.getBBox() != null && primitive.getBBox().intersects(dirtyBounds))) {
                    elementsToRemove.add(element);
                }
            }
        } else {
            UrbanEye3dPlugin.debugMsg("Full scene update.");
        }

        // A map to cache the expensive-to-create Contour objects for each primitive.
        HashMap<OsmPrimitive, Contour> primitiveContours = new HashMap<>();

        //preliminary list of building parts. Needed to check buildings
        ArrayList<OsmPrimitive> buildings = new ArrayList<>();
        ArrayList<OsmPrimitive> buildingParts = new ArrayList<>();
        HashMap<OsmPrimitive, OsmPrimitive> partParents = new HashMap<>();
        ArrayList<OsmPrimitive> manmades = new ArrayList<>();

        // Collect primitives to process
        Collection<OsmPrimitive> primitivesToProcess;
        // JOSM's DataSet is not thread-safe, and allPrimitives() returns a live view or 
        // a collection that can be modified while we iterate in the background thread.
        // We must synchronize and copy to avoid ConcurrentModificationException.
        Collection<OsmPrimitive> allPrimitivesCopy;
        synchronized (dataSet) {
            allPrimitivesCopy = new ArrayList<>(dataSet.allPrimitives());
        }

        if (isFullUpdate) {
            primitivesToProcess = allPrimitivesCopy;
        } else {
            // Optimization: Filter primitives by dirty bounds once
            primitivesToProcess = new ArrayList<>();
            for (OsmPrimitive p : allPrimitivesCopy) {
                if (p.getBBox() != null && p.getBBox().intersects(dirtyBounds)) {
                    primitivesToProcess.add(p);
                }
            }
        }
        int totalToProcess = primitivesToProcess.size();

        //building parts are rendered all
        // buildings -- only if they do not contain building parts.

        for (OsmPrimitive primitive : primitivesToProcess) {
            if (primitive instanceof Node || !isPrimitiveComplete(primitive)) {
                continue;
            }

            if (primitive.hasKey("building:part") && !primitive.get("building:part").equals("no")) {
                buildingParts.add(primitive);
                // Create and cache the contour for the building part.
                primitiveContours.put(primitive, new Contour(primitive));
            }
        }

        for (OsmPrimitive primitive : primitivesToProcess) {
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
            LatLon primitiveOrigin = (primitive.getBBox() != null) ? primitive.getBBox().getCenter() : null;
            if (primitiveOrigin == null) continue;

            Map<String, String> parentTags=null;
            if (parent!=null){
                parentTags=parent.getInterestingTags();
            }

            if (primitive instanceof Relation && mainContour.outerRings.size() > 1 && mainContour.innerRings.isEmpty()) {
                // Split multipolygon with multiple outer rings and no inner rings
                for (ArrayList<Point2D> outerRing : mainContour.outerRings) {
                    //TODO: this is not exactly correct. primitiveOrigin should be adjusted also (like blender ORIGIN_TO_GEOMETRY)
                    Contour partContour = new Contour(outerRing, mainContour.mode);

                    var element = RenderableElement.createBuildingOrPart(primitive, primitiveOrigin, partContour, primitive.getInterestingTags(), parentTags);
                    if (element != null) {
                        newElements.add(element);
                        element.isSelected = primitive.isSelected();
                    }
                }
            } else {
                // Single outer ring, or multiple outer rings with inner rings, or a Way
                var element = RenderableElement.createBuildingOrPart(primitive, primitiveOrigin, mainContour, primitive.getInterestingTags(), parentTags);
                if (element != null) {
                    newElements.add(element);
                    element.isSelected = primitive.isSelected();
                }
            }
        }

        /*
        * Barriers
        */
        for (OsmPrimitive primitive : primitivesToProcess) {
            if (isBuildingOrPart(primitive)){
                continue;
            }
            if (primitive instanceof Way && primitive.hasKey("barrier")) {
                var element = RenderableElement.createBarrier(primitive);
                if (element != null){
                    newElements.add(element);
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

            var element = RenderableElement.createManMade(primitive, contour);
            if (element != null){
                newElements.add(element);
            }

        }

        // Some elements like ad columns might have been already rendered by one of the other loops. Be careful to not double-add them. 
        var alreadyRenderedPrimitiveIds = new HashSet<>(newElements.stream().map(e -> e.primitiveId).collect(Collectors.toCollection(HashSet::new)));

        /*
         * Trees and other objects.
         */
        for (OsmPrimitive p : primitivesToProcess) {
            if (!(p instanceof Node)) continue;
            Node node = (Node) p;
            if (node.hasTag("natural", "tree")) {
                var element = RenderableElement.createTree(node);
                if (element != null){
                    newElements.add(element);
                }
            }
            
            if (node.hasTag("advertising", "column")) {
                if (alreadyRenderedPrimitiveIds.contains(node.getPrimitiveId())) continue;
                var element = RenderableElement.createAdColumn(node, node.getCoor(), node.getInterestingTags(), new Random(node.getId()));
                if (element != null) newElements.add(element);
            }
        }

        /*
         * Forests
         */
        int forestDensitySetting = Config.getPref().getInt("urbaneye3d.forest-density", 50);
        if (forestDensitySetting > 0) {
            double densityRatio = forestDensitySetting / 100.0;
            // The user requested R = DEFAULT_TREE_HEIGHT / 3.0 at full density.
            // We scale R inversely with the square root of density to maintain uniform coverage.
            double minDist = (DEFAULT_TREE_HEIGHT / 3.0) / Math.sqrt(densityRatio);

            for (OsmPrimitive primitive : primitivesToProcess) {
                if (primitive instanceof Node || !isPrimitiveComplete(primitive)) {
                    continue;
                }

                if (primitive.hasTag("natural", "wood") || primitive.hasTag("landuse", "forest")) {
                    Contour forestContour = new Contour(primitive);
                    LatLon center = (primitive.getBBox() != null) ? primitive.getBBox().getCenter() : null;
                    if (center == null) continue;

                    forestContour.toLocalCoords(center);
                    Geometry forestGeom = forestContour.toJTSGeometry();

                    if (forestGeom == null || !forestGeom.isValid()) {
                        continue;
                    }
                    if (!(forestGeom instanceof Polygon) && !(forestGeom instanceof MultiPolygon)) {
                        //if for some reason contour is neither polygon or multipolygon, we can do nothing
                        continue;
                    }

                    Random random = new Random(primitive.getId());
                    for (int i = 0; i < forestGeom.getNumGeometries(); i++) {
                        Polygon forestPolygon = (Polygon) forestGeom.getGeometryN(i);


                        PreparedGeometry preparedPolygon = PreparedGeometryFactory.prepare(forestPolygon);
                        Envelope envelope = forestPolygon.getEnvelopeInternal();

                        List<Point2D> treePoints = PoissonDiskSampler.generatePoints(envelope, minDist, preparedPolygon, random);

                        for (Point2D p : treePoints) {
                            LatLon treeOrigin = FlatEarth.fromLocalCoords(p.x, p.y, center);

                            // Randomize height slightly
                            double baseHeight = DEFAULT_TREE_HEIGHT * (0.75 + random.nextDouble() / 2); //50% variance

                            // Synthetic tags for the individual tree
                            Map<String, String> treeTags = new HashMap<>(primitive.getInterestingTags());
                            treeTags.put("natural", "tree");
                            treeTags.remove("landuse"); // prevent wood/forest from inflating scores

                            // Handle mixed forest
                            if ("mixed".equals(treeTags.get("leaf_type"))) {
                                if (random.nextBoolean()) {
                                    treeTags.put("leaf_type", "broadleaved");
                                } else {
                                    treeTags.put("leaf_type", "needleleaved");
                                }
                            }
                            treeTags.put("height", String.valueOf(baseHeight));

                            RenderableElement element = RenderableElement.createTree(primitive, treeOrigin, treeTags, random);
                            if (element != null) {
                                newElements.add(element);
                            }
                        }
                    }
                }
            }
        }
        long endTime = System.currentTimeMillis();
        UrbanEye3dPlugin.debugMsg(String.format("Scene update completed in %d ms. Primitives processed: %d, Removed: %d, Added: %d", 
            (endTime - startTime), totalToProcess, elementsToRemove.size(), newElements.size()));
        return new SceneUpdate(elementsToRemove, newElements, modifiedIds, isFullUpdate);
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

    /**
     *  Returns the bounds of visible area based on active GROUND TILES.
     *  It is assumed that ground tiles have been created and activated already based on camera position and other settings
     */
    public Bounds getVisibleArea() {
        var tiles=this.groundPlane.getActiveTiles();
        Bounds bounds = null;
        for (var tile:tiles){
            if (bounds==null){
                bounds = new Bounds(tile.bounds);
            }else {
                bounds.extend(tile.bounds);
            }
        }
        return bounds;
    }

}