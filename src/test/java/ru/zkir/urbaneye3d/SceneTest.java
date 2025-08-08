package ru.zkir.urbaneye3d;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.spi.preferences.Config;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    /*
        If there building parts belonging to the building, the building it self is not rendered.
     */
    void testBuildingWithPartIsNotRendered() throws Exception {
        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("building_with_part.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        scene.updateData(dataSet);

        // Assert: Verify the outcome
        // We expect only the building:part to be rendered, not the parent building.
        assertEquals(1, scene.renderableElements.size());
    }

    @Test
    /*
        More complex belonging topology test
        Usually nodes of part lie on the same contour as building .
        it's not a problem with a simple bbox test of belonging,
        but is always very tricky for polygon/polygon topology test
     */
    void testNodesOnContourBelonging() throws Exception {
        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("nodes_on_contour.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        scene.updateData(dataSet);

        // Assert: Verify the outcome
        // We expect only the building:part to be rendered, not the parent building.
        assertEquals(2, scene.renderableElements.size());
    }

    @Test
    /*
        Even more complex belonging topology test
        part should be inside outer ring(s), but outside inner ring(s).

     */
    void testMultipolygonBelonging() throws Exception {
        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("multipolygons_belonging.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        scene.updateData(dataSet);

        // Assert: Verify the outcome
        // We expect only the building:part to be rendered, not the parent building.
        assertEquals(3, scene.renderableElements.size());
    }

    @Test
    /*
        Test various buildings just from raw osm data
     */
    void testCityCenter() throws Exception {
        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("city_center.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        scene.updateData(dataSet);

        // Assert: Verify the outcome
        //resulting number of  buildings is not so important.
        //Just to understan how picture changes.
        int NumberOfBuildings =scene.renderableElements.size();
        assertTrue(NumberOfBuildings>=4377 && NumberOfBuildings<=4381, "Number of building " + NumberOfBuildings + " in reasonable range");

        //4395 - for all roofs
        //4211 -- zero height parts excluded (without height inheritance)
    }

    @Test
    /*
        Here we test that buildings and building parts do not disappear suddenly.
        Building is rendered even without specified height, and a part should follow
        the height of the parent.
     */
    void testPartWithoutHeight() throws Exception {
        // Arrange: Load the specific test case
        DataSet dataSet = loadDataSetFromOsmFile("part_without_height.osm");
        Scene scene = new Scene();

        // Act: Run the method being tested
        scene.updateData(dataSet);

        // Assert: Verify the outcome
        assertEquals(1, scene.renderableElements.size());

    }




}
