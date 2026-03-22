package ru.zkir.urbaneye3d;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.spi.preferences.Config;
import ru.zkir.urbaneye3d.roofgenerators.RoofShapes;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static ru.zkir.urbaneye3d.RoofGeneratorTopologyTest.createRectangularBase;
import static ru.zkir.urbaneye3d.RoofGeneratorTopologyTest.createPentagonalBase;
import static ru.zkir.urbaneye3d.RoofGeneratorTopologyTest.createTestBuilding;

public class RoofGeneratorGoldenMasterTest {
    static {
        Config.setPreferencesInstance(new org.openstreetmap.josm.data.Preferences());
    }

    @Test
    void testGabledRoof_GoldenMaster() {
        ArrayList<Point2D> base = createRectangularBase(20, 10);
        BuildingRecipe test_building = createTestBuilding(base, RoofShapes.GABLED, 0, 5, 10);
        Mesh mesh = RoofShapes.GABLED.getMesher().generate(test_building);
        String result = ru.zkir.urbaneye3d.utils.ObjExporter.meshToString(mesh);
        String expected = "# Blender-compatible OBJ\n" +
                "v -10.000000 5.000000 -5.000000\n" +
                "v -10.000000 10.000000 0.000000\n" +
                "v -10.000000 5.000000 5.000000\n" +
                "v 10.000000 5.000000 -5.000000\n" +
                "v 10.000000 10.000000 0.000000\n" +
                "v 10.000000 5.000000 5.000000\n" +
                "v -10.000000 0.000000 -5.000000\n" +
                "v 10.000000 0.000000 -5.000000\n" +
                "v 10.000000 0.000000 5.000000\n" +
                "v -10.000000 0.000000 5.000000\n" +
                "\ng object_default\n" +
                "\n# Faces\n" +
                "f 1 2 3\n" +
                "f 6 5 4\n" +
                "f 1 4 5 2\n" +
                "f 2 5 6 3\n" +
                "f 8 4 1 7\n" +
                "f 9 6 4 8\n" +
                "f 10 3 6 9\n" +
                "f 7 1 3 10\n" +
                "f 7 10 9 8\n";

        assertEquals(expected.trim().replaceAll("\\s+", " "), result.trim().replaceAll("\\s+", " "));
    }


    @Test
    void testRoundRoof_GoldenMaster() {
        ArrayList<Point2D> base = createRectangularBase(20, 10);
        BuildingRecipe test_building = createTestBuilding(base, RoofShapes.ROUND, 0, 5, 10);
        Mesh mesh = RoofShapes.ROUND.getMesher().generate(test_building);
        String result = ru.zkir.urbaneye3d.utils.ObjExporter.meshToString(mesh);
        String expected = "# Blender-compatible OBJ\n" +
                //"mtllib default.mtl\n\n" +
                "v -10.000000 5.000000 -5.000000\n" +
                "v -10.000000 5.975000 -4.900000\n" +
                "v -10.000000 6.915000 -4.620000\n" +
                "v -10.000000 7.780000 -4.160000\n" +
                "v -10.000000 8.535000 -3.540000\n" +
                "v -10.000000 9.155000 -2.780000\n" +
                "v -10.000000 9.620000 -1.910000\n" +
                "v -10.000000 9.905000 -0.980000\n" +
                "v -10.000000 10.000000 0.000000\n" +
                "v -10.000000 9.905000 0.980000\n" +
                "v -10.000000 9.620000 1.910000\n" +
                "v -10.000000 9.155000 2.780000\n" +
                "v -10.000000 8.535000 3.540000\n" +
                "v -10.000000 7.780000 4.160000\n" +
                "v -10.000000 6.915000 4.620000\n" +
                "v -10.000000 5.975000 4.900000\n" +
                "v -10.000000 5.000000 5.000000\n" +
                "v 10.000000 5.000000 -5.000000\n" +
                "v 10.000000 5.975000 -4.900000\n" +
                "v 10.000000 6.915000 -4.620000\n" +
                "v 10.000000 7.780000 -4.160000\n" +
                "v 10.000000 8.535000 -3.540000\n" +
                "v 10.000000 9.155000 -2.780000\n" +
                "v 10.000000 9.620000 -1.910000\n" +
                "v 10.000000 9.905000 -0.980000\n" +
                "v 10.000000 10.000000 0.000000\n" +
                "v 10.000000 9.905000 0.980000\n" +
                "v 10.000000 9.620000 1.910000\n" +
                "v 10.000000 9.155000 2.780000\n" +
                "v 10.000000 8.535000 3.540000\n" +
                "v 10.000000 7.780000 4.160000\n" +
                "v 10.000000 6.915000 4.620000\n" +
                "v 10.000000 5.975000 4.900000\n" +
                "v 10.000000 5.000000 5.000000\n" +
                "v -10.000000 0.000000 -5.000000\n" +
                "v 10.000000 0.000000 -5.000000\n" +
                "v 10.000000 0.000000 5.000000\n" +
                "v -10.000000 0.000000 5.000000\n" +
                "\ng object_default\n" +
                "# Faces\n" +
                "f 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17\n" +
                "f 34 33 32 31 30 29 28 27 26 25 24 23 22 21 20 19 18\n" +
                "f 1 18 19 2\n" +
                "f 2 19 20 3\n" +
                "f 3 20 21 4\n" +
                "f 4 21 22 5\n" +
                "f 5 22 23 6\n" +
                "f 6 23 24 7\n" +
                "f 7 24 25 8\n" +
                "f 8 25 26 9\n" +
                "f 9 26 27 10\n" +
                "f 10 27 28 11\n" +
                "f 11 28 29 12\n" +
                "f 12 29 30 13\n" +
                "f 13 30 31 14\n" +
                "f 14 31 32 15\n" +
                "f 15 32 33 16\n" +
                "f 16 33 34 17\n" +
                "f 36 18 1 35\n" +
                "f 37 34 18 36\n" +
                "f 38 17 34 37\n" +
                "f 35 1 17 38\n" +
                "f 35 38 37 36\n";

        assertEquals(expected.trim().replaceAll("\\s+", " "), result.trim().replaceAll("\\s+", " "));
    }

