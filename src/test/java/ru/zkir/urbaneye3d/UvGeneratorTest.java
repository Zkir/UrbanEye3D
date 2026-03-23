package ru.zkir.urbaneye3d;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Way;
import ru.zkir.urbaneye3d.roofgenerators.RoofShapes;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Settings;
import ru.zkir.urbaneye3d.utils.UvGenerator;
import ru.zkir.urbaneye3d.utils.ObjExporter;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;
import static ru.zkir.urbaneye3d.RoofGeneratorTopologyTest.createRectangularBase;
import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_ROOF_THICKNESS;
import static ru.zkir.urbaneye3d.utils.Settings.SAVE_TEST_RESULTS_TO_FILE;

class UvGeneratorTest {

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(Paths.get("target/test-output/uv-generator"));
    }

    @Test
    void testGenerateUvMapForSimpleBuilding() throws IOException {
        // 1. Arrange: Create a simple building mesh
        Contour contour = new Contour(createRectangularBase(25, 10), "XY");
        LatLon origin = new LatLon(55, 37);

        var tags = Map.of("building", "yes",
                "height" , "10",
                "building:colour" , "red",
                "roof:colour" , "green",
                "roof:shape" , "gabled"
        );

        var element = RenderableElement.createBuildingOrPart(new Way(), origin, contour, tags, null);

        assertNotNull(element);
        Mesh mesh = element.getMesh();

        // 2. Act: Generate UV map and texture atlas
        // This part will fail until we implement UvGenerator
        UvGenerator uvGenerator = new UvGenerator(mesh);
        Mesh meshWithUvs = uvGenerator.getMeshWithUvs();
        BufferedImage textureAtlas = uvGenerator.getTextureAtlas(true);

        // 3. Assert & Verify
        assertNotNull(meshWithUvs, "Mesh with UVs should not be null.");
        assertNotNull(textureAtlas, "Texture atlas image should not be null.");

        // Basic assertions on UV data
        assertNotNull(meshWithUvs.uvs, "UV coordinates array should be initialized.");
        assertTrue(meshWithUvs.uvs.size() > 0, "UV coordinates array should not be empty.");
        //assertEquals(meshWithUvs.vertices.length, meshWithUvs.uvs.size(), "UVs count should match vertices count.");

        // Check if UVs are within the valid [0, 1] range
        for (Point2D uv : meshWithUvs.uvs) {
            assertNotNull(uv, "UV coordinate should not be null.");
            assertTrue(uv.x >= 0.0f && uv.x <= 1.0f, "UV.x should be in [0, 1] range.");
            assertTrue(uv.y >= 0.0f && uv.y <= 1.0f, "UV.y should be in [0, 1] range.");
        }

        // 4. Export the result to OBJ for external verification
        String objPath = "target/test-output/uv-generator/simpleBuilding.obj";
        ObjExporter.saveMeshToObj(meshWithUvs, textureAtlas, objPath);

        File objFile = new File(objPath);
        File mtlFile = new File("target/test-output/uv-generator/simpleBuilding.mtl");
        File pngFile = new File("target/test-output/uv-generator/simpleBuilding.png");

        assertTrue(objFile.exists() && objFile.length() > 0, "OBJ file should be created.");
        assertTrue(mtlFile.exists() && mtlFile.length() > 0, "MTL file should be created.");
        assertTrue(pngFile.exists() && pngFile.length() > 0, "Texture PNG file should be created for OBJ.");
    }
}
