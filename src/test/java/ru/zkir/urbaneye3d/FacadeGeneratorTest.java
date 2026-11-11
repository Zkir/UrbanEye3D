package ru.zkir.urbaneye3d;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Way;
import ru.zkir.urbaneye3d.facades.FacadeApplicator;
import ru.zkir.urbaneye3d.facades.FacadeDefinition;
import ru.zkir.urbaneye3d.facades.FacadeParser;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.ObjExporter;
import ru.zkir.urbaneye3d.utils.UvGenerator;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static java.lang.Math.abs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.zkir.urbaneye3d.RoofGeneratorTopologyTest.createRectangularBase;
import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_LEVEL_HEIGHT;
import static ru.zkir.urbaneye3d.utils.Settings.SAVE_TEST_RESULTS_TO_FILE;

class FacadeGeneratorTest {

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(Paths.get("target/test-output/facade-generator"));
    }

    @Test
    void testApplyFacadeToBuilding() throws IOException {
        // Step 1: Create a test building mesh
        Contour contour = new Contour(createRectangularBase(32, 12), "XY");
        LatLon origin = new LatLon(55, 37);
        var tags = Map.of("building", "yes",
                "height" , "16",
                "building:colour" , "red",
                "roof:colour" , "green",
                "roof:shape" , "hipped"
        );

        BuildingRecipe buildingRecipe = RenderableElement.createBuildingOrPartRecipe(new Way(), origin, contour,  tags,  null);
        assertNotNull(buildingRecipe);

        Mesh buildingMesh = RenderableElement.composeMesh(buildingRecipe);
        assertNotNull(buildingMesh);

        // Step 2 Create mesh with UV coords, and partially painted (base) atlas.
        //   Note: We want to take advantage of building:colour and roof:colours tags.
        UvGenerator uvGenerator = new UvGenerator(buildingMesh); // Use UvGenerator to get initial mesh with UVs and a base atlas
        Mesh meshWithUvs = uvGenerator.getMeshWithUvs();
        BufferedImage baseAtlasFromUvGenerator = uvGenerator.getTextureAtlas(false);

        // Step 3: Parse the .fac file into a data structure
        FacadeDefinition facadeDef = FacadeParser.parse("industrial_01.fac");
        assertNotNull(facadeDef);

        // Steps 4: Obtain facade texture
        assertNotNull(facadeDef.texture);

        // Steps 5: Apply facade rules to the building walls
        // This applicator only paints facade texture over base one

        FacadeApplicator applicator = new FacadeApplicator(meshWithUvs, facadeDef, baseAtlasFromUvGenerator);
        BufferedImage facadeAtlas = applicator.getAppliedTexture();

        assertNotNull(facadeAtlas);

        // The facade must actually paint something over the flat base atlas.
        assertNotEquals(baseAtlasFromUvGenerator, facadeAtlas, "Facade atlas must differ from the base atlas.");
        assertTrue(differentPixels(baseAtlasFromUvGenerator, facadeAtlas) > 100,
                "Facade texture must paint a significant number of pixels over the base atlas.");

        // 7. Save the resulting mesh and texture to an Obj file
        if (SAVE_TEST_RESULTS_TO_FILE) {
            String objPath = "target/test-output/facade-generator/buildingWithFacade.obj";
            ObjExporter.saveMeshToObj(meshWithUvs, facadeAtlas, objPath);

            // Assert that all files were created
            File objFile = new File(objPath);
            File mtlFile = new File("target/test-output/facade-generator/buildingWithFacade.mtl");
            File pngFile = new File("target/test-output/facade-generator/buildingWithFacade.png");

            assertTrue(objFile.exists() && objFile.length() > 0, "OBJ file should be created.");
            assertTrue(mtlFile.exists() && mtlFile.length() > 0, "MTL file should be created.");
            assertTrue(pngFile.exists() && pngFile.length() > 0, "Texture PNG file should be created for OBJ.");
        }
    }

    /** Counts how many pixels differ between two atlases of the same size. */
    private static int differentPixels(BufferedImage a, BufferedImage b) {
        assertEquals(a.getWidth(), b.getWidth());
        assertEquals(a.getHeight(), b.getHeight());
        int different = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    different++;
                }
            }
        }
        return different;
    }

    /**
     * Integration check: a building that matches a facade rule must come out of the element factory
     * with UV coordinates and a baked atlas, while a building part must stay a plain colored mesh.
     */
    @Test
    void testFacadeIsAppliedToBuildingButNotToItsPart() {
        Contour contour = new Contour(createRectangularBase(20, 10), "XY");
        LatLon origin = new LatLon(55, 37);

        // industrial + brick matches industrial_01.fac in facades.cfg
        var buildingTags = Map.of("building", "industrial", "building:material", "brick", "height", "12");
        var element = RenderableElement.createBuildingOrPart(new Way(), origin, contour, buildingTags, null);
        assertNotNull(element);
        assertNotNull(element.getFacadeAtlas(), "A matching building should get a baked facade atlas.");
        assertNotNull(element.getFacadeAtlasKey(), "The atlas must have a key TextureManager can own it by.");

        Mesh mesh = element.getMesh();
        int texturedWalls = 0;
        for (int i = 0; i < mesh.faces.size(); i++) {
            if (Integer.valueOf(Mesh.WALL_COLOUR_IDX).equals(mesh.faceMaterials.get(i))) {
                assertNotNull(mesh.faceUVs.get(i), "Wall face #" + i + " of a facade building should have UVs.");
                texturedWalls++;
            }
        }
        assertTrue(texturedWalls > 0, "The building should have wall faces at all.");

        // A part of a building is a wall without windows of its own: no facade, no atlas.
        var partTags = Map.of("building:part", "yes", "height", "6");
        var part = RenderableElement.createBuildingOrPart(new Way(), origin, contour, partTags, buildingTags);
        assertNotNull(part);
        assertNull(part.getFacadeAtlas(), "Building parts must not get facade textures.");
    }

    /**
     *  The idea here is that  the number of visualized levels should match specified in OSM (levels=*)
     */
    @Test
    void testSliceSequenceCreation() throws IOException {
        FacadeDefinition facadeDef = FacadeParser.parse("apartments-constructivism_01.fac");
        assertNotNull(facadeDef);
        for(int n=1; n<=15; n++ ) {
            var wall = facadeDef.lods.get(0).walls.get(0);
            //TODO: remove black magic, and ensure that the generated number of levels is strictly equal to the given number
            //   Also sequence calculation should be improved a bit.
            var sequence = FacadeApplicator.getSliceSequence(wall.verticalSlices, wall.scaleY,  DEFAULT_LEVEL_HEIGHT*n , "BOTTOM", "MIDDLE", "TOP");
            assertTrue (abs(sequence.size()-n)<=1, "sequence length should match number of levels. n=" + n +", sequence length=" + sequence.size() + "   " + sequence);
        }
    }

    /**
     * We ensure that all referenced facades are present and parsable.
    */
    @Test void testFacadesIntegrity() throws IOException {
        var assetPicker = new AssetPicker("/facades/facades.cfg");
        var allFacades = assetPicker.getAllNames();
        for (String facadeName: allFacades ){
            FacadeDefinition facadeDef = FacadeParser.parse(facadeName);
            assertNotNull(facadeDef);
            assertNotNull(facadeDef.texture, "Texture "+ facadeDef.textureName + " cannot be loaded for facade: "+ facadeName );

            var wall = facadeDef.lods.get(0).walls.get(0);
            for(int n=1; n<=8; n++ ) {
                //check the facade health. generated number of levels should match expected.
                // This is not a dogma, because level height for particular building should not be always 3.
                var sequence = FacadeApplicator.getSliceSequence(wall.verticalSlices, wall.scaleY, DEFAULT_LEVEL_HEIGHT * n, "BOTTOM", "MIDDLE", "TOP");
                if (wall.isVerticallyStretchable()) {
                    assertTrue(abs(sequence.size() - n) <= 1, facadeName + ": sequence length should match number of levels. n=" + n + ", sequence length=" + sequence.size() + "   " + sequence);
                }
            }
        }
    }
}
