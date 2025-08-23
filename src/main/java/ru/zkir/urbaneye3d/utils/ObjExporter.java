package ru.zkir.urbaneye3d.utils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

public class ObjExporter {

    public static String meshToString(Mesh mesh) {
        StringWriter stringWriter = new StringWriter();
        try (BufferedWriter writer = new BufferedWriter(stringWriter)) {
            // Blender-compatible headers
            writer.write("# Blender-compatible OBJ\n");
            //writer.write("mtllib default.mtl\n\n");
            DecimalFormat df = new DecimalFormat("0.000000", new DecimalFormatSymbols(Locale.US));

            // Vertices
            for (Point3D v : mesh.verts) {
                writer.write("v " + df.format(v.x) + " " + df.format(v.z) + " " + df.format(v.y) + "\n");
            }

            // Default object group
            writer.write("\ng object_default\n");
            writer.write("usemtl default\n");

            // Faces
            writer.write("\n# Roof\n");
            writeFaces(writer, mesh.roofFaces);

            writer.write("\n# Walls \n");
            writeFaces(writer, mesh.wallFaces);

            writer.write("\n# Base\n");
            writeFaces(writer, mesh.bottomFaces);

        } catch (IOException e) {
            // StringWriter does not throw IOException
            throw new RuntimeException(e);
        }
        return stringWriter.toString();
    }

    public static void saveMeshToObj(Mesh mesh, String filePath)  {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(meshToString(mesh));
        }
        catch (Exception e){
            throw new RuntimeException(e.getMessage());
        }

    }

    private static void writeFaces(BufferedWriter writer, List<int[]> faces) throws IOException {
        for (int[] face : faces) {
            if (face.length >= 3) {
                writer.write("f");
                for (int index : face) {
                    writer.write(" " + (index + 1));
                }
                writer.write("\n");
            }
        }
    }
}