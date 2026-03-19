package ru.zkir.urbaneye3d;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.preferences.JosmBaseDirectories;
import org.openstreetmap.josm.data.preferences.JosmUrls;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.data.projection.Projections;
import org.openstreetmap.josm.gui.mappaint.mapcss.MapCSSStyleSource;
import org.openstreetmap.josm.gui.mappaint.mapcss.parsergen.MapCSSParser;
import org.openstreetmap.josm.gui.mappaint.mapcss.parsergen.ParseException;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.MemoryPreferences;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.Http1Client;
import org.openstreetmap.josm.tools.ResourceProvider;
import org.openstreetmap.josm.tools.Territories;
import ru.zkir.urbaneye3d.mapcssproxy.MapCssProxy;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static ru.zkir.urbaneye3d.GroundPlane.ImageryType.MapCSS;
import static ru.zkir.urbaneye3d.utils.Settings.countUniqueColors;


public class MapCSSTest {
    private static final String TEST_OUTPUT_DIR = "target/test-output/map-css";
    private static final String MAPCSS_DIR = "src/main/resources/mapcss-styles";
    private final DataSet dataSet =  loadDataSetFromOsmFile("city_center.osm");
    private final Bounds bounds = new Bounds(55.747526, 37.6072523, 55.759571,37.6278517 );

    public MapCSSTest() throws IllegalDataException {
    }


    @BeforeAll
    public static void setup() throws IOException {
        Files.createDirectories(Paths.get(TEST_OUTPUT_DIR));
        initialize();

    }

    private static Stream<Path> mapCssFilesProvider() throws IOException {
        return Files.walk(Paths.get(MAPCSS_DIR))
                .filter(path -> path.toString().endsWith(".mapcss"));
    }

    @ParameterizedTest
    @MethodSource("mapCssFilesProvider")
    public void testMapCssFilesValidity(Path mapCssFile) throws IOException {
        String content = new String(Files.readAllBytes(mapCssFile));

        // 1. Validate syntax
        try (Reader reader = new StringReader(content)) {
            MapCSSStyleSource styleSource = new MapCSSStyleSource(content); // Needed for evalSupportsDeclCondition
            MapCSSParser parser = new MapCSSParser(reader, MapCSSParser.LexicalState.DEFAULT);
            parser.sheet(styleSource);
        } catch (ParseException e) {
            //TODO: unfortunately, JOSM does not really raise this error!!!
            //  Patch for JOSM is required!!
            fail("MapCSS file validation failed for " + mapCssFile + ": " + e.getMessage(), e);
        }

        // 2. Validate that all referenced resources exist
        List<String> resourcePaths = new ArrayList<>();

        // Find paths in url("...")
        Pattern urlPattern = Pattern.compile("url\\s*\\((['\"]?)(.*?)\\1\\)");
        java.util.regex.Matcher urlMatcher = urlPattern.matcher(content);
        while (urlMatcher.find()) {
            resourcePaths.add(urlMatcher.group(2));
        }

        // Find paths in fill-image: "..."; image: "..."; icon-image: "..."
        Pattern imagePattern = Pattern.compile("(?:pattern-image|fill-image|icon-image|image)\\s*:\\s*['\"]([^'\"]*?)['\"]");
        java.util.regex.Matcher imageMatcher = imagePattern.matcher(content);
        while (imageMatcher.find()) {
            resourcePaths.add(imageMatcher.group(1));
        }

        Path baseResourceDir = Paths.get("images"); // JOSM convention: plugin image resources are searched in /images folder at the root of classpath.

        int missingResources=0;
        for (String resourcePath : resourcePaths) {
            //if (resourcePath.startsWith("data:") || resourcePath.isEmpty()) {
            //    continue; // Skip data URIs or empty paths
            //}

            // The resource path for ResourceProvider is relative to the classpath root.
            // MapCSS files are in src/main/resources/mapcss-styles, which becomes /mapcss-styles/ in classpath.
            // So, a reference like url("symbols/forest.png") from a MapCSS file needs to be resolved as /mapcss-styles/symbols/forest.png.
            Path fullPath = baseResourceDir.resolve(resourcePath).normalize();
            String josmResourcePath = fullPath.toString().replace('\\', '/');

            if(ResourceProvider.getResource(josmResourcePath)==null){
                System.out.println("Resource not found for " + mapCssFile + ": '" + resourcePath + "' (resolved to '" + josmResourcePath + "')");
                missingResources++;
            }

        }
        assertEquals(0, missingResources, missingResources + " referenced images are missing");
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
        var testLayer = new GroundPlane.Layer2dInfo("Test dataset");

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
