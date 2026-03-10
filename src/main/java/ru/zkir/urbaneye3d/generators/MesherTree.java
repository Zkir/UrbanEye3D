package ru.zkir.urbaneye3d.generators;

import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point3D;

public class MesherTree {
    public static Mesh generate(double width, double height) {
        Mesh mesh = new Mesh(null, null, null);

        // 1. UV coordinates for the whole texture
        int uv0 = mesh.addUV(0, 0); // bottom-left
        int uv1 = mesh.addUV(1, 0); // bottom-right
        int uv2 = mesh.addUV(1, 1); // top-right
        int uv3 = mesh.addUV(0, 1); // top-left
        int[] texIndices = new int[]{uv0, uv1, uv2, uv3};

        // 2. Vertices for two crossed rectangles centered at (0,0,0)
        double halfWidth = width / 2.0;

        // Rectangle #1 (along the X-axis)
        int v0 = mesh.addVertex(new Point3D(-halfWidth, 0, 0));
        int v1 = mesh.addVertex(new Point3D(halfWidth, 0, 0));
        int v2 = mesh.addVertex(new Point3D(halfWidth, 0, height));
        int v3 = mesh.addVertex(new Point3D(-halfWidth, 0, height));

        // Rectangle #2 (along the Y-axis)
        int v4 = mesh.addVertex(new Point3D(0, -halfWidth, 0));
        int v5 = mesh.addVertex(new Point3D(0, halfWidth, 0));
        int v6 = mesh.addVertex(new Point3D(0, halfWidth, height));
        int v7 = mesh.addVertex(new Point3D(0, -halfWidth, height));

        // 3. Two faces (quads)
        mesh.addFace(new int[]{v0, v1, v2, v3}, texIndices);
        mesh.addFace(new int[]{v4, v5, v6, v7}, texIndices);

        return mesh;
    }
}
