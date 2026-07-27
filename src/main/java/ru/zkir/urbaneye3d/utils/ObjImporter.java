package ru.zkir.urbaneye3d.utils;

import ru.zkir.urbaneye3d.UrbanEye3dPlugin;

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
        public final List<int[]> faces = new ArrayList<>();
        public final List<String> faceMaterials = new ArrayList<>();
        public final Map<String, Color> materials = new HashMap<>();
    }

    private Map<String, Color> parseMtlFile(String mtlPath) throws IOException {
        Map<String, Color> materials = new HashMap<>();
        String currentMtlName = null;
        float currentR = 0, currentG = 0, currentB = 0;
        float currentAlpha = 1.0f;

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
                            materials.put(currentMtlName, new Color(currentR, currentG, currentB, currentAlpha));
                        }
                        currentMtlName = line.split(" +")[1];
                        currentAlpha = 1.0f;
                    } else if (line.startsWith("Kd ") && currentMtlName != null) {
                        String[] parts = line.split(" +");
                        currentR = Float.parseFloat(parts[1]);
                        currentG = Float.parseFloat(parts[2]);
                        currentB = Float.parseFloat(parts[3]);
                    } else if ((line.startsWith("d ") || line.startsWith("Tr ")) && currentMtlName != null) {
                        String[] parts = line.split(" +");
                        float val = Float.parseFloat(parts[1]);
                        // In MTL, 'd' (dissolve) 1.0 is opaque, 0.0 is transparent.
                        // 'Tr' (transparency) can be inverted depending on implementation, 
                        // but usually 'Tr' 1.0 means transparent. Let's stick to 'd' logic first.
                        if (line.startsWith("Tr ")) {
                             currentAlpha = 1.0f - val;
                        } else {
                             currentAlpha = val;
                        }
                    }
                }
                if (currentMtlName != null) {
                    materials.put(currentMtlName, new Color(currentR, currentG, currentB, currentAlpha));
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
                    } else if (line.startsWith("f ")) {
                        modelData.faces.add(parseFace(line));
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
        // To convert from a Y-up coordinate system (common in 3D modeling)
        // to our Z-up system, we perform a 90-degree rotation around the X-axis.
        // (x, y, z) -> (x, -z, y)
        return new Point3D(x, -z, y);
    }

    private int[] parseFace(String line) {
        String[] parts = line.split(" +");
        int[] faceIndices = new int[parts.length - 1];
        for (int i = 1; i < parts.length; i++) {
            // OBJ format is 1-based, our list is 0-based.
            // We only care about the vertex index, ignore texture/normal indices.
            String vertexIndexStr = parts[i].split("/")[0];
            faceIndices[i - 1] = Integer.parseInt(vertexIndexStr) - 1;
        }
        return faceIndices;
    }

    public Mesh loadModel(String resourcePath) {
        try {
            ModelData modelData = new ModelData();
            importModelData(resourcePath, modelData);

            Mesh mesh = new Mesh();
            Map<String, Integer> materialNameToIndex = new HashMap<>();

            // Populate mesh materials and create a name-to-index map
            if (modelData.materials.isEmpty()) {
                // Add a default material if none are loaded
                mesh.materials.add(Color.GRAY);
                materialNameToIndex.put(null, 0);
            } else {
                for (Map.Entry<String, Color> entry : modelData.materials.entrySet()) {
                    mesh.materials.add(entry.getValue());
                    materialNameToIndex.put(entry.getKey(), mesh.materials.size() - 1);
                }
            }

            // Add vertices
            int[] indexMap = new int[modelData.vertices.size()];
            for (int i = 0; i < modelData.vertices.size(); i++) {
                indexMap[i] = mesh.addVertex(modelData.vertices.get(i));
            }

            // Add faces with correct material
            for (int i = 0; i < modelData.faces.size(); i++) {
                int[] oldFace = modelData.faces.get(i);
                String materialName = modelData.faceMaterials.get(i);

                int[] newFace = new int[oldFace.length];
                for (int j = 0; j < oldFace.length; j++) {
                    newFace[j] = indexMap[oldFace[j]];
                }

                Integer materialIndex = materialNameToIndex.get(materialName);
                if (materialIndex == null) {
                    materialIndex = 0; // Default to the first material (or gray)
                }

                mesh.addFace(newFace, materialIndex);
            }

            return mesh;
        } catch (IOException e) {
            UrbanEye3dPlugin.debugMsg("Failed to load model " + resourcePath + ": " + e.getMessage());
            return null;
        }
    }
}

