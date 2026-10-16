package ru.zkir.urbaneye3d.utils;

import ru.zkir.urbaneye3d.UrbanEye3dPlugin;

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
    public String textureName;          // Optional texture name for the whole mesh

    /** Main array for faces. Contains Vertex indices*/
    public final List<int[]> faces;

    /**  Contains uvs indices for each face vertex */
    public final List<int[]> faceUVs;

    /**  Contains color indices for each face  */
    public final List<Integer> faceMaterials;

    public static final int BOTTOM_COLOUR_IDX = 0;
    public static final int WALL_COLOUR_IDX = 1;
    public static final int ROOF_COLOUR_IDX = 2;

    /** Cache to store unique vertices and avoid duplicates. */
    private final transient Map<Point3D, Integer> vertexCache = new HashMap<>();

    private Point3D minBounds;
    private Point3D maxBounds;

    /** The default (and public) constructor. Empty arrays are initialized for vertices and faces */
    public Mesh() {
        this.verts = new ArrayList<>();
        this.uvs = new ArrayList<>();
        this.materials = new ArrayList<>();

        this.faces = new ArrayList<>();
        this.faceUVs = new ArrayList<>();
        this.faceMaterials = new ArrayList<>();

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
        //   Precision value is set this way because we want to prevent adding of the SAME vertex several times.
        //   We do not expect glitches that coordinate goes back and force say, for several centimeters (0.01)
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
     *   Alternative version of the addVertex() method
     *   It merges vertices aggressively
     */
    public int addVertex2(Point3D p) {
        final double EPSILON = 0.01;
        final double eps2 = EPSILON * EPSILON;
        for (int i=0, n=verts.size(); i<n; i++){
            Point3D q=verts.get(i);
            double dx = p.x - q.x, dy = p.y - q.y, dz = p.z - q.z;
            var d2 = dx*dx + dy*dy + dz*dz;
            if (d2 < eps2){
                return i;
            }
        }
        verts.add(p);
        return verts.size() - 1;
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
    }

    /** Adds face to "wall" group */
    public void addWallFace(int[] indices) {
        faces.add(indices);
        faceMaterials.add(WALL_COLOUR_IDX);
        faceUVs.add(null);
    }

    /** Adds face to "roof" group */
    public void addRoofFace(int[] indices) {
        faces.add(indices);
        faceMaterials.add(ROOF_COLOUR_IDX);
        faceUVs.add(null);
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
     * Rotates all vertices of the mesh around the Z axis by the given angle.
     * @param angleDegrees the rotation angle in degrees.
     */
    public void rotate(double angleDegrees) {
        double angleRad = Math.toRadians(angleDegrees);
        double cosA = Math.cos(angleRad);
        double sinA = Math.sin(angleRad);
        
        for (int i = 0; i < verts.size(); i++) {
            Point3D p = verts.get(i);
            double newX = p.x * cosA - p.y * sinA;
            double newY = p.x * sinA + p.y * cosA;
            verts.set(i, new Point3D(newX, newY, p.z));
        }
        
        // Invalidate bounding box and vertex cache as coordinates have changed
        invalidateBBOX();

    }
    public void rotateY(double angleDegrees) {
        double angleRad = Math.toRadians(angleDegrees);
        double cosA = Math.cos(angleRad);
        double sinA = Math.sin(angleRad);

        for (int i = 0; i < verts.size(); i++) {
            Point3D p = verts.get(i);
            double newX = p.x * cosA - p.z * sinA;
            double newZ = p.x * sinA + p.z * cosA;
            verts.set(i, new Point3D(newX, p.y, newZ));
        }

        // Invalidate bounding box and vertex cache as coordinates have changed
        invalidateBBOX();

    }

    public void translate(Point3D translation){
        for (int i = 0; i < verts.size(); i++) {
            verts.set(i, verts.get(i).add(translation));
        }
        invalidateBBOX();
    }

    public void scale(double scaleFactor){
        for (int i = 0; i < verts.size(); i++) {
            verts.set(i, verts.get(i).mult(scaleFactor));
        }
        // Invalidate bounding box and vertex cache as coordinates have changed
        invalidateBBOX();
    }

    public void scale(double sx, double sy, double sz){
        for (int i = 0; i < verts.size(); i++) {
            var v=verts.get(i);
            v.x = v.x * sx;
            v.y = v.y * sy;
            v.z = v.z * sz;
        }
        // Invalidate bounding box and vertex cache as coordinates have changed
        invalidateBBOX();
    }

    void invalidateBBOX(){
        minBounds = null;
        maxBounds = null;
        vertexCache.clear();
        for (int i = 0; i < verts.size(); i++) {
            Point3D p = verts.get(i);
            double scale = 1e6;
            Point3D roundedP = new Point3D(
                    Math.round(p.x * scale) / scale,
                    Math.round(p.y * scale) / scale,
                    Math.round(p.z * scale) / scale
            );
            vertexCache.put(roundedP, i);
        }
    }

    /**
     * Creates a deep copy of this Mesh.
     * @return a new Mesh instance with copied data.
     */
    public Mesh clone() {
        Mesh cloned = new Mesh();
        
        for (Point3D p : this.verts) {
            cloned.verts.add(new Point3D(p.x, p.y, p.z));
        }
        for (Point2D uv : this.uvs) {
            cloned.uvs.add(new Point2D(uv.x, uv.y));
        }
        cloned.materials.addAll(this.materials);
        
        for (int[] face : this.faces) {
            cloned.faces.add(face != null ? Arrays.copyOf(face, face.length) : null);
        }
        for (int[] faceUV : this.faceUVs) {
            cloned.faceUVs.add(faceUV != null ? Arrays.copyOf(faceUV, faceUV.length) : null);
        }
        cloned.faceMaterials.addAll(this.faceMaterials);

        for (Map.Entry<Point3D, Integer> entry : this.vertexCache.entrySet()) {
            Point3D p = entry.getKey();
            cloned.vertexCache.put(new Point3D(p.x, p.y, p.z), entry.getValue());
        }
        
        if (this.minBounds != null) {
            cloned.minBounds = new Point3D(this.minBounds.x, this.minBounds.y, this.minBounds.z);
        }
        if (this.maxBounds != null) {
            cloned.maxBounds = new Point3D(this.maxBounds.x, this.maxBounds.y, this.maxBounds.z);
        }

        cloned.textureName = this.textureName;
        
        return cloned;
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
        if (!this.getBottomFaces().isEmpty() || !this.getWallFaces().isEmpty()) {
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
        List<int[]> result = new ArrayList<>();
        for (int index=0; index<faces.size(); index++ ) {
            if (faceMaterials.get(index) == ROOF_COLOUR_IDX) {
                result.add(faces.get(index));
            }
        }
        return result;
    }
    public final List<int[]> getWallFaces() {
        List<int[]> result = new ArrayList<>();
        for (int index=0; index<faces.size(); index++ ) {
            if (faceMaterials.get(index) == WALL_COLOUR_IDX) {
                //the only way to fetch wall faces is by material index
                result.add(faces.get(index));
            }
        }
        return result;
    }

    public final List<int[]> getBottomFaces() {
        List<int[]> result = new ArrayList<>();
        for (int index=0; index<faces.size(); index++ ) {
            if (faceMaterials.get(index) == BOTTOM_COLOUR_IDX) {
                result.add(faces.get(index));
            }
        }
        return result;
    }

    public void computeBounds() {
        if (verts.isEmpty()) {
            minBounds = new Point3D(0, 0, 0);
            maxBounds = new Point3D(0, 0, 0);
            return;
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Point3D p : verts) {
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            minZ = Math.min(minZ, p.z);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
            maxZ = Math.max(maxZ, p.z);
        }
        minBounds = new Point3D(minX, minY, minZ);
        maxBounds = new Point3D(maxX, maxY, maxZ);
    }

    public Point3D getMinBounds() {
        if (minBounds == null) computeBounds();
        return minBounds;
    }

    public Point3D getMaxBounds() {
        if (maxBounds == null) computeBounds();
        return maxBounds;
    }

    final double EDGE_TOLERANCE_M = 0.05;

    /**
     * Finds the topmost intersection of the vertical line going through (x,y) with the given mesh.
     * @return z of the topmost intersection, or Double.NaN if the line does not cross the mesh
     */
    public double getTopmostVerticalRayIntersection(double x, double y) {


        Point3D min = this.getMinBounds();
        Point3D max = this.getMaxBounds();
        if (x < min.x - EDGE_TOLERANCE_M || x > max.x + EDGE_TOLERANCE_M ||
                y < min.y - EDGE_TOLERANCE_M || y > max.y + EDGE_TOLERANCE_M) {
            return Double.NaN;
        }

        double topmost = Double.NaN;
        for (int[] face : this.faces) {
            double z = getFaceZ(face, x, y);
            if (!Double.isNaN(z) && (Double.isNaN(topmost) || z > topmost)) {
                topmost = z;
            }
        }
        return topmost;
    }

    /**
     * Finds z of the intersection of the vertical line going through (x,y) with the given face.
     * The intersection is considered to exist only if (x,y) belongs to the horizontal projection
     * of the face.
     * @return z of the intersection, or Double.NaN if there is no intersection
     */
    private double getFaceZ(int[] face, double x, double y) {

        final double MIN_NORMAL_Z_RATIO = 1e-6; //Minimal relative z-component of a face normal. Faces with a smaller value are considered vertical.

        if (face == null || face.length < 3) {
            return Double.NaN;
        }
        int n = face.length;

        //Newell's method: it gives a correct normal for any planar polygon, convex or not
        double nx = 0;
        double ny = 0;
        double nz = 0;
        for (int i = 0; i < n; i++) {
            Point3D a = verts.get(face[i]);
            Point3D b = verts.get(face[(i + 1) % n]);
            nx += (a.y - b.y) * (a.z + b.z);
            ny += (a.z - b.z) * (a.x + b.x);
            nz += (a.x - b.x) * (a.y + b.y);
        }

        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1e-12) {
            return Double.NaN; //degenerate face
        }
        if (Math.abs(nz) / length < MIN_NORMAL_Z_RATIO) {
            return Double.NaN; //vertical face, the vertical line either lies in its plane or does not cross it
        }

        //the face lies in the plane: nx*(x-x0) + ny*(y-y0) + nz*(z-z0) = 0
        Point3D v0 = verts.get(face[0]);
        double z = v0.z - (nx * (x - v0.x) + ny * (y - v0.y)) / nz;

        return containsXY(face, verts, x, y) ? z : Double.NaN;
    }

    /**
     * Checks whether (x,y) belongs to the horizontal projection of the face.
     * Points lying on the border of the face (within EDGE_TOLERANCE_M) are considered to belong to it,
     * because in this case the vertical line crosses the border between two neighbouring faces,
     * and both of them should give (almost) the same z.
     */
    private boolean containsXY(int[] face, List<Point3D> verts, double x, double y) {
        boolean inside = false;
        int n = face.length;
        for (int i = 0; i < n; i++) {
            Point3D a = verts.get(face[i]);
            Point3D b = verts.get(face[(i + 1) % n]);

            if (distanceToSegment(x, y, a.x, a.y, b.x, b.y) <= EDGE_TOLERANCE_M) {
                return true;
            }
            //standard ray casting
            if (((a.y > y) != (b.y > y)) && (x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x)) {
                inside = !inside;
            }
        }
        return inside;
    }

    private double distanceToSegment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSq = dx * dx + dy * dy;
        double t = 0;
        if (lengthSq > 1e-12) {
            t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lengthSq));
        }
        double cx = ax + t * dx;
        double cy = ay + t * dy;
        return Math.hypot(px - cx, py - cy);
    }


    /**
     * Fixes "T-junction" geometry errors.
     *
     * <p>A T-junction occurs when a vertex X lies geometrically on an edge A-B
     * of a face, but the edge A-B itself is not split at point X.
     * This results in "gaps" appearing between faces during rendering.</p>
     *
     * <p>The method iterates through all faces; for each edge A-B, it identifies all
     * vertices that lie strictly within the segment A-B, sorts them by the parameter t
     * (their position along the edge from A to B), and inserts them in order.
     *
     * <p>If a face has UV coordinates, the UV values for the inserted vertices
     * are interpolated between the UV coordinates of the edge's endpoints.</p>
     */
    public void fixTJunctions() {
        final double tolerance = 0.01; // допуск коллинеарности (в единицах сетки)

        for (int faceIdx = 0; faceIdx < faces.size(); faceIdx++) {
            final int[] face = faces.get(faceIdx);
            if (face == null || face.length < 3) continue;

            final int[] faceUV = faceUVs.get(faceIdx);

            // Вершины, уже входящие в данную грань — их повторно не вставляем.
            final Set<Integer> faceVertices = new HashSet<>();
            for (int v : face) faceVertices.add(v);

            final List<Integer> newFace   = new ArrayList<>(face.length);
            final List<Integer> newFaceUV = (faceUV != null) ? new ArrayList<>(face.length) : null;

            boolean modified = false;
            final int n = face.length;

            for (int i = 0; i < n; i++) {
                final int a = face[i];
                final int b = face[(i + 1) % n];

                // Всегда добавляем вершину A
                newFace.add(a);
                if (faceUV != null) newFaceUV.add(faceUV[i]);

                final Point3D pa = verts.get(a);
                final Point3D pb = verts.get(b);

                // Быстрая AABB-отсечка кандидатов
                final double minX = Math.min(pa.x, pb.x) - tolerance;
                final double maxX = Math.max(pa.x, pb.x) + tolerance;
                final double minY = Math.min(pa.y, pb.y) - tolerance;
                final double maxY = Math.max(pa.y, pb.y) + tolerance;
                final double minZ = Math.min(pa.z, pb.z) - tolerance;
                final double maxZ = Math.max(pa.z, pb.z) + tolerance;

                // Ищем вершины, лежащие строго внутри отрезка A-B
                final List<double[]> onEdge = new ArrayList<>(); // {t, vertexIndex}
                for (int v = 0; v < verts.size(); v++) {
                    if (v == a || v == b) continue;
                    if (faceVertices.contains(v)) {
                        //continue;
                    }

                    final Point3D p = verts.get(v);
                    if (p.x < minX || p.x > maxX ||
                            p.y < minY || p.y > maxY ||
                            p.z < minZ || p.z > maxZ) {
                        continue;
                    }

                    final double t = pointOnSegment(pa, pb, p, tolerance);
                    if (t > 0.0 && t < 1.0) {
                        onEdge.add(new double[]{t, v});
                    }
                }

                if (onEdge.isEmpty()) continue;

                modified = true;

                // Сортируем по положению вдоль ребра (от A к B)
                onEdge.sort((p1, p2) -> Double.compare(p1[0], p2[0]));

                // Вставляем промежуточные вершины по порядку
                for (double[] entry : onEdge) {
                    final int midV = (int) entry[1];
                    final double t  = entry[0];

                    newFace.add(midV);

                    if (faceUV != null) {
                        final int uvA = faceUV[i];
                        final int uvB = faceUV[(i + 1) % n];
                        final Point2D pUVa = uvs.get(uvA);
                        final Point2D pUVb = uvs.get(uvB);
                        final int newUVIdx = addUV(
                                pUVa.x + (pUVb.x - pUVa.x) * t,
                                pUVa.y + (pUVb.y - pUVa.y) * t
                        );
                        newFaceUV.add(newUVIdx);
                    }
                }
            }

            if (modified) {
                final int[] updatedFace = new int[newFace.size()];
                for (int i = 0; i < newFace.size(); i++) updatedFace[i] = newFace.get(i);
                faces.set(faceIdx, updatedFace);

                if (faceUV != null) {
                    final int[] updatedUV = new int[newFaceUV.size()];
                    for (int i = 0; i < newFaceUV.size(); i++) updatedUV[i] = newFaceUV.get(i);
                    faceUVs.set(faceIdx, updatedUV);
                }
            }
        }
    }

    /**
     * Checks whether point {@code p} lies strictly inside the line segment {@code a-b}
     * (i.e., not at its endpoints), given a specified tolerance.
     *
     * @return the parameter {@code t} in the range (0, 1) — representing the position
     *         of {@code p} along the segment (where 0 corresponds to point {@code a}
     *         and 1 to point {@code b}) — or {@code -1} if {@code p} does not lie
     *         on the segment.
     */
    private double pointOnSegment(Point3D a, Point3D b, Point3D p, double tolerance) {
        final double dx = b.x - a.x;
        final double dy = b.y - a.y;
        final double dz = b.z - a.z;
        final double lenSq = dx * dx + dy * dy + dz * dz;
        if (lenSq < 1e-20) return -1; // вырожденный отрезок

        // Проекция p на прямую a-b
        double t = ((p.x - a.x) * dx + (p.y - a.y) * dy + (p.z - a.z) * dz) / lenSq;
        if (t <= 0.0 || t >= 1.0) return -1;

        // Перпендикулярное расстояние от p до прямой
        final double cx = a.x + t * dx;
        final double cy = a.y + t * dy;
        final double cz = a.z + t * dz;
        final double distSq = (p.x - cx) * (p.x - cx)
                + (p.y - cy) * (p.y - cy)
                + (p.z - cz) * (p.z - cz);

        return (distSq <= tolerance * tolerance) ? t : -1;
    }

    /**
     * Removes duplicate vertices from the mesh and reindexes all faces.
     *
     * <p>Two vertices are considered identical if their coordinates, when rounded
     * to a precision of 1e-6 (the same precision used in {@link #vertexCache}),
     * are equal. This allows for "welding" vertices that may have been created
     * by in-place coordinate-modifying operations (such as {@link #scale(double, double, double)})
     * or following a load/deserialization process.</p>
     *
     * <p>Side effects:
     * <ul>
     *   <li>All face arrays are reindexed to reflect the new vertex indices.</li>
     *   <li>Consecutive duplicate indices within faces are removed (including
     *       wraparound duplicates), effectively eliminating "zero-length" edges (A-A).</li>
     *   <li>Faces that end up with fewer than 3 vertices after this cleanup are
     *       removed, along with their corresponding entries in {@code faceUVs} and
     *       {@code faceMaterials}.</li>
     *   <li>UV indices are preserved only for the vertex positions that remain in the face.</li>
     *   <li>The {@link #vertexCache} is rebuilt, and the bounding box is reset.</li>
     * </ul>
     * </p>
     *
     * <p>Materials ({@code materials}) and {@code textureName} remain unaffected.</p>
     */
    public void fixDuplicateVertices() {
        final double scale = 1e1;

        // 1. Строим карту "округлённая точка -> новый индекс" и oldToNew
        final Map<Point3D, Integer> canonicalMap = new HashMap<>();
        final List<Point3D> newVerts = new ArrayList<>(verts.size());
        final int[] oldToNew = new int[verts.size()];

        for (int i = 0; i < verts.size(); i++) {
            final Point3D p = verts.get(i);
            final Point3D roundedP = new Point3D(
                    Math.round(p.x * scale) / scale,
                    Math.round(p.y * scale) / scale,
                    Math.round(p.z * scale) / scale
            );
            final Integer existing = canonicalMap.get(roundedP);
            if (existing != null) {
                oldToNew[i] = existing;
            } else {
                final int newIdx = newVerts.size();
                newVerts.add(p);
                canonicalMap.put(roundedP, newIdx);
                oldToNew[i] = newIdx;
            }
        }

        // 2. Если ничего не склеилось — выходим, чтобы не пересобирать структуры зря
        if (newVerts.size() == verts.size()) {
            return;
        }

        // 3. Переиндексация граней с удалением вырожденных
        final List<int[]> newFaces         = new ArrayList<>(faces.size());
        final List<int[]> newFaceUVs       = new ArrayList<>(faceUVs.size());
        final List<Integer> newFaceMats    = new ArrayList<>(faceMaterials.size());

        for (int fi = 0; fi < faces.size(); fi++) {
            final int[] face = faces.get(fi);
            if (face == null || face.length < 3) {
                continue; // некорректная грань — пропускаем
            }

            // 3a. Индексы оригинальных позиций, которые остаются в грани.
            //     Убираем подряд идущие одинаковые (уже переиндексированные) вершины.
            final List<Integer> keptPositions = new ArrayList<>(face.length);
            for (int i = 0; i < face.length; i++) {
                final int newV = oldToNew[face[i]];
                if (keptPositions.isEmpty()) {
                    keptPositions.add(i);
                } else {
                    final int prevNewV = oldToNew[face[keptPositions.get(keptPositions.size() - 1)]];
                    if (prevNewV != newV) {
                        keptPositions.add(i);
                    }
                }
            }
            // Циклический дубликат: последняя совпала с первой
            while (keptPositions.size() > 1) {
                final int firstNewV = oldToNew[face[keptPositions.get(0)]];
                final int lastNewV  = oldToNew[face[keptPositions.get(keptPositions.size() - 1)]];
                if (firstNewV == lastNewV) {
                    keptPositions.remove(keptPositions.size() - 1);
                } else {
                    break;
                }
            }

            if (keptPositions.size() < 3) {
                continue; // грань выродилась — выбрасываем
            }

            // 3b. Формируем новую грань
            final int[] newFace = new int[keptPositions.size()];
            for (int i = 0; i < keptPositions.size(); i++) {
                newFace[i] = oldToNew[face[keptPositions.get(i)]];
            }
            newFaces.add(newFace);

            // 3c. Переносим UV-индексы для оставшихся позиций (если они есть)
            final int[] faceUV = faceUVs.get(fi);
            if (faceUV != null) {
                final int[] newUV = new int[keptPositions.size()];
                for (int i = 0; i < keptPositions.size(); i++) {
                    newUV[i] = faceUV[keptPositions.get(i)];
                }
                newFaceUVs.add(newUV);
            } else {
                newFaceUVs.add(null);
            }

            // 3d. Материал переносим как есть
            newFaceMats.add(faceMaterials.get(fi));
        }

        // 4. Заменяем содержимое на пересобранное
        verts.clear();
        verts.addAll(newVerts);

        faces.clear();
        faces.addAll(newFaces);

        faceUVs.clear();
        faceUVs.addAll(newFaceUVs);

        faceMaterials.clear();
        faceMaterials.addAll(newFaceMats);

        // 5. Пересобираем vertexCache и сбрасываем bounding box
        invalidateBBOX();
    }


    /**
     * Removes duplicate faces from the mesh.
     *
     * <p>Two faces are considered duplicates if they consist of the same set of
     * vertices (treated as a set of indices). The starting vertex and the
     * traversal direction do not matter; for instance, the faces
     * <code>[A,B,C]</code>, <code>[B,C,A]</code>, and <code>[C,B,A]</code> are
     * considered duplicates of one another.</p>
     *
     * <p>The first encountered face is preserved (along with its associated
     * UV indices and material). All subsequent copies are removed, as are their
     * corresponding entries in {@link #faceUVs} and {@link #faceMaterials}.</p>
     *
     * <p>Faces that are {@code null}, have fewer than 3 vertices, or contain
     * repeated vertices (e.g., <code>[A,B,A]</code>) are handled in the standard
     * way: their key is generated as a sorted array of indices that accounts for
     * multiplicity. Consequently, <code>[A,B,A]</code> and <code>[A,A,B]</code>
     * are considered duplicates, whereas <code>[A,B,A]</code> and
     * <code>[A,B,C]</code> are not.</p>
     *
     * <p>The relative order of the remaining faces is preserved, and the
     * correspondence between {@code faces}, {@code faceUVs}, and
     * {@code faceMaterials} remains intact.</p>
     */
    public void fixDuplicateFaces() {
        // Ключ: отсортированный список индексов вершин (с учётом кратности).
        final Set<List<Integer>> seen = new HashSet<>();

        final List<int[]> newFaces      = new ArrayList<>(faces.size());
        final List<int[]> newFaceUVs    = new ArrayList<>(faceUVs.size());
        final List<Integer> newFaceMats = new ArrayList<>(faceMaterials.size());

        for (int fi = 0; fi < faces.size(); fi++) {
            final int[] face = faces.get(fi);

            // Пропускаем заведомо некорректные грани.
            if (face == null || face.length < 3) {
                continue;
            }

            // Строим канонический ключ — отсортированные индексы вершин.
            final int[] sorted = Arrays.copyOf(face, face.length);
            Arrays.sort(sorted);
            final List<Integer> key = new ArrayList<>(sorted.length);
            for (int v : sorted) {
                key.add(v);
            }

            // Если такая грань уже была — это дубликат, выбрасываем.
            if (!seen.add(key)) {
                continue;
            }

            newFaces.add(face);
            newFaceUVs.add(faceUVs.get(fi));
            newFaceMats.add(faceMaterials.get(fi));
        }

        // Если ничего не удалилось — структуры не трогаем.
        if (newFaces.size() == faces.size()) {
            return;
        }

        faces.clear();
        faces.addAll(newFaces);

        faceUVs.clear();
        faceUVs.addAll(newFaceUVs);

        faceMaterials.clear();
        faceMaterials.addAll(newFaceMats);
    }

    /**
     *  Remove degenerated edge sequence from faces, ..-A-B-A-.. is collapsed to just ..-A-..
     *  This problem appears as a glitch in camp-skeleton algorithm.
     */
    public void fixDegeneratedFaceParts(){

        for (int faceIdx = 0; faceIdx < faces.size(); faceIdx++) {
            final int[] face = faces.get(faceIdx);
            var newFace = new ArrayList<Integer>();
            boolean modified = false;

            for (int j = 0; j < face.length; j++){

                int current = face[j];
                int next =  face[(j + 1) % face.length];
                int third = face[(j + 2) % face.length];
                newFace.add(current);

                if (current == third) {
                    //UrbanEye3dPlugin.debugMsg("degenerated segment found " + current + " " + next + " " + third);
                    j+=2; //jump to the same node, skipping degenrated segment
                    modified = true;
                }


            };
            if (modified){
                final int[] updatedFace = new int[newFace.size()];
                for (int i = 0; i < newFace.size(); i++){
                    updatedFace[i] = newFace.get(i);
                }
                faces.set(faceIdx, updatedFace);

                if (faceUVs.get(faceIdx) !=null){
                    throw new RuntimeException("UVs are not yet supported here");
                }
            }

        }

    }
}
