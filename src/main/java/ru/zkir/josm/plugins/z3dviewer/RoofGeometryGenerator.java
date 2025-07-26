package ru.zkir.josm.plugins.z3dviewer;

import java.util.ArrayList;
import java.util.List;
import static java.lang.Math.*;

public class RoofGeometryGenerator {

    public static class Mesh {
        public List<Point3D> verts = new ArrayList<>();
        public List<int[]> roofFaces = new ArrayList<>();
        public List<int[]> wallFaces = new ArrayList<>();
    }

    // Generatrix for a dome
    private static List<Point2D> domeProfile(int rows) {
        List<Point2D> profile = new ArrayList<>();
        for (int j = 0; j <= rows; j++) {
            double x = cos((double) j / rows * PI / 2);
            double z = sin((double) j / rows * PI / 2);
            profile.add(new Point2D(x, z));
        }
        return profile;
    }

    // Generatrix for an onion roof
    private static List<Point2D> onionProfile(){
        List<Point2D> profile = new ArrayList<>();

        profile.add(new Point2D(1.0000, 0.0000));
        profile.add(new Point2D(1.2971, 0.0999));
        profile.add(new Point2D(1.2971, 0.2462));
        profile.add(new Point2D(1.1273, 0.3608));
        profile.add(new Point2D(0.6219, 0.4785));
        profile.add(new Point2D(0.2131, 0.5984));
        profile.add(new Point2D(0.1003, 0.7243));
        profile.add(new Point2D(0.0000, 1.0000));
        return profile;
    }

    // Generatrix for a pyramidal roof
    // Just 2 points indeed.
    private static List<Point2D> pyramidalProfile(){
        List<Point2D> profile = new ArrayList<>();
        profile.add(new Point2D(1.0000, 0.0000));
        profile.add(new Point2D(0.0000, 1.0000));
        return profile;
    }

    public static Mesh generateConicalRoof(RoofShapes roofShape, List<Point2D> basePoints, double minHeight, double wallHeight, double height) {
        Mesh mesh = new Mesh();
        List<Point3D> verts = new ArrayList<>();
        for (Point2D p:basePoints){
            verts.add(new Point3D(p.x, p.y, minHeight));
        }

        List<Point2D> profile;
        if ((roofShape==RoofShapes.DOME) || (roofShape == RoofShapes.HALF_DOME)) {
            profile = domeProfile(7);
        } else if (roofShape == RoofShapes.PYRAMIDAL){
            profile = pyramidalProfile();
        } else if (roofShape == RoofShapes.ONION) {
            profile = onionProfile();
        } else {
            throw new IllegalArgumentException("Unknown roof type: " + roofShape);
        }

        int n = basePoints.size();
        double z1 = wallHeight;
        double z2 = height;

        Point3D center;
        if (roofShape== RoofShapes.HALF_DOME) {
            center = calculateMidpointOfLongestEdge(basePoints);
        } else {
            center = calculateCentroid(basePoints);
        }

        // Create walls
        if (minHeight < wallHeight) { // Only create walls if there's a height difference
            for (int i = 0; i < n; i++) {
                verts.add(new Point3D(basePoints.get(i).x, basePoints.get(i).y, z1));
            }
            int indexOffset = n;
            for (int i = 0; i < n - 1; i++) {
                mesh.wallFaces.add(new int[]{i, i + 1, i + n + 1, i + n});
            }
            mesh.wallFaces.add(new int[]{n - 1, 0, n, 2 * n - 1});
        }

        // Create roof mesh vertices
        int rows = profile.size() - 1;
        for (int j = 1; j < rows; j++) {
            for (int i = 0; i < n; i++) {
                double xi = basePoints.get(i).x + (1 - profile.get(j).x) * (center.x - basePoints.get(i).x);
                double yi = basePoints.get(i).y + (1 - profile.get(j).x) * (center.y - basePoints.get(i).y);
                double zi = wallHeight + (height - wallHeight) * profile.get(j).y;
                verts.add(new Point3D(xi, yi, zi));
            }
        }

        // Add the top vertex (apex)
        verts.add(new Point3D(center.x, center.y, z2));
        int centreIdx = verts.size() - 1;

        // Create roof faces
        int indexOffset = (minHeight < wallHeight) ? n : 0; // Adjust offset if walls were created

        for (int j = 0; j < rows - 1; j++) {
            for (int i = 0; i < n - 1; i++) {
                mesh.roofFaces.add(new int[]{
                    indexOffset + j * n + i,
                    indexOffset + j * n + i + 1,
                    indexOffset + j * n + i + 1 + n,
                    indexOffset + j * n + i + n
                });
            }
            mesh.roofFaces.add(new int[]{
                indexOffset + j * n + n - 1,
                indexOffset + j * n + 0,
                indexOffset + j * n + n,
                indexOffset + j * n + 2 * n - 1
            });
        }

        // Faces in the last loop are triangles (connecting to the apex)
        for (int i = 0; i < n - 1; i++) {
            mesh.roofFaces.add(new int[]{
                indexOffset + (rows - 1) * n + i,
                indexOffset + (rows - 1) * n + i + 1,
                centreIdx
            });
        }
        mesh.roofFaces.add(new int[]{
            indexOffset + (rows - 1) * n + n - 1,
            indexOffset + (rows - 1) * n + 0,
            centreIdx
        });

        mesh.verts = verts;
        return mesh;
    }

