package ru.zkir.urbaneye3d.generators;

import java.lang.reflect.Array;
import java.util.ArrayList;

import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point3D;

public class MesherAdColumn {
    public static Mesh generate(double width, double height) {
        Mesh mesh = new Mesh(null, null, null);
        double radius = width / 2.0;
        int segments = 20;
        ArrayList<Integer> upVertices = new ArrayList<>();
        ArrayList<Integer> downVertices = new ArrayList<>();

        for(int i = 0; i < segments; i++) {
            double angle = (2 * Math.PI / segments) * i;
            double x = radius * Math.cos(angle);
            double y = radius * Math.sin(angle);

            int up = mesh.addVertex(new Point3D(x, y, height));
            int down = mesh.addVertex(new Point3D(x, y, 0));
            upVertices.add(up);
            downVertices.add(down);
        }

        for(int i = 0; i < segments; i++) {
            int next = (i + 1) % segments;
            mesh.addFace(new int[]{upVertices.get(i), downVertices.get(i), downVertices.get(next), upVertices.get(next)}, new int[]{0, 0, 0, 0});
        }
        mesh.addFace(upVertices.stream().mapToInt(Integer::valueOf).toArray(), new int[upVertices.size()]);
        return mesh;
    }
}