    @Test
    void testGabledRoofPentagonalBase_GoldenMaster() {
        ArrayList<Point2D> base = createPentagonalBase();
        BuildingRecipe test_building = createTestBuilding(base, RoofShapes.GABLED, 0, 5, 10);
        Mesh mesh = RoofShapes.GABLED.getMesher().generate(test_building);
        String result = ru.zkir.urbaneye3d.utils.ObjExporter.meshToString(mesh);
        String expected = "# Blender-compatible OBJ\n" +
                "v -51.000000 0.000000 -223.000000\n" +
                "v -43.000000 0.000000 325.000000\n" +
                "v 129.000000 0.000000 323.000000\n" +
                "v 64.000000 0.000000 38.000000\n" +
                "v 120.000000 0.000000 -225.000000\n" +
                "v 129.000000 5.000000 323.000000\n" +
                "v -43.000000 5.000000 325.000000\n" +
                "v 43.000000 10.000000 324.000000\n" +
                "v -51.000000 5.000000 -223.000000\n" +
                "v 120.000000 5.058130 -225.000000\n" +
                "v 34.999915 10.000000 -224.005847\n" +
                "v 64.000000 8.536575 38.000000\n" +
                "\ng object_default\n" +
                "\n# Faces\n" +
                "f 1 2 3 4 5\n" +
                "f 3 2 7 8 6\n" +
                "f 2 1 9 7\n" +
                "f 1 5 10 11 9\n" +
                "f 5 4 12 10\n" +
                "f 4 3 6 12\n" +
                "f 11 10 12 6 8\n" +
                "f 8 7 9 11\n";

        assertEquals(expected.trim().replaceAll("\\s+", " "), result.trim().replaceAll("\\s+", " "));
    }


