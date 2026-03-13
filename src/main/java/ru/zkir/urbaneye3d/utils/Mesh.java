package ru.zkir.urbaneye3d.utils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Mesh {

    public final List<Point3D> verts;   //vertex coordinates (3D)
    public final List<Point2D> uvs;     //UV coordinates  (2D)
    public final List<Color> materials; //currently we have Color only.

    /** Main array for faces. Contains Vertex indices*/
    public final List<int[]> faces;

    /**  Contains uvs indices for each face vertex */
    public final List<int[]> faceUVs;

    /**  Contains color indices for each face  */
    public final List<Integer> faceMaterials;


    //face groups for "backward compatibility" with mesher logic.
    private final List<Integer> roofFaces;
    private final List<Integer> wallFaces;
    private final List<Integer> bottomFaces;

    private static final int BOTTOM_COLOUR_IDX = 0;
    private static final int WALL_COLOUR_IDX = 1;
    private static final int ROOF_COLOUR_IDX = 2;

    /** Cache to store unique vertices and avoid duplicates. */
    private final transient Map<Point3D, Integer> vertexCache = new HashMap<>();

    /** The default (and public) constructor. Empty arrays are initialized for vertices and faces */
    public Mesh() {
        this.verts = new ArrayList<>();
        this.uvs = new ArrayList<>();
        this.materials = new ArrayList<>();

        this.faces = new ArrayList<>();
        this.faceUVs = new ArrayList<>();
        this.faceMaterials = new ArrayList<>();

        this.bottomFaces = new ArrayList<>();
        this.wallFaces = new ArrayList<>();
        this.roofFaces = new ArrayList<>();
    }

    /** For buildings, colors should be specified */
    public Mesh(Color bottomColor, Color wallColor, Color roofColor){
        this();
        this.materials.add(bottomColor);
        this.materials.add(wallColor);
        this.materials.add(roofColor);
    }

    /**
     * Adds a vertex to the mesh, ensuring uniqueness to avoid duplicates.
     * It uses a cache with rounded coordinates to merge vertices that are very close.
     * @param p The Point3D to add.
     * @return The index of the existing or newly added vertex.
     */
    public int addVertex(Point3D p) {
        // Round the point to a certain precision to use as a key in the cache.
        double scale = 1e6;
        Point3D roundedP = new Point3D(
            Math.round(p.x * scale) / scale,
            Math.round(p.y * scale) / scale,
            Math.round(p.z * scale) / scale
        );

        // If a close enough vertex is already in the cache, return its index.
        // Otherwise, add the new (original precision) point to the list and cache it.
        return vertexCache.computeIfAbsent(roundedP, k -> {
            verts.add(p);
            return verts.size() - 1;
        });
    }

    public int addUV(double u, double v){
        uvs.add(new Point2D(u,v));
        return uvs.size() - 1;
    }

    /** Adds face to "bottom" group. */
    public void addBottomFace(int[] indices) {
        faces.add(indices);
        faceMaterials.add(BOTTOM_COLOUR_IDX);
        faceUVs.add(null);
        bottomFaces.add(faces.size()-1);
    }

    /** Adds face to "wall" group */
    public void addWallFace(int[] indices) {
        faces.add(indices);
        faceMaterials.add(WALL_COLOUR_IDX);
        faceUVs.add(null);
        wallFaces.add(faces.size()-1);
    }

    /** Adds face to "roof" group */
    public void addRoofFace(int[] indices) {
        faces.add(indices);
        faceMaterials.add(ROOF_COLOUR_IDX);
        faceUVs.add(null);
        roofFaces.add(faces.size()-1);
    }

    /*Adds face to general group. UV texture is used instead of material  */
    public void addFace(int[] vertIndices, int[] uvIndices){
        faces.add(vertIndices);
        faceMaterials.add(0); //TODO: it should be rather NULL
        faceUVs.add(uvIndices);
    }

    /** Adds a face with a specified material index. */
    public void addFace(int[] indices, int materialIndex) {
        faces.add(indices);
        faceMaterials.add(materialIndex);
        faceUVs.add(null); // No UV for this kind of face
    }

    /**
     * Checks whether the vertex with the given coordinates exists in the mesh
     * @param p The Point3D to check.
     * @return The index of the vertex, if it exists, and -1 if it does not.
     */
    public int getVertexId(Point3D p) {
        // Round the point to a certain precision to use as a key in the cache.
        double scale = 1e6;
        Point3D roundedP = new Point3D(
            Math.round(p.x * scale) / scale,
            Math.round(p.y * scale) / scale,
            Math.round(p.z * scale) / scale
        );
        return vertexCache.getOrDefault(roundedP, -1);
    }

    /**
     * Extracts a subset of faces from a source mesh into a new, clean mesh.
     * The new mesh will contain only the specified faces and the vertices required by them.
     * Vertex indices in the new faces will be re-mapped accordingly.
     *
     * @param sourceMesh The mesh to extract from.
     * @param facesToExtract A list of face arrays (each array contains vertex indices from the source mesh).
     * @return A new Mesh containing only the extracted geometry.
     */
    public static Mesh extractFaces(Mesh sourceMesh, List<int[]> facesToExtract) {
        Mesh newMesh = new Mesh();
        Map<Integer, Integer> oldToNewIndexMap = new HashMap<>();
        Set<Integer> usedIndices = new HashSet<>();

        // 1. Find all unique vertices used by the faces to extract
        facesToExtract.forEach(face -> Arrays.stream(face).forEach(usedIndices::add));

        // 2. Create new vertices and a map from old to new indices
        for (int oldIndex : usedIndices) {
            Point3D vertex = sourceMesh.verts.get(oldIndex);
            int newIndex = newMesh.addVertex(vertex);
            oldToNewIndexMap.put(oldIndex, newIndex);
        }

        // 3. Create re-indexed faces for the new mesh
        for (int[] face : facesToExtract) {
            int[] newFace = new int[face.length];
            for (int i = 0; i < face.length; i++) {
                newFace[i] = oldToNewIndexMap.get(face[i]);
            }
            // Assuming all extracted faces are roof faces for this new mesh context
            newMesh.addRoofFace(newFace);
        }
        newMesh.materials.addAll(sourceMesh.materials);

        return newMesh;
    }

    /**
     * Extrudes a roof mesh downwards to create a solid body.
     * @param depth The distance to extrude downwards.
     * @return A new Mesh object representing the solid body.
     */
    public Mesh extrude(double depth) {
        // Validation: Ensure the mesh is a roof-like surface.
        if (!this.bottomFaces.isEmpty() || !this.wallFaces.isEmpty()) {
            throw new IllegalArgumentException("Cannot extrude a mesh that already contains bottom or wall faces.");
        }

        List<int[]> allRoofFaces = this.getRoofFaces();

        Mesh newMesh = new Mesh();
        newMesh.materials.addAll(this.materials);

        newMesh.verts.addAll(this.verts);
        for (int[] face: allRoofFaces) {
            newMesh.addRoofFace(face);
        }

        Map<Integer, Integer> extrudedVertsMap = new HashMap<>();
        Set<Integer> uniqueVertIndices = new HashSet<>();
        for (int[] face : allRoofFaces) {
            for (int index : face) {
                uniqueVertIndices.add(index);
            }
        }

        for (int index : uniqueVertIndices) {
            Point3D p = this.verts.get(index);
            Point3D extrudedP = new Point3D(p.x, p.y, p.z - depth);
            // Use newMesh.addVertex to handle potential duplicates in the extruded points
            int newIndex = newMesh.addVertex(extrudedP);
            extrudedVertsMap.put(index, newIndex);
        }

        // Find boundary edges of the entire roof shell
        Map<String, int[]> edgeToFaceOwner = new HashMap<>();
        for (int[] face : allRoofFaces) {
            for (int i = 0; i < face.length; i++) {
                int v1 = face[i];
                int v2 = face[(i + 1) % face.length];
                String edgeKey = (v1 < v2) ? (v1 + "-" + v2) : (v2 + "-" + v1);
                if (edgeToFaceOwner.containsKey(edgeKey)) {
                    edgeToFaceOwner.remove(edgeKey); // Edge is shared, remove it
                } else {
                    edgeToFaceOwner.put(edgeKey, new int[]{v1, v2});
                }
            }
        }
        List<int[]> boundaryEdges = new ArrayList<>(edgeToFaceOwner.values());

        // Create new wall faces for the extruded sides
        for (int[] edge : boundaryEdges) {
            int v1_orig = edge[0];
            int v2_orig = edge[1];
            int v1_new = extrudedVertsMap.get(v1_orig);
            int v2_new = extrudedVertsMap.get(v2_orig);
            newMesh.addWallFace(new int[]{v2_orig, v1_orig, v1_new, v2_new});
        }

        // Create bottom faces
        for (int[] face : allRoofFaces) {
            int[] newFace = new int[face.length];
            boolean allVerticesFound = true;
            for (int i = 0; i < face.length; i++) {
                Integer newIndex = extrudedVertsMap.get(face[i]);
                if (newIndex == null) {
                    allVerticesFound = false;
                    break;
                }
                newFace[i] = newIndex;
            }

            if (allVerticesFound) {
                 // Reverse the face to point downwards
                for (int i = 0; i < newFace.length / 2; i++) {
                    int temp = newFace[i];
                    newFace[i] = newFace[newFace.length - 1 - i];
                    newFace[newFace.length - 1 - i] = temp;
                }
                newMesh.addBottomFace(newFace);
            }
        }

        return newMesh;
    }

    // Getters for face groups are still needed for special operations, like roof extrusion for building=roof
    //TODO: reduce usage of those getters.
    public final List<int[]> getRoofFaces(){
        List<int[]> result = new ArrayList<>(roofFaces.size());
        for (int index : roofFaces) {
            result.add(faces.get(index));
        }
        return result;
    }
    public final List<int[]> getWallFaces() {
        List<int[]> result = new ArrayList<>(wallFaces.size());
        for (int index : wallFaces) {
            result.add(faces.get(index));
        }
        return result;
    }

    public final List<int[]> getBottomFaces() {
        List<int[]> result = new ArrayList<>(bottomFaces.size());
        for (int index : bottomFaces) {
            result.add(faces.get(index));
        }
        return result;
    }

}
