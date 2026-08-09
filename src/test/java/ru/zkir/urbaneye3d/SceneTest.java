package ru.zkir.urbaneye3d;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.spi.preferences.Config;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point3D;
import ru.zkir.urbaneye3d.utils.Settings;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.coor.LatLon;

import java.awt.Color;
import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import ru.zkir.urbaneye3d.utils.ObjImporter;

import static org.junit.jupiter.api.Assertions.*;
import static ru.zkir.urbaneye3d.RoofGeneratorTopologyTest.*;
import static ru.zkir.urbaneye3d.utils.Settings.SAVE_TEST_RESULTS_TO_FILE;

import org.openstreetmap.josm.data.preferences.JosmBaseDirectories;
import org.openstreetmap.josm.data.preferences.JosmUrls;
import org.openstreetmap.josm.spi.preferences.MemoryPreferences;

class SceneTest {

    @BeforeAll
    public static void setUp() {
        Config.setPreferencesInstance(new MemoryPreferences());
        Config.setBaseDirectoriesProvider(JosmBaseDirectories.getInstance());
        Config.setUrlsProvider(JosmUrls.getInstance());
    }

    private DataSet loadDataSetFromOsmFile(String resourceName) throws Exception {
        InputStream is = getClass().getResourceAsStream("/osm_test_files/" + resourceName);
        if (is == null) {
            throw new IllegalArgumentException("Resource not found: " + resourceName);
        }
        return OsmReader.parseDataSet(is, null);
    }