    private static Point3D calculateCentroid(List<Point2D> points) {
        double signedArea = 0.0;
        double cx = 0.0;
        double cy = 0.0;

        for (int i = 0; i < points.size(); i++) {
            Point2D p1 = points.get(i);
            Point2D p2 = points.get((i + 1) % points.size());
            double crossProduct = (p1.x * p2.y) - (p2.x * p1.y);
            signedArea += crossProduct;
            cx += (p1.x + p2.x) * crossProduct;
            cy += (p1.y + p2.y) * crossProduct;
        }
        signedArea *= 0.5;

        if (Math.abs(signedArea) < 1e-9) {
            // Fallback for zero-area polygons (collinear points) to simple average
            cx = 0;
            cy = 0;
            for (Point2D p : points) {
                cx += p.x;
                cy += p.y;
            }
            cx /= points.size();
            cy /= points.size();
        } else {
            cx /= (6.0 * signedArea);
            cy /= (6.0 * signedArea);
        }
        return new Point3D(cx, cy, 0);
    }


    private static Point3D calculateMidpointOfLongestEdge(List<Point2D> points) {
        if (points == null || points.size() < 2) {
            return null; // Or throw an exception
        }

        double maxDistSq = -1;
        Point3D midpoint = null;

        for (int i = 0; i < points.size(); i++) {
            Point2D p1 = points.get(i);
            Point2D p2 = points.get((i + 1) % points.size()); // Wrap around for the last segment

            double dx = p2.x - p1.x;
            double dy = p2.y - p1.y;
            double distSq = dx * dx + dy * dy;

            if (distSq > maxDistSq) {
                maxDistSq = distSq;
                midpoint = new Point3D((p1.x + p2.x) / 2.0, (p1.y + p2.y) / 2.0, 0);
            }
        }
        return midpoint;
    }

