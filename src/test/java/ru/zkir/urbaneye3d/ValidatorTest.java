package ru.zkir.urbaneye3d;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.data.preferences.JosmBaseDirectories;
import org.openstreetmap.josm.data.preferences.JosmUrls;
import ru.zkir.urbaneye3d.validator.SpatialConsistencyChecks;
import ru.zkir.urbaneye3d.validator.TagChecks;
import ru.zkir.urbaneye3d.validator.OverlappingWallsCheck;

import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class ValidatorTest {

    @BeforeAll
    public static void setUp() {
        Preferences prefs = Preferences.main();
        org.openstreetmap.josm.spi.preferences.Config.setPreferencesInstance(prefs);
        org.openstreetmap.josm.spi.preferences.Config.setBaseDirectoriesProvider(JosmBaseDirectories.getInstance());
        org.openstreetmap.josm.spi.preferences.Config.setUrlsProvider(JosmUrls.getInstance());
    }

    private DataSet loadDataSetFromOsmFile(String resourceName) throws Exception {
        InputStream is = getClass().getResourceAsStream("/osm_test_files/" + resourceName);
        if (is == null) {
            throw new IllegalArgumentException("Resource not found: " + resourceName);
        }
        return OsmReader.parseDataSet(is, null);
    }

    /**
     * In this test we check that the expected errors are raised (no false negatives)
     */
    @Test
    void testValidatorFalseNegatives() throws Exception {
        // Arrange
        DataSet dataSet = loadDataSetFromOsmFile("validator_test_errors.osm");
        MainApplication.getLayerManager().addLayer(new OsmDataLayer(dataSet, "test", null));
        SpatialConsistencyChecks validator1 = new SpatialConsistencyChecks();
        TagChecks validator2 = new TagChecks();

        //First test -- Spatial Consistency
        validator1.startTest(null);
        validator1.visit(dataSet.allPrimitives());
        validator1.endTest();
        var errors = validator1.getErrors();

        assertEquals(3, errors.size());

        assertEquals(1, errors.stream().filter(e -> e.getCode() == SpatialConsistencyChecks.BUILDING_NOT_COVERED_BY_PARTS).count());
        assertEquals(1, errors.stream().filter(e -> e.getCode() == SpatialConsistencyChecks.ORPHANED_BUILDING_PART).count());
        assertEquals(1, errors.stream().filter(e -> e.getCode() == SpatialConsistencyChecks.BUILDING_HEIGHT_MISMATCH).count());

        //Second test -- Tag Validity
        validator2.startTest(null);
        validator2.visit(dataSet.allPrimitives());
        validator2.endTest();
        errors = validator2.getErrors();
        assertEquals(7, errors.size());
        assertEquals(1, errors.stream().filter(e -> e.getCode() == TagChecks.NO_HEIGHT_OR_LEVELS_SPECIFIED).count());
        assertEquals(1, errors.stream().filter(e -> e.getCode() == TagChecks.INVALID_ROOF_ORIENTATION).count());
        assertEquals(1, errors.stream().filter(e -> e.getCode() == TagChecks.INVALID_ROOF_DIRECTION).count());
        assertEquals(1, errors.stream().filter(e -> e.getCode() == TagChecks.ROOF_DIRECTION_MISSING).count());
        assertEquals(1, errors.stream().filter(e -> e.getCode() == TagChecks.ROOF_SHAPE_MANY_NOT_ALLOWED_FOR_PARTS ).count());
        assertEquals(1, errors.stream().filter(e -> e.getCode() == TagChecks.UNKNOWN_TREE_SPECIES).count());
        assertEquals(1, errors.stream().filter(e -> e.getCode() == TagChecks.UNKNOWN_TREE_GENUS).count());

    }

    /**
     * In this test we check that in case of valid elements there are no false positives
     */
    @Test
    void testValidatorFalsePositives() throws Exception {
        // Arrange
        DataSet dataSet = loadDataSetFromOsmFile("validator_test_no_errors.osm");
        MainApplication.getLayerManager().addLayer(new OsmDataLayer(dataSet, "test", null));
        SpatialConsistencyChecks validator1 = new SpatialConsistencyChecks();
        TagChecks validator2 = new TagChecks();

        //First test -- Spatial Consistency
        validator1.startTest(null);
        validator1.visit(dataSet.allPrimitives());
        validator1.endTest();
        assertEquals(0, validator1.getErrors().size());

        //Second test -- Tag Validity
        validator2.startTest(null);
        validator2.visit(dataSet.allPrimitives());
        validator2.endTest();
        assertEquals(0, validator2.getErrors().size());
    }

    @Test
    void testValidatorFalsePositives2() throws Exception {
        // Arrange
        DataSet dataSet = loadDataSetFromOsmFile("shukhov_tower.osm");
        MainApplication.getLayerManager().addLayer(new OsmDataLayer(dataSet, "test", null));
        SpatialConsistencyChecks validator = new SpatialConsistencyChecks();

        //First test -- Spatial Consistency
        validator.startTest(null);
        validator.visit(dataSet.allPrimitives());
        validator.endTest();

        // Assert
        assertEquals(0, validator.getErrors().size());
    }

    @Test
    void testCityCenter() throws Exception {
        // Arrange
        DataSet dataSet = loadDataSetFromOsmFile("city_center.osm");
        MainApplication.getLayerManager().addLayer(new OsmDataLayer(dataSet, "test", null));
        SpatialConsistencyChecks validator1 = new SpatialConsistencyChecks();
        TagChecks validator2 = new TagChecks();

        //First test -- Spatial Consistency
        validator1.startTest(null);
        validator1.visit(dataSet.allPrimitives());
        validator1.endTest();
        var errors = validator1.getErrors();

        // The resulting number of buildings is not so important.
        // we just need to understand how the picture changes.
        int EXPECTED_NUMBER_OF_ERRORS = 806;
        assertTrue(errors.size() == EXPECTED_NUMBER_OF_ERRORS,
                   "Number of errors  found by the Validator Spatial Test ("+errors.size()+") differs from  the expected number (" + EXPECTED_NUMBER_OF_ERRORS+")");


        //Second test -- Tag Validity
        validator2.startTest(null);
        validator2.visit(dataSet.allPrimitives());
        validator2.endTest();
        errors = validator2.getErrors();

        // The resulting number of buildings is not so important.
        // we just need to understand how the picture changes.
        EXPECTED_NUMBER_OF_ERRORS = 180;
        assertTrue(errors.size() == EXPECTED_NUMBER_OF_ERRORS,
                   "Number of errors  found by the Validator Tag Test ("+errors.size()+") differs from  the expected number (" + EXPECTED_NUMBER_OF_ERRORS+")");
    }

    @Test
    void testOverlappingWalls() throws Exception {
        // Arrange
        DataSet dataSet = loadDataSetFromOsmFile("validator_overlapping_walls.osm");
        MainApplication.getLayerManager().addLayer(new OsmDataLayer(dataSet, "test", null));
        OverlappingWallsCheck validator = new OverlappingWallsCheck();

        // Act
        validator.startTest(null);
        validator.visit(dataSet.allPrimitives());
        validator.endTest();
        var errors = validator.getErrors();

        // Assert
        // We expect 5 errors (pairs of conflicting objects).
        // Case 3 (touching walls -105, -106) should NOT have errors.
        assertEquals(5, errors.size());
        assertEquals(5, errors.stream().filter(e -> e.getCode() == OverlappingWallsCheck.OVERLAPPING_3D_WALLS).count());
    }

    @Test
    void testOverlappingSidesFalsePositives() throws Exception {
        // Arrange
        DataSet dataSet = loadDataSetFromOsmFile("validator_overlapping_walls_no_errors.osm");
        MainApplication.getLayerManager().addLayer(new OsmDataLayer(dataSet, "test", null));
        OverlappingWallsCheck validator = new OverlappingWallsCheck();

        // Act
        validator.startTest(null);
        validator.visit(dataSet.allPrimitives());
        validator.endTest();
        var errors = validator.getErrors();

        // Assert
        assertEquals(0, errors.size(), "Should not have false positive overlapping wall errors in this sample");
    }

}