    @Test
    void testRoundRoofPentagonalBase_GoldenMaster() {
        ArrayList<Point2D> base = createPentagonalBase();
        BuildingRecipe test_building = createTestBuilding(base, RoofShapes.ROUND, 0, 5, 10);
        Mesh mesh = RoofShapes.ROUND.getMesher().generate(test_building);
        String result = ru.zkir.urbaneye3d.utils.ObjExporter.meshToString(mesh);
        String expected = "# Blender-compatible OBJ\n" +
                "v -51.000000 0.000000 -223.000000\n" +
                "v -43.000000 0.000000 325.000000\n" +
                "v 129.000000 0.000000 323.000000\n" +
                "v 64.000000 0.000000 38.000000\n" +
                "v 120.000000 0.000000 -225.000000\n" +
                "v 129.000000 5.000000 323.000000\n" +
                "v -43.000000 5.000000 325.000000\n" +
                "v -41.280000 5.975000 324.980000\n" +
                "v -36.464000 6.915000 324.924000\n" +
                "v -28.552000 7.780000 324.832000\n" +
                "v -17.888000 8.535000 324.708000\n" +
                "v -4.816000 9.155000 324.556000\n" +
                "v 10.148000 9.620000 324.382000\n" +
                "v 26.144000 9.905000 324.196000\n" +
                "v 43.000000 10.000000 324.000000\n" +
                "v 59.856000 9.905000 323.804000\n" +
                "v 75.852000 9.620000 323.618000\n" +
                "v 90.816000 9.155000 323.444000\n" +
                "v 103.888000 8.535000 323.292000\n" +
                "v 114.552000 7.780000 323.168000\n" +
                "v 122.464000 6.915000 323.076000\n" +
                "v 127.280000 5.975000 323.020000\n" +
                "v -51.000000 5.000000 -223.000000\n" +
                "v 120.000000 5.566764 -225.000000\n" +
                "v 119.279831 5.975000 -224.991577\n" +
                "v 114.463836 6.915000 -224.935250\n" +
                "v 106.551844 7.780000 -224.842712\n" +
                "v 95.887854 8.535000 -224.717987\n" +
                "v 82.815867 9.155000 -224.565098\n" +
                "v 67.851882 9.620000 -224.390080\n" +
                "v 51.855898 9.905000 -224.202993\n" +
                "v 34.999915 10.000000 -224.005847\n" +
                "v 18.143931 9.905000 -223.808701\n" +
                "v 2.147947 9.620000 -223.621613\n" +
                "v -12.816038 9.155000 -223.446596\n" +
                "v -25.888025 8.535000 -223.293707\n" +
                "v -36.552014 7.780000 -223.168982\n" +
                "v -44.464006 6.915000 -223.076444\n" +
                "v -49.280002 5.975000 -223.020117\n" +
                "v 64.000000 9.756854 38.000000\n" +
                "v 71.189476 9.620000 4.235137\n" +
                "v 85.195734 9.155000 -61.544249\n" +
                "v 97.431085 8.535000 -119.006701\n" +
                "v 107.412555 7.780000 -165.883965\n" +
                "v 114.818162 6.915000 -200.663870\n" +
                "v 119.325923 5.975000 -221.834247\n" +
                "v 127.162064 5.975000 314.941356\n" +
                "v 122.015842 6.915000 292.377152\n" +
                "v 113.561334 7.780000 255.307388\n" +
                "v 102.166128 8.535000 205.343794\n" +
                "v 88.197812 9.155000 144.098097\n" +
                "v 72.207765 9.620000 73.987892\n" +
                "\ng object_default\n" +
                "# Faces\n" +
                "f 1 2 3 4 5\n" +
                "f 3 2 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 6\n" +
                "f 2 1 23 7\n" +
                "f 1 5 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 23\n" +
                "f 5 4 40 41 42 43 44 45 46 24\n" +
                "f 4 3 6 47 48 49 50 51 52 40\n\n" +
                "f 47 6 22\n" +
                "f 25 24 46\n" +
                "f 46 45 26 25\n" +
                "f 22 21 48 47\n" +
                "f 45 44 27 26\n" +
                "f 21 20 49 48\n" +
                "f 44 43 28 27\n" +
                "f 20 19 50 49\n" +
                "f 43 42 29 28\n" +
                "f 19 18 51 50\n" +
                "f 42 41 30 29\n" +
                "f 18 17 52 51\n" +
                "f 41 40 52 17 16 31 30\n" +
                "f 16 15 32 31\n" +
                "f 15 14 33 32\n" +
                "f 14 13 34 33\n" +
                "f 13 12 35 34\n" +
                "f 12 11 36 35\n" +
                "f 11 10 37 36\n" +
                "f 10 9 38 37\n" +
                "f 9 8 39 38\n" +
                "f 8 7 23 39\n\n" ;
        assertEquals(expected.trim().replaceAll("\\s+", " "), result.trim().replaceAll("\\s+", " "));
    }
}