    public static Mesh generateSkillionRoof(List<Point2D> basePoints, double minHeight, double wallHeight, double height, double roofDirection) {
        Mesh mesh = new Mesh();
        List<Point3D> verts = new ArrayList<>();
        int n = basePoints.size();

        // --- Calculate slope vector ---
        Point2D slopeVector;
        if (!Double.isNaN(roofDirection)) {
            // OSM direction is azimuth: 0 is North, 90 is East. Vector should point opposite to the downslope direction.
            double angleRad = Math.toRadians(roofDirection);
            slopeVector = new Point2D(-Math.sin(angleRad), -Math.cos(angleRad));
        } else {
            // Fallback to longest edge, direction is arbitrary but consistent.
            int[] longestEdgeIndices = findLongestEdge(basePoints);
            Point2D p1 = basePoints.get(longestEdgeIndices[0]);
            Point2D p2 = basePoints.get(longestEdgeIndices[1]);
            double dx = p2.x - p1.x;
            double dy = p2.y - p1.y;
            slopeVector = new Point2D(-dy, dx);
        }
        slopeVector.normalize();

        // --- Calculate projections to determine height for each vertex ---
        double maxProj = -Double.MAX_VALUE;
        double minProj = Double.MAX_VALUE;
        List<Double> projections = new ArrayList<>();
        for (Point2D p : basePoints) {
            double proj = p.x * slopeVector.x + p.y * slopeVector.y;
            projections.add(proj);
            maxProj = Math.max(maxProj, proj);
            minProj = Math.min(minProj, proj);
        }

        double roofHeight = height - wallHeight;
        double projDiff = maxProj - minProj;
        double tan = (projDiff > 1e-9) ? roofHeight / projDiff : 0;

        // --- Create Vertices ---
        // 1. Base vertices (at the bottom of the walls)
        int baseStartIndex = 0;
        for (Point2D p : basePoints) {
            verts.add(new Point3D(p.x, p.y, minHeight));
        }

        // 2. Roof vertices (at the top of the sloped roof)
        int roofStartIndex = verts.size();
        for (int i = 0; i < n; i++) {
            double z = wallHeight + (projections.get(i) - minProj) * tan;
            verts.add(new Point3D(basePoints.get(i).x, basePoints.get(i).y, z));
        }

        // --- Create Faces ---
        // 1. Top roof face (a single polygon)
        int[] roofIndices = new int[n];
        for (int i = 0; i < n; i++) {
            roofIndices[i] = roofStartIndex + i;
        }
        mesh.roofFaces.add(roofIndices);

        // 2. Trapezoidal wall faces
        for (int i = 0; i < n; i++) {
            int next = (i + 1) % n;
            int p1_base = baseStartIndex + i;
            int p2_base = baseStartIndex + next;
            int p1_roof = roofStartIndex + i;
            int p2_roof = roofStartIndex + next;
            mesh.wallFaces.add(new int[]{p1_base, p2_base, p2_roof, p1_roof});
        }

        mesh.verts = verts;
        return mesh;
    }

    private static int[] findLongestEdge(List<Point2D> points) {
        if (points == null || points.size() < 2) {
            return new int[]{-1, -1};
        }

        double maxDistSq = -1;
        int[] edgeIndices = new int[2];

        for (int i = 0; i < points.size(); i++) {
            Point2D p1 = points.get(i);
            Point2D p2 = points.get((i + 1) % points.size());

            double dx = p2.x - p1.x;
            double dy = p2.y - p1.y;
            double distSq = dx * dx + dy * dy;

            if (distSq > maxDistSq) {
                maxDistSq = distSq;
                edgeIndices[0] = i;
                edgeIndices[1] = (i + 1) % points.size();
            }
        }
        return edgeIndices;
    }

