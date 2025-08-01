package ru.zkir.urbaneye3d.utils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class ObjExporter {

    public static void saveMeshToObj(Mesh mesh, String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            // Запись вершин
            for (Point3D v : mesh.verts) {
                writer.write(String.format("v %.6f %.6f %.6f\n", v.x, v.y, v.z));
            }

            writer.write("\n# Крыша\n");
            writeFaces(writer, mesh.roofFaces);

            writer.write("\n# Стены\n");
            writeFaces(writer, mesh.wallFaces);

            writer.write("\n# Основание\n");
            writeFaces(writer, mesh.bottomFaces);
        }
    }

    private static void writeFaces(BufferedWriter writer, List<int[]> faces) throws IOException {
        for (int[] face : faces) {
            if (face.length >= 3) {  // Для треугольников и полигонов
                writer.write("f");
                for (int index : face) {
                    // OBJ индексы начинаются с 1
                    writer.write(" " + (index + 1));
                }
                writer.write("\n");
            }
        }
    }
}
