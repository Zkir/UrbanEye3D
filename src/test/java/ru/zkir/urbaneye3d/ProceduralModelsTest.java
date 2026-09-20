package ru.zkir.urbaneye3d;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.preferences.JosmBaseDirectories;
import org.openstreetmap.josm.data.preferences.JosmUrls;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.MemoryPreferences;
import ru.zkir.urbaneye3d.assetconfig.GeneratorRegistry;
import ru.zkir.urbaneye3d.assetconfig.ProceduralGenerator;
import ru.zkir.urbaneye3d.utils.ColorUtils;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.MeshOperations;
import ru.zkir.urbaneye3d.utils.ObjExporter;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;
import java.util.SplittableRandom;

import static ru.zkir.urbaneye3d.RoofGeneratorTopologyTest.AssertMeshTopology;
import static ru.zkir.urbaneye3d.RoofGeneratorTopologyTest.AssertMeshTopology2;


public class ProceduralModelsTest {
    private static final String TEST_OUTPUT_DIR = "target/test-output/procedural-models";

    @BeforeAll
    public static void setUp() throws IOException{
        Files.createDirectories(Paths.get(TEST_OUTPUT_DIR));
        Config.setPreferencesInstance(new MemoryPreferences());
        Config.setBaseDirectoriesProvider(JosmBaseDirectories.getInstance());
        Config.setUrlsProvider(JosmUrls.getInstance());
    }

    @Test
    void testStreetCabinet(){
        Node node = new Node(new LatLon(55.0, 37.0));
        node.put("man_made", "street_cabinet");
        node.put("height", "1.6");
        node.put("width", "0.75");
        node.put("length", "1.3");

        ProceduralGenerator generator = GeneratorRegistry.getInstance().get("street_cabinet");

        var mesh = generator.generate(node, new SplittableRandom(node.getId()));
        ObjExporter.saveMeshToObj(mesh,TEST_OUTPUT_DIR +"/street_cabinet.obj");

        AssertMeshTopology(mesh, 0, 1.6,"street_cabinet");
    }

    @Test
    void testAdColumn(){
        Node node = new Node(new LatLon(55.0, 37.0));
        node.put("advertising", "column");
        node.put("height", "5.25");
        node.put("width", "1.3");

        ProceduralGenerator generator = GeneratorRegistry.getInstance().get("ad_column");

        var mesh = generator.generate(node, new SplittableRandom(node.getId()));
        ObjExporter.saveMeshToObj(mesh,TEST_OUTPUT_DIR +"/ad_column.obj");

        AssertMeshTopology(mesh, 0, 5.25,"ad_column");
    }

    @Test
    void testFlagpole(){
        Node node = new Node(new LatLon(55.0, 37.0));
        node.put("man_made", "flagpole");
        node.put("height", "12.25");
        node.put("flag:colour", "blue");

        ProceduralGenerator generator = GeneratorRegistry.getInstance().get("flagpole");

        var mesh = generator.generate(node, new SplittableRandom(node.getId()));
        ObjExporter.saveMeshToObj(mesh,TEST_OUTPUT_DIR +"/flagpole.obj");

        AssertMeshTopology2(mesh, 0, 12.25,"flagpole",true);
    }

    @Test
    void testChimney(){
        Node node = new Node(new LatLon(55.0, 37.0));
        node.put("man_made", "chimney");
        node.put("height", "30");
        node.put("colour", "red");

        ProceduralGenerator generator = GeneratorRegistry.getInstance().get("chimney");

        var mesh = generator.generate(node, new SplittableRandom(node.getId()));
        ObjExporter.saveMeshToObj(mesh,TEST_OUTPUT_DIR +"/chimney.obj");

        AssertMeshTopology(mesh, 0, 30,"chimney");
    }

    @Test
    void testWindTurbine(){
        Node node = new Node(new LatLon(55.0, 37.0));
        node.put("power", "generator");
        node.put("height", "120");
        node.put("rotor:diameter", "50");
        //node.put("height:hub", "40");

        ProceduralGenerator generator = GeneratorRegistry.getInstance().get("wind_turbine");

        var mesh = generator.generate(node, new SplittableRandom(node.getId()));
        ObjExporter.saveMeshToObj(mesh,TEST_OUTPUT_DIR +"/wind_turbine.obj");

        AssertMeshTopology2(mesh, 0, 119.589,"wind_turbine", true);
    }
}
