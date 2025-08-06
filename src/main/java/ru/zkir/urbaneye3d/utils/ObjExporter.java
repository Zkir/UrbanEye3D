package ru.zkir.urbaneye3d.utils;


import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

public class ObjExporter {

    public static void saveMeshToObj(Mesh mesh, String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            // Обязательный заголовок
            writer.write("# Blender-compatible OBJ\n");
            writer.write("mtllib default.mtl\n\n");  // Файл материалов (даже если пустой)
            DecimalFormat df = new DecimalFormat("0.000000", new DecimalFormatSymbols(Locale.US));

            // Запись вершин
            for (Point3D v : mesh.verts) {
                writer.write("v "+df.format( v.x) + " " + df.format( v.z) + " " + df.format( v.y)+"\n");
            }

            // Группа по умолчанию (обязательная для Blender 4.4)
            writer.write("\ng object_default\n");

            // Материал для всех граней
            writer.write("usemtl default\n");

            writer.write("\n# Roof\n");
            writeFaces(writer, mesh.roofFaces);

            writer.write("\n# Walls \n");
            writeFaces(writer, mesh.wallFaces);

            writer.write("\n# Base\n");
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
