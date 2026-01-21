package ru.zkir.customtms;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.*;
import org.openstreetmap.josm.data.Bounds;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CustomTmsIntegrationTest{

    private static MapRenderer mapRenderer;
    private static final String TEST_OUTPUT_DIR = "target/test-output/integrated";

    @BeforeAll
    public static void InitTest() throws IOException {
        mapRenderer = new MapRenderer();
        Files.createDirectories(Paths.get(TEST_OUTPUT_DIR));
    }

    @BeforeEach
    public void setUp() {
        mapRenderer.clearCache(); // Clear cache before each test to ensure fresh downloads
        mapRenderer.setCurrentImagery(ImageryProvider.OSM_CARTO.getImageryInfo());
    }

    @AfterAll
    public static void tearDown() {
        if (mapRenderer != null) {
            //mapRenderer.shutdown();
        }
    }

    /**
     *  Simple test that the given area is get rendered, tiles are downloaded, cache is working
     */
    @Test  public void testRenderMapWithPreload() throws IOException, InterruptedException {

        // Bounding box for Moscow
        Bounds bbox = new Bounds( 55,37.0, 56.0, 38.0);
        int width = 729*2;//1024;
        int height =1286*2; // 768;
        int zoom = 11;

        // First render pass (will trigger tile downloads)
        mapRenderer.renderMap(zoom, bbox, width, height);

        // Wait for tiles to download.
        System.out.println("Waiting for tiles to download...");
        while (mapRenderer.getPendingRequestsCount()>0){
            //System.out.println("  "+ mapRenderer.getTileCache().getPendingRequestsCount());
            Thread.sleep(100); // wait for 10 seconds for tiles to load
        }

        // Second render pass (should use cached tiles, but they are fresh)
        System.out.println("Rendering final image...");
        BufferedImage renderedImage = mapRenderer.renderMap(zoom, bbox, width, height);

        verifyRenderedImage(renderedImage, "rendered-map.png", 1000, true, width, height);
    }


    /**
     *  In this test we check that if tiles are not yest loaded, we get placeholders.
     */
    @Test public void testRenderMapWithoutPreload() throws IOException {

        // Bounding box for Moscow
        Bounds bbox = new Bounds( 55,37.0, 56.0, 38.0);
        int width = 729*3;//1024;
        int height =1286*3; // 768;
        int zoom = 10;

        // The first and only request for rendering
        BufferedImage renderedImage = mapRenderer.renderMap(zoom, bbox, width, height);

        verifyRenderedImage(renderedImage, "rendered-map-placeholders.png", 3, false, width, height);
    }
    /**
     *  Test of foolproofness for tile number.
     *  if zoom level is specified wrongly, number of tiles to be rendered can be billons (!)
     * */
    @Test public void testTileNumberLimit() throws IOException {

        // huge boundaries and detailed zoom
        Bounds bbox = new Bounds( 50,30.0, 60.0, 40.0);
        int width =  1024;
        int height = 1024;
        int zoom = 15;
        boolean exceptionRaised=false;
        try {
            BufferedImage renderedImage = mapRenderer.renderMap(zoom, bbox, width, height);
        }catch (MapRenderer.ImageTooBigException e){
            exceptionRaised = true;
        }
        assertTrue(exceptionRaised, "Exception 'ImageTooBigException' should be raised in this case, because 1,456,689 tiles cannot be rendered");
    }

    /** Test how invalid TMS URL, e.g. with unknown placeholders is processed.
     * If url cannot be parsed, exception is expected.
    */
    @Test public void testInvalidTMSUrl(){
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            mapRenderer.setCurrentImagery(ImageryProvider.INVALID_PLACEHOLDERS.getImageryInfo());
        });
    }


    private void verifyRenderedImage(BufferedImage renderedImage, String fileName, int colorThreshold, boolean assertMoreThanThreshold, int expectedWidth, int expectedHeight) throws IOException {
        saveImageForManualInspection(renderedImage, fileName); // Use the new helper
        // Analyze the image for "peppiness"
        int uniqueColors = countUniqueColors(renderedImage);
        System.out.println("Found " + uniqueColors + " unique colors. Threshold is " + colorThreshold);

        if (assertMoreThanThreshold) {
            assertTrue(uniqueColors > colorThreshold,
                    "The rendered image is not sufficiently varied. It might be blank or only contain placeholders.");
        } else {
            assertTrue(uniqueColors <= colorThreshold,
                    "The map image is too motley. Has actual tiles been rendered");
        }

        assertTrue( renderedImage.getHeight()== expectedHeight && renderedImage.getWidth()==expectedWidth,
                "The rendered image dimensions does not match requested." );
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

    private int countUniqueColors(BufferedImage image) {
        Set<Integer> colors = new HashSet<>();
        int width = image.getWidth();
        int height = image.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                colors.add(image.getRGB(x, y));
            }
        }
        return colors.size();
    }
    



}