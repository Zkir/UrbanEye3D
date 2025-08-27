package ru.zkir.urbaneye3d.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Mesh {
    public List<Point3D> verts = new ArrayList<>();
    public List<int[]> roofFaces = new ArrayList<>();
    public List<int[]> wallFaces = new ArrayList<>();
    public List<int[]> bottomFaces = new ArrayList<>();

    // Cache to store unique vertices and avoid duplicates.
    private transient Map<Point3D, Integer> vertexCache = new HashMap<>();

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
}
