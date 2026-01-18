package ru.zkir.urbaneye3d;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.data.preferences.JosmBaseDirectories;
import org.openstreetmap.josm.data.preferences.JosmUrls;
import ru.zkir.urbaneye3d.validator.SpatialConsistencyChecks;
import ru.zkir.urbaneye3d.validator.TagChecks;

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
        assertEquals(4, errors.size());
        assertEquals(1, errors.stream().filter(e -> e.getCode() == TagChecks.NO_HEIGHT_OR_LEVELS_SPECIFIED).count());
        assertEquals(1, errors.stream().filter(e -> e.getCode() == TagChecks.INVALID_ROOF_ORIENTATION).count());
        assertEquals(1, errors.stream().filter(e -> e.getCode() == TagChecks.INVALID_ROOF_DIRECTION).count());
        assertEquals(1, errors.stream().filter(e -> e.getCode() == TagChecks.ROOF_DIRECTION_MISSING).count());

    }

    /**
     * In this test we check that in case of valid elements there are no false positives
     */
    @Test
    void testValidatorFalsePositives() throws Exception {
        // Arrange
        DataSet dataSet = loadDataSetFromOsmFile("validator_test_no_errors.osm");
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
}
