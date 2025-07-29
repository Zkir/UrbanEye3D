package ru.zkir.urbaneye3d.roofgenerators;

import ru.zkir.urbaneye3d.RenderableBuildingElement;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;

import java.util.ArrayList;
import java.util.List;

public class MesherGabled extends RoofGenerator{
    @Override
    public Mesh generate(RenderableBuildingElement building) {


        List<Point2D> basePoints = building.getContour();
        double height = building.height;
        double minHeight = building.minHeight;
        double wallHeight = building.wallHeight;

        String roofOrientation = building.roofOrientation;
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
        int baseIdx = verts.size();
        for (Point2D p : basePoints) {
            verts.add(new Point3D(p.x, p.y, minHeight));
        }

        // 2. Wall top vertices (at the height of the eaves)
        int wallIdx;
        if (wallHeight > minHeight) {
            wallIdx = verts.size();
            for (Point2D p : basePoints) {
                verts.add(new Point3D(p.x, p.y, wallHeight));
            }
        } else {
            wallIdx = baseIdx; // Reuse base vertices if no walls
        }

        // 3. Roof ridge vertices
        Point2D g1_p0 = basePoints.get(g1_idx0);
        Point2D g1_p1 = basePoints.get(g1_idx1);
        Point2D g2_p0 = basePoints.get(g2_idx0);
        Point2D g2_p1 = basePoints.get(g2_idx1);
        Point2D mid1 = new Point2D((g1_p0.x + g1_p1.x) / 2, (g1_p0.y + g1_p1.y) / 2);
        Point2D mid2 = new Point2D((g2_p0.x + g2_p1.x) / 2, (g2_p0.y + g2_p1.y) / 2);
        int ridge1Idx = verts.size();
        verts.add(new Point3D(mid1.x, mid1.y, height));
        int ridge2Idx = verts.size();
        verts.add(new Point3D(mid2.x, mid2.y, height));

        mesh.verts = verts;

        // --- Create Faces ---
        // Find the indices of the vertices that form the eave walls
        int eave1_idx0 = g1_idx1;
        int eave1_idx1 = g2_idx0;
        int eave2_idx0 = g2_idx1;
        int eave2_idx1 = g1_idx0;

        // Create Walls only if they have height
        if (wallHeight > minHeight) {
            // Create Eave Walls (Quads)
            mesh.wallFaces.add(new int[]{baseIdx + eave1_idx0, baseIdx + eave1_idx1, wallIdx + eave1_idx1, wallIdx + eave1_idx0});
            mesh.wallFaces.add(new int[]{baseIdx + eave2_idx0, baseIdx + eave2_idx1, wallIdx + eave2_idx1, wallIdx + eave2_idx0});

            // Create Gable Walls (Pentagons)
            mesh.wallFaces.add(new int[]{baseIdx + g1_idx0, baseIdx + g1_idx1, wallIdx + g1_idx1, ridge1Idx, wallIdx + g1_idx0});
            mesh.wallFaces.add(new int[]{baseIdx + g2_idx0, baseIdx + g2_idx1, wallIdx + g2_idx1, ridge2Idx, wallIdx + g2_idx0});
        } else {
            // If there are no walls, the gables are triangles, added to the wall faces
            mesh.wallFaces.add(new int[]{wallIdx + g1_idx0, wallIdx + g1_idx1, ridge1Idx});
            mesh.wallFaces.add(new int[]{wallIdx + g2_idx0, wallIdx + g2_idx1, ridge2Idx});
        }

        // Create Roof Planes (Quads)
        mesh.roofFaces.add(new int[]{wallIdx + eave1_idx0, wallIdx + eave1_idx1, ridge2Idx, ridge1Idx});
        mesh.roofFaces.add(new int[]{wallIdx + eave2_idx0, wallIdx + eave2_idx1, ridge1Idx, ridge2Idx});

        // Create bottom face
        int[] bottomFace = new int[n];
        for (int i = 0; i < n; i++) {
            bottomFace[i] = baseIdx + n - 1 - i; // Reverse order for correct normal
        }
        mesh.bottomFaces.add(bottomFace);

        return mesh;
    }
}
