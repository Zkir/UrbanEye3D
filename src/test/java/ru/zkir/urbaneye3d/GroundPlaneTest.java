package ru.zkir.urbaneye3d;

import com.jogamp.opengl.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.data.Preferences;

import ru.zkir.customtms.ImageryProvider;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;


public class GroundPlaneTest {

    private static final String TEST_OUTPUT_DIR = "target/test-output/ground-tile-creation";


    @BeforeAll
    public static void setup() throws IOException {
        //1. Partial init of JOSM runtime
        Config.setPreferencesInstance(new Preferences());

        // 2. Setup directories for test output
        Files.createDirectories(Paths.get(TEST_OUTPUT_DIR));
    }

    @AfterAll
    public static void teardown() {

    }

    @Test
    public void testGroundPlaneCreation() throws InterruptedException, IOException {
        // 1. Configure the environment

        //TODO: get rid of Scene and Renderer3D -- they are not really needed in this test

        var scene = new Scene();
        var renderer = new Renderer3D(scene);
        var groundPlane = scene.getGroundPlane();
        groundPlane.setRenderer(renderer);

        ImageryInfo testLayer = ImageryProvider.OSM_CARTO.getImageryInfo();

        // 2. Trigger tile creation and loading
        LatLon visibleAreaCenter = new LatLon(55.753960, 37.620393);
        groundPlane.update(visibleAreaCenter, testLayer);

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
            assertTrue(uniqueColors > 1000, "Rendered image does not seem to contain a map texture (too few colors).");
        }
        System.out.println("Images saved to: " + new File(TEST_OUTPUT_DIR).getAbsolutePath());

        // Cleanup after the test
        groundPlane.clearAllTiles();
    }

    private int countUniqueColors(BufferedImage image) {
        if (image == null) return 0;
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                colors.add(image.getRGB(x, y));
            }
        }
        return colors.size();
    }
}