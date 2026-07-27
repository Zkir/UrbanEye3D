package ru.zkir.urbaneye3d.utils;

import java.awt.Color;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A simple importer for Wavefront OBJ files.
 * This class is designed to parse vertex and face data from an OBJ file stream,
 * including basic material color information from an associated MTL file.
 */
public class ObjImporter {

    /**
     * A simple data structure to hold the raw data from an OBJ file.
     */
    private static class ModelData {
        public final List<Point3D> vertices = new ArrayList<>();
        public final List<Point2D> uvs = new ArrayList<>();
        public final List<int[]> faces = new ArrayList<>();
        public final List<int[]> faceUVs = new ArrayList<>();
        public final List<String> faceMaterials = new ArrayList<>();
        public final Map<String, MaterialInfo> materials = new HashMap<>();
        public String textureName;
    }

    private static class MaterialInfo {
        public final Color color;
        public final String textureName;

        public MaterialInfo(Color color, String textureName) {
            this.color = color;
            this.textureName = textureName;
        }
    }

    private Map<String, MaterialInfo> parseMtlFile(String mtlPath) throws IOException {
        Map<String, MaterialInfo> materials = new HashMap<>();
        String currentMtlName = null;
        float currentR = 1.0f, currentG = 1.0f, currentB = 1.0f;
        float currentAlpha = 1.0f;
        String currentTexture = null;

        try (InputStream mtlStream = getClass().getResourceAsStream(mtlPath)) {
            if (mtlStream == null) {
                throw new FileNotFoundException("Could not find MTL file: " + mtlPath);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(mtlStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("newmtl ")) {
                        if (currentMtlName != null) {
                            materials.put(currentMtlName, new MaterialInfo(new Color(currentR, currentG, currentB, currentAlpha), currentTexture));
                        }
                        currentMtlName = line.split(" +")[1];
                        currentAlpha = 1.0f;
                        currentTexture = null;
                        currentR = 1.0f; currentG = 1.0f; currentB = 1.0f;
                    } else if (line.startsWith("Kd ") && currentMtlName != null) {
                        String[] parts = line.split(" +");
                        currentR = Float.parseFloat(parts[1]);
                        currentG = Float.parseFloat(parts[2]);
                        currentB = Float.parseFloat(parts[3]);
                    } else if (line.startsWith("map_Kd ") && currentMtlName != null) {
                        String[] parts = line.split(" +");
                        currentTexture = parts[1];
                    } else if ((line.startsWith("d ") || line.startsWith("Tr ")) && currentMtlName != null) {
                        String[] parts = line.split(" +");
                        float val = Float.parseFloat(parts[1]);
                        if (line.startsWith("Tr ")) {
                             currentAlpha = 1.0f - val;
                        } else {
                             currentAlpha = val;
                        }
                    }
                }
                if (currentMtlName != null) {
                    materials.put(currentMtlName, new MaterialInfo(new Color(currentR, currentG, currentB, currentAlpha), currentTexture));
                }
            }
        }
        return materials;
    }


    private void importModelData(String objPath, ModelData modelData) throws IOException {
        String currentMaterialName = null;

        try (InputStream objStream = getClass().getResourceAsStream(objPath)) {
            if (objStream == null) {
                throw new IOException("Failed to find model resource: " + objPath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(objStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("v ")) {
                        modelData.vertices.add(parseVertex(line));
                    } else if (line.startsWith("vt ")) {
                        modelData.uvs.add(parseUV(line));
                    } else if (line.startsWith("f ")) {
                        int[][] faceData = parseFaceWithUV(line);
                        modelData.faces.add(faceData[0]);
                        modelData.faceUVs.add(faceData[1]);
                        modelData.faceMaterials.add(currentMaterialName);
                    } else if (line.startsWith("mtllib ")) {
                        String mtlFileName = line.split(" +")[1];
                        String parentDir = objPath.substring(0, objPath.lastIndexOf('/'));
                        String mtlPath = parentDir + "/" + mtlFileName;
                        modelData.materials.putAll(parseMtlFile(mtlPath));
                    } else if (line.startsWith("usemtl ")) {
                        currentMaterialName = line.split(" +")[1];
                    }
                }
            }
        }
    }

    private Point3D parseVertex(String line) {
        String[] parts = line.split(" +");
        double x = Double.parseDouble(parts[1]);
        double y = Double.parseDouble(parts[2]);
        double z = Double.parseDouble(parts[3]);
        // (x, y, z) -> (x, -z, y)
        return new Point3D(x, -z, y);
    }

    private Point2D parseUV(String line) {
        String[] parts = line.split(" +");
        double u = Double.parseDouble(parts[1]);
        double v = Double.parseDouble(parts[2]);
        return new Point2D(u, v);
    }

    private int[][] parseFaceWithUV(String line) {
        String[] parts = line.split(" +");
        int[] vIndices = new int[parts.length - 1];
        int[] uvIndices = new int[parts.length - 1];
        boolean hasUV = false;
        for (int i = 1; i < parts.length; i++) {
            String[] subParts = parts[i].split("/");
            vIndices[i - 1] = Integer.parseInt(subParts[0]) - 1;
            if (subParts.length > 1 && !subParts[1].isEmpty()) {
                uvIndices[i - 1] = Integer.parseInt(subParts[1]) - 1;
                hasUV = true;
            } else {
                uvIndices[i - 1] = -1;
            }
        }
        return new int[][]{vIndices, hasUV ? uvIndices : null};
    }

    public Mesh loadModel(String resourcePath) {
        try {
            ModelData modelData = new ModelData();
            importModelData(resourcePath, modelData);

            Mesh mesh = new Mesh();
            Map<String, Integer> materialNameToIndex = new HashMap<>();

            // Populate mesh materials and create a name-to-index map
            int lastSlash = resourcePath.lastIndexOf('/');
            String parentDir = lastSlash >= 0 ? resourcePath.substring(0, lastSlash) : "";
            
            if (modelData.materials.isEmpty()) {
                mesh.materials.add(Color.GRAY);
                materialNameToIndex.put(null, 0);
            } else {
                for (Map.Entry<String, MaterialInfo> entry : modelData.materials.entrySet()) {
                    mesh.materials.add(entry.getValue().color);
                    materialNameToIndex.put(entry.getKey(), mesh.materials.size() - 1);
                    if (entry.getValue().textureName != null && mesh.textureName == null) {
                        String texName = entry.getValue().textureName;
                        String finalPath;
                        if (!texName.startsWith("/")) {
                            finalPath = parentDir + (parentDir.isEmpty() || parentDir.endsWith("/") ? "" : "/") + texName;
                        } else {
                            finalPath = texName;
                        }
                        // Ensure it's an absolute resource path
                        if (!finalPath.startsWith("/")) {
                            finalPath = "/" + finalPath;
                        }
                        mesh.textureName = finalPath;
                    }
                }
            }

            // Add vertices
            int[] vIndexMap = new int[modelData.vertices.size()];
            for (int i = 0; i < modelData.vertices.size(); i++) {
                vIndexMap[i] = mesh.addVertex(modelData.vertices.get(i));
            }

            // Add UVs
            int[] uvIndexMap = new int[modelData.uvs.size()];
            for (int i = 0; i < modelData.uvs.size(); i++) {
                Point2D uv = modelData.uvs.get(i);
                uvIndexMap[i] = mesh.addUV(uv.x, uv.y);
            }

            // Add faces with correct material and UVs
            for (int i = 0; i < modelData.faces.size(); i++) {
                int[] oldVFace = modelData.faces.get(i);
                int[] oldUVFace = modelData.faceUVs.get(i);
                String materialName = modelData.faceMaterials.get(i);

                int[] newVFace = new int[oldVFace.length];
                for (int j = 0; j < oldVFace.length; j++) {
                    newVFace[j] = vIndexMap[oldVFace[j]];
                }

                int[] newUVFace = null;
                if (oldUVFace != null) {
                    newUVFace = new int[oldUVFace.length];
                    for (int j = 0; j < oldUVFace.length; j++) {
                        newUVFace[j] = uvIndexMap[oldUVFace[j]];
                    }
                }

                if (newUVFace != null) {
                    // Use the official API to ensure all lists are synchronized
                    mesh.addFace(newVFace, newUVFace);
                    // Also set the material color for this face
                    Integer materialIndex = materialNameToIndex.get(materialName);
                    if (materialIndex != null) {
                        mesh.faceMaterials.set(mesh.faces.size() - 1, materialIndex);
                    }
                } else {
                    Integer materialIndex = materialNameToIndex.get(materialName);
                    mesh.addFace(newVFace, materialIndex != null ? materialIndex : 0);
                }
            }

            return mesh;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load model " + resourcePath + ": " + e.getMessage());
        }
    }
}

