package ru.zkir.urbaneye3d.utils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
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
                if (material != null) {
                    writeMaterial(writer, "material" + i, material, df);
                }
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
        Path path = Path.of(filePath);
        Path parentDir = path.getParent();
        if (parentDir!=null){
            try {
                Files.createDirectories(parentDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

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

    /** Name of the MTL material that carries a generated texture atlas (facades, UV-unwrapped meshes). */
    private static final String ATLAS_MATERIAL_NAME = "generatedAtlas";

    /**
     * Saves the mesh together with a generated texture atlas: OBJ with vertex and texture coordinates,
     * an MTL file referencing that atlas and the atlas itself as a PNG.
     * This is how a UV-unwrapped building (with a facade applied) is inspected in an external viewer.
     *
     * @param mesh     mesh that already carries UV coordinates (see {@link UvGenerator}).
     * @param texture  the atlas painted for those UV coordinates.
     * @param filePath path of the .obj file to write.
     */
    public static void saveMeshToObj(Mesh mesh, BufferedImage texture, String filePath) {
        Path path = Path.of(filePath);
        Path parentDir = path.getParent();
        if (parentDir != null) {
            try {
                Files.createDirectories(parentDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        String baseName = filePath.substring(0, filePath.lastIndexOf('.'));
        String mtlPath = baseName + ".mtl";
        String pngFileName = new File(baseName + ".png").getName();

        try {
            if (texture != null) {
                ImageIO.write(texture, "png", new File(baseName + ".png"));
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(mtlPath))) {
                writer.write(mtlToString(mesh));
                if (texture != null) {
                    writer.write("newmtl " + ATLAS_MATERIAL_NAME + "\n");
                    writer.write("Ka 1.000000 1.000000 1.000000\n");
                    writer.write("Kd 1.000000 1.000000 1.000000\n");
                    writer.write("Ks 0.000000 0.000000 0.000000\n");
                    writer.write("d 1.000000\n");
                    writer.write("illum 1\n");
                    writer.write("map_Kd " + pngFileName + "\n\n");
                }
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
                writer.write(meshWithTextureToString(mesh, mtlPath));
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to export OBJ with texture: " + e.getMessage(), e);
        }
    }

    /**
     * OBJ text for a mesh that has UV coordinates: texture coordinates are written as "vt" lines,
     * faces that carry them refer to them as "vertex/uv", other faces are written by material.
     */
    public static String meshWithTextureToString(Mesh mesh, String mtl_path) {
        var mtl_name = new File(mtl_path).getName();
        if (mtl_name.isBlank()) {
            mtl_name = "default.mtl";
        }

        StringWriter stringWriter = new StringWriter();
        try (BufferedWriter writer = new BufferedWriter(stringWriter)) {
            writer.write("# Blender-compatible OBJ\n");
            writer.write("# Created by UrbanEye3D\n");
            writer.write("mtllib " + mtl_name + "\n\n");

            DecimalFormat df = new DecimalFormat("0.000000", new DecimalFormatSymbols(Locale.US));

            for (Point3D v : mesh.verts) {
                writer.write("v " + df.format(v.x) + " " + df.format(v.z) + " " + df.format(v.y) + "\n");
            }

            if (!mesh.uvs.isEmpty()) {
                writer.write("\n# Texture coordinates\n");
                for (Point2D uv : mesh.uvs) {
                    writer.write("vt " + df.format(uv.x) + " " + df.format(uv.y) + "\n");
                }
            }

            writer.write("\ng object_default\n");

            String currentMaterial = null;
            for (int i = 0; i < mesh.faces.size(); i++) {
                int[] face = mesh.faces.get(i);
                if (face == null || face.length < 3) continue;

                int[] faceUVs = i < mesh.faceUVs.size() ? mesh.faceUVs.get(i) : null;
                String material = faceUVs != null ? ATLAS_MATERIAL_NAME
                        : "material" + mesh.faceMaterials.get(i);
                if (!material.equals(currentMaterial)) {
                    writer.write("usemtl " + material + "\n");
                    currentMaterial = material;
                }

                writer.write("f");
                for (int j = 0; j < face.length; j++) {
                    writer.write(" " + (face[j] + 1));
                    if (faceUVs != null && j < faceUVs.length) {
                        writer.write("/" + (faceUVs[j] + 1));
                    }
                }
                writer.write("\n");
            }

        } catch (IOException e) {
            // StringWriter does not throw IOException
            throw new RuntimeException(e);
        }
        return stringWriter.toString();
    }
}