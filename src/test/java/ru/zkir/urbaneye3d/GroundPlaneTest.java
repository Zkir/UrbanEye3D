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
import ru.zkir.customtms.TileCache;

import static org.junit.jupiter.api.Assertions.*;
import static ru.zkir.customtms.ImageryProvider.STUB_NO_HTTP;
import static ru.zkir.urbaneye3d.GroundPlane.ImageryType.TMS;
import static ru.zkir.urbaneye3d.utils.Settings.countUniqueColors;


public class GroundPlaneTest {

    private static final String TEST_OUTPUT_DIR = "target/test-output/ground-tile-creation";


    @BeforeAll
    public static void setup() throws IOException {
        //1. Partial init of JOSM runtime
        Config.setPreferencesInstance(new Preferences());

        // 2. Setup directories for test output
        Files.createDirectories(Paths.get(TEST_OUTPUT_DIR));

        System.setProperty("urbaneye3d.unittest", "true");
    }

    @BeforeEach void prepare(){
        TileCache.getInstance().clearCache();
    }

    @AfterAll
    public static void teardown() {

    }

    /**
     * In this test we check that ground plane tiles are created and get sane textures
     */
    @Test
    public void testGroundPlaneCreation() throws InterruptedException, IOException {
        // 1. Configure the environment

        var groundPlane = new GroundPlane();
        groundPlane.setRenderer(null);

        var testLayer = new GroundPlane.Layer2dInfo(STUB_NO_HTTP.getImageryInfo());

        // 2. Trigger tile creation and loading
        LatLon visibleAreaCenter = new LatLon(55.753960, 37.620393);
        groundPlane.update(visibleAreaCenter, testLayer, null, false, null);

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

    /**
     *  We would like to prevent the unpleasant situation when ground plane tiles are disposed,
     *  but the download still continues
     */
    @Test
    public void testGroundPlaneDisposeTiles() throws InterruptedException, IOException {

        // 1. Configure the environment
        var groundPlane = new GroundPlane();
        groundPlane.setRenderer(null);

        var testLayer = new GroundPlane.Layer2dInfo(STUB_NO_HTTP.getImageryInfo());

        // 2. Trigger tile creation and loading
        LatLon visibleAreaCenter = new LatLon(55.753960, 37.620393);
        groundPlane.update(visibleAreaCenter, testLayer, null, false, null);
        //wait a little bit until the download process starts.
        TimeUnit.MILLISECONDS.sleep(100);
        assertTrue(groundPlane.getPendingTileUpdateRequests() > 0, "To continue test, we need some pending tile requests");

        //3. Imagery layer is turned off. Nothing to draw or request from tms tile cache.
        groundPlane.update(visibleAreaCenter, null, null, false, null);

        //4. Assertions
        // we would like to test that:
        // 1) There are no ground tiles
        // 2) There are no pending tile requests

        assertEquals(0, groundPlane.getActiveTiles().size(), "Since tiles are cleared, no active tiles expected");
        //delayed updates for ground "big" tiles
        assertEquals(0, groundPlane.getPendingGroundTilePaintRequests() , "There are no tiles after layer removal, so there should be no texture paint requests");
        //delayed updates for TMS "small" tiles
        assertEquals(0, groundPlane.getPendingTileUpdateRequests() , "There are no tiles after layer removal, so there should be no tms download requests");

    }

    /**
     *  Here we imitate some kind of DDOS attack for the tms server.
     *  if map is panned quickly, tens of thousands tile requests can be created.
     *  obviously, when view point changed, all those requests become obsolete
     */
    @Test
    public void testGroundPlaneSpeedPan() throws InterruptedException {
        // 1. Configure the environment
        var groundPlane = new GroundPlane();
        groundPlane.setRenderer(null);

        var testLayer = new GroundPlane.Layer2dInfo(STUB_NO_HTTP.getImageryInfo());

        // 2. Trigger tile creation and loading
        for (int i=-179; i<=179; i++) {
            LatLon visibleAreaCenter = new LatLon(55.75, i);
            groundPlane.update(visibleAreaCenter, testLayer, null, false, null);
            //wait a little bit until the download process starts.
            TimeUnit.MILLISECONDS.sleep(5);
            //assertTrue(groundPlane.getPendingTileUpdateRequests() > 0, "To continue test, we need some pending tile requests");
        }
        System.out.println("Pending ground tile paint requests: " + groundPlane.getPendingGroundTilePaintRequests());
        System.out.println("Pending tms tile download request: " + groundPlane.getPendingTileUpdateRequests());

        //4. Assertions
        // we would like to test that:
        // 1) There are no ground tiles
        // 2) There are no pending tile requests

        assertEquals(8, groundPlane.getActiveTiles().size(), "Normal number of ground tiles should be present");
        //delayed updates for ground "big" tiles
        assertTrue(groundPlane.getPendingGroundTilePaintRequests() <= groundPlane.getAllTiles().size() , "There is limited number of ground tiles, active and cache, so number of pending paint requests should be also limited");
        //delayed updates for TMS "small" tiles
        assertTrue( groundPlane.getPendingTileUpdateRequests() < 256, "There is limited number of ground tiles, active and cache, so number of pending download requests should be also limited");

        // Cleanup after the test
        groundPlane.clearAllTiles();

    }

}