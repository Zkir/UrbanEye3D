package ru.zkir.josm.plugins.z3dviewer.roofgenerators;

import ru.zkir.josm.plugins.z3dviewer.RenderableBuildingElement;
import ru.zkir.josm.plugins.z3dviewer.utils.Mesh;
import ru.zkir.josm.plugins.z3dviewer.utils.Point2D;
import ru.zkir.josm.plugins.z3dviewer.utils.Point3D;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MesherSkillion extends RoofGenerator {
    @Override
    public Mesh generate(RenderableBuildingElement building) {
        List<Point2D> basePoints = building.getContour();
        double minHeight = building.minHeight;
        double height = building.height;
        double wallHeight = building.height - building.roofHeight;

        double roofDirection = building.roofDirection;

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
        for (Point2D p : basePoints) {
            verts.add(new Point3D(p.x, p.y, minHeight));
        }

        // 2. Roof vertices (at the top of the sloped roof)
        HashMap<Integer, Integer> topFaceIndicies = new HashMap<>();
        int roofStartIndex = verts.size();
        int k = verts.size();
        for (int i = 0; i < n; i++) {
            double z = wallHeight + (projections.get(i) - minProj) * tan;
            if (z>minHeight) {
                verts.add(new Point3D(basePoints.get(i).x, basePoints.get(i).y, z));
                topFaceIndicies.put(i, k);
                k++;
            }else{
                topFaceIndicies.put(i, i);
            }
        }

        // --- Create Faces ---
        // 1. Top roof face (a single polygon)
        int[] roofIndices = new int[n];
        for (int i = 0; i < n; i++) {
            roofIndices[i] = topFaceIndicies.get(i);
        }
        mesh.roofFaces.add(roofIndices);

        // 2. Trapezoidal or triangular wall faces
        for (int i = 0; i < n; i++) {
            int next = (i + 1) % n;
            int p1_base = i;
            int p2_base = next;
            int p1_roof =  topFaceIndicies.get(i);
            int p2_roof =  topFaceIndicies.get(next);
            if (p1_base==p1_roof && p2_base == p2_roof) {
                //this is a sharp edge, wall is not needed here
                continue;
            }

            if (p1_base!=p1_roof && p2_base != p2_roof) {
                mesh.wallFaces.add(new int[]{p1_base, p2_base, p2_roof, p1_roof});
            } else if(p1_base==p1_roof){
                mesh.wallFaces.add(new int[]{p1_base, p2_base, p2_roof});
            } else  {
                mesh.wallFaces.add(new int[]{p1_base, p2_base,  p1_roof});
            }
        }

        // Create bottom face
        int[] bottomFace = new int[n];
        for (int i = 0; i < n; i++) {
            bottomFace[i] = n - 1 - i; // Reverse order for correct normal
        }
        mesh.bottomFaces.add(bottomFace);

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


}
