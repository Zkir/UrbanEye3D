package ru.zkir.urbaneye3d;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.preferences.JosmBaseDirectories;
import org.openstreetmap.josm.data.preferences.JosmUrls;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.MemoryPreferences;
import ru.zkir.urbaneye3d.utils.BuildingPassage;
import ru.zkir.urbaneye3d.utils.FlatEarth;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point3D;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.zkir.urbaneye3d.RoofGeneratorTopologyTest.AssertMeshTopology;

/**
 * Tests for the support of {@code tunnel=building_passage}: the zone occupied by the passage
 * is cut out of the buildings it crosses, and the part of the building above the passage
 * is rendered as a separate piece with its own roof.
 * <p>
 * All the test scenes are built programmatically: buildings are rectangles defined in meters,
 * which are then converted to LatLon with the same projection the plugin uses.
 */
class BuildingPassageTest {

    private static final LatLon ORIGIN = new LatLon(55, 37);

    /**
     * The defaults are taken from the production code on purpose: the tests must follow the defaults
     * instead of duplicating them as their own literals.
     */
    private static final double PASSAGE_HEIGHT = BuildingPassage.DEFAULT_HEIGHT;
    private static final double PASSAGE_WIDTH = BuildingPassage.DEFAULT_WIDTH;

    @BeforeAll
    static void setUp() {
        Config.setPreferencesInstance(new MemoryPreferences());
        Config.setBaseDirectoriesProvider(JosmBaseDirectories.getInstance());
        Config.setUrlsProvider(JosmUrls.getInstance());
    }

    // ------------------------------------------------------------------ scene helpers

    private static Node addNode(DataSet dataSet, double x, double y) {
        Node node = new Node(FlatEarth.fromLocalCoords(x, y, ORIGIN));
        dataSet.addPrimitive(node);
        return node;
    }

    /** Adds a rectangular closed way. Coordinates are in meters, relative to ORIGIN. */
    private static void addRectangle(DataSet dataSet, double x1, double y1, double x2, double y2, Map<String, String> tags) {
        Way way = new Way();
        way.addNode(addNode(dataSet, x1, y1));
        way.addNode(addNode(dataSet, x2, y1));
        way.addNode(addNode(dataSet, x2, y2));
        way.addNode(addNode(dataSet, x1, y2));
        way.addNode(way.firstNode());
        way.setKeys(tags);
        dataSet.addPrimitive(way);
    }

    /** Adds an open linear way (the passage itself). Coordinates are in meters, relative to ORIGIN. */
    private static void addLine(DataSet dataSet, double[][] points, Map<String, String> tags) {
        Way way = new Way();
        for (double[] point : points) {
            way.addNode(addNode(dataSet, point[0], point[1]));
        }
        way.setKeys(tags);
        dataSet.addPrimitive(way);
    }

