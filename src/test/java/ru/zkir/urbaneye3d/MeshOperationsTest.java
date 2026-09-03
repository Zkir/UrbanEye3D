package ru.zkir.urbaneye3d;

import org.junit.jupiter.api.Test;
import ru.zkir.urbaneye3d.utils.ColorUtils;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.ObjExporter;

import java.awt.Color;

import static ru.zkir.urbaneye3d.RoofGeneratorTopologyTest.AssertMeshTopology;
import static ru.zkir.urbaneye3d.utils.MeshOperations.createCube;
import static ru.zkir.urbaneye3d.utils.MeshOperations.insertHorizontalEdgeRing;
import static ru.zkir.urbaneye3d.utils.MeshOperations.scale;
import static ru.zkir.urbaneye3d.utils.MeshOperations.selectVerticesByZ;

public class MeshOperationsTest {

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
}