    public static Mesh generateGabledRoof(List<Point2D> basePoints, double minHeight, double wallHeight, double height, String roofOrientation) {
        Mesh mesh = new Mesh();
        if (basePoints.size() != 4) {
            // Fallback to flat roof for non-quadrilaterals
            return null;
        }

        List<Point3D> verts = new ArrayList<>();
        int n = basePoints.size();

        // --- Find the two edges which will form the gables ---
        int[] gableEdgeIndices;
        if ("across".equals(roofOrientation)) {
            gableEdgeIndices = findLongestOppositeEdges(basePoints);
        } else { // Default to "along"
            gableEdgeIndices = findShortestEdges(basePoints);
        }

        int g1_idx0 = gableEdgeIndices[0];
        int g1_idx1 = (g1_idx0 + 1) % n;
        int g2_idx0 = gableEdgeIndices[1];
        int g2_idx1 = (g2_idx0 + 1) % n;

        // --- Create Vertices ---
        // 1. Base vertices (at the bottom of the walls)
        for (Point2D p : basePoints) {
            verts.add(new Point3D(p.x, p.y, minHeight));
        }
        // 2. Wall top vertices (at the height of the eaves)
        for (Point2D p : basePoints) {
            verts.add(new Point3D(p.x, p.y, wallHeight));
        }
        // 3. Roof ridge vertices
        Point2D g1_p0 = basePoints.get(g1_idx0);
        Point2D g1_p1 = basePoints.get(g1_idx1);
        Point2D g2_p0 = basePoints.get(g2_idx0);
        Point2D g2_p1 = basePoints.get(g2_idx1);
        Point2D mid1 = new Point2D((g1_p0.x + g1_p1.x) / 2, (g1_p0.y + g1_p1.y) / 2);
        Point2D mid2 = new Point2D((g2_p0.x + g2_p1.x) / 2, (g2_p0.y + g2_p1.y) / 2);
        verts.add(new Point3D(mid1.x, mid1.y, height)); // Ridge point 1 (index 8)
        verts.add(new Point3D(mid2.x, mid2.y, height)); // Ridge point 2 (index 9)

        mesh.verts = verts;

        // --- Create Faces ---
        int baseIdx = 0;
        int wallIdx = n;
        int ridge1Idx = 2 * n;
        int ridge2Idx = 2 * n + 1;

        // Find the indices of the vertices that form the eave walls
        int eave1_idx0 = g1_idx1;
        int eave1_idx1 = g2_idx0;
        int eave2_idx0 = g2_idx1;
        int eave2_idx1 = g1_idx0;

        // Create Eave Walls (Quads)
        mesh.wallFaces.add(new int[]{baseIdx + eave1_idx0, baseIdx + eave1_idx1, wallIdx + eave1_idx1, wallIdx + eave1_idx0});
        mesh.wallFaces.add(new int[]{baseIdx + eave2_idx0, baseIdx + eave2_idx1, wallIdx + eave2_idx1, wallIdx + eave2_idx0});

        // Create Gable Walls (Pentagons)
        mesh.wallFaces.add(new int[]{baseIdx + g1_idx0, baseIdx + g1_idx1, wallIdx + g1_idx1, ridge1Idx, wallIdx + g1_idx0});
        mesh.wallFaces.add(new int[]{baseIdx + g2_idx0, baseIdx + g2_idx1, wallIdx + g2_idx1, ridge2Idx, wallIdx + g2_idx0});

        // Create Roof Planes (Quads)
        mesh.roofFaces.add(new int[]{wallIdx + eave1_idx0, wallIdx + eave1_idx1, ridge2Idx, ridge1Idx});
        mesh.roofFaces.add(new int[]{wallIdx + eave2_idx0, wallIdx + eave2_idx1, ridge1Idx, ridge2Idx});

        return mesh;
    }