    private static Map<String, String> tags(String... keyValues) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put(keyValues[i], keyValues[i + 1]);
        }
        return result;
    }

    private static Map<String, String> passageTags(String... extraKeyValues) {
        Map<String, String> result = tags("highway", "footway", "tunnel", "building_passage");
        for (int i = 0; i < extraKeyValues.length; i += 2) {
            result.put(extraKeyValues[i], extraKeyValues[i + 1]);
        }
        return result;
    }

    private static Scene buildScene(DataSet dataSet) {
        Scene scene = new Scene();
        scene.applyUpdate(scene.calculateUpdate(dataSet));
        return scene;
    }

    // ------------------------------------------------------------------ mesh helpers

    private static double minZ(Mesh mesh) {
        return mesh.getMinBounds().z;
    }

    private static double maxZ(Mesh mesh) {
        return mesh.getMaxBounds().z;
    }

    /** Area of the horizontal projection of the bottom faces, i.e. the footprint of the solid. */
    private static double footprintArea(Mesh mesh) {
        double area = 0;
        for (int[] face : mesh.getBottomFaces()) {
            double sum = 0;
            for (int i = 0; i < face.length; i++) {
                Point3D a = mesh.verts.get(face[i]);
                Point3D b = mesh.verts.get(face[(i + 1) % face.length]);
                sum += a.x * b.y - b.x * a.y;
            }
            area += Math.abs(sum) / 2.0;
        }
        return area;
    }

    private static boolean hasVertexAtZ(Mesh mesh, double z) {
        for (Point3D vertex : mesh.verts) {
            if (Math.abs(vertex.z - z) < 1e-6) {
                return true;
            }
        }
        return false;
    }

    private static void assertAllMeshesAreValid(Scene scene) {
        for (RenderableElement element : scene.renderableElements) {
            Mesh mesh = element.getMesh();
            AssertMeshTopology(mesh, minZ(mesh), maxZ(mesh), "building passage");
        }
    }

    // ------------------------------------------------------------------ tests

    /**
     * The main case: a footway crosses a rectangular building. The building (20 x 10 m, 15 m high)
     * is split into two blocks by the default 3.5 m wide corridor, and the rest of the building is drawn above it.
     */
    @Test
    void testPassageThroughBuilding() {
        DataSet dataSet = new DataSet();
        addRectangle(dataSet, -10, -5, 10, 5, tags("building", "yes", "height", "15"));
        addLine(dataSet, new double[][]{{-15, 0}, {15, 0}}, passageTags());

        Scene scene = buildScene(dataSet);

        assertEquals(3, scene.renderableElements.size(), "two blocks below the passage plus the part above it");
        assertAllMeshesAreValid(scene);

        int blocks = 0;
        int above = 0;
        double blocksArea = 0;
        for (RenderableElement element : scene.renderableElements) {
            Mesh mesh = element.getMesh();
            if (maxZ(mesh) <= PASSAGE_HEIGHT + 1e-6) {
                blocks++;
                blocksArea += footprintArea(mesh);
                assertEquals(0.0, minZ(mesh), 1e-6, "the walls of the passage start from the ground");
                assertEquals(PASSAGE_HEIGHT, maxZ(mesh), 1e-6, "the passage is as high as the default");
            } else {
                above++;
                assertEquals(PASSAGE_HEIGHT, minZ(mesh), 1e-6, "the part above the passage starts at its ceiling");
                assertEquals(15.0, maxZ(mesh), 1e-6, "the roof of the building is not changed");
                assertEquals(200.0, footprintArea(mesh), 1e-6, "the part above the passage covers the whole building");
            }
        }
        assertEquals(2, blocks, "the passage splits the building in two");
        assertEquals(1, above);
        // 20 x 10 m building minus the 20 x 3.5 m corridor
        assertEquals(200.0 - 20.0 * PASSAGE_WIDTH, blocksArea, 1e-6);
    }

    /**
     * The part of the building above the passage keeps the roof shape (gabled in this test),
     * while the blocks below the passage always have flat roofs.
     */
    @Test
    void testPassageKeepsRoofShape() {
        DataSet dataSet = new DataSet();
        addRectangle(dataSet, -10, -5, 10, 5,
                tags("building", "yes", "height", "15", "roof:shape", "gabled", "roof:height", "4"));
        addLine(dataSet, new double[][]{{-15, 0}, {15, 0}}, passageTags());

        Scene scene = buildScene(dataSet);

        assertEquals(3, scene.renderableElements.size());
        assertAllMeshesAreValid(scene);

        for (RenderableElement element : scene.renderableElements) {
            Mesh mesh = element.getMesh();
            if (maxZ(mesh) <= PASSAGE_HEIGHT + 1e-6) {
                //the blocks below the passage are flat-roofed slabs
                assertEquals(1, mesh.getRoofFaces().size(), "the slab below the passage has a flat top");
                assertFalse(hasVertexAtZ(mesh, 11.0), "there is no gable below the passage");
            } else {
                //11 m is the level of the eaves (15 - 4), it exists only if the gabled roof is preserved
                assertEquals(2, mesh.getRoofFaces().size(), "the gabled roof is kept above the passage");
                assertTrue(hasVertexAtZ(mesh, 11.0), "the gabled roof above the passage has its eaves at 11 m");
                assertTrue(hasVertexAtZ(mesh, 15.0), "the ridge of the roof is still there");
            }
        }
    }

    /**
     * A passage which does not cross any building must not change anything.
     */
    @Test
    void testPassageAwayFromBuilding() {
        DataSet dataSet = new DataSet();
        addRectangle(dataSet, -10, -5, 10, 5, tags("building", "yes", "height", "15"));
        addLine(dataSet, new double[][]{{-15, 20}, {15, 20}}, passageTags());

        Scene scene = buildScene(dataSet);

        assertEquals(1, scene.renderableElements.size());
        assertEquals(0.0, minZ(scene.renderableElements.get(0).getMesh()), 1e-6);
        assertEquals(15.0, maxZ(scene.renderableElements.get(0).getMesh()), 1e-6);
    }

    /**
     * Only the building crossed by the passage is modified.
     */
    @Test
    void testOnlyCrossedBuildingIsCut() {
        DataSet dataSet = new DataSet();
        addRectangle(dataSet, -10, -5, 10, 5, tags("building", "yes", "height", "15"));
        addRectangle(dataSet, -10, 20, 10, 30, tags("building", "yes", "height", "9"));
        addLine(dataSet, new double[][]{{-15, 0}, {15, 0}}, passageTags());

        Scene scene = buildScene(dataSet);

        //two blocks + the part above the passage + the untouched building
        assertEquals(4, scene.renderableElements.size());
        assertAllMeshesAreValid(scene);
        long untouched = scene.renderableElements.stream()
                .filter(e -> maxZ(e.getMesh()) == 9.0)
                .count();
        assertEquals(1, untouched, "the building which is not crossed by the passage stays intact");
    }

    /**
     * A passage below a floating building part (min_height is above the passage) changes nothing.
     */
    @Test
    void testPassageBelowFloatingPart() {
        DataSet dataSet = new DataSet();
        addRectangle(dataSet, -10, -5, 10, 5, tags("building:part", "yes", "height", "20", "min_height", "6"));
        addLine(dataSet, new double[][]{{-15, 0}, {15, 0}}, passageTags());

        Scene scene = buildScene(dataSet);

        assertEquals(1, scene.renderableElements.size());
        assertEquals(6.0, minZ(scene.renderableElements.get(0).getMesh()), 1e-6);
        assertEquals(20.0, maxZ(scene.renderableElements.get(0).getMesh()), 1e-6);
    }

    /**
     * Passages at negative layers (underground crossings, subway entrances etc) are ignored.
     */
    @Test
    void testUndergroundPassageIsIgnored() {
        DataSet dataSet = new DataSet();
        addRectangle(dataSet, -10, -5, 10, 5, tags("building", "yes", "height", "15"));
        addLine(dataSet, new double[][]{{-15, 0}, {15, 0}}, passageTags("layer", "-1"));

        Scene scene = buildScene(dataSet);

        assertEquals(1, scene.renderableElements.size());
        assertEquals(0.0, minZ(scene.renderableElements.get(0).getMesh()), 1e-6);
        assertEquals(15.0, maxZ(scene.renderableElements.get(0).getMesh()), 1e-6);
    }

    /**
     * A way which ends inside the building (badly mapped or a real dead end) produces a notch
     * instead of a through passage.
     */
    @Test
    void testDeadEndPassageProducesNotch() {
        DataSet dataSet = new DataSet();
        addRectangle(dataSet, -10, -5, 10, 5, tags("building", "yes", "height", "15"));
        //the way is extended by 1 m, so the notch is cut from the west wall up to x = -1
        addLine(dataSet, new double[][]{{-15, 0}, {-2, 0}}, passageTags());

        Scene scene = buildScene(dataSet);

        assertEquals(2, scene.renderableElements.size(), "one notched block below the passage plus the part above it");
        assertAllMeshesAreValid(scene);

        double blocksArea = 0;
        for (RenderableElement element : scene.renderableElements) {
            if (maxZ(element.getMesh()) <= PASSAGE_HEIGHT + 1e-6) {
                blocksArea += footprintArea(element.getMesh());
            }
        }
        assertEquals(200.0 - 9.0 * PASSAGE_WIDTH, blocksArea, 1e-6);
    }

    /**
     * Height and width of the passage can be set with the tags of the way.
     */
    @Test
    void testPassageDimensionsFromTags() {
        DataSet dataSet = new DataSet();
        addRectangle(dataSet, -10, -5, 10, 5, tags("building", "yes", "height", "15"));
        //The values are deliberately different from the defaults, otherwise the test would not prove
        //that the tags of the way are respected at all.
        addLine(dataSet, new double[][]{{-15, 0}, {15, 0}}, passageTags("height", "6", "width", "9"));

        Scene scene = buildScene(dataSet);

        assertEquals(3, scene.renderableElements.size());
        assertAllMeshesAreValid(scene);

        double blocksArea = 0;
        for (RenderableElement element : scene.renderableElements) {
            Mesh mesh = element.getMesh();
            if (maxZ(mesh) <= 6.0 + 1e-6) {
                assertEquals(6.0, maxZ(mesh), 1e-6, "height is taken from the way");
                blocksArea += footprintArea(mesh);
            } else {
                assertEquals(6.0, minZ(mesh), 1e-6);
            }
        }
        assertEquals(200.0 - 20.0 * 9.0, blocksArea, 1e-6, "width is taken from the way");
    }

    /**
     * The fake ambient occlusion of the pieces of a cut object is calculated from the vertical range
     * of the whole original object, so the cut is not visible as a restart of the shading gradient.
     */
    @Test
    void testAmbientOcclusionRangeIsNotRestartedOnCutPieces() {
        DataSet dataSet = new DataSet();
        addRectangle(dataSet, -10, -5, 10, 5, tags("building", "yes", "height", "15"));
        addLine(dataSet, new double[][]{{-15, 0}, {15, 0}}, passageTags());

        Scene scene = buildScene(dataSet);

        assertEquals(3, scene.renderableElements.size());
        for (RenderableElement element : scene.renderableElements) {
            assertEquals(0.0, element.getAoMinHeight(), 1e-6, "shading starts at the bottom of the original building");
            assertEquals(15.0, element.getAoMaxHeight(), 1e-6, "shading ends at the roof of the original building");
        }

        //the shading is continuous at the ceiling of the passage
        RenderableElement block = elementWithMaxZ(scene, PASSAGE_HEIGHT);
        RenderableElement above = elementWithMaxZ(scene, 15.0);
        assertNotNull(block, "a block below the passage");
        assertNotNull(above, "the part above the passage");
        assertEquals(aoFactor(PASSAGE_HEIGHT, block), aoFactor(PASSAGE_HEIGHT, above), 1e-9,
                "the pieces must be shaded as one body");
    }

    /**
     * Objects which are not cut keep the ambient occlusion range of their own mesh.
     */
    @Test
    void testAmbientOcclusionRangeOfUntouchedObjects() {
        DataSet dataSet = new DataSet();
        addRectangle(dataSet, -10, -5, 10, 5, tags("building", "yes", "height", "15"));
        addRectangle(dataSet, 30, -5, 50, 5, tags("building:part", "yes", "height", "20", "min_height", "6"));
        addLine(dataSet, new double[][]{{-15, 0}, {15, 0}}, passageTags());

        Scene scene = buildScene(dataSet);

        assertEquals(4, scene.renderableElements.size(), "three pieces of the cut building plus an untouched part");
        for (RenderableElement element : scene.renderableElements) {
            if (Math.abs(minZ(element.getMesh()) - 6.0) < 1e-6) {
                assertEquals(6.0, element.getAoMinHeight(), 1e-6, "an untouched object keeps its own range");
                assertEquals(20.0, element.getAoMaxHeight(), 1e-6);
            } else {
                assertEquals(0.0, element.getAoMinHeight(), 1e-6);
                assertEquals(15.0, element.getAoMaxHeight(), 1e-6);
            }
        }
    }

    /** The same fake ambient occlusion formula as in Renderer3D.drawVertexWithFakeAO. */
    private static double aoFactor(double z, RenderableElement element) {
        double totalHeight = element.getAoMaxHeight() - element.getAoMinHeight();
        if (totalHeight <= 0.1) {
            return 1.0;
        }
        double factor = (1 - 0.3) + 0.3 * ((z - element.getAoMinHeight()) / totalHeight);
        return Math.max(0, Math.min(1, factor));
    }

    private static RenderableElement elementWithMaxZ(Scene scene, double maxZ) {
        for (RenderableElement element : scene.renderableElements) {
            if (Math.abs(maxZ(element.getMesh()) - maxZ) < 1e-6) {
                return element;
            }
        }
        return null;
    }

    /**
     * A building lower than the passage is cut through completely: there is nothing above the passage,
     * so both blocks become flat-roofed.
     */
    @Test
    void testPassageHigherThanBuilding() {
        DataSet dataSet = new DataSet();
        addRectangle(dataSet, -10, -5, 10, 5, tags("building", "yes", "height", "15"));
        addLine(dataSet, new double[][]{{-15, 0}, {15, 0}}, passageTags("height", "20"));

        Scene scene = buildScene(dataSet);

        assertEquals(2, scene.renderableElements.size(), "there is nothing above a 20 m passage");
        assertAllMeshesAreValid(scene);

        double blocksArea = 0;
        for (RenderableElement element : scene.renderableElements) {
            assertEquals(0.0, minZ(element.getMesh()), 1e-6);
            assertEquals(15.0, maxZ(element.getMesh()), 1e-6);
            assertEquals(1, element.getMesh().getRoofFaces().size());
            blocksArea += footprintArea(element.getMesh());
        }
        assertEquals(200.0 - 20.0 * PASSAGE_WIDTH, blocksArea, 1e-6);
    }

    /**
     * End-to-end test on a real .osm file. The same file
     * (src/test/resources/osm_test_files/building_passage.osm) is used for the manual test in JOSM:
     * it contains a through passage, a passage with explicit dimensions, a niche (the way stops inside
     * the building), a floating building part above the passage and an underground passage.
     */
    @Test
    void testBuildingPassageOsmFile() throws Exception {
        DataSet dataSet;
        try (InputStream is = getClass().getResourceAsStream("/osm_test_files/building_passage.osm")) {
            assertNotNull(is, "test file not found");
            dataSet = OsmReader.parseDataSet(is, null);
        }

        Scene scene = buildScene(dataSet);
        assertAllMeshesAreValid(scene);

        //Two buildings in this file are cut by passages without an explicit height, so their ranges
        //are built from the default one, and changing that default does not break the test.
        String defaultTop = rounded(PASSAGE_HEIGHT);
        Map<String, Integer> expected = new HashMap<>();
        expected.put("0.0-" + defaultTop, 3);   // blocks below the passages: two halves of the first building plus the notched one
        expected.put(defaultTop + "-15.0", 1);  // the part of the 15 m building above the passage
        expected.put("0.0-5.0", 2);             // blocks below the passage with height=5
        expected.put("5.0-15.0", 1);            // the gabled part above that passage
        expected.put(defaultTop + "-6.0", 1);   // the top of the low building, above the passage
        expected.put("6.0-20.0", 1);            // the floating building part: the passage is below it, so it is not cut
        expected.put("0.0-12.0", 1);            // the building with an underground passage: not cut

        Map<String, Integer> actual = new HashMap<>();
        for (RenderableElement element : scene.renderableElements) {
            String key = rounded(minZ(element.getMesh())) + "-" + rounded(maxZ(element.getMesh()));
            actual.merge(key, 1, Integer::sum);
        }
        assertEquals(expected, actual);

        //The pieces of a cut object are shaded as one body, so there are only as many ambient occlusion
        //ranges as there are original objects in the file: 6 m, 12 m, 15 m (two buildings) and the part from 6 to 20 m.
        Set<String> aoRanges = new TreeSet<>();
        for (RenderableElement element : scene.renderableElements) {
            aoRanges.add(rounded(element.getAoMinHeight()) + ".." + rounded(element.getAoMaxHeight()));
        }
        assertEquals(new TreeSet<>(Arrays.asList("0.0..6.0", "0.0..12.0", "0.0..15.0", "6.0..20.0")), aoRanges);
    }

    /** Rounds a height to millimeters, to make it usable as a key of a map. */
    private static String rounded(double z) {
        return Double.toString(Math.round(z * 1000) / 1000.0);
    }
}
