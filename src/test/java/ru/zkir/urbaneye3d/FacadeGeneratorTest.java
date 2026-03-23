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
import java.io.InputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

import static java.lang.Math.abs;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.zkir.urbaneye3d.RoofGeneratorTopologyTest.createRectangularBase;
import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_LEVEL_HEIGHT;

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
                "height" , "26",
                "building:colour" , "red",
                "roof:colour" , "green",
                "roof:shape" , "hipped"
        );
        var element = RenderableElement.createBuildingOrPart(new Way(), origin, contour, tags, null);
        assertNotNull(element);
        Mesh buildingMesh = element.getMesh();

        // Step 2 Create mesh with UV coords, and partially painted (base) atlas.
        //   Note: We want to take advantage of building:colour and roof:colours tags.
        UvGenerator uvGenerator = new UvGenerator(buildingMesh); // Use UvGenerator to get initial mesh with UVs and a base atlas
        Mesh meshWithUvs = uvGenerator.getMeshWithUvs();
        BufferedImage baseAtlasFromUvGenerator = uvGenerator.getTextureAtlas(false);

        // Step 3: Parse the .fac file into a data structure
        FacadeDefinition facadeDef = FacadeParser.parse("building_10.fac");
        assertNotNull(facadeDef);

        // Steps 4: Obtain facade texture
        assertNotNull(facadeDef.texture);

        // Steps 5: Apply facade rules to the building walls
        // This applicator only paints facade texture over base one

        FacadeApplicator applicator = new FacadeApplicator(meshWithUvs, facadeDef, baseAtlasFromUvGenerator);
        BufferedImage facadeAtlas = applicator.getAppliedTexture();

        assertNotNull(facadeAtlas);

        // Step 6: Save the resulting mesh and texture to an Obj file
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

    /**
     *  The idea here is that  the number of visualized levels should match specified in OSM (levels=*)
     */
    @Test
    void testSliceSequenceCreation() throws IOException {
        FacadeDefinition facadeDef = FacadeParser.parse("building_03.fac");
        assertNotNull(facadeDef);

        for(int n=1; n<=10; n++ ) {
            var wall = facadeDef.lods.get(0).walls.get(0);
            //TODO: remove black magic, and ensure that the generated number of levels is strictly equal to the given number
            //   Also sequence calculation should be improved a bit.
            var sequence = FacadeApplicator.getSliceSequence(wall.verticalSlices, wall.scaleY,  1.2*DEFAULT_LEVEL_HEIGHT*n , "BOTTOM", "MIDDLE", "TOP");
            assertTrue (abs(sequence.size()-n)<=1, "sequence length should match number of levels. n=" + n +", sequence length=" + sequence.size() + "   " + sequence);
        }
    }
}
