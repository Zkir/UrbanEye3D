package ru.zkir.urbaneye3d;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.spi.preferences.Config;
import ru.zkir.urbaneye3d.meshers.s3db.RoofShapes;
import ru.zkir.urbaneye3d.meshers.buildingpassage.BuildingPassage;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.meshers.buildingpassage.PassageCutter;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.zkir.urbaneye3d.RoofGeneratorTopologyTest.AssertMeshTopology;
import static ru.zkir.urbaneye3d.RoofGeneratorTopologyTest.createRectangularBase;
import static ru.zkir.urbaneye3d.RoofGeneratorTopologyTest.createRectangularBaseWithHole;
import static ru.zkir.urbaneye3d.RoofGeneratorTopologyTest.createTestBuilding;
import static ru.zkir.urbaneye3d.utils.ObjExporter.saveMeshToObj;
import static ru.zkir.urbaneye3d.utils.Settings.SAVE_TEST_RESULTS_TO_FILE;

/**
 * Ways tagged tunnel=building_passage must cut a real opening through the building:
 * a hole in the walls, the walls of the passage and the ceiling of the arch, while the body of
 * the building stays watertight and the walls are NOT triangulated.
 */
class BuildingPassageTest {

    private static final String TEST_OUTPUT_DIR = "target/test-output/building-passage";

    static {
        Config.setPreferencesInstance(new org.openstreetmap.josm.data.Preferences());
    }

    @BeforeAll
    public static void setUp() throws Exception {
        if (SAVE_TEST_RESULTS_TO_FILE) {
            Files.createDirectories(Paths.get(TEST_OUTPUT_DIR));
        }
    }

    private DataSet loadDataSetFromOsmFile(String resourceName) throws Exception {
        InputStream is = getClass().getResourceAsStream("/osm_test_files/" + resourceName);
        if (is == null) {
            throw new IllegalArgumentException("Resource not found: " + resourceName);
        }
        return OsmReader.parseDataSet(is, null);
    }

    private static BuildingPassage passage(double width, Double height, Point2D... axis) {
        if (height==null){
            height = BuildingPassage.DEFAULT_ARCH_HEIGHT;
        }
        return new BuildingPassage(null, List.of(axis), width, height);
    }

    /** Number of faces of the mesh with the given material and the given number of edges. */
    private static int countFaces(Mesh mesh, int material, int edges) {
        int result = 0;
        for (int i = 0; i < mesh.faces.size(); i++) {
            if (mesh.faceMaterials.get(i) == material && mesh.faces.get(i).length == edges) {
                result++;
            }
        }
        return result;
    }

    /** Number of horizontal faces lying at the given height. */
    private static int countHorizontalFaces(Mesh mesh, double z) {
        int result = 0;
        for (int[] face : mesh.faces) {
            boolean flat = true;
            for (int index : face) {
                if (Math.abs(mesh.verts.get(index).z - z) > 1e-6) {
                    flat = false;
                    break;
                }
            }
            if (flat) {
                result++;
            }
        }
        return result;
    }

