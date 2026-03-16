package ru.zkir.urbaneye3d;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.spi.preferences.Config;
import ru.zkir.urbaneye3d.utils.Settings;

import java.io.File;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        int NumberOfBuildings = scene.renderableElements.size();
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
        RoofGeneratorTopologyTest.AssertMeshTopology(re.getMesh(),  re.minHeight, re.height, re.roofShape.toString());

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
            RoofGeneratorTopologyTest.AssertMeshTopology(re.getMesh(), re.minHeight, re.height, re.roofShape.toString());
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
            RoofGeneratorTopologyTest.AssertMeshTopology(re.getMesh(), re.minHeight, re.height, re.roofShape.toString());
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

}
