package ru.zkir.urbaneye3d.generators;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import ru.zkir.urbaneye3d.RenderableElement;
import ru.zkir.urbaneye3d.assetconfig.AssetRule;
import ru.zkir.urbaneye3d.assetconfig.ProceduralGenerator;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.OsmDataWasher;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;

import java.util.Random;

public class MesherFountain implements ProceduralGenerator {
    @Override
    public RenderableElement generate(OsmPrimitive primitive, LatLon origin, AssetRule rule, Random random) {
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

        if (primitive instanceof Node) {
            return RenderableElement.createFromModel((Node) primitive, mesh, 0.0, 0.0);
        } else {
            return null;
        }
    }
}