    /**
     * If there building parts belonging to the building, the building itself is not rendered.
     */
    @Test
    void testBuildingWithPartIsNotRendered() throws Exception {
        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("building_with_part.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        Scene.SceneUpdate update = scene.calculateUpdate(dataSet);
        scene.applyUpdate(update);

        // Assert: Verify the outcome
        // We expect only the building:part to be rendered, not the parent building.
        assertEquals(1, scene.renderableElements.size());
    }


    /**
     * More complex belonging topology test.
     * Usually nodes of part lie on the same contour as building.
     * it's not a problem with a simple bbox test of belonging,
     * but is always very tricky for polygon/polygon topology test
     */
    @Test
    void testNodesOnContourBelonging() throws Exception {
        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("nodes_on_contour.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        Scene.SceneUpdate update = scene.calculateUpdate(dataSet);
        scene.applyUpdate(update);

        // Assert: Verify the outcome
        // We expect only the building:part to be rendered, not the parent building.
        assertEquals(2, scene.renderableElements.size());
    }


    /**
     * Even more complex belonging topology test.
     * Part should be inside outer ring(s), but outside inner ring(s).
     */
    @Test
    void testMultipolygonBelonging() throws Exception {
        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("multipolygons_belonging.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        Scene.SceneUpdate update = scene.calculateUpdate(dataSet);
        scene.applyUpdate(update);

        // Assert: Verify the outcome
        // We expect only the building:part to be rendered, not the parent building.
        assertEquals(3, scene.renderableElements.size());
    }


    /**
     * Even more complex multipolygon belonging topology test.
     * This time with common nodes for both outer and inner rings.
     */
    @Test
    void testMultipolygonBelonging2() throws Exception {
        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("multipolygons_belonging2.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        Scene.SceneUpdate update = scene.calculateUpdate(dataSet);
        scene.applyUpdate(update);

        // Assert: Verify the outcome
        // We expect only the building:part to be rendered, not the parent building.
        assertEquals(1, scene.renderableElements.size());
    }


    @Test
    void roundRoofPentagon() throws Exception {

        DataSet dataSet = loadDataSetFromOsmFile("round_roof_pentagon.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        Scene.SceneUpdate update = scene.calculateUpdate(dataSet);
        scene.applyUpdate(update);

        // Assert: Verify the outcome
        // We expect only the building:part to be rendered, not the parent building.
        assertEquals(1, scene.renderableElements.size());
    }


    /**
     * Test various buildings just from raw osm data
     */
    @Test
    void testCityCenter() throws Exception {

        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("city_center.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        Scene.SceneUpdate update = scene.calculateUpdate(dataSet);
        scene.applyUpdate(update);

        // Assert: Verify the outcome
        //resulting number of  buildings is not so important.
        //Just to understand how the picture changes.
        long NumberOfBuildings = scene.renderableElements.stream().filter(e -> e.textureName == null).count();
        int MIN_BUILDINGS = 5152;
        int MAX_BUILDINGS = 5550;      //4395 - for all roofs;  4211 -- zero height parts excluded (without height inheritance); 5458 -- with gates; 5509 -- current state
        assertTrue(NumberOfBuildings >= MIN_BUILDINGS && NumberOfBuildings <= MAX_BUILDINGS, "Number of building " + NumberOfBuildings + " is NOT in the reasonable range " + MIN_BUILDINGS + ".." + MAX_BUILDINGS);

        if (SAVE_TEST_RESULTS_TO_FILE) {
            int i = 0;
            String outputFolder = Settings.prepareTestOutputFolder("city_center");
            for (var re : scene.renderableElements) {
                ru.zkir.urbaneye3d.utils.ObjExporter.saveMeshToObj(re.getMesh(), outputFolder + "/" + re.primitiveId.toString() + ".obj");
                //RoofGeneratorTopologyTest.AssertMeshTopology(re.getMesh(),  re.minHeight, re.height, re.roofShape.toString());
                i++;
            }
        }
    }


    /**
     * Here we test that buildings and building parts do not disappear suddenly.
     * Building is rendered even without specified height, and a part should follow
     * the height of the parent.
     */
    @Test
    void testPartWithoutHeight() throws Exception {
        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("part_without_height.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        Scene.SceneUpdate update = scene.calculateUpdate(dataSet);
        scene.applyUpdate(update);

        // Assert: Verify the outcome
        assertEquals(1, scene.renderableElements.size());

    }

    @Test
    void testSkillionSteps() throws Exception {
        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("steps.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        Scene.SceneUpdate update = scene.calculateUpdate(dataSet);
        scene.applyUpdate(update);

        // Assert: Verify the outcome
        assertEquals(1, scene.renderableElements.size());
        var re = scene.renderableElements.get(0);
        //ru.zkir.urbaneye3d.utils.ObjExporter.saveMeshToObj(re.getMesh(), "tests/output/skillion_steps.obj");
        RoofGeneratorTopologyTest.AssertMeshTopology(re.getMesh(), re.minHeight, re.height, "unknown");

    }

    @Test
    void testBarriers() throws Exception {
        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("barriers.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        Scene.SceneUpdate update = scene.calculateUpdate(dataSet);
        scene.applyUpdate(update);

        // Assert: Verify the outcome
        assertEquals(4, scene.renderableElements.size(), "Expected 4 barrier, but got " + scene.renderableElements.size());
        for (var re : scene.renderableElements) {
            RoofGeneratorTopologyTest.AssertMeshTopology(re.getMesh(), re.minHeight, re.height, "unknown");
        }
    }


    /**
     * This test checks to things:
     * 1. Man made should not be rendered, if specified as an outline role in building relation.
     * 2. Hyperboloids are generated correctly.
     */
    @Test
    void testManMadeAndParts1() throws Exception {
        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("shukhov_tower.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        Scene.SceneUpdate update = scene.calculateUpdate(dataSet);
        scene.applyUpdate(update);

        // Assert: Verify the outcome
        assertEquals(6, scene.renderableElements.size(), "Expected 6 building parts rendered, but got " + scene.renderableElements.size());
        for (var re : scene.renderableElements) {
            RoofGeneratorTopologyTest.AssertMeshTopology(re.getMesh(), re.minHeight, re.height, "unknown");
        }
    }

    /**
     * Man-made object is suppressed if there are parts inside, even without building relation.
     * (I doubt however that it is always true)
     */
    @Test
    void testManMadeAndParts2() throws Exception {
        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("manmade_and_parts.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        Scene.SceneUpdate update = scene.calculateUpdate(dataSet);
        scene.applyUpdate(update);

        // Assert: Verify the outcome
        assertEquals(1, scene.renderableElements.size(), "Expected 1 elements rendered, but got " + scene.renderableElements.size());
    }


    @Test
    void testSingleTree() {
        // Arrange
        DataSet dataSet = new DataSet();
        Node treeNode = new Node(new LatLon(55.75, 37.61));
        treeNode.put("natural", "tree");
        treeNode.put("height", "15");
        dataSet.addPrimitive(treeNode);

        Scene scene = new Scene();

        // Act
        Scene.SceneUpdate update = scene.calculateUpdate(dataSet);
        scene.applyUpdate(update);

        // Assert
        assertEquals(1, scene.renderableElements.size(), "Should have exactly one renderable element for the tree.");

        RenderableElement treeElement = scene.renderableElements.get(0);
        assertNotNull(treeElement.textureName, "The texture name should be set for the tree.");
        assertTrue(treeElement.textureName.contains("tree_"), "The texture name should contain 'tree_'.");

        Mesh treeMesh = treeElement.getMesh();
        assertNotNull(treeMesh, "Tree mesh should not be null.");

        // More detailed topology assertions
        assertBillboardTopology(treeMesh);
    }

    @Test
    void testForest() {
        // Arrange
        DataSet dataSet = new DataSet();
        Node n1 = new Node(new LatLon(55.750, 37.610));
        Node n2 = new Node(new LatLon(55.751, 37.610));
        Node n3 = new Node(new LatLon(55.751, 37.611));
        Node n4 = new Node(new LatLon(55.750, 37.611));
        dataSet.addPrimitive(n1);
        dataSet.addPrimitive(n2);
        dataSet.addPrimitive(n3);
        dataSet.addPrimitive(n4);

        Way forestWay = new Way();
        forestWay.setNodes(Arrays.asList(n1, n2, n3, n4, n1));
        forestWay.put("natural", "wood");
        dataSet.addPrimitive(forestWay);

        // Set high density for testing
        Config.getPref().putInt("urbaneye3d.forest-density", 100);

        Scene scene = new Scene();

        // Act
        Scene.SceneUpdate update = scene.calculateUpdate(dataSet);
        scene.applyUpdate(update);

        // Assert
        // Area is roughly 111m * 63m = 7000 m2.
        // treeDensity = 1.0 * 0.01 = 0.01 trees/m2.
        // Expected treeCount = 7000 * 0.01 = 70 trees.
        assertTrue(scene.renderableElements.size() > 10, "Should have generated multiple trees for the forest. Got: " + scene.renderableElements.size());

        for (RenderableElement element : scene.renderableElements) {

            assertTrue(element.textureName.contains("tree_"), "Unexpected tree texture name '" + element.textureName + "'");
            assertTrue(element.origin.lat() >= 55.7499 && element.origin.lat() <= 55.7511);
            assertTrue(element.origin.lon() >= 37.6099 && element.origin.lon() <= 37.6111);
        }
    }

    @Test
    void testMixedForest() {
        // Arrange
        DataSet dataSet = new DataSet();
        Node n1 = new Node(new LatLon(55.750, 37.610));
        Node n2 = new Node(new LatLon(55.751, 37.610));
        Node n3 = new Node(new LatLon(55.751, 37.611));
        Node n4 = new Node(new LatLon(55.750, 37.611));
        dataSet.addPrimitive(n1);
        dataSet.addPrimitive(n2);
        dataSet.addPrimitive(n3);
        dataSet.addPrimitive(n4);

        Way forestWay = new Way();
        forestWay.setNodes(Arrays.asList(n1, n2, n3, n4, n1));
        forestWay.put("natural", "wood");
        forestWay.put("leaf_type", "mixed");
        dataSet.addPrimitive(forestWay);

        // Set high density for testing
        Config.getPref().putInt("urbaneye3d.forest-density", 100);

        Scene scene = new Scene();

        // Act
        Scene.SceneUpdate update = scene.calculateUpdate(dataSet);
        scene.applyUpdate(update);

        // Assert
        assertTrue(scene.renderableElements.size() > 20, "Should have generated many trees for the mixed forest.");

        long broadleavedCount = scene.renderableElements.stream()
                .filter(e -> "/textures/trees/tree_000.png".equals(e.textureName))
                .count();
        long needleleavedCount = scene.renderableElements.stream()
                .filter(e -> "/textures/trees/tree_001.png".equals(e.textureName))
                .count();

        assertTrue(broadleavedCount > 0, "Mixed forest should contain broadleaved trees (/textures/trees/tree_000.png).");
        assertTrue(needleleavedCount > 0, "Mixed forest should contain needleleaved trees (/textures/trees/tree_001.png).");
    }

    private void assertBillboardTopology(Mesh mesh) {
        assertNotNull(mesh, "Mesh should not be null for a billboard.");
        assertEquals(8, mesh.verts.size(), "Billboard mesh should have 8 vertices.");
        assertEquals(2, mesh.faces.size(), "Billboard mesh should have 2 faces.");
        assertEquals(4, mesh.uvs.size(), "Billboard mesh should have 4 UV coordinates.");

        assertNoZeroLengthEdges(mesh, "BillboardTree");
        assertNoDuplicatesInFaces(mesh, "BillboardTree");
        assertFaceListEquity(mesh, "BillboardTree");
        assertBillboardNormals(mesh);
    }

    private void assertBillboardNormals(Mesh mesh) {
        assertEquals(2, mesh.faces.size(), "Billboard normal check requires exactly 2 faces.");

        // Calculate normal for the first face
        int[] face1 = mesh.faces.get(0);
        Point3D v0 = mesh.verts.get(face1[0]);
        Point3D v1 = mesh.verts.get(face1[1]);
        Point3D v2 = mesh.verts.get(face1[2]);
        Point3D normal1 = calculateNormal(v0, v1, v2);

        // Calculate normal for the second face
        int[] face2 = mesh.faces.get(1);
        v0 = mesh.verts.get(face2[0]);
        v1 = mesh.verts.get(face2[1]);
        v2 = mesh.verts.get(face2[2]);
        Point3D normal2 = calculateNormal(v0, v1, v2);

        // Assert that normals are perpendicular (dot product is close to zero)
        assertEquals(0, normal1.dot(normal2), 1e-6, "Normals of billboard planes should be perpendicular.");

        // Assert that normals are horizontal (Z component is zero)
        assertEquals(0, normal1.z, 1e-6, "Normal of the first plane should be horizontal (Z=0).");
        assertEquals(0, normal2.z, 1e-6, "Normal of the second plane should be horizontal (Z=0).");
    }

    @Test
    void testStreetLamp() throws Exception {
        // Arrange
        DataSet dataSet = loadDataSetFromOsmFile("street_lamp.osm");
        Scene scene = new Scene();

        // Act
        scene.applyUpdate(scene.calculateUpdate(dataSet));

        // Assert
        assertEquals(1, scene.renderableElements.size(), "Should have exactly one renderable element for the street lamp.");

        RenderableElement lampElement = scene.renderableElements.get(0);
        assertNotNull(lampElement.getMesh(), "Street lamp mesh should not be null.");
        assertTrue(!lampElement.getMesh().verts.isEmpty(), "Street lamp mesh should have vertices.");
        assertNull(lampElement.textureName, "The texture name should be null for the street lamp.");
    }

    @Test
    void testColoredModelLoads() {
        // Arrange
        ObjImporter importer = new ObjImporter();

        // Act
        Mesh mesh = importer.loadModel("/models/colored_cube.obj");

        // Assert
        assertNotNull(mesh, "Mesh should not be null.");
        assertEquals(8, mesh.verts.size(), "Should have 8 vertices.");
        assertEquals(6, mesh.faces.size(), "Should have 6 faces.");
        assertEquals(3, mesh.materials.size(), "Should have 3 materials loaded from .mtl file.");

        // Check that the specific colors were loaded
        assertTrue(mesh.materials.contains(Color.RED), "Material list should contain RED");
        assertTrue(mesh.materials.contains(Color.GREEN), "Material list should contain GREEN");
        assertTrue(mesh.materials.contains(Color.BLUE), "Material list should contain BLUE");

        // Check that the faces are assigned the correct materials
        long redFaces = mesh.faceMaterials.stream().map(i -> mesh.materials.get(i)).filter(c -> c.equals(Color.RED)).count();
        long greenFaces = mesh.faceMaterials.stream().map(i -> mesh.materials.get(i)).filter(c -> c.equals(Color.GREEN)).count();
        long blueFaces = mesh.faceMaterials.stream().map(i -> mesh.materials.get(i)).filter(c -> c.equals(Color.BLUE)).count();

        assertEquals(2, redFaces, "Should be 2 red faces.");
        assertEquals(2, greenFaces, "Should be 2 green faces.");
        assertEquals(2, blueFaces, "Should be 2 blue faces.");
    }

    @Test
    void testTreeSpeciesEnrichment() {
        // Arrange
        DataSet dataSet = new DataSet();
        Node treeNode = new Node(new LatLon(55.0, 37.0));
        treeNode.put("natural", "tree");
        treeNode.put("species", "Abies alba");
        dataSet.addPrimitive(treeNode);

        Scene scene = new Scene();

        // Act
        Scene.SceneUpdate update = scene.calculateUpdate(dataSet);
        scene.applyUpdate(update);

        // Assert
        assertEquals(1, scene.renderableElements.size());
        RenderableElement treeElement = scene.renderableElements.get(0);

        // Abies alba should be enriched to needleleaved, so it should get tree_001.png
        // (based on current textures.cfg)
        assertEquals("/textures/trees/tree_001.png", treeElement.textureName);
    }

    @Test
    /**
     * The main purpose of this test is to verify that barrier=gate makes hole in liner barrier and
     *  gate object put in the hole is oriented properly
     */
    void testBarrierGate() throws Exception {
        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("barrier_gate.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        Scene.SceneUpdate update = scene.calculateUpdate(dataSet);
        scene.applyUpdate(update);

        // Assert: Verify the outcome
        // We expect 2 elements: the wall and the the gate
        assertEquals(2, scene.renderableElements.size(), "Expected 2 elements, but got " + scene.renderableElements.size());

        RenderableElement wall = null;
        RenderableElement gate = null;
        for (var re : scene.renderableElements) {
            if (re.primitiveId.toString().equals("way -13723")) {
                wall = re;
            }

            if (re.primitiveId.toString().equals("node -5007079")) {
                gate = re;
            }
        }

        assertNotNull(wall, "Wall element not found");
        assertNotNull(gate, "Gate element not found");

        // The wall should have been split into two segments by the gate gap.
        // For a simple buffered line without gap, we'd have a single closed polygon (box).
        // With a gap in the middle, we have two separate polygons.
        // In our Mesh, this means more vertices/faces than a single box.
        // A single wall segment (box) has 24 vertices (6 faces * 4 verts).
        // Two wall segments should have roughly double.
        assertTrue(wall.getMesh().faces.size() >= 12, "Wall should have at least 12 faces (two segments), but got " + wall.getMesh().faces.size());

        // Verify gate orientation.
        assertEquals(120, Math.round(gate.direction) % 180, "gate should be orientated along the wall");

    }

    @Test
    /**
     * here we test automatic orientations of various objects
     */
    void testAutomaticOrientations() throws Exception {
        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("auto_orientation.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        Scene.SceneUpdate update = scene.calculateUpdate(dataSet);
        scene.applyUpdate(update);

        // Assert: Verify the outcome
        assertEquals(15, scene.renderableElements.size(), "Expected 15 elements, but got " + scene.renderableElements.size());

        // Mapping ref -> expected orientation (rounded and normalized to [0..359])
        java.util.Map<String, Integer> expectedOrientations = new java.util.HashMap<>();
        expectedOrientations.put("1", 120);
        expectedOrientations.put("2", 72);
        expectedOrientations.put("3", 120);
        expectedOrientations.put("4", 94);
        expectedOrientations.put("5", 301);
        expectedOrientations.put("6", 209);
        expectedOrientations.put("7", 0);
        expectedOrientations.put("8", 120);
        expectedOrientations.put("9", 298);
        expectedOrientations.put("10", 121);
        expectedOrientations.put("11", 102);
        expectedOrientations.put("12", 295);
        expectedOrientations.put("13", 107);
        expectedOrientations.put("14", 86);

        for (var re : scene.renderableElements) {
            OsmPrimitive primitive = dataSet.getPrimitiveById(re.primitiveId);

            String ref = primitive.get("ref");
            if (ref != null ) {
                int expected = expectedOrientations.get(ref);
                int actual = (int) (Math.round(re.direction + 360) % 360);
                assertEquals(expected, actual, "Orientation mismatch for ref=" + ref + " (" + primitive.getPrimitiveId() + ")" + " Note: " + primitive.get("note"));
            }

            //UrbanEye3dPlugin.debugMsg(ref + " "   + Math.round(re.direction+360) % 360 + " Note: " + primitive.get("note"));
        }

    }
}