package ru.zkir.urbaneye3d;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.preferences.JosmBaseDirectories;
import org.openstreetmap.josm.data.preferences.JosmUrls;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.MemoryPreferences;
import ru.zkir.urbaneye3d.utils.ColorUtils;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.ObjExporter;

import java.awt.Color;
import java.util.Random;

import static ru.zkir.urbaneye3d.RoofGeneratorTopologyTest.AssertMeshTopology;
import static ru.zkir.urbaneye3d.utils.MeshOperations.createCube;
import static ru.zkir.urbaneye3d.utils.MeshOperations.insertHorizontalEdgeRing;
import static ru.zkir.urbaneye3d.utils.MeshOperations.scale;
import static ru.zkir.urbaneye3d.utils.MeshOperations.selectVerticesByZ;

public class MeshOperationsTest {
    @BeforeAll
    public static void setUp() {
        Config.setPreferencesInstance(new MemoryPreferences());
        Config.setBaseDirectoriesProvider(JosmBaseDirectories.getInstance());
        Config.setUrlsProvider(JosmUrls.getInstance());
    }

    @Test
    void testConstructiveGeometry(){

        double height = 1.4;
        double width = 0.6;
        double length = 1.2;

        final double DELTA = 0.001;

        Mesh mesh = createCube();
        insertHorizontalEdgeRing(mesh, 0.1);   // first ring
        insertHorizontalEdgeRing(mesh, 0.2);   // second ring
        insertHorizontalEdgeRing(mesh, 0.95);  // third ring
        insertHorizontalEdgeRing(mesh, 0.96);  // forth ring
        scale(mesh, width, length, 1);

        var v = selectVerticesByZ(mesh, 0-DELTA, 0.1+DELTA);
        scale(mesh, v, 1-0.15/width, 1-0.15/length, 1);

        v = selectVerticesByZ(mesh, 0.96-DELTA, 1+DELTA);
        scale(mesh, v, 1 + 0.05/width, 1+0.05/length, 1);

        Color matColor = ColorUtils.parseColor("#2020A0");
        mesh.materials.add(matColor);
        mesh.materials.add(matColor);
        mesh.materials.add(matColor);


        scale(mesh, 1, 1, height);

        //ObjExporter.saveMeshToObj(mesh,"d:/test.obj");

        AssertMeshTopology(mesh, 0, height,"flat");
    }

    @Test
    void testAdColumn(){
        double height = 5;
        Node node = new Node(new LatLon(55.75, 37.61));
        node.put("advertising", "column");
        node.put("height", String.valueOf(height));

        Mesh mesh = RenderableElement.createAdColumn(node, node.getCoor(), node.getInterestingTags(), new Random());
        //insertHorizontalEdgeRing(mesh, 0.3);   // first ring
        insertHorizontalEdgeRing(mesh, 0.5);   // second ring

        ObjExporter.saveMeshToObj(mesh,"d:/test.obj");
        AssertMeshTopology(mesh, 0, height,"flat");
    }
}
