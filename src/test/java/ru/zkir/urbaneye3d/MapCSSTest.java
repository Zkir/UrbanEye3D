package ru.zkir.urbaneye3d;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.preferences.JosmBaseDirectories;
import org.openstreetmap.josm.data.preferences.JosmUrls;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.data.projection.Projections;
import org.openstreetmap.josm.gui.mappaint.RenderingCLI;
import org.openstreetmap.josm.gui.mappaint.RenderingHelper;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.MemoryPreferences;
import org.openstreetmap.josm.tools.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static ru.zkir.urbaneye3d.utils.Settings.countUniqueColors;


public class MapCSSTest {
    private static final String TEST_OUTPUT_DIR = "target/test-output/map-css";


    @BeforeAll
    public static void setup() throws IOException {


        Files.createDirectories(Paths.get(TEST_OUTPUT_DIR));
        initialize();


    }

    /**
     *   This test checks rather our built-in MapCSS style itself.
     *   We just invoke Josm MapCSS engine directly.
     */
    @Test public void testJMapCssStyle() throws Exception {

        List<RenderingHelper.StyleData> argStyles;
        argStyles = new ArrayList<>();
        var argCurrentStyle = new RenderingHelper.StyleData();
        argCurrentStyle.styleUrl = "resource://mapcss-styles/urbaneye2d.mapcss";

        argStyles.add(argCurrentStyle);

        DataSet ds =  loadDataSetFromOsmFile("city_center.osm");
        Bounds bounds = new Bounds(55.747526, 37.6072523, 55.759571,37.6278517 );
        double scale = 1;

        RenderingHelper rh = new RenderingHelper(ds, bounds, scale, argStyles);
        //checkPreconditions(rh);
        BufferedImage image = rh.render();

        writeImageToFile(image);

        //The image is created, has expected resolution, and sane colours
        assertNotNull(image);
        assertTrue(image.getWidth()==2293 && image.getHeight()==2383);
        assertTrue(countUniqueColors(image) > 50000, "Rendered image does not seem to contain a map texture (too few colors).");

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
