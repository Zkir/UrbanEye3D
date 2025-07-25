package ru.zkir.josm.plugins.z3dviewer;

import java.util.ArrayList;
import java.util.List;
import static java.lang.Math.*;

public class RoofGeometryGenerator {

    public static class RoofMesh {
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

    public static RoofMesh generateConicalRoof(RoofShapes roofShape, List<Point3D> basePoints, double minHeight, double wallHeight, double height) {
        RoofMesh mesh = new RoofMesh();
        List<Point3D> verts = new ArrayList<>();
        for (Point3D p:basePoints){
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

    private static Point3D calculateCentroid(List<Point3D> points) {
        double signedArea = 0.0;
        double cx = 0.0;
        double cy = 0.0;

        for (int i = 0; i < points.size(); i++) {
            Point3D p1 = points.get(i);
            Point3D p2 = points.get((i + 1) % points.size());
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
            for (Point3D p : points) {
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


    private static Point3D calculateMidpointOfLongestEdge(List<Point3D> points) {
        if (points == null || points.size() < 2) {
            return null; // Or throw an exception
        }

        double maxDistSq = -1;
        Point3D midpoint = null;

        for (int i = 0; i < points.size(); i++) {
            Point3D p1 = points.get(i);
            Point3D p2 = points.get((i + 1) % points.size()); // Wrap around for the last segment

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
}
