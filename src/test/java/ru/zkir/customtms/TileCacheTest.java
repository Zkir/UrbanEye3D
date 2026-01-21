package ru.zkir.customtms;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.imagery.ImageryInfo;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TileCacheTest {
    private static final String TEST_OUTPUT_DIR = "target/test-output/race-condition";

    @BeforeAll
    public static void InitTest() throws IOException {
        Files.createDirectories(Paths.get(TEST_OUTPUT_DIR));
    }

    /**
     * Data race is no longer possible, but we still test that we can request tiles from different layers randomly.
     */
    @Test
    public void testRaceConditionOnSetImagery() throws InterruptedException, IOException {
        final int zoom = 10;
        final int x = 588; // Some tile over Europe
        final int y = 360;

        final TileCache tileCache = TileCache.getInstance();
        tileCache.clearCache();
        final ImageryInfo imageryA = ImageryProvider.OSM_CARTO.getImageryInfo();
        final ImageryInfo imageryB = ImageryProvider.ESRI_WORLD_IMAGERY.getImageryInfo();

        // 1. Trigger a download for imageryA
        tileCache.getTile(imageryA, zoom, x, y); // Returns null, but triggers async download

        // 2. Immediately switch imagery, to cause the race condition
        tileCache.getTile(imageryB, zoom, x, y);

        // 3. Wait for the download to complete
        while (tileCache.getPendingRequestsCount() > 0) {
            Thread.sleep(100);
        }

        // 4. Now, check what's in the cache for imageryA
        BufferedImage cachedTile = tileCache.getTile(imageryA, zoom, x, y);

        // 5. Manually download the correct tile for imageryA for comparison
        BufferedImage correctTileA = downloadTileDirectly(imageryA.getUrl(), zoom, x, y);
        Assertions.assertNotNull(correctTileA, "Failed to download correct tile for imagery A to compare");

        BufferedImage correctTileB = downloadTileDirectly(imageryB.getUrl(), zoom, x, y);
        Assertions.assertNotNull(correctTileB, "Failed to download correct tile for imagery B to compare");

        // Save images for manual inspection
        saveImageForManualInspection(cachedTile, "race_cached_tile.png");
        saveImageForManualInspection(correctTileA, "race_correct_tile_A.png");
        saveImageForManualInspection(correctTileB, "race_correct_tile_B.png"); // Save for comparison

        // 6.

        Assertions.assertNotNull(cachedTile, "Tile should be in cache now");
        // B) The cached tile should be the one for imageryA
        Assertions.assertTrue(areImagesEqual(cachedTile, correctTileA),
                "Race condition did occur. The cached tile should be one the one from imagery A's correct tile.");

        // C) Verify it is NOT the tile from imageryB
        Assertions.assertFalse(areImagesEqual(cachedTile, correctTileB), "The cached tile should differ from imagery B");
    }

    // Helper method to download a tile directly, for test verification
    private BufferedImage downloadTileDirectly(String urlTemplate, int zoom, int x, int y) throws IOException {
        String urlString = urlTemplate
                .replace("{zoom}", String.valueOf(zoom))
                .replace("{x}", String.valueOf(x))
                .replace("{y}", String.valueOf(y));
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "JOSM/1.5 (Test)");
        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
            return ImageIO.read(conn.getInputStream());
        }
        return null;
    }

    // Helper method to compare two images
    private boolean areImagesEqual(BufferedImage img1, BufferedImage img2) {
        if (img1.getWidth() != img2.getWidth() || img1.getHeight() != img2.getHeight()) {
            return false;
        }
        for (int x = 0; x < img1.getWidth(); x++) {
            for (int y = 0; y < img1.getHeight(); y++) {
                if (img1.getRGB(x, y) != img2.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void saveImageForManualInspection(BufferedImage image, String fileName) throws IOException {
        if (image == null) {
            System.err.println("Warning: Attempted to save a null image to " + fileName);
            return;
        }
        File outputFile = new File(TEST_OUTPUT_DIR, fileName);
        ImageIO.write(image, "png", outputFile);
        System.out.println("Image saved to: " + outputFile.getAbsolutePath());
    }

    @Test
    public void testSwitchPlaceholder() {
        TileCache tileCache = TileCache.getInstance();
        ImageryInfo imageryWithSwitch = ImageryProvider.SWITCH_VALID.getImageryInfo();

        // 1. Test validation
        Assertions.assertDoesNotThrow(() -> {
            tileCache.validateImageryInfo(imageryWithSwitch);
        });

        // 2. Test resolution
        String resolvedUrl = tileCache.resolveTileUrl(imageryWithSwitch.getUrl(), 12, 1024, 768);

        // 3. Verify the result
        Assertions.assertTrue(
                resolvedUrl.matches("https://[abc]\\.example\\.com/map/12/1024/768"),
                "Resolved URL '" + resolvedUrl + "' does not match expected pattern."
        );
    }

    @Test
    public void testQuadKeyGeneration() {
        // Test cases based on Microsoft's documentation and examples
        // https://learn.microsoft.com/en-us/bingmaps/articles/bing-maps-tile-system
        Assertions.assertEquals("213", TileCache.tileXYToQuadKey(3, 5, 3));
        Assertions.assertEquals("0", TileCache.tileXYToQuadKey(0, 0, 1));
        Assertions.assertEquals("3", TileCache.tileXYToQuadKey(1, 1, 1));
        Assertions.assertEquals("12", TileCache.tileXYToQuadKey(2, 1, 2));
        Assertions.assertEquals("", TileCache.tileXYToQuadKey(0, 0, 0));
    }
}