    private static int[] findShortestEdges(List<Point2D> points) {
        if (points.size() != 4) {
            return new int[]{-1, -1}; // Should not happen with the check in generateGabledRoof
        }

        double[] lengthsSq = new double[4];
        for (int i = 0; i < 4; i++) {
            Point2D p1 = points.get(i);
            Point2D p2 = points.get((i + 1) % 4);
            lengthsSq[i] = (p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y);
        }

        // In a quad, if we sort edges by length, the two shortest are not necessarily opposite.
        // We need to find the two shortest edges that don't share a vertex.
        int[] indices = {0, 1, 2, 3};

        // Find the indices of the two shortest edges
        int shortest1 = -1, shortest2 = -1;
        double minLength1 = Double.MAX_VALUE, minLength2 = Double.MAX_VALUE;

        for (int i = 0; i < 4; i++) {
            if (lengthsSq[i] < minLength1) {
                minLength2 = minLength1;
                shortest2 = shortest1;
                minLength1 = lengthsSq[i];
                shortest1 = i;
            } else if (lengthsSq[i] < minLength2) {
                minLength2 = lengthsSq[i];
                shortest2 = i;
            }
        }

        // Check if the two shortest edges are adjacent. If so, they can't form the gables.
        // In a typical rectangular building, the two shortest sides will be opposite.
        // If the building is not rectangular, we must decide which pair of opposite sides to use.
        // We'll choose the pair with the shorter average length.
        if (Math.abs(shortest1 - shortest2) == 1 || Math.abs(shortest1 - shortest2) == 3) { // Adjacent
             // Edges 0 and 2 vs 1 and 3
            if (lengthsSq[0] + lengthsSq[2] < lengthsSq[1] + lengthsSq[3]) {
                return new int[]{0, 2};
            } else {
                return new int[]{1, 3};
            }
        } else { // Opposite
            return new int[]{shortest1, shortest2};
        }
    }

    private static int[] findLongestOppositeEdges(List<Point2D> points) {
        if (points.size() != 4) {
            return new int[]{-1, -1};
        }
        double[] lengthsSq = new double[4];
        for (int i = 0; i < 4; i++) {
            Point2D p1 = points.get(i);
            Point2D p2 = points.get((i + 1) % 4);
            lengthsSq[i] = (p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y);
        }

        // Return the pair of opposite edges with the greater combined length.
        if (lengthsSq[0] + lengthsSq[2] > lengthsSq[1] + lengthsSq[3]) {
            return new int[]{0, 2};
        } else {
            return new int[]{1, 3};
        }
    }

