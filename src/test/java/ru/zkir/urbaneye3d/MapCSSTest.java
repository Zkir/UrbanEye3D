package ru.zkir.urbaneye3d;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.preferences.JosmBaseDirectories;
import org.openstreetmap.josm.data.preferences.JosmUrls;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.data.projection.Projections;
import org.openstreetmap.josm.gui.mappaint.RenderingHelper;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.MemoryPreferences;
import org.openstreetmap.josm.tools.*;
import ru.zkir.urbaneye3d.mapcssproxy.MapCssProxy;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static ru.zkir.urbaneye3d.GroundPlane.ImageryType.MapCSS;
import static ru.zkir.urbaneye3d.utils.Settings.countUniqueColors;


public class MapCSSTest {
    private static final String TEST_OUTPUT_DIR = "target/test-output/map-css";
    private final DataSet dataSet =  loadDataSetFromOsmFile("city_center.osm");
    private final Bounds bounds = new Bounds(55.747526, 37.6072523, 55.759571,37.6278517 );

    public MapCSSTest() throws IllegalDataException {
    }


    @BeforeAll
    public static void setup() throws IOException {
        Files.createDirectories(Paths.get(TEST_OUTPUT_DIR));
        initialize();
    }

    /**
     *   This test checks rather our built-in MapCSS style itself.
     *   We just invoke Josm MapCSS engine directly.
     */
    @Test public void testMapCssStyle() throws Exception {

        double scale = 1;

        var mapCssProxy = new MapCssProxy();
        var styleUrls= new ArrayList<String>();
        styleUrls.add("resource://mapcss-styles/urbaneye2d.general.mapcss");
        styleUrls.add("resource://mapcss-styles/urbaneye2d.roads.mapcss");

        BufferedImage image=mapCssProxy.render(dataSet, bounds, scale, styleUrls);
        writeImageToFile(image);

        var colourCount=countUniqueColors(image);

        //The image is created, has expected resolution, and sane colours
        assertNotNull(image);
        assertTrue(image.getWidth()==2293 && image.getHeight()==2383);
        assertTrue( colourCount> 25000, "Rendered image does not seem to contain a map texture (too few colors: "+colourCount+").");

    }
    /**
     * In this test we check that ground plane tiles are created and get sane textures
     */
    @Test
    public void testGroundPlaneCreation() throws InterruptedException, IOException {
        // 1. Configure the environment
        var groundPlane = new GroundPlane();
        groundPlane.setRenderer(null);
        var testLayer = new GroundPlane.Layer2dInfo(MapCSS, null);

        LatLon visibleAreaCenter = bounds.getCenter();
        groundPlane.update(visibleAreaCenter, testLayer, dataSet, false);

        // 3. Wait for tiles to be ready
        boolean allReady = false;
        // Give it up to 20 seconds to download tiles and render
        for (int i = 0; i < 200; i++) {

            boolean allTilesHaveData = !groundPlane.getActiveTiles().isEmpty() &&
                    groundPlane.getActiveTiles().stream().allMatch(GroundTile::hasImageData);

            if (allTilesHaveData && groundPlane.getPendingTileUpdateRequests()==0) {
                allReady = true;
                System.out.println("All active tiles are ready to render after " + (i * 100) + " ms.");
                break;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        //Assertions.
        // we would like to test that:
        // 1) Expected number of tiles are created
        // 2) those tiles have image texture.
        // 3) Those tiles are motley enough, so we would consider them to be filled with map data.

        assertTrue(allReady, "Timeout: Not all ground tiles became ready to render in time.");

        assertEquals(12, groundPlane.getActiveTiles().size());
        for(var tile : groundPlane.getActiveTiles()){
            BufferedImage image = tile.getImage();
            assertNotNull(image, "Rendered image is null for tile " + tile.coord);
            File outputFile = new File(TEST_OUTPUT_DIR, "rendered-ground-plane" + tile.coord + ".png");
            ImageIO.write(image, "png", outputFile);

            int uniqueColors = countUniqueColors(image);

            //assertTrue(uniqueColors > 1000, "Rendered image does not seem to contain a map texture (too few colors).");
        }
        System.out.println("Images saved to: " + new File(TEST_OUTPUT_DIR).getAbsolutePath());

        // Cleanup after the test
        groundPlane.clearAllTiles();
    }


    private void writeImageToFile(BufferedImage image) throws IOException {
        File outputFile = new File(TEST_OUTPUT_DIR, "rendered image" + ".png");
        ImageIO.write(image, "png", outputFile);

    }

    static void initialize() {
        String argProjection=null;

        HttpClient.setFactory(Http1Client::new);

        Config.setBaseDirectoriesProvider(JosmBaseDirectories.getInstance()); // for right-left-hand traffic cache file
        Config.setPreferencesInstance(new MemoryPreferences());
        Config.setUrlsProvider(JosmUrls.getInstance());
        Config.getPref().putBoolean("mappaint.auto_reload_local_styles", false); // unnecessary to listen for external changes
        String projCode = Optional.ofNullable(argProjection).orElse("epsg:3857");
        Config.getPref().putList("mappaint.icon.sources", Arrays.asList("resource://mapcss-styles/", "resource://mapcss-styles/symbols/"));
        Config.getPref().putBoolean("mappaint.icon.enable-defaults", true);
        ProjectionRegistry.setProjection(Projections.getProjectionByCode(projCode.toUpperCase(Locale.US)));

        Territories.initializeInternalData();
    }

    private DataSet loadDataSetFromOsmFile(String resourceName) throws IllegalDataException {
        InputStream is = getClass().getResourceAsStream("/osm_test_files/" + resourceName);
        if (is == null) {
            throw new IllegalArgumentException("Resource not found: " + resourceName);
        }
        return OsmReader.parseDataSet(is, null);
    }




}
