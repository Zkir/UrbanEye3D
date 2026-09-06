package ru.zkir.urbaneye3d.utils;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

public class ObjExporter {

    public static String meshToString(Mesh mesh, String mtl_path) {
        var mtl_name = new File(mtl_path).getName();
        if (mtl_name.isBlank()) {
            mtl_name = "default.mtl";
        }

        StringWriter stringWriter = new StringWriter();
        try (BufferedWriter writer = new BufferedWriter(stringWriter)) {
            // Blender-compatible headers
            writer.write("# Blender-compatible OBJ\n");
            writer.write("# Created by UrbanEye3D\n");
            writer.write("mtllib " + mtl_name + "\n\n");

            DecimalFormat df = new DecimalFormat("0.000000", new DecimalFormatSymbols(Locale.US));

            // Vertices
            for (Point3D v : mesh.verts) {
                writer.write("v " + df.format(v.x) + " " + df.format(v.z) + " " + df.format(v.y) + "\n");
            }

            // Default object group
            writer.write("\ng object_default\n");

            // Faces
            writer.write("\n# Roof\n");
            writer.write("usemtl material" + mesh.ROOF_COLOUR_IDX + "\n");
            writeFaces(writer, mesh.getRoofFaces());

            writer.write("\n# Walls \n");
            writer.write("usemtl material" + mesh.WALL_COLOUR_IDX + "\n");

            writeFaces(writer, mesh.getWallFaces());

            writer.write("\n# Base\n");
            writer.write("usemtl material" + mesh.BOTTOM_COLOUR_IDX + "\n");
            writeFaces(writer, mesh.getBottomFaces());

        } catch (IOException e) {
            // StringWriter does not throw IOException
            throw new RuntimeException(e);
        }
        return stringWriter.toString();
    }

    public static String mtlToString(Mesh mesh) {
        StringWriter stringWriter = new StringWriter();
        try (BufferedWriter writer = new BufferedWriter(stringWriter)) {
            writer.write("# Blender-compatible MTL\n");
            writer.write("# Created by UrbanEye3D\n\n");
            DecimalFormat df = new DecimalFormat("0.000000", new DecimalFormatSymbols(Locale.US));

            var i =0;
            for (var material: mesh.materials){
                writeMaterial(writer, "material" + i, material, df);
                i++;
            }

            // If texture is specified, add texture material
            //if (mesh.textureName != null && !mesh.textureName.isEmpty()) {
            //    writer.write("\nnewmtl textured\n");
            //    writer.write("map_Kd " + mesh.textureName + "\n");
            //}

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return stringWriter.toString();
    }

    private static void writeMaterial(BufferedWriter writer, String materialName, Color color, DecimalFormat df) throws IOException {
        writer.write("newmtl " + materialName + "\n");
        writer.write("Ns 96.078431\n");
        writer.write("Ka 1.000000 1.000000 1.000000\n");

        float r = color.getRed()   / 255.0f;
        float g = color.getGreen() / 255.0f;
        float b = color.getBlue()  / 255.0f;
        float a = color.getAlpha() / 255.0f;

        writer.write("Kd " + df.format(r) + " " + df.format(g) + " " + df.format(b) + "\n");
        writer.write("Ks 0.500000 0.500000 0.500000\n");
        writer.write("Ke 0.000000 0.000000 0.000000\n");
        writer.write("Ni 1.500000\n");
        writer.write("d " + df.format(a) + "\n");

        writer.write("illum 2\n\n");
    }

    public static void saveMeshToObj(Mesh mesh, String filePath)  {
        String mtlPath = filePath.substring(0, filePath.lastIndexOf('.')) + ".mtl";
        try {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
                writer.write(meshToString(mesh, mtlPath));
            }

            // Save MTL file (same name but with .mtl extension)

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(mtlPath))) {
                writer.write(mtlToString(mesh));
            }

        } catch (Exception e){
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