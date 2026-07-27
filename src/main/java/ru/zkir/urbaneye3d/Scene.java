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
    final List<RenderableWire> renderableWires = new ArrayList<>();

    private int objectCount = 0;
    private int faceCount = 0;
    public int getObjectCount() {
        return objectCount;
    }

    public int getFaceCount() {
        return faceCount;
    }
    private final Map<String, Mesh> modelCache = new HashMap<>();

    private static final Map<String, List<Point3D>> POWER_ATTACHMENTS = Map.of(
        "tower", List.of(
            new Point3D(-6.0, 0, 14.0), new Point3D(6.0, 0, 14.0),
            new Point3D(-4.5, 0, 20.0), new Point3D(4.5, 0, 20.0),
            new Point3D(0, 0, 25.0)
        ),
        "pole", List.of(
            new Point3D(-1, 0, 9), new Point3D(1, 0, 9)
        )
    );

    public static class SceneUpdate {
        final List<RenderableElement> renderableElements;
        final List<RenderableWire> renderableWires;

        public SceneUpdate(List<RenderableElement> renderableElements, List<RenderableWire> renderableWires) {
            this.renderableElements = renderableElements;
            this.renderableWires = renderableWires;
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
        renderableWires.clear();
        objectCount = 0;
        faceCount = 0;
        if (update != null) {
            renderableElements.addAll(update.renderableElements);
            renderableWires.addAll(update.renderableWires);
            objectCount = renderableElements.size() + renderableWires.size();
            for (RenderableElement element : renderableElements) {
                if (element.getMesh() != null && element.getMesh().faces != null) {
                    faceCount += element.getMesh().faces.size();
                }
            }
        }

    }

    public SceneUpdate calculateUpdate(DataSet dataSet) {
        List<RenderableElement> newElements = new ArrayList<>();
        List<RenderableWire> newWires = new ArrayList<>();
        if (dataSet == null){
            return new SceneUpdate(newElements, newWires);
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

        // Pre-filter roads for automatic orientation
        List<Way> roads = new ArrayList<>();
        List<String> nonRoadHighwayValues = Arrays.asList("footway", "cycleway", "path", "pedestrian", "steps", "corridor", "bridleway", "track", "service", "platform", "sidewalk");
        for (Way way : dataSet.getWays()) {
            if (way.hasKey("highway") && !nonRoadHighwayValues.contains(way.get("highway"))) {
                roads.add(way);
            }
        }

        // Pre-filter power lines for tower orientation
        Map<Node, Double> powerNodeAngles = new HashMap<>();
        Map<Node, List<Way>> powerLinesPerNode = new HashMap<>();
        for (Way way : dataSet.getWays()) {
            if (way.hasKey("power") && ("line".equals(way.get("power")) || "minor_line".equals(way.get("power")))) {
                for (Node n : way.getNodes()) {
                    powerLinesPerNode.computeIfAbsent(n, k -> new ArrayList<>()).add(way);
                }
            }
        }

        // Pre-calculate angles for all power nodes
        for (Node node : powerLinesPerNode.keySet()) {
            List<Way> lines = powerLinesPerNode.get(node);
            List<Point2D> neighbors = new ArrayList<>();
            LatLon nodeCoor = node.getCoor();

            for (Way line : lines) {
                List<Node> wayNodes = line.getNodes();
                for (int i = 0; i < wayNodes.size(); i++) {
                    if (wayNodes.get(i).equals(node)) {
                        if (i > 0) neighbors.add(FlatEarth.getLocalCoords(wayNodes.get(i-1).lat(), wayNodes.get(i-1).lon(), nodeCoor));
                        if (i < wayNodes.size() - 1) neighbors.add(FlatEarth.getLocalCoords(wayNodes.get(i+1).lat(), wayNodes.get(i+1).lon(), nodeCoor));
                    }
                }
            }

            if (!neighbors.isEmpty()) {
                Point2D direction;
                double rotationOffset = 90.0; // Standard perpendicular orientation

                if (neighbors.size() >= 2) {
                    // v1 and v2 are vectors FROM node TO neighbors.
                    Point2D v1 = neighbors.get(0).normalized();
                    Point2D v2 = neighbors.get(1).normalized();
                    
                    // Chord vector (v2 - v1)
                    direction = new Point2D(v2.x - v1.x, v2.y - v1.y);
                    
                    // Check if the turn is sharp (angle between v1 and v2 is < 90 degrees)
                    // Dot product > 0 means acute angle (sharp turn)
                    if (v1.x * v2.x + v1.y * v2.y > 0) {
                        rotationOffset = 0.0; // Snap! Align traverse with the chord
                    }

                    if (direction.length() < 1e-6) {
                        direction = v2;
                        rotationOffset = 90.0;
                    }
                } else {
                    // End node
                    Point2D v1 = neighbors.get(0).normalized();
                    direction = new Point2D(-v1.x, -v1.y);
                }
                powerNodeAngles.put(node, Math.toDegrees(Math.atan2(direction.y, direction.x)) + rotationOffset);
            }
        }

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
                        
                        boolean isRotatable = "true".equals(rule.properties.get("rotatable"));
                        boolean isSnapToRoads = "yes".equals(rule.properties.get("snap_to_roads"));

                        // Check if rotation is allowed or automatic orientation is requested
                        Double direction = null;
                        if (isRotatable) { //rotatable means that direction tag is defined for this object
                            if (node.hasKey("direction")) {
                                // JOSM direction is Degrees CW from North. 
                                // Our Mesh.rotate expects Degrees CCW from East OR model's Y+ matches North.
                                direction = OsmDataWasher.parseDirection(node.get("direction"));
                            }
                        }

                        // Automatic orientation if direction is missing and snap_to_roads is enabled
                        if (direction == null && isSnapToRoads){
                            direction = calculateDirectionToNearestRoad(node, roads);
                        }
                        
                        // Special case for power towers/poles orientation
                        if (direction == null && node.hasKey("power") && (node.hasTag("power", "tower") || node.hasTag("power", "pole"))) {
                            Double lineAngle = powerNodeAngles.get(node);
                            if (lineAngle != null) {
                                instanceMesh = mesh.clone();
                                // lineAngle already includes the necessary offset (0 or 90)
                                instanceMesh.rotate(lineAngle);
                                direction = 0.0; // Handled
                            }
                        }

                        if (direction != null && direction != 0.0) {
                            instanceMesh = mesh.clone();
                            // If model faces North (Y+) by default, rotate(-directionCWFromNorth) works perfectly.
                            instanceMesh.rotate(-direction);
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
         * Power Lines (Wires)
         */
        for (Way way : dataSet.getWays()) {
            if (way.isDeleted() || !way.hasKey("power")) continue;
            String power = way.get("power");
            if (!"line".equals(power) && !"minor_line".equals(power)) continue;

            float lineWidth = "line".equals(power) ? 1.5f : 1.0f;
            double sag = "line".equals(power) ? 2.5 : 1.0;

            List<Node> wayNodes = way.getNodes();
            for (int i = 0; i < wayNodes.size() - 1; i++) {
                Node n1 = wayNodes.get(i);
                Node n2 = wayNodes.get(i + 1);

                double angle1 = powerNodeAngles.getOrDefault(n1, 0.0);
                double angle2 = powerNodeAngles.getOrDefault(n2, 0.0);

                List<Point3D> offsets1 = getAttachmentOffsets(n1);
                List<Point3D> offsets2 = getAttachmentOffsets(n2);

                LatLon origin = n1.getCoor();
                // Base points in local coords
                Point3D base1 = new Point3D(0, 0, 0);
                double cosLat = Math.cos(Math.toRadians(origin.lat()));
                Point3D base2 = new Point3D(
                        (n2.lon() - origin.lon()) * cosLat * FlatEarth.GRAD_LENGTH_M,
                        (n2.lat() - origin.lat()) * FlatEarth.GRAD_LENGTH_M,
                        0
                );

                // Segment vector and its right-hand normal in 2D (XY plane)
                Point2D segV = new Point2D(base2.x - base1.x, base2.y - base1.y);
                Point2D segRight = new Point2D(segV.y, -segV.x);

                int wireCount = Math.min(offsets1.size(), offsets2.size());
                // We process wires in pairs (except for the top single wire of a tower)
                // to prevent crossing within each traverse level.
                for (int j = 0; j < wireCount; ) {
                    // Check if it's a pair (like indices 0,1 or 2,3) or a single wire (like index 4)
                    int batchSize = (j + 1 < wireCount && Math.abs(offsets1.get(j).z - offsets1.get(j+1).z) < 0.1) ? 2 : 1;

                    if (batchSize == 2) {
                        // It's a pair on one traverse. Match them to prevent twisting.
                        Point3D p1a = offsets1.get(j).rotateZ(angle1);
                        Point3D p1b = offsets1.get(j+1).rotateZ(angle1);
                        Point3D p2a = base2.add(offsets2.get(j).rotateZ(angle2));
                        Point3D p2b = base2.add(offsets2.get(j+1).rotateZ(angle2));

                        // Decide which is "right" at n1 and n2 based on projection onto segRight
                        boolean p1aIsRight = (p1a.x * segRight.x + p1a.y * segRight.y) > (p1b.x * segRight.x + p1b.y * segRight.y);
                        boolean p2aIsRight = (p2a.x * segRight.x + p2a.y * segRight.y) > (p2b.x * segRight.x + p2b.y * segRight.y);

                        // Connect right to right, left to left
                        Point3D startR = p1aIsRight ? p1a : p1b;
                        Point3D startL = p1aIsRight ? p1b : p1a;
                        Point3D endR = p2aIsRight ? p2a : p2b;
                        Point3D endL = p2aIsRight ? p2b : p2a;

                        newWires.add(new RenderableWire(way, origin, PowerLineMath.generateSaggingWire(startR, endR, sag, 10), lineWidth));
                        newWires.add(new RenderableWire(way, origin, PowerLineMath.generateSaggingWire(startL, endL, sag, 10), lineWidth));
                        j += 2;
                    } else {
                        // Single wire (e.g. lightning protector)
                        Point3D p1 = base1.add(offsets1.get(j).rotateZ(angle1));
                        Point3D p2 = base2.add(offsets2.get(j).rotateZ(angle2));
                        newWires.add(new RenderableWire(way, origin, PowerLineMath.generateSaggingWire(p1, p2, sag, 10), lineWidth));
                        j++;
                    }
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
		
        return new SceneUpdate(newElements, newWires);
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

    private Double calculateDirectionToNearestRoad(Node node, List<Way> roads) {
        if (roads.isEmpty()) return null;

        LatLon nodeCoor = node.getCoor();
        double minDistSq = Double.POSITIVE_INFINITY;
        Point2D closestPoint = null;

        for (Way road : roads) {
            List<Node> roadNodes = road.getNodes();
            for (int i = 0; i < roadNodes.size() - 1; i++) {
                Node n1 = roadNodes.get(i);
                Node n2 = roadNodes.get(i + 1);

                // Fast distance check (within ~100m)
                if (Math.abs(n1.lat() - nodeCoor.lat()) > 0.001 && Math.abs(n2.lat() - nodeCoor.lat()) > 0.001) continue;
                if (Math.abs(n1.lon() - nodeCoor.lon()) > 0.001 && Math.abs(n2.lon() - nodeCoor.lon()) > 0.001) continue;

                Point2D p1 = FlatEarth.getLocalCoords(n1.lat(), n1.lon(), nodeCoor);
                Point2D p2 = FlatEarth.getLocalCoords(n2.lat(), n2.lon(), nodeCoor);

                // Find closest point on segment to origin (0,0)
                double dx = p2.x - p1.x;
                double dy = p2.y - p1.y;
                double lenSq = dx * dx + dy * dy;
                if (lenSq < 1e-9) continue;

                double t = ((-p1.x) * dx + (-p1.y) * dy) / lenSq;
                t = Math.max(0, Math.min(1, t));

                double cpX = p1.x + t * dx;
                double cpY = p1.y + t * dy;

                double distSq = cpX * cpX + cpY * cpY;
                if (distSq < minDistSq) {
                    minDistSq = distSq;
                    closestPoint = new Point2D(cpX, cpY);
                }
            }
        }

        if (closestPoint != null && minDistSq < 2500) { // Limit to 50m
            // Azimuth: 0 is North (Y+), 90 is East (X+)
            double azimuth = Math.toDegrees(Math.atan2(closestPoint.x, closestPoint.y));
            if (azimuth < 0) azimuth += 360.0;
            return azimuth;
        }

        return null;
    }

    private List<Point3D> getAttachmentOffsets(Node node) {
        if (node.hasTag("power", "tower")) {
            return POWER_ATTACHMENTS.get("tower");
        }
        if (node.hasTag("power", "pole")) {
            return POWER_ATTACHMENTS.get("pole");
        }
        // Default for unknown nodes on the line
        return List.of(new Point3D(0, 0, 10));
    }

}