    /** The highest point of the mesh below the given z, i.e. the top of the walls that are lower than z. */
    private static List<Point3D> verticesAt(Mesh mesh, double z) {
        List<Point3D> result = new ArrayList<>();
        for (Point3D p : mesh.verts) {
            if (Math.abs(p.z - z) < 1e-6) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * Every vertex of the cut mesh must lie inside the contour of the building: a wall or a ceiling of the
     * arch that sticks out of the building would be visible from outside as a flap.
     */
    private static void assertInsideTheContour(Mesh mesh, Contour contour, String name) {
        org.locationtech.jts.geom.Geometry footprint = contour.toJTSGeometry();
        org.locationtech.jts.geom.GeometryFactory factory = footprint.getFactory();
        for (int i = 0; i < mesh.verts.size(); i++) {
            Point3D p = mesh.verts.get(i);
            assertTrue(footprint.covers(factory.createPoint(new org.locationtech.jts.geom.Coordinate(p.x, p.y))),
                    name + ": the vertex " + i + " (" + p.x + ", " + p.y + ") is outside the contour of the building");
        }
    }

    /**
     * A rectangular building with a straight passage through it.
     * The opening in each facade wall must be a single non-convex "Π"-shaped face: the wall above the arch
     * is not split by a seam, and nothing is triangulated.
     */
    @Test
    void testStraightPassageThroughRectangularBuilding() {
        ArrayList<Point2D> base = createRectangularBase(20, 12); // x in [-10..10], y in [-6..6]
        BuildingRecipe recipe = createTestBuilding(base, RoofShapes.FLAT, 0, 0, 10);
        Mesh mesh = RenderableElement.composeMesh(recipe);
        assertEquals(6, mesh.faces.size(), "a box without a passage has 6 faces");

        List<BuildingPassage> passages = List.of(passage(4, null, new Point2D(-12, 0), new Point2D(12, 0)));
        assertEquals(1, PassageCutter.apply(mesh, recipe.getContour2(), recipe.minHeight, recipe.wallHeight, passages),
                "the passage must be cut");

        //with 10 m of walls and no maxheight the arch is one storey high
        double archZ = BuildingPassage.DEFAULT_ARCH_HEIGHT;
        AssertMeshTopology(mesh, 0, 10, "straight passage");
        assertInsideTheContour(mesh, recipe.getContour2(), "straight passage");
        assertEquals(10, mesh.faces.size(), "4 walls + 2 walls of the passage + its ceiling + roof + 2 parts of the floor");

        //two "Π"-shaped faces in the walls, each with 8 edges
        assertEquals(2, countFaces(mesh, Mesh.WALL_COLOUR_IDX, 8), "the openings in the facades must be single faces");
        //two walls that are not crossed stay quads, plus two walls of the passage and the ceiling of the arch
        assertEquals(5, countFaces(mesh, Mesh.WALL_COLOUR_IDX, 4), "2 untouched walls + 2 walls + the ceiling");
        assertEquals(1, countHorizontalFaces(mesh, archZ), "the ceiling of the arch is one face");
        //the floor is cut into two parts
        assertEquals(2, countFaces(mesh, Mesh.BOTTOM_COLOUR_IDX, 4), "the floor is cut into two parts");
        assertEquals(1, countFaces(mesh, Mesh.ROOF_COLOUR_IDX, 4), "the roof is not touched");
        //nothing is triangulated
        for (int[] face : mesh.faces) {
            assertTrue(face.length >= 4, "the passage must be cut without triangulation of the walls");
        }
        //the arch is 4 m wide, and all the new vertices of the openings, of the walls of the passage and of
        //its ceiling are shared, so the body is closed
        List<Point3D> top = verticesAt(mesh, archZ);
        assertEquals(4, top.size(), "the top of the arch has 4 corners, shared by the walls and the ceiling");
        for (Point3D p : top) {
            assertTrue(Math.abs(Math.abs(p.y) - 2) < 1e-6, "the passage is 4 m wide: " + p);
        }

        if (SAVE_TEST_RESULTS_TO_FILE) {
            saveMeshToObj(mesh, TEST_OUTPUT_DIR + "/straight_passage.obj");
        }
    }

    /**
     * Two passages crossing inside the building.  The plan of the opening becomes a cross with 12 corners,
     * which is one non-convex face for the ceiling, and every corner of the cross must be a single vertex
     * shared by the walls, by the ceiling and by the parts of the floor.
     */
    @Test
    void testCrossingPassagesShareTheirCorners() {
        ArrayList<Point2D> base = createRectangularBase(20, 12); // x in [-10..10], y in [-6..6]
        BuildingRecipe recipe = createTestBuilding(base, RoofShapes.FLAT, 0, 0, 10);
        Mesh mesh = RenderableElement.composeMesh(recipe);
        int verticesBefore = mesh.verts.size();

        List<BuildingPassage> passages = List.of(
                passage(4, null, new Point2D(-12, 0), new Point2D(12, 0)),
                passage(4, null, new Point2D(0, -8), new Point2D(0, 8)));
        assertEquals(2, PassageCutter.apply(mesh, recipe.getContour2(), recipe.minHeight, recipe.wallHeight, passages),
                "both passages must be cut");

        double archZ = BuildingPassage.DEFAULT_ARCH_HEIGHT;
        AssertMeshTopology(mesh, 0, 10, "crossing passages");
        assertInsideTheContour(mesh, recipe.getContour2(), "crossing passages");
        assertEquals(18, mesh.faces.size(), "4 crossed walls + 8 walls of the passages + the ceiling + roof + 4 parts of the floor");
        assertEquals(4, countFaces(mesh, Mesh.WALL_COLOUR_IDX, 8), "an opening in each of the four walls");
        assertEquals(8, countFaces(mesh, Mesh.WALL_COLOUR_IDX, 4), "the walls of the two passages");
        //the ceiling of both passages is one cross-shaped face, because the arches are of the same height
        assertEquals(1, countHorizontalFaces(mesh, archZ), "the ceiling of the crossing arches is one face");
        assertEquals(4, countFaces(mesh, Mesh.BOTTOM_COLOUR_IDX, 4), "the floor is cut into four parts");
        assertEquals(1, countFaces(mesh, Mesh.ROOF_COLOUR_IDX, 4), "the roof is not touched");
        for (int[] face : mesh.faces) {
            assertTrue(face.length >= 4, "the passages must be cut without triangulation of the walls");
        }
        //12 corners of the cross: the walls of the passages, the ceiling and the floor meet exactly there
        List<Point3D> top = verticesAt(mesh, archZ);
        assertEquals(12, top.size(), "the corners of the crossing must be shared, not duplicated");
        assertTrue(mesh.verts.size() > verticesBefore, "new vertices were added");

        if (SAVE_TEST_RESULTS_TO_FILE) {
            saveMeshToObj(mesh, TEST_OUTPUT_DIR + "/crossing_passages.obj");
        }
    }

    /**
     * A part raised above the ground (min_height) gets an opening only if the height of the passage is
     * mapped explicitly and reaches the part; otherwise the floor of the part would be cut for nothing.
     */
    @Test
    void testRaisedPartNeedsAMappedHeight() {
        ArrayList<Point2D> base = createRectangularBase(20, 12);
        BuildingRecipe recipe = createTestBuilding(base, RoofShapes.FLAT, 3, 0, 10); // the part stands on stilts
        assertEquals(3, recipe.minHeight, "the part stands on stilts");

        Mesh untouched = RenderableElement.composeMesh(recipe);
        int verticesBefore = untouched.verts.size();
        List<BuildingPassage> notMapped = List.of(passage(4, null, new Point2D(-12, 0), new Point2D(12, 0)));
        assertEquals(0, PassageCutter.apply(untouched, recipe.getContour2(), recipe.minHeight, recipe.wallHeight, notMapped),
                "a passage of unknown height must not be cut in a raised part");
        assertEquals(6, untouched.faces.size(), "the mesh is not touched");
        assertEquals(verticesBefore, untouched.verts.size(), "not even extra vertices are left in the mesh");

        Mesh cut = RenderableElement.composeMesh(recipe);
        List<BuildingPassage> mapped = List.of(passage(4, 6.0, new Point2D(-12, 0), new Point2D(12, 0)));
        assertEquals(1, PassageCutter.apply(cut, recipe.getContour2(), recipe.minHeight, recipe.wallHeight, mapped),
                "a mapped passage that reaches the part is cut");
        //the ceiling of the part is one storey above its floor: 3 m + 3 m
        double archZ = 3 + BuildingPassage.DEFAULT_ARCH_HEIGHT;
        AssertMeshTopology(cut, 3, 10, "passage in a raised part");
        assertEquals(1, countHorizontalFaces(cut, archZ), "the ceiling of the arch");
        assertEquals(2, countFaces(cut, Mesh.WALL_COLOUR_IDX, 8), "the openings in the facades");

        if (SAVE_TEST_RESULTS_TO_FILE) {
            saveMeshToObj(cut, TEST_OUTPUT_DIR + "/raised_part_passage.obj");
        }
    }

    /**
     * The same, but the passage is bent and part of the wall is at an angle: the opening has a reflex corner,
     * so the walls of the passage and the ceiling must still close the body.
     */
    @Test
    void testBentPassageThroughLShapedBuilding() {
        ArrayList<Point2D> base = new ArrayList<>();
        base.add(new Point2D(-10, -6));
        base.add(new Point2D(10, -6));
        base.add(new Point2D(10, 0));
        base.add(new Point2D(2, 0));
        base.add(new Point2D(2, 6));
        base.add(new Point2D(-10, 6));

        BuildingRecipe recipe = createTestBuilding(base, RoofShapes.FLAT, 0, 0, 10);
        Mesh mesh = RenderableElement.composeMesh(recipe);
        int facesBefore = mesh.faces.size();

        List<BuildingPassage> passages = List.of(
                passage(3, null, new Point2D(-16, -3), new Point2D(0, -3), new Point2D(0, 9)));
        assertEquals(1, PassageCutter.apply(mesh, recipe.getContour2(), recipe.minHeight, recipe.wallHeight, passages),
                "the bent passage must be cut");

        assertTrue(mesh.faces.size()>facesBefore , "Passage should add faces to the mesh");

        AssertMeshTopology(mesh, 0, 10, "bent passage");
        //the entrance and the exit are cut as single faces in the walls
        assertTrue(countFaces(mesh, Mesh.WALL_COLOUR_IDX, 8) >= 2, "both openings must be non-convex single faces");

        if (SAVE_TEST_RESULTS_TO_FILE) {
            saveMeshToObj(mesh, TEST_OUTPUT_DIR + "/bent_passage.obj");
        }
    }

    /**
     * The same corner road, but mapped as two ways in a row that turn at their common node by a right angle.  Those
     * two ways are not one corridor — they lead through the building in different directions — so they stay two
     * passages, and the body of the building must still be closed around them.
     */
    @Test
    void testRoadThatTurnsInsideTheBuildingIsTwoPassagesNotOneCorridor() {
        ArrayList<Point2D> base = new ArrayList<>();
        base.add(new Point2D(-10, -6));
        base.add(new Point2D(10, -6));
        base.add(new Point2D(10, 0));
        base.add(new Point2D(2, 0));
        base.add(new Point2D(2, 6));
        base.add(new Point2D(-10, 6));

        BuildingRecipe recipe = createTestBuilding(base, RoofShapes.FLAT, 0, 0, 10);
        Mesh mesh = RenderableElement.composeMesh(recipe);

        List<BuildingPassage> passages = List.of(
                passage(3, null, new Point2D(-16, -3), new Point2D(0, -3)),
                passage(3, null, new Point2D(0, -3), new Point2D(0, 9)));
        assertEquals(2, PassageCutter.apply(mesh, recipe.getContour2(), recipe.minHeight, recipe.wallHeight, passages),
                "both ways of the corner road must be cut");

        AssertMeshTopology(mesh, 0, 10, "a road that turns at a right angle inside the building");
        assertTrue(countFaces(mesh, Mesh.WALL_COLOUR_IDX, 8) >= 2, "both openings must be non-convex single faces");
        for (int[] face : mesh.faces) {
            assertTrue(face.length >= 4, "the passages must be cut without triangulation of the walls");
        }

        if (SAVE_TEST_RESULTS_TO_FILE) {
            saveMeshToObj(mesh, TEST_OUTPUT_DIR + "/bent_passage_as_two_ways.obj");
        }
    }

    /**
     * A passage that does not cross the building must not change its mesh.
     */
    @Test
    void testPassageBesideTheBuildingDoesNotCutAnything() {
        ArrayList<Point2D> base = createRectangularBase(20, 12);
        BuildingRecipe recipe = createTestBuilding(base, RoofShapes.FLAT, 0, 0, 10);
        Mesh mesh = RenderableElement.composeMesh(recipe);

        //the axis passes diagonally outside the corner of the building: the corner is 2.8 m away from the axis,
        //while the half-width of the passage is 2 m.  The bounding boxes still overlap, so the cutter is called.
        List<BuildingPassage> passages = List.of(
                passage(4, null, new Point2D(-14, -6), new Point2D(-6, -14)));
        assertEquals(0, PassageCutter.apply(mesh, recipe.getContour2(), recipe.minHeight, recipe.wallHeight, passages));
        assertEquals(6, mesh.faces.size(), "nothing must be cut");
    }

    /** The height of the arch never leaves less than 0.3 m of the wall under the roof. */
    @Test
    void testLowBuildingKeepsALintel() {
        ArrayList<Point2D> base = createRectangularBase(20, 12);
        BuildingRecipe recipe = createTestBuilding(base, RoofShapes.FLAT, 0, 0, 2.5);
        Mesh mesh = RenderableElement.composeMesh(recipe);

        List<BuildingPassage> passages = List.of(
                passage(4, 10.0, new Point2D(-12, 0), new Point2D(12, 0))); //maxheight=10 for a 2.5 m building
        assertEquals(1, PassageCutter.apply(mesh, recipe.getContour2(), recipe.minHeight, recipe.wallHeight, passages));
        AssertMeshTopology(mesh, 0, 2.5, "passage in a low building");
        double archZ = 2.5 - BuildingPassage.MIN_LINTEL;
        assertEquals(1, countHorizontalFaces(mesh, archZ), "the ceiling of the arch is just under the roof");
    }

    /** A building that is lower than the shortest arch we cut must be left alone. */
    @Test
    void testTooLowBuildingIsNotCut() {
        ArrayList<Point2D> base = createRectangularBase(20, 12);
        BuildingRecipe recipe = createTestBuilding(base, RoofShapes.FLAT, 0, 0, 1.2);
        Mesh mesh = RenderableElement.composeMesh(recipe);

        List<BuildingPassage> passages = List.of(passage(4, null, new Point2D(-12, 0), new Point2D(12, 0)));
        assertEquals(0, PassageCutter.apply(mesh, recipe.getContour2(), recipe.minHeight, recipe.wallHeight, passages));
        assertEquals(6, mesh.faces.size());
    }

    /** A passage through a building with a courtyard: the arch is cut in the outer wall and in the wall of the courtyard. */
    @Test
    void testPassageThroughBuildingWithCourtyard() {
        //30x20 building with a 10x10 courtyard in the middle
        Contour contour = createRectangularBaseWithHole(30, 20, 10, 10);
        BuildingRecipe recipe = createTestBuilding(contour, RoofShapes.FLAT, 0, 0, 10);
        Mesh mesh = RenderableElement.composeMesh(recipe);
        int facesBefore = mesh.faces.size();

        //the passage goes along the x axis, so it crosses the building in two places and the courtyard in between
        List<BuildingPassage> passages = List.of(passage(4, null, new Point2D(-20, 0), new Point2D(20, 0)));
        int cuts = PassageCutter.apply(mesh, recipe.getContour2(), recipe.minHeight, recipe.wallHeight, passages);
        assertEquals(1, cuts, "the passage must be cut in both wings of the building");
        assertTrue(mesh.faces.size() > facesBefore, "the passage should add faces to the building");

        double archZ = BuildingPassage.DEFAULT_ARCH_HEIGHT;
        AssertMeshTopology(mesh, 0, 10, "passage through a building with a courtyard");
        assertInsideTheContour(mesh, recipe.getContour2(), "passage through a building with a courtyard");
        //four Π-faces: the outer wall of each wing and the wall of the courtyard of each wing
        assertEquals(4, countFaces(mesh, Mesh.WALL_COLOUR_IDX, 8), "the arch has four openings in the walls");
        assertEquals(2, countHorizontalFaces(mesh, archZ), "the ceilings of the two parts of the arch");
        assertEquals(0, countFaces(mesh, Mesh.WALL_COLOUR_IDX, 3), "the walls of the arch are not triangulated");

        if (SAVE_TEST_RESULTS_TO_FILE) {
            saveMeshToObj(mesh, TEST_OUTPUT_DIR + "/courtyard_passage.obj");
        }
    }

    /**
     * A passage whose axis runs exactly through a corner of the building: the strip crosses the two walls that
     * meet in that corner.  The opening is cut in both of them, the body stays closed.
     */
    @Test
    void testPassageAcrossACorner() {
        ArrayList<Point2D> base = createRectangularBase(20, 12); //a corner of the building is in (10, 6)
        BuildingRecipe recipe = createTestBuilding(base, RoofShapes.FLAT, 0, 0, 10);
        Mesh mesh = RenderableElement.composeMesh(recipe);
        int facesBefore = mesh.faces.size();

        //the axis goes from (-6, -10) to (16, 12): both of its ends are outside the building, and it runs exactly
        //through the corner (10, 6), so the strip of the passage chews that corner off
        List<BuildingPassage> passages = List.of(passage(4, null, new Point2D(-6, -10), new Point2D(16, 12)));
        int cut = PassageCutter.apply(mesh, recipe.getContour2(), recipe.minHeight, recipe.wallHeight, passages);
        assertEquals(1, cut, "the walls of this building follow its contour, so the passage must be cut");
        assertTrue(mesh.faces.size() > facesBefore, "the passage should add faces to the building");

        AssertMeshTopology(mesh, 0, 10, "passage running across a corner of the building");
        assertInsideTheContour(mesh, recipe.getContour2(), "passage across a corner");

        assertEquals(1, countFaces(mesh, Mesh.WALL_COLOUR_IDX, 8),
                "the facade the passage enters through keeps a Π-shaped face with an opening in it");
        assertEquals(2, countFaces(mesh, Mesh.WALL_COLOUR_IDX, 6),
                "the two walls that meet in the corner get their opening, which opens towards the corner");
        assertEquals(1, countHorizontalFaces(mesh, BuildingPassage.DEFAULT_ARCH_HEIGHT),
                "one ceiling of the arch");

        if (SAVE_TEST_RESULTS_TO_FILE) {
            saveMeshToObj(mesh, TEST_OUTPUT_DIR + "/passage_through_the_corner.obj");
        }
    }

    /**
     * The strip of a straight passage must be a plain rectangle: prolonging the axis moves its end points
     * outward, it does not add points to the polyline.  A point added in the middle of a straight stretch makes
     * JTS keep a redundant corner in the boundary of the strip, exactly opposite the end of the mapped axis, and
     * the mitre of that almost straight join is not straight either: the corner of the strip stands about a
     * millimetre off the side of the passage.  Where a wall of the building passes close to the end of the axis
     * — and it does when the axis ends on a node of the outline that the contour dropped as too straight — that
     * redundant corner and the point where the strip really crosses the wall are a hair apart, the opening gets
     * an edge of a fraction of a millimetre between them, and the faces of the arch stop sharing their edges
     * with the wall.
     */
    @Test
    void testTheStripOfAStraightPassageIsARectangle() {
        BuildingPassage straight = passage(3.5, null, new Point2D(0, 0), new Point2D(10, 0));
        org.locationtech.jts.geom.Geometry strip = straight.strip(new org.locationtech.jts.geom.GeometryFactory());
        org.locationtech.jts.geom.LinearRing ring = ((org.locationtech.jts.geom.Polygon) strip).getExteriorRing();
        assertEquals(5, ring.getNumPoints(), "the strip of a straight passage has four corners and nothing else");
        org.locationtech.jts.geom.Envelope box = strip.getEnvelopeInternal();
        assertEquals(-1.0, box.getMinX(), 1e-9, "the strip reaches one metre beyond the first end of the axis");
        assertEquals(11.0, box.getMaxX(), 1e-9, "the strip reaches one metre beyond the other end of the axis");
        assertEquals(3.5, box.getHeight(), 1e-9, "the strip is as wide as the passage");
    }

    /**
     * A way mapped node by node may have a node that is a fraction of a degree off straight.  The contour of a building
     * drops such nodes, so a wall goes straight through the place where the axis of the passage bends, and the strip
     * gets a corner of its own that the wall does not know about, a hair away from the point where the strip really
     * crosses the wall.  Such a node must not make a corner in the corridor: the strip of a passage that goes on almost
     * straight is the same rectangle it would be without that node.
     */
    @Test
    void testAPassageWithAnAlmostStraightNodeStillGetsARectangularStrip() {
        //the axis bends at (10, 0) by 0.08 of a degree: the tan of the turn there is 0.0014
        BuildingPassage almost = passage(3.5, null, new Point2D(0, 0), new Point2D(10, 0), new Point2D(20, 0.014));
        org.locationtech.jts.geom.Geometry strip = almost.strip(new org.locationtech.jts.geom.GeometryFactory());
        org.locationtech.jts.geom.LinearRing ring = ((org.locationtech.jts.geom.Polygon) strip).getExteriorRing();
        assertEquals(5, ring.getNumPoints(), "a node that is a fraction of a degree off straight makes no corner");
        assertEquals(77.0, strip.getArea(), 1e-3,
                "the corridor is 22 by 3.5 metres, as if the node had never been mapped");
    }

    /** The whole pipeline: OSM data in, buildings with openings out.
     *  We test that:
     *     number of created passages matches expectations
     *     number of faces in each mesh matches expectation (if passage is not cut, number of faces drops)
     * */
    @Test
    void testPassageIsCutInScene() throws Exception {
        DataSet dataSet = loadDataSetFromOsmFile("building_passage.osm");
        Scene scene = new Scene();
        scene.applyUpdate(scene.calculateUpdate(dataSet));

        // Mapping ref -> expected orientation (rounded and normalized to [0..359])
        Map<String, Integer> expectedCuts = new HashMap<>();
        Map<String, Integer> expectedFaces = new HashMap<>();

        expectedCuts.put("1", 1);
        expectedFaces.put("1", 10);

        expectedCuts.put("2", 1);
        expectedFaces.put("2", 18);

        expectedCuts.put("3", 0);
        expectedFaces.put("3", 35);

        expectedCuts.put("4", 0);
        expectedFaces.put("4", 6);

        expectedCuts.put("5", 3);
        expectedFaces.put("5", 39);

        // Assert: Verify the outcome
        assertEquals(expectedCuts.size()  , scene.renderableElements.size(),
                "Expected "+expectedCuts.size()+" elements, but got " + scene.renderableElements.size());

        for (var re : scene.renderableElements) {
            OsmPrimitive primitive = dataSet.getPrimitiveById(re.primitiveId);

            String ref = primitive.get("ref");
            if (ref != null ) {
                assertEquals(expectedCuts.get(ref), re.building_passages, "Number of passages mismatch for ref=" + ref + " (" + primitive.getPrimitiveId() + ")" + " Note: " + primitive.get("note"));
                assertEquals(expectedFaces.get(ref), re.getMesh().faces.size(), "Number of faces mismatch for ref=" + ref + " (" + primitive.getPrimitiveId() + ")" + " Note: " + primitive.get("note"));
            } else{
                throw new RuntimeException("unexpected object: " + primitive.getPrimitiveId());
            }
            AssertMeshTopology(re.getMesh(), re.getMesh().getMinBounds().z, re.getMesh().getMaxBounds().z, "passage from the scene");
        }

        //if (SAVE_TEST_RESULTS_TO_FILE) {
        //    saveMeshToObj(mesh, TEST_OUTPUT_DIR + "/scene_passage.obj");
        //}
    }

    /**
     * Two parallel passages in one pair of walls, one of them mapping a taller maxheight.  Their corridors do not
     * touch one another in plan, so each of them is roofed with an arch of its own height: the wall between them
     * gets a staircase-shaped opening rather than both arches being stretched to the tallest one.
     */
    @Test
    void testPassagesThatDoNotTouchHaveTheirOwnArchHeight() {
        ArrayList<Point2D> base = createRectangularBase(20, 12); // x in [-10..10], y in [-6..6]
        BuildingRecipe recipe = createTestBuilding(base, RoofShapes.FLAT, 0, 0, 10);
        Mesh mesh = RenderableElement.composeMesh(recipe);

        List<BuildingPassage> passages = List.of(
                passage(4, null, new Point2D(-12, -3), new Point2D(12, -3)),  // no maxheight: one storey
                passage(4, 6.0, new Point2D(-12, 3), new Point2D(12, 3)));    // maxheight=6
        assertEquals(2, PassageCutter.apply(mesh, recipe.getContour2(), recipe.minHeight, recipe.wallHeight, passages),
                "both passages must be cut");

        double lowArch = BuildingPassage.DEFAULT_ARCH_HEIGHT;
        double highArch = 6.0;
        AssertMeshTopology(mesh, 0, 10, "two passages of different heights");
        assertInsideTheContour(mesh, recipe.getContour2(), "two passages of different heights");
        assertEquals(14, mesh.faces.size(),
                "4 walls + 4 walls of the passages + 2 ceilings + roof + 3 parts of the floor");

        //each arch stops at its own height: neither is stretched to the taller one, nor lowered to the shorter
        assertEquals(1, countHorizontalFaces(mesh, lowArch), "the ceiling of the low passage");
        assertEquals(1, countHorizontalFaces(mesh, highArch), "the ceiling of the tall passage");
        //both holes are cut in the same wall face, which becomes one face with a step between the openings
        assertEquals(2, countFaces(mesh, Mesh.WALL_COLOUR_IDX, 12),
                "the wall with two openings of different heights is one face with a step");
        assertEquals(8, countFaces(mesh, Mesh.WALL_COLOUR_IDX, 4),
                "2 untouched walls + 2 walls of each passage + the ceiling of each arch");
        assertEquals(3, countFaces(mesh, Mesh.BOTTOM_COLOUR_IDX, 4), "the floor is cut into three parts");
        assertEquals(1, countFaces(mesh, Mesh.ROOF_COLOUR_IDX, 4), "the roof is not touched");
        for (int[] face : mesh.faces) {
            assertTrue(face.length >= 4, "the passages must be cut without triangulation of the walls");
        }
        //the walls of each passage stop at the ceiling of its own arch
        assertEquals(4, verticesAt(mesh, lowArch).size(), "the low arch has four corners");
        assertEquals(4, verticesAt(mesh, highArch).size(), "the tall arch has four corners");

        if (SAVE_TEST_RESULTS_TO_FILE) {
            saveMeshToObj(mesh, TEST_OUTPUT_DIR + "/passages_of_different_heights.obj");
        }
    }

    /**
     * Two passages crossing inside the building and mapping different heights: their strips touch in plan, so they
     * are roofed with one arch of the tallest of them, and the crossing stays one ceiling.
     */
    @Test
    void testCrossingPassagesTakeTheTallestMappedHeight() {
        ArrayList<Point2D> base = createRectangularBase(20, 12);
        BuildingRecipe recipe = createTestBuilding(base, RoofShapes.FLAT, 0, 0, 10);
        Mesh mesh = RenderableElement.composeMesh(recipe);

        List<BuildingPassage> passages = List.of(
                passage(4, 4.0, new Point2D(-12, 0), new Point2D(12, 0)),
                passage(4, 6.0, new Point2D(0, -8), new Point2D(0, 8)));
        assertEquals(2, PassageCutter.apply(mesh, recipe.getContour2(), recipe.minHeight, recipe.wallHeight, passages),
                "both passages must be cut");

        AssertMeshTopology(mesh, 0, 10, "crossing passages of different mapped heights");
        assertInsideTheContour(mesh, recipe.getContour2(), "crossing passages of different mapped heights");
        assertEquals(18, mesh.faces.size(), "the crossing is roofed exactly as two arches of the same height");
        assertEquals(1, countHorizontalFaces(mesh, 6.0), "the ceiling of the crossing arch is one face");
        assertEquals(0, countHorizontalFaces(mesh, 4.0), "the shorter passage is not roofed below the taller one");
        assertEquals(12, verticesAt(mesh, 6.0).size(), "the corners of the crossing are shared by both arches");

        if (SAVE_TEST_RESULTS_TO_FILE) {
            saveMeshToObj(mesh, TEST_OUTPUT_DIR + "/crossing_passages_different_heights.obj");
        }
    }

    /**
     * One road mapped as several ways in a row with different maxheights: the corridor is one passage, so it is
     * roofed with the tallest of its ways, and the arch does not step in the middle of the corridor.
     */
    @Test
    void testChainOfWaysTakesTheTallestMappedHeight() {
        ArrayList<Point2D> base = createRectangularBase(20, 12);
        BuildingRecipe recipe = createTestBuilding(base, RoofShapes.FLAT, 0, 0, 10);
        Mesh mesh = RenderableElement.composeMesh(recipe);

        List<BuildingPassage> passages = List.of(
                passage(4, 4.0, new Point2D(-12, 0), new Point2D(0, 0)),
                passage(4, 6.0, new Point2D(0, 0), new Point2D(12, 0)));
        assertEquals(2, PassageCutter.apply(mesh, recipe.getContour2(), recipe.minHeight, recipe.wallHeight, passages),
                "both ways of the road must be cut");

        AssertMeshTopology(mesh, 0, 10, "one road mapped as two ways of different heights");
        assertInsideTheContour(mesh, recipe.getContour2(), "one road mapped as two ways of different heights");
        assertEquals(10, mesh.faces.size(), "the corridor is cut as one straight passage");
        assertEquals(1, countHorizontalFaces(mesh, 6.0), "one ceiling of the corridor, at the tallest mapped height");
        assertEquals(0, countHorizontalFaces(mesh, 4.0), "the arch does not step inside the corridor");
        assertEquals(2, countFaces(mesh, Mesh.WALL_COLOUR_IDX, 8), "the two openings of the corridor");

        if (SAVE_TEST_RESULTS_TO_FILE) {
            saveMeshToObj(mesh, TEST_OUTPUT_DIR + "/chain_of_ways_different_heights.obj");
        }
    }

  

}
