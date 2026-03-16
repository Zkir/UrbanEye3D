package ru.zkir.urbaneye3d;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.spi.preferences.Config;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point3D;
import ru.zkir.urbaneye3d.utils.Settings;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.coor.LatLon;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static ru.zkir.urbaneye3d.RoofGeneratorTopologyTest.*;
import static ru.zkir.urbaneye3d.utils.Settings.SAVE_TEST_RESULTS_TO_FILE;

class SceneTest {

    @BeforeAll
    public static void setUp() {
        Config.setPreferencesInstance(new Preferences());
    }

    private DataSet loadDataSetFromOsmFile(String resourceName) throws Exception {
        InputStream is = getClass().getResourceAsStream("/osm_test_files/" + resourceName);
        if (is == null) {
            throw new IllegalArgumentException("Resource not found: " + resourceName);
        }
        return OsmReader.parseDataSet(is, null);
    }

    /**
      *  If there building parts belonging to the building, the building itself is not rendered.
     */
    @Test
    void testBuildingWithPartIsNotRendered() throws Exception {
        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("building_with_part.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        scene.updateData(dataSet, null);

        // Assert: Verify the outcome
        // We expect only the building:part to be rendered, not the parent building.
        assertEquals(1, scene.renderableElements.size());
    }


    /**
     *  More complex belonging topology test.
     *    Usually nodes of part lie on the same contour as building.
     *    it's not a problem with a simple bbox test of belonging,
     *    but is always very tricky for polygon/polygon topology test
     */
    @Test
    void testNodesOnContourBelonging() throws Exception {
        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("nodes_on_contour.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        scene.updateData(dataSet, null);

        // Assert: Verify the outcome
        // We expect only the building:part to be rendered, not the parent building.
        assertEquals(2, scene.renderableElements.size());
    }


    /**
      *   Even more complex belonging topology test.
      *   Part should be inside outer ring(s), but outside inner ring(s).
     */
    @Test
    void testMultipolygonBelonging() throws Exception {
        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("multipolygons_belonging.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        scene.updateData(dataSet, null);

        // Assert: Verify the outcome
        // We expect only the building:part to be rendered, not the parent building.
        assertEquals(3, scene.renderableElements.size());
    }


    /**
     *   Even more complex multipolygon belonging topology test.
     *   This time with common nodes for both outer and inner rings.
     */
    @Test
    void testMultipolygonBelonging2() throws Exception {
        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("multipolygons_belonging2.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        scene.updateData(dataSet, null);

        // Assert: Verify the outcome
        // We expect only the building:part to be rendered, not the parent building.
        assertEquals(1, scene.renderableElements.size());
    }


    @Test
    void roundRoofPentagon() throws Exception{

        DataSet dataSet = loadDataSetFromOsmFile("round_roof_pentagon.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        scene.updateData(dataSet, null);

        // Assert: Verify the outcome
        // We expect only the building:part to be rendered, not the parent building.
        assertEquals(1, scene.renderableElements.size());
    }


    /**
      *  Test various buildings just from raw osm data
     */
    @Test
    void testCityCenter() throws Exception {

        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("city_center.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        scene.updateData(dataSet, null);

        // Assert: Verify the outcome
        //resulting number of  buildings is not so important.
        //Just to understand how the picture changes.
        long NumberOfBuildings = scene.renderableElements.stream().filter( e-> e.textureName == null).count();
        int MIN_BUILDINGS=4377;
        int MAX_BUILDINGS=4700;      //4395 - for all roofs;  4211 -- zero height parts excluded (without height inheritance)
        assertTrue(NumberOfBuildings>=MIN_BUILDINGS && NumberOfBuildings<=MAX_BUILDINGS, "Number of building " + NumberOfBuildings + " is NOT in the reasonable range " + MIN_BUILDINGS + ".." + MAX_BUILDINGS);

        if (SAVE_TEST_RESULTS_TO_FILE) {
            int i = 0;
            String outputFolder = Settings.prepareTestOutputFolder("city_center");
            for (var re : scene.renderableElements) {
                ru.zkir.urbaneye3d.utils.ObjExporter.saveMeshToObj(re.getMesh(), outputFolder + "/"  + re.primitiveId.toString() + ".obj");
                //RoofGeneratorTopologyTest.AssertMeshTopology(re.getMesh(),  re.minHeight, re.height, re.roofShape.toString());
                i++;
            }
        }
    }


    /**
     *   Here we test that buildings and building parts do not disappear suddenly.
     *   Building is rendered even without specified height, and a part should follow
     *   the height of the parent.
     */
    @Test
    void testPartWithoutHeight() throws Exception {
        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("part_without_height.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        scene.updateData(dataSet, null);

        // Assert: Verify the outcome
        assertEquals(1, scene.renderableElements.size());

    }

    @Test
    void testSkillionSteps() throws Exception {
        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("steps.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        scene.updateData(dataSet, null);

        // Assert: Verify the outcome
        assertEquals(1, scene.renderableElements.size());
        var re = scene.renderableElements.get(0);
        //ru.zkir.urbaneye3d.utils.ObjExporter.saveMeshToObj(re.getMesh(), "tests/output/skillion_steps.obj");
        RoofGeneratorTopologyTest.AssertMeshTopology(re.getMesh(),  re.minHeight, re.height, "unknown");

    }

    @Test
    void testBarriers() throws Exception {
        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("barriers.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        scene.updateData(dataSet, null);

        // Assert: Verify the outcome
        assertEquals(4, scene.renderableElements.size(), "Expected 4 barrier, but got "+scene.renderableElements.size());
        for (var re:scene.renderableElements) {
            RoofGeneratorTopologyTest.AssertMeshTopology(re.getMesh(), re.minHeight, re.height, "unknown");
        }
    }


    /** This test checks to things:
     *  1. Man made should not be rendered, if specified as an outline role in building relation.
     *  2. Hyperboloids are generated correctly.
    */
    @Test
    void testManMadeAndParts1() throws Exception {
        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("shukhov_tower.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        scene.updateData(dataSet, null);

        // Assert: Verify the outcome
        assertEquals(6, scene.renderableElements.size(), "Expected 6 building parts rendered, but got "+scene.renderableElements.size());
        for (var re:scene.renderableElements) {
            RoofGeneratorTopologyTest.AssertMeshTopology(re.getMesh(), re.minHeight, re.height, "unknown");
        }
    }
	
	/**
     *  Man-made object is suppressed if there are parts inside, even without building relation.
     *  (I doubt however that it is always true)
     */
    @Test
    void testManMadeAndParts2() throws Exception {
        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("manmade_and_parts.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        scene.updateData(dataSet, null);

        // Assert: Verify the outcome
        assertEquals(1, scene.renderableElements.size(), "Expected 1 elements rendered, but got "+scene.renderableElements.size());
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
        scene.updateData(dataSet, null);

        // Assert
        assertEquals(1, scene.renderableElements.size(), "Should have exactly one renderable element for the tree.");

        RenderableElement treeElement = scene.renderableElements.get(0);
        assertNotNull(treeElement.textureName, "The texture name should be set for the tree.");
        assertTrue(treeElement.textureName.startsWith("tree_") , "The texture name should start with 'tree_'.");

        Mesh treeMesh = treeElement.getMesh();
        assertNotNull(treeMesh, "Tree mesh should not be null.");

        // More detailed topology assertions
        assertBillboardTopology(treeMesh);
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
}
