package ru.zkir.josm.plugins.z3dviewer.roofgenerators;

import ru.zkir.josm.plugins.z3dviewer.RenderableBuildingElement;
import ru.zkir.josm.plugins.z3dviewer.utils.Mesh;
import ru.zkir.josm.plugins.z3dviewer.utils.Point2D;
import ru.zkir.josm.plugins.z3dviewer.utils.Point3D;

import java.util.ArrayList;
import java.util.List;

public class MesherMansard extends RoofGenerator {
    @Override
    public Mesh generate(RenderableBuildingElement building) {
        List<Point2D> basePoints = building.getContour();
        if (basePoints.size() != 4) {
            return null; // Fallback for non-quadrilaterals
        }

        double minHeight = building.minHeight;
        double wallHeight = building.wallHeight;
        double height = building.height;
        double roofHeight = building.roofHeight;

        Mesh mesh = new Mesh();
        List<Point3D> verts = mesh.verts;
        int n = basePoints.size();

        // --- Create Vertices ---
        // 1. Base vertices
        int baseIdx = verts.size();
        for (Point2D p : basePoints) {
            verts.add(new Point3D(p.x, p.y, minHeight));
        }

        // 2. Wall top vertices
        int wallIdx;
        if (wallHeight > minHeight) {
            wallIdx = verts.size();
            for (Point2D p : basePoints) {
                verts.add(new Point3D(p.x, p.y, wallHeight));
            }
        } else {
            wallIdx = baseIdx;
        }

        // 3. Inset vertices for the lower part of the roof
        double insetSize = 2.0; // This might need to be configurable later
        List<Point2D> insetPoints = insetPolygon(basePoints, insetSize);
        int insetIdx = verts.size();
        double lowerRoofHeight = wallHeight + roofHeight / 2.0;
        for (Point2D p : insetPoints) {
            verts.add(new Point3D(p.x, p.y, lowerRoofHeight));
        }

        // --- Create Lower Roof Faces (Trapezoids) ---
        for (int i = 0; i < n; i++) {
            int next = (i + 1) % n;
            mesh.roofFaces.add(new int[]{wallIdx + i, wallIdx + next, insetIdx + next, insetIdx + i});
        }

        // --- Generate Upper (Hipped) Roof Part ---
        // This part is essentially a hipped roof on top of the inset polygon
        String roofOrientation = building.roofOrientation;
        int[] gableEdgeIndices;
        if ("across".equals(roofOrientation)) {
            gableEdgeIndices = findLongestOppositeEdges(insetPoints);
        } else { // Default to "along"
            gableEdgeIndices = findShortestEdges(insetPoints);
        }

        int g1_idx0 = gableEdgeIndices[0];
        int g1_idx1 = (g1_idx0 + 1) % n;
        int g2_idx0 = gableEdgeIndices[1];
        int g2_idx1 = (g2_idx0 + 1) % n;

        Point2D g1_p0 = insetPoints.get(g1_idx0);
        Point2D g1_p1 = insetPoints.get(g1_idx1);
        Point2D g2_p0 = insetPoints.get(g2_idx0);
        Point2D g2_p1 = insetPoints.get(g2_idx1);

        Point2D mid1 = new Point2D((g1_p0.x + g1_p1.x) / 2, (g1_p0.y + g1_p1.y) / 2);
        Point2D mid2 = new Point2D((g2_p0.x + g2_p1.x) / 2, (g2_p0.y + g2_p1.y) / 2);

        double a = g1_p0.subtract(g2_p1).length();
        double b = g1_p0.subtract(g1_p1).length();
        double ridge_length = a - b;
        if (ridge_length < 0.1) ridge_length = 0.1;

        Point2D[] shortened_ridge = shortenSegment(mid1, mid2, ridge_length / a);
        int ridge1Idx = verts.size();
        verts.add(new Point3D(shortened_ridge[0].x, shortened_ridge[0].y, height));
        int ridge2Idx = verts.size();
        verts.add(new Point3D(shortened_ridge[1].x, shortened_ridge[1].y, height));

        // Create Upper Roof Faces
        int eave1_idx0 = g1_idx1;
        int eave1_idx1 = g2_idx0;
        int eave2_idx0 = g2_idx1;
        int eave2_idx1 = g1_idx0;

        mesh.roofFaces.add(new int[]{insetIdx + eave1_idx0, insetIdx + eave1_idx1, ridge2Idx, ridge1Idx});
        mesh.roofFaces.add(new int[]{insetIdx + eave2_idx0, insetIdx + eave2_idx1, ridge1Idx, ridge2Idx});
        mesh.roofFaces.add(new int[]{insetIdx + g1_idx1, ridge1Idx, insetIdx + g1_idx0});
        mesh.roofFaces.add(new int[]{insetIdx + g2_idx1, ridge2Idx, insetIdx + g2_idx0});

        // --- Create Walls and Bottom ---
        if (wallHeight > minHeight) {
            for (int i = 0; i < n; i++) {
                int next = (i + 1) % n;
                mesh.wallFaces.add(new int[]{baseIdx + i, baseIdx + next, wallIdx + next, wallIdx + i});
            }
        }

        int[] bottomFace = new int[n];
        for (int i = 0; i < n; i++) {
            bottomFace[i] = baseIdx + n - 1 - i;
        }
        mesh.bottomFaces.add(bottomFace);

        return mesh;
    }

    private List<Point2D> insetPolygon(List<Point2D> polygon, double inset) {
        // A simple inset implementation for convex quadrilaterals
        Point2D center = new Point2D(calculateCentroid(polygon).x, calculateCentroid(polygon).y);
        List<Point2D> insetPoints = new ArrayList<>();
        for (Point2D p : polygon) {
            Point2D dir = new Point2D(p.x - center.x, p.y - center.y);
            dir.normalize();
            insetPoints.add(new Point2D(p.x - dir.x * inset, p.y - dir.y * inset));
        }
        return insetPoints;
    }
}
