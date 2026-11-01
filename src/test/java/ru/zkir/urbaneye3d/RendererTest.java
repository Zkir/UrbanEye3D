package ru.zkir.urbaneye3d;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.preferences.JosmBaseDirectories;
import org.openstreetmap.josm.data.preferences.JosmUrls;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.data.projection.Projections;
import org.openstreetmap.josm.io.OsmReader;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.MemoryPreferences;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static ru.zkir.urbaneye3d.utils.Settings.countUniqueColors;

class RendererTest {

    private static final String TEST_OUTPUT_DIR = "target/test-output/rendering";

    @BeforeAll
    public static void setUp() throws Exception {
        Config.setPreferencesInstance(new MemoryPreferences());
        Config.setBaseDirectoriesProvider(JosmBaseDirectories.getInstance());
        Config.setUrlsProvider(JosmUrls.getInstance());
        ProjectionRegistry.setProjection(Projections.getProjectionByCode("EPSG:3857"));
        Files.createDirectories(Paths.get(TEST_OUTPUT_DIR));
    }

    private DataSet loadDataSetFromOsmFile(String resourceName) throws Exception {
        InputStream is = getClass().getResourceAsStream("/osm_test_files/" + resourceName);
        if (is == null) {
            throw new IllegalArgumentException("Resource not found: " + resourceName);
        }
        return OsmReader.parseDataSet(is, null);
    }

    @Test
    void testCityCenterRendering() throws Exception {
        DataSet dataSet = loadDataSetFromOsmFile("city_center.osm");
        Scene scene = new Scene();
        Scene.SceneUpdate update = scene.calculateUpdate(dataSet);
        scene.applyUpdate(update);

        int width = 1024;
        int height = 768;

        GLProfile profile = GLProfile.get(GLProfile.GL2);
        GLCapabilities capabilities = new GLCapabilities(profile);
        GLDrawableFactory factory = GLDrawableFactory.getFactory(profile);

        GLOffscreenAutoDrawable offscreen = factory.createOffscreenAutoDrawable(null, capabilities, null, width, height);

        Renderer3D renderer3D = new Renderer3D(scene);
        renderer3D.setCameraPosition(new LatLon(55.7538654, 37.6168927)); //hack: manual scene center
        offscreen.addGLEventListener(renderer3D);

        // Configure ground plane and layer to render ground tiles (MapCSS / 2d imagery)
        GroundPlane groundPlane = scene.getGroundPlane();
        groundPlane.setRenderer(renderer3D);
        GroundPlane.Layer2dInfo testLayer = new GroundPlane.Layer2dInfo("Test dataset");
        LatLon center =  renderer3D.getCameraPosition();
        groundPlane.update(center, testLayer, dataSet, false, null);

        BufferedImage[] capturedImage = new BufferedImage[1];

        // Add a capture listener that waits for ground tiles and reads pixels during display
        offscreen.addGLEventListener(new GLEventListener() {
            @Override
            public void init(GLAutoDrawable drawable) {
            }

            @Override
            public void dispose(GLAutoDrawable drawable) {
            }

            @Override
            public void display(GLAutoDrawable drawable) {
                try {
                    // Wait for ground plane tiles to load and become ready (up to 4 seconds delay)
                    for (int i = 0; i < 40; i++) {
                        boolean allTilesHaveData = !groundPlane.getActiveTiles().isEmpty() &&
                                groundPlane.getActiveTiles().stream().allMatch(GroundTile::hasImageData);
                        if (allTilesHaveData && groundPlane.getPendingTileUpdateRequests() == 0) {
                            break;
                        }
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // Force second pass / redraw to ensure ground plane tiles are rendered
                renderer3D.display(drawable);

                // Read pixels safely within GL context
                GL2 gl = drawable.getGL().getGL2();
                ByteBuffer buffer = Buffers.newDirectByteBuffer(width * height * 4);
                gl.glReadPixels(0, 0, width, height, GL2.GL_RGBA, GL2.GL_UNSIGNED_BYTE, buffer);

                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                int[] pixels = new int[width * height];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int r = buffer.get() & 0xFF;
                        int g = buffer.get() & 0xFF;
                        int b = buffer.get() & 0xFF;
                        int a = buffer.get() & 0xFF;
                        int targetY = height - 1 - y;
                        pixels[targetY * width + x] = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                }
                image.setRGB(0, 0, width, height, pixels, 0, width);
                capturedImage[0] = image;
            }

            @Override
            public void reshape(GLAutoDrawable drawable, int x, int y, int w, int h) {
                renderer3D.reshape(drawable, x, y, w, h);
            }
        });

        // Trigger display (runs init, reshape, display)
        offscreen.display();

        assertNotNull(capturedImage[0], "Captured image should not be null.");

        File outputFile = new File(TEST_OUTPUT_DIR, "city_center_render.png");
        ImageIO.write(capturedImage[0], "png", outputFile);

        int uniqueColors = countUniqueColors(capturedImage[0]);
        System.out.println("Rendered image saved to: " + outputFile.getAbsolutePath() + " with " + uniqueColors + " unique colors.");

        assertTrue(outputFile.exists(), "Rendered image file should exist.");
        assertTrue(outputFile.length() > 0, "Rendered image file should not be empty.");
        assertTrue(uniqueColors > 100, "Rendered image does not seem to contain a 3D scene (too few colors: " + uniqueColors + ").");

        offscreen.destroy();
    }
}