    public static List<Point2D> getRoundProfile(int segments) {
        List<Point2D> profile = new ArrayList<>();
        // Profile X goes from -1 to 1, Y from 0 to 1 (for semicircle)
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * i / segments;
            profile.add(new Point2D(-Math.cos(angle), Math.sin(angle)));
        }
        return profile;
    }

    public static Mesh generateLinearProfileRoof(List<Point2D> basePoints, double minHeight, double wallHeight, double height, String roofOrientation, List<Point2D> profile) {
        Mesh mesh = new Mesh();
        if (basePoints.size() != 4) {
            return null; // This generator is for quads only
        }

        // 1. Identify Gable and Eave edges
        int[] shortEdges = findShortestEdges(basePoints);
        int[] longEdges = findLongestOppositeEdges(basePoints);
        int[] gableEdgeIndices;
        int[] eaveEdgeIndices;

        if ("across".equals(roofOrientation)) {
            gableEdgeIndices = longEdges;
            eaveEdgeIndices = shortEdges;
        } else { // "along" is default
            gableEdgeIndices = shortEdges;
            eaveEdgeIndices = longEdges;
        }

        // 2. Define vertices for the two gables
        int g1_idx0 = gableEdgeIndices[0];
        int g1_idx1 = (g1_idx0 + 1) % 4;
        int g2_idx0 = gableEdgeIndices[1];
        int g2_idx1 = (g2_idx0 + 1) % 4;

        Point2D g1_p0_base = basePoints.get(g1_idx0);
        Point2D g1_p1_base = basePoints.get(g1_idx1);
        Point2D g2_p0_base = basePoints.get(g2_idx0);
        Point2D g2_p1_base = basePoints.get(g2_idx1);

        // 3. Create base and wall-top vertices
        List<Point3D> verts = new ArrayList<>();
        for (Point2D p : basePoints) {
            verts.add(new Point3D(p.x, p.y, minHeight));
        }
        for (Point2D p : basePoints) {
            verts.add(new Point3D(p.x, p.y, wallHeight));
        }

        // 4. Generate the extruded roof profile vertices
        double roofHeight = height - wallHeight;

        // Define coordinate system for the first gable
        Point3D C1 = new Point3D((g1_p0_base.x + g1_p1_base.x) / 2, (g1_p0_base.y + g1_p1_base.y) / 2, wallHeight);
        double gableWidth = g1_p0_base.distance(g1_p1_base);
        Point3D xAxis = new Point3D(g1_p1_base.x - g1_p0_base.x, g1_p1_base.y - g1_p0_base.y, 0).normalize();

        // Define extrusion vector
        Point3D C2 = new Point3D((g2_p0_base.x + g2_p1_base.x) / 2, (g2_p0_base.y + g2_p1_base.y) / 2, wallHeight);
        Point3D extrusionVector = new Point3D(C2.x - C1.x, C2.y - C1.y, 0);

        int roofVtxStart = verts.size();
        int profileSize = profile.size();

        // Generate vertices for the first gable end
        for (Point2D prof : profile) {
            double offsetX = prof.x * (gableWidth / 2);
            double offsetY = prof.y * roofHeight;
            Point3D p = new Point3D(
                C1.x + xAxis.x * offsetX,
                C1.y + xAxis.y * offsetX,
                C1.z + offsetY
            );
            verts.add(p);
        }

        // Generate vertices for the second gable end by extruding
        for (int i = 0; i < profileSize; i++) {
            Point3D p1 = verts.get(roofVtxStart + i);
            verts.add(new Point3D(p1.x + extrusionVector.x, p1.y + extrusionVector.y, p1.z + extrusionVector.z));
        }

        mesh.verts = verts;

        // 5. Generate Faces
        int baseIdx = 0;
        int wallIdx = 4;

        // Eave walls (the flat ones under the roof overhang)
        int eave1_v0 = eaveEdgeIndices[0];
        int eave1_v1 = (eave1_v0 + 1) % 4;
        int eave2_v0 = eaveEdgeIndices[1];
        int eave2_v1 = (eave2_v0 + 1) % 4;
        mesh.wallFaces.add(new int[]{baseIdx + eave1_v0, baseIdx + eave1_v1, wallIdx + eave1_v1, wallIdx + eave1_v0});
        mesh.wallFaces.add(new int[]{baseIdx + eave2_v0, baseIdx + eave2_v1, wallIdx + eave2_v1, wallIdx + eave2_v0});

        // Add the lower part of the gable walls
        mesh.wallFaces.add(new int[]{baseIdx + g1_idx0, baseIdx + g1_idx1, wallIdx + g1_idx1, wallIdx + g1_idx0});
        mesh.wallFaces.add(new int[]{baseIdx + g2_idx0, baseIdx + g2_idx1, wallIdx + g2_idx1, wallIdx + g2_idx0});

        // Gable end walls (the walls with the curved profile)
        int[] cap1 = new int[profileSize + 2];
        cap1[0] = wallIdx + g1_idx0;
        cap1[1] = wallIdx + g1_idx1;
        for (int i = 0; i < profileSize; i++) {
            cap1[i + 2] = roofVtxStart + (profileSize - 1 - i);
        }
        mesh.wallFaces.add(cap1);

        int[] cap2 = new int[profileSize + 2];
        cap2[0] = wallIdx + g2_idx1;
        cap2[1] = wallIdx + g2_idx0;
        for (int i = 0; i < profileSize; i++) {
            cap2[i + 2] = roofVtxStart + profileSize + i;
        }
        mesh.wallFaces.add(cap2);

        // Roof surface
        for (int i = 0; i < profileSize - 1; i++) {
            int v1 = roofVtxStart + i;
            int v2 = roofVtxStart + i + 1;
            int v3 = roofVtxStart + profileSize + i + 1;
            int v4 = roofVtxStart + profileSize + i;
            mesh.roofFaces.add(new int[]{v1, v4, v3, v2});
        }

        return mesh;
    }
}