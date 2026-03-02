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
        if (building.getContour().size() <= 4) {
            return generateRectangular(building);
        } else{
            return generateStraightSkeleton(building);
        }
    }

    private int createNewRoofVertex(Mesh mesh, Point3d pt, double wallHeight, double scaledZ, Map<Point3d, Integer> map) {
        Point3D newVert = new Point3D(pt.x, pt.y, wallHeight + scaledZ);
        int newIndex = mesh.verts.size();
        mesh.verts.add(newVert);
        map.put(pt, newIndex);
        return newIndex;
    }

    private Mesh generateStraightSkeleton(RenderableBuildingElement building) {
        List<Point2D> basePoints = building.getContour();
        double minHeight = building.minHeight;
        double wallHeight = building.wallHeight;
        double roofHeight = building.roofHeight;

        Mesh mesh = new Mesh(building.bottomColor, building.color, building.roofColor);
        int baseIdx = 0;
        for (Point2D p : basePoints) {
            mesh.verts.add(new Point3D(p.x, p.y, minHeight));
        }

        int wallIdx;
        if (wallHeight > minHeight) {
            wallIdx = mesh.verts.size();
            for (Point2D p : basePoints) {
                mesh.verts.add(new Point3D(p.x, p.y, wallHeight));
            }
        } else {
            wallIdx = baseIdx;
        }

        if (wallHeight > minHeight) {
            for (int i = 0; i < basePoints.size(); i++) {
                int j = (i + 1) % basePoints.size();
                mesh.addWallFace(new int[]{baseIdx + i, baseIdx + j, wallIdx + j, wallIdx + i});
            }
        }

        int[] bottomFace = new int[basePoints.size()];
        for (int i = 0; i < basePoints.size(); i++) {
            bottomFace[i] = baseIdx + basePoints.size() - 1 - i;
        }
        mesh.addBottomFace(bottomFace);

        Loop<Edge> loop = new Loop<>();
        Map<Point2D, Corner> cornerMap = new HashMap<>();
        for (Point2D p : basePoints) {
            cornerMap.put(p, new Corner(p.x, p.y));
        }

        for (int i = 0; i < basePoints.size(); i++) {
            Point2D p1 = basePoints.get(i);
            Point2D p2 = basePoints.get((i + 1) % basePoints.size());
            Edge edge = new Edge(cornerMap.get(p1), cornerMap.get(p2));
            edge.machine = new Machine(Math.PI / 4);
            loop.append(edge);
        }

        LoopL<Edge> loopl = new LoopL<>();
        loopl.add(loop);

        Skeleton skel = new Skeleton(loopl, true);
        skel.skeleton();

        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (Output.Face face : skel.output.faces.values()) {
            for (Loop<Point3d> lp3 : face.points) {
                for (Point3d pt : lp3) {
                    if (pt.z < minZ) minZ = pt.z;
                    if (pt.z > maxZ) maxZ = pt.z;
                }
            }
        }

        double roofScale = (maxZ > minZ) ? roofHeight / (maxZ - minZ) : 0;

        Map<String, Integer> wallTopVertsMap = new HashMap<>();
        for (int i = 0; i < basePoints.size(); i++) {
            Point3D vert = mesh.verts.get(wallIdx + i);
            String key = String.format("%.5f,%.5f", vert.x, vert.y);
            wallTopVertsMap.put(key, wallIdx + i);
        }

        Map<Point3d, Integer> vertexIndexMap = new HashMap<>();

        for (Output.Face face : skel.output.faces.values()) {
            for (Loop<Point3d> lp3 : face.points) {
                List<Integer> faceIndices = new ArrayList<>();
                for (Point3d pt : lp3) {
                    Integer existingIndex = vertexIndexMap.get(pt);
                    if (existingIndex != null) {
                        faceIndices.add(existingIndex);
                    } else {
                        if (Math.abs(pt.z - minZ) < 1e-6) {
                            String key = String.format("%.5f,%.5f", pt.x, pt.y);
                            Integer wallVertIndex = wallTopVertsMap.get(key);
                            if (wallVertIndex != null) {
                                vertexIndexMap.put(pt, wallVertIndex);
                                faceIndices.add(wallVertIndex);
                            } else {
                                int newIndex = createNewRoofVertex(mesh, pt, wallHeight, 0, vertexIndexMap);
                                faceIndices.add(newIndex);
                            }
                        } else {
                            double scaledZ = (pt.z - minZ) * roofScale;
                            int newIndex = createNewRoofVertex(mesh, pt, wallHeight, scaledZ, vertexIndexMap);
                            faceIndices.add(newIndex);
                        }
                    }
                }

                if (faceIndices.size() > 2) {
                    int[] faceArray = new int[faceIndices.size()];
                    for (int i = 0; i < faceIndices.size(); i++) {
                        faceArray[i] = faceIndices.get(i);
                    }
                    mesh.addRoofFace(faceArray);
                }
            }
        }
        return mesh;
    }

    private Mesh generateRectangular(RenderableBuildingElement building) {
        List<Point2D> basePoints = building.getContour();
        double minHeight = building.minHeight;
        double wallHeight = building.wallHeight;
        double height = building.height;
        String roofOrientation = building.roofOrientation;

        Mesh mesh = new Mesh(building.bottomColor, building.color, building.roofColor);
        if (basePoints.size() < 3) return null;
        if (basePoints.size() != 4) {
            return null;
        }

        int n = basePoints.size();

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

        int baseIdx = mesh.verts.size();
        for (Point2D p : basePoints) {
            mesh.verts.add(new Point3D(p.x, p.y, minHeight));
        }

        int wallIdx;
        if (wallHeight > minHeight) {
            wallIdx = mesh.verts.size();
            for (Point2D p : basePoints) {
                mesh.verts.add(new Point3D(p.x, p.y, wallHeight));
            }
        } else {
            wallIdx = baseIdx;
        }

        Point2D g1_p0 = basePoints.get(g1_idx0);
        Point2D g1_p1 = basePoints.get(g1_idx1);
        Point2D g2_p0 = basePoints.get(g2_idx0);
        Point2D g2_p1 = basePoints.get(g2_idx1);

        Point2D mid1 = new Point2D((g1_p0.x + g1_p1.x) / 2, (g1_p0.y + g1_p1.y) / 2);
        Point2D mid2 = new Point2D((g2_p0.x + g2_p1.x) / 2, (g2_p0.y + g2_p1.y) / 2);

        double a = g1_p0.subtract(g2_p1).length();
        double b = g1_p0.subtract(g1_p1).length();

        double ridge_length = a-b;
        if (ridge_length<0.1){
            ridge_length = 0.1;
        }

        if ("across".equals(roofOrientation)) {
            if(ridge_length < a/3){
                ridge_length = a/3;
            }
        }

        Point2D[] shortened_ridge = shortenSegment(mid1, mid2,ridge_length/a);
        int ridge1Idx = mesh.verts.size();
        mesh.verts.add(new Point3D(shortened_ridge[0].x, shortened_ridge[0].y, height));
        int ridge2Idx = mesh.verts.size();
        mesh.verts.add(new Point3D(shortened_ridge[1].x, shortened_ridge[1].y, height));

        int eave1_idx0 = g1_idx1;
        int eave1_idx1 = g2_idx0;
        int eave2_idx0 = g2_idx1;
        int eave2_idx1 = g1_idx0;

        if (wallHeight > minHeight) {
            mesh.addWallFace(new int[]{baseIdx + eave1_idx0, baseIdx + eave1_idx1, wallIdx + eave1_idx1, wallIdx + eave1_idx0});
            mesh.addWallFace(new int[]{baseIdx + eave2_idx0, baseIdx + eave2_idx1, wallIdx + eave2_idx1, wallIdx + eave2_idx0});

            mesh.addWallFace(new int[]{baseIdx + g1_idx0, baseIdx + g1_idx1, wallIdx + g1_idx1,  wallIdx + g1_idx0});
            mesh.addWallFace(new int[]{baseIdx + g2_idx0, baseIdx + g2_idx1, wallIdx + g2_idx1,  wallIdx + g2_idx0});
        }

        mesh.addRoofFace(new int[]{ wallIdx + g1_idx1, ridge1Idx, wallIdx + g1_idx0});
        mesh.addRoofFace(new int[]{ wallIdx + g2_idx1, ridge2Idx, wallIdx + g2_idx0});

        mesh.addRoofFace(new int[]{wallIdx + eave1_idx0, wallIdx + eave1_idx1, ridge2Idx, ridge1Idx});
        mesh.addRoofFace(new int[]{wallIdx + eave2_idx0, wallIdx + eave2_idx1, ridge1Idx, ridge2Idx});

        int[] bottomFaceRect = new int[n];
        for (int i = 0; i < n; i++) {
            bottomFaceRect[i] = baseIdx + n - 1 - i;
        }
        mesh.addBottomFace(bottomFaceRect);

        return mesh;
    }
}
