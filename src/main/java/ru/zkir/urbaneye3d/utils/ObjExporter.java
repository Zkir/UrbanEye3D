package ru.zkir.urbaneye3d.utils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

public class ObjExporter {

    // For non-textured meshes
    public static String meshToString(Mesh mesh) {
        StringWriter stringWriter = new StringWriter();
        try (BufferedWriter writer = new BufferedWriter(stringWriter)) {
            writer.write("# Blender-compatible OBJ\n");
            DecimalFormat df = new DecimalFormat("0.000000", new DecimalFormatSymbols(Locale.US));

            for (Point3D v : mesh.verts) {
                writer.write("v " + df.format(v.x) + " " + df.format(v.z) + " " + df.format(v.y) + "\n");
            }

            writer.write("\ng object_default\n");
            writer.write("\n# Faces\n");
            writeFaces(writer, mesh.faces);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return stringWriter.toString();
    }

    // For textured meshes
    private static String meshWithTextureToString(Mesh mesh, String mtlFileName) {
        StringWriter stringWriter = new StringWriter();
        try (BufferedWriter writer = new BufferedWriter(stringWriter)) {
            writer.write("# Blender-compatible OBJ\n");
            writer.write("mtllib " + mtlFileName + "\n\n");
            DecimalFormat df = new DecimalFormat("0.000000", new DecimalFormatSymbols(Locale.US));

            // Vertices
            for (Point3D v : mesh.verts) {
                writer.write("v " + df.format(v.x) + " " + df.format(v.z) + " " + df.format(v.y) + "\n");
            }

            // Texture Coords
            writer.write("\n");
            for (Point2D uv : mesh.uvs) {
                writer.write("vt " + df.format(uv.x) + " " + df.format(uv.y) + "\n");
            }

            // Faces
            writer.write("\ng object_default\n");
            writer.write("usemtl TexturedMaterial\n");
            writeFacesWithUvs(writer, mesh);

        } catch (IOException e) {
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

    public static void saveMeshToObj(Mesh mesh, BufferedImage texture, String objFilePath) {
        File objFile = new File(objFilePath);
        String baseName = objFile.getName();
        if (baseName.toLowerCase().endsWith(".obj")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }

        String mtlFileName = baseName + ".mtl";
        String pngFileName = baseName + ".png";

        String parentDir = objFile.getParent();
        if (parentDir == null) parentDir = ".";


        try {
            // 1. Save PNG texture
            File pngFile = Paths.get(parentDir, pngFileName).toFile();
            ImageIO.write(texture, "png", pngFile);

            // 2. Save MTL file
            File mtlFile = Paths.get(parentDir, mtlFileName).toFile();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(mtlFile))) {
                writer.write(generateMtlFileContent(pngFileName));
            }

            // 3. Save OBJ file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(objFile))) {
                writer.write(meshWithTextureToString(mesh, mtlFileName));
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to export OBJ with texture", e);
        }
    }


    private static String generateMtlFileContent(String textureFileName) {
        return "newmtl TexturedMaterial\n" +
               "Ka 1.0 1.0 1.0\n" + // Ambient color
               "Kd 1.0 1.0 1.0\n" + // Diffuse color
               "Ks 0.0 0.0 0.0\n" + // Specular color
               "d 1.0\n" +          // Alpha
               "illum 1\n" +        // Illumination model
               "map_Kd " + textureFileName + "\n"; // Diffuse texture map
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

    private static void writeFacesWithUvs(BufferedWriter writer, Mesh mesh) throws IOException {
        for (int i = 0; i < mesh.faces.size(); i++) {
            int[] faceVerts = mesh.faces.get(i);
            int[] faceUvs = mesh.faceUVs.get(i);

            if (faceVerts.length >= 3 && faceUvs != null && faceVerts.length == faceUvs.length) {
                writer.write("f");
                for (int j = 0; j < faceVerts.length; j++) {
                    int vertIndex = faceVerts[j] + 1;
                    int uvIndex = faceUvs[j] + 1;
                    writer.write(" " + vertIndex + "/" + uvIndex);
                }
                writer.write("\n");
            }
        }
    }
}