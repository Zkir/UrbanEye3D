package ru.zkir.urbaneye3d.roofgenerators;

import org.twak.camp.Corner;
import org.twak.camp.Edge;
import org.twak.camp.Machine;
import org.twak.camp.Output;
import org.twak.camp.Skeleton;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import ru.zkir.urbaneye3d.RenderableBuildingElement;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;

import javax.vecmath.Point3d;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MesherHipped extends RoofGenerator {
    @Override
    public Mesh generate(RenderableBuildingElement building) {

        List<Point2D> basePoints = building.getContour();
        if (basePoints.size() <= 4) {
            // Fallback to old implementation for quadrilaterals and triangles
            return generateOriginal(building);
        }

        double minHeight = building.minHeight;
        double wallHeight = building.wallHeight;
        double roofHeight = building.roofHeight;

        Mesh mesh = new Mesh();
        // Base vertices
        int baseIdx = 0;
        for (Point2D p : basePoints) {
            mesh.verts.add(new Point3D(p.x, p.y, minHeight));
        }

        // Wall top vertices
        int wallIdx;
        if (wallHeight > minHeight) {
            wallIdx = mesh.verts.size();
            for (Point2D p : basePoints) {
                mesh.verts.add(new Point3D(p.x, p.y, wallHeight));
            }
        } else {
            wallIdx = baseIdx;
        }

        // Walls
        if (wallHeight > minHeight) {
            for (int i = 0; i < basePoints.size(); i++) {
                int j = (i + 1) % basePoints.size();
                mesh.wallFaces.add(new int[]{baseIdx + i, baseIdx + j, wallIdx + j, wallIdx + i});
            }
        }

        // Bottom face
        int[] bottomFace = new int[basePoints.size()];
        for (int i = 0; i < basePoints.size(); i++) {
            bottomFace[i] = baseIdx + basePoints.size() - 1 - i;
        }
        mesh.bottomFaces.add(bottomFace);


        // Roof via CampSkeleton
        Loop<Edge> loop = new Loop<>();
        Corner firstCorner = null;
        Corner prevCorner = null;

        Map<Point2D, Corner> cornerMap = new HashMap<>();
        for (Point2D p : basePoints) {
            cornerMap.put(p, new Corner(p.x, p.y));
        }

        for (int i = 0; i < basePoints.size(); i++) {
            Point2D p1 = basePoints.get(i);
            Point2D p2 = basePoints.get((i + 1) % basePoints.size());
            Edge edge = new Edge(cornerMap.get(p1), cornerMap.get(p2));
            // 45 degree roof slope. Should be configurable later.
            edge.machine = new Machine(Math.PI / 4);
            loop.append(edge);
        }

        LoopL<Edge> loopl = new LoopL<>();
        loopl.add(loop);

        Skeleton skel = new Skeleton(loopl, true);
        skel.skeleton();

        // Find max Z to scale the roof height correctly
        double maxZ = 0;
        for (Output.Face face : skel.output.faces.values()) {
            for (Loop<Point3d> lp3 : face.points) {
                for (Point3d pt : lp3) {
                    if (pt.z > maxZ) {
                        maxZ = pt.z;
                    }
                }
            }
        }

        double roofScale = (maxZ > 0) ? roofHeight / maxZ : 0;

        // Process skeleton output into our mesh
        int roofVertsStartIndex = mesh.verts.size();
        Map<Point3d, Integer> vertexIndexMap = new HashMap<>();

        for (Output.Face face : skel.output.faces.values()) {
            for (Loop<Point3d> lp3 : face.points) {
                List<Integer> faceIndices = new ArrayList<>();
                for (Point3d pt : lp3) {
                    Integer existingIndex = vertexIndexMap.get(pt);
                    if (existingIndex != null) {
                        faceIndices.add(existingIndex);
                    } else {
                        double newZ = wallHeight + pt.z * roofScale;
                        Point3D newVert = new Point3D(pt.x, pt.y, newZ);
                        int newIndex = mesh.verts.size();
                        mesh.verts.add(newVert);
                        vertexIndexMap.put(pt, newIndex);
                        faceIndices.add(newIndex);
                    }
                }

                if (!faceIndices.isEmpty()) {
                    int[] faceArray = new int[faceIndices.size()];
                    for (int i = 0; i < faceIndices.size(); i++) {
                        faceArray[i] = faceIndices.get(i);
                    }
                    mesh.roofFaces.add(faceArray);
                }
            }
        }
        return mesh;
    }

    private Mesh generateOriginal(RenderableBuildingElement building) {
        List<Point2D> basePoints = building.getContour();
        double minHeight = building.minHeight;
        double wallHeight = building.wallHeight;
        double height = building.height;
        String roofOrientation = building.roofOrientation;

        Mesh mesh = new Mesh();
        if (basePoints.size() < 3) return null; // Cannot create a roof from less than 3 points
        if (basePoints.size() != 4) {
            // This original method is only for quads. For others, we should have used the new method.
            // However, as a fallback, we can return a flat roof (or null to let the caller handle it).
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

        double a = g1_p0.subtract(g2_p1).length();//length of longer side
        double b = g1_p0.subtract(g1_p1).length();//length of shorter side

        double ridge_length = a-b;
        if (ridge_length<0.1){
            ridge_length = 0.1;
        }

        if ("across".equals(roofOrientation)) {
            //nobody knows how "across" hipped roof should like.
            // without this limit it looks like a pyramid.
            if(ridge_length < a/3){
                ridge_length = a/3;
            }
        }


        Point2D[] shortened_ridge = shortenSegment(mid1, mid2,ridge_length/a);
        int ridge1Idx = verts.size();
        verts.add(new Point3D(shortened_ridge[0].x, shortened_ridge[0].y, height));
        int ridge2Idx = verts.size();
        verts.add(new Point3D(shortened_ridge[1].x, shortened_ridge[1].y, height));

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

            // Create Gable Walls (also Quads for hipped)
            mesh.wallFaces.add(new int[]{baseIdx + g1_idx0, baseIdx + g1_idx1, wallIdx + g1_idx1,  wallIdx + g1_idx0});
            mesh.wallFaces.add(new int[]{baseIdx + g2_idx0, baseIdx + g2_idx1, wallIdx + g2_idx1,  wallIdx + g2_idx0});
        }

        //Create Roof Planes (Triangles)
        mesh.roofFaces.add(new int[]{ wallIdx + g1_idx1, ridge1Idx, wallIdx + g1_idx0});
        mesh.roofFaces.add(new int[]{ wallIdx + g2_idx1, ridge2Idx, wallIdx + g2_idx0});


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
