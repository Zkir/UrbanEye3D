package ru.zkir.urbaneye3d.meshers.custom;

import org.openstreetmap.josm.data.osm.OsmPrimitive;
import ru.zkir.urbaneye3d.assetconfig.ProceduralGenerator;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.OsmDataWasher;
import ru.zkir.urbaneye3d.utils.Point3D;

import java.util.SplittableRandom;

public class MesherFountain {
    public static Mesh generate(OsmPrimitive primitive, SplittableRandom random) {
        double height = OsmDataWasher.getTagD("height", primitive, 3.0);
        double radius = 0.5;

        Mesh mesh = new Mesh();
        mesh.textureName = "/textures/water.png";
        mesh.shaderName = "fountain"; // We will use this in Renderer3D to select the shader

        int segments = 8;
        int vBottomCenter = mesh.addVertex(new Point3D(0, 0, 0));
        int vTopCenter = mesh.addVertex(new Point3D(0, 0, height));

        int[] bottomRing = new int[segments];
        for (int i = 0; i < segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            bottomRing[i] = mesh.addVertex(new Point3D(radius * Math.cos(angle), radius * Math.sin(angle), 0));
        }

        // UVs for the jet
        int uv00 = mesh.addUV(0, 0);
        int uv10 = mesh.addUV(1, 0);
        int uv11 = mesh.addUV(1, 1);
        int uv01 = mesh.addUV(0, 1);
        int[] texIndices = {uv00, uv10, uv11, uv01};

        // Side faces (triangles or quads)
        for (int i = 0; i < segments; i++) {
            int next = (i + 1) % segments;
            // A simple cone for the jet
            mesh.addFace(new int[]{bottomRing[i], bottomRing[next], vTopCenter, vTopCenter}, texIndices);
        }

        return mesh;
    }
}
