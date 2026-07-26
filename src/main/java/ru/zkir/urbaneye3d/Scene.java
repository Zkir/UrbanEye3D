package ru.zkir.urbaneye3d;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.spi.preferences.Config;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import ru.zkir.urbaneye3d.utils.*;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.ObjImporter;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.assetconfig.AssetConfig;
import ru.zkir.urbaneye3d.assetconfig.AssetConfigLoader;
import ru.zkir.urbaneye3d.assetconfig.AssetRule;
import ru.zkir.urbaneye3d.assetconfig.GeneratorRegistry;
import ru.zkir.urbaneye3d.assetconfig.ProceduralGenerator;

import java.util.*;
import java.util.stream.Collectors;

import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_TREE_HEIGHT;
import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getTagD;
import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getTagStr;


public class Scene {
    /** The list of scene "elements" that should be rendered.
    * renderable element can be either a building or a building part. */
    final List<RenderableElement> renderableElements = new ArrayList<>();
    private int objectCount = 0;
    private int faceCount = 0;
    public int getObjectCount() {
        return objectCount;
    }

    public int getFaceCount() {
        return faceCount;
    }
    private final Map<String, Mesh> modelCache = new HashMap<>();

    public static class SceneUpdate {
        final List<RenderableElement> renderableElements;

        public SceneUpdate(List<RenderableElement> renderableElements) {
            this.renderableElements = renderableElements;
        }
    }

    public void updateSelection(Collection<PrimitiveId> selectedPrimitivesIds) {
        for (RenderableElement element : renderableElements) {
            element.isSelected = selectedPrimitivesIds.contains(element.primitiveId);
        }
    }
    
    private Mesh loadModel(String resourcePath) {
        if (modelCache.containsKey(resourcePath)) {
            return modelCache.get(resourcePath);
        }
        ObjImporter importer = new ObjImporter();
        Mesh mesh = importer.loadModel(resourcePath);
        modelCache.put(resourcePath, mesh);
        return mesh;
    }


    /** ground plane represents earth surface with projected satellite image.
     *  Currently, it's separated from other scene objects    */
    final GroundPlane groundPlane = new GroundPlane();

    public GroundPlane getGroundPlane() {
        return groundPlane;
    }

    public void applyUpdate(SceneUpdate update) {
        renderableElements.clear();
        objectCount = 0;
        faceCount = 0;
        if (update != null) {
            renderableElements.addAll(update.renderableElements);
            objectCount = renderableElements.size();
            for (RenderableElement element : renderableElements) {
                if (element.getMesh() != null && element.getMesh().faces != null) {
                    faceCount += element.getMesh().faces.size();
                }
            }
        }

    }

    public SceneUpdate calculateUpdate(DataSet dataSet) {
        List<RenderableElement> newElements = new ArrayList<>();
        if (dataSet == null){
            return new SceneUpdate(newElements);
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
        for (OsmPrimitive primitive : dataSet.allPrimitives()) {
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

        // Get asset config singleton or initialize it if we haven't
        AssetConfig assetConfig = AssetConfigLoader.getInstance().getConfig();

        /*
         * Trees, street furniture, and other objects (using AssetConfig).
         */
        for (Node node : dataSet.getNodes()) {
            if (alreadyRenderedPrimitiveIds.contains(node.getPrimitiveId())) continue;

            // For trees, we want to enrich tags BEFORE querying the config so that specific leaf_type rules can match
            Node nodeForConfig = node;
            if (node.hasTag("natural", "tree")) {
                var enrichedTags = TreeSpeciesDatabase.getInstance().enrichTags(node.getInterestingTags(), node.getCoor(), new Random(node.getId()));
                nodeForConfig = new Node();
                nodeForConfig.setKeys(enrichedTags);
            }

            // Find best matching rule for LOD 0 (currently using LOD 0 by default)
            // In future we will get all the LODs
            AssetRule rule = assetConfig.findBestMatch(nodeForConfig);

            if (rule != null) {
                RenderableElement element = null;
                if (rule.properties.containsKey("procedure")) {
                    String procedure = rule.properties.get("procedure");

                    ProceduralGenerator generator = GeneratorRegistry.getInstance().get(procedure);
                    if (generator != null) {
                        element = generator.generate(node, node.getCoor(), rule, new Random(node.getId()));
                    }
                } else if (rule.properties.containsKey("model")) {
                    String modelPath = rule.properties.get("model");
                    Mesh mesh = loadModel(modelPath);
                    if (mesh != null) {
                        Mesh instanceMesh = mesh;
                        
                        // Check if rotation is allowed and requested
                        if ("true".equals(rule.properties.get("rotatable"))) {
                            Double direction = null;
                            if (node.hasKey("direction")) {
                                direction = OsmDataWasher.parseDirection(node.get("direction"));
                            }
                            if (direction != null) {
                                instanceMesh = mesh.clone();
                                instanceMesh.rotate(-direction);
                            }
                        }
                        
                        element = RenderableElement.createFromModel(node, instanceMesh);
                    }
                } else if (rule.properties.containsKey("billboard")) {
                    String texturePath = rule.properties.get("billboard");
                    Map<String, String> tags = nodeForConfig.getInterestingTags();
                    
                    // Determine dimensions
                    double defaultHeight = rule.properties.containsKey("height") ? Double.parseDouble(rule.properties.get("height")) : 1.0;
                    double height = getTagD("height", tags, defaultHeight); //height is a proper tag
                    double defaultWidth = rule.properties.containsKey("width") ? Double.parseDouble(rule.properties.get("width")) : 0.9*defaultHeight;
                    double width = height * ( defaultWidth/defaultHeight); //width is not a tag, default width is just a rate.

                    element = RenderableElement.createBillboard(node, node.getCoor(), texturePath, width, height);
                }

                if (element != null) {
                    newElements.add(element);
                }
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

            for (OsmPrimitive primitive : dataSet.allPrimitives()) {
                if (primitive instanceof Node || !isPrimitiveComplete(primitive)) {
                    continue;
                }

                if (primitive.hasTag("natural", "wood") || primitive.hasTag("landuse", "forest")) {
                    Contour forestContour = new Contour(primitive);
                    LatLon center = primitive.getBBox().getCenter();
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
                                //treeTags.remove("leaf_type");
                            }
                            Node dummyNode = new Node();
                            if (!treeTags.containsKey("leaf_type")) {
                                treeTags = TreeSpeciesDatabase.getInstance().enrichTags(treeTags, center, new Random(dummyNode.getId()));
                            }

                            treeTags.put("height", String.valueOf(baseHeight));

                            // Query AssetConfig to determine the correct texture based on enriched tags

                            dummyNode.setKeys(treeTags);
                            AssetRule treeRule = assetConfig.findBestMatch(dummyNode);
                            String texturePath;
                            if (treeRule != null && treeRule.properties.containsKey("billboard")) {
                                texturePath = treeRule.properties.get("billboard");
                            }else {
                                throw new RuntimeException("Unable to find proper tree model for forest " + primitive.getPrimitiveId() );
                            }

                            double width = baseHeight * 0.9;
                            RenderableElement element = RenderableElement.createBillboard(primitive, treeOrigin, texturePath, width, baseHeight);
                            if (element != null) {
                                newElements.add(element);
                            }
                        }
                    }
                }
            }
        }
		
        return new SceneUpdate(newElements);
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