package ru.zkir.urbaneye3d.meshers.s3db;

import org.twak.camp.Corner;
import org.twak.camp.Edge;
import org.twak.camp.Machine;
import org.twak.camp.Output;
import org.twak.camp.Skeleton;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import ru.zkir.urbaneye3d.BuildingRecipe;
import ru.zkir.urbaneye3d.UrbanEye3dPlugin;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;
import ru.zkir.urbaneye3d.utils.Tessellator;

import javax.vecmath.Point3d;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.abs;
import static java.lang.Math.min;
import static ru.zkir.urbaneye3d.utils.Mesh.ROOF_COLOUR_IDX;
import static ru.zkir.urbaneye3d.utils.Mesh.WALL_COLOUR_IDX;

public class MesherHipped extends RoofGenerator {
    @Override
    public Mesh generate(BuildingRecipe building) {
        if ((building.getContour().size() <= 4) && (!building.hasComplexContour())) {
            return generateRectangular(building);
        } else{
            var mesh = generateStraightSkeleton(building);
            return mesh;
        }
    }

    public Mesh makeGabled(Mesh mesh, BuildingRecipe building){
        var v = mesh.verts;
        for (var i=0; i<mesh.faces.size();i++){
            var face =  mesh.faces.get(i);
            //The following conditions should be met:
            //  A) face should belong to the roof group.
            //  B) face should be triangular
            //  C) at least two of three vertices should belong to wall top.
            // TODO: how to check C ?
            if ((mesh.faceMaterials.get(i) == ROOF_COLOUR_IDX) &&  (face.length==3)){

                //enforce condition C
                int n=0;
                for (var vert_idx: face){
                    if(abs(v.get(vert_idx).z - building.wallHeight)<1e-6 ){
                        n++;
                    }
                }
                if (n!=2){
                    continue;
                }

                // find the topmost vertex
                int top = 0;
                if (v.get(face[1]).z > v.get(face[top]).z) {
                    top = 1;
                }
                if (v.get(face[2]).z > v.get(face[top]).z) {
                    top = 2;
                }

                //find the indices of the two remaining vertex
                int first = -1;
                int second = -1;

                for (int k = 0; k < 3; k++) {
                    if (k == top) continue;

                    if (first == -1) {
                        first = k;
                    } else {
                        second = k;
                    }
                }

                Point3D p = v.get(face[top]);
                Point3D p1 = v.get(face[first]);
                Point3D p2 = v.get(face[second]);

                p.x = (p1.x+p2.x) / 2.0;
                p.y = (p1.y+p2.y) / 2.0;

                //hopefully we do not need to push p back into the list, it is already there

                // the face should be considered rather as wall face
                mesh.faceMaterials.set(i, WALL_COLOUR_IDX);

            }
        }
        return mesh;
    }

    private int createNewRoofVertex(Mesh mesh, Point3d pt, double wallHeight, double scaledZ) {
        Point3D newVert = new Point3D(pt.x, pt.y, wallHeight + scaledZ);
        //for some reason, the skeleton algorithm has LOW precision for calculated coordinates,
        //so we need to merge vertices aggressively.
        int newIndex = mesh.addVertex2(newVert);
        return newIndex;
    }

    private Mesh generateStraightSkeleton(BuildingRecipe building) {
        List<Point2D> basePoints = building.getContour();
        double minHeight = building.minHeight;
        double wallHeight = building.wallHeight;
        double roofHeight = building.roofHeight;

        Mesh mesh = new Mesh(building.bottomColor, building.color, building.roofColor);

        //Create the walls and the bottom
        int baseIdx = 0;
        for (Point2D p : basePoints) {
            mesh.addVertex(new Point3D(p.x, p.y, minHeight));
        }

        int wallIdx;
        if (wallHeight > minHeight) {
            wallIdx = mesh.verts.size();
            for (Point2D p : basePoints) {
                mesh.addVertex(new Point3D(p.x, p.y, wallHeight));
            }
        } else {
            wallIdx = baseIdx;
        }

        if (wallHeight > minHeight) {
            //outer ring, aka main contour
            for (int i = 0; i < basePoints.size(); i++) {
                int j = (i + 1) % basePoints.size();
                mesh.addWallFace(new int[]{baseIdx + i, baseIdx + j, wallIdx + j, wallIdx + i});
            }

            //inner rings, if any
            for(var innerRing: building.getContourInnerRings()){
                int n = innerRing.size();
                for (int i=0; i<n; i++){
                    int j = (i + 1) % n;
                    var p = innerRing.get(i);
                    var q = innerRing.get(j);
                    int i1 = mesh.addVertex(new Point3D(p.x, p.y, minHeight));
                    int i2 = mesh.addVertex(new Point3D(q.x, q.y, minHeight));
                    int i3 = mesh.addVertex(new Point3D(q.x, q.y, wallHeight));
                    int i4 = mesh.addVertex(new Point3D(p.x, p.y, wallHeight));

                    mesh.addWallFace(new int[]{i4, i3, i2, i1});
                }
            }
        }
        if (!building.hasComplexContour()){
            // we have simple contour, and we can just add all the contour points to the bottom face
            int[] bottomFace = new int[basePoints.size()];
            for (int i = 0; i < basePoints.size(); i++) {
                bottomFace[i] = baseIdx + basePoints.size() - 1 - i;
            }
            mesh.addBottomFace(bottomFace);
        }else{
            //Contour has holes, so triangulation is necessary!
            var tessellator = new Tessellator(basePoints, building.getContourInnerRings());
            List<int[]> roofTriangles = tessellator.triangulate();

            for (var face_idx: roofTriangles){
                Point2D p1 = tessellator.getPoint(face_idx[0]);
                Point2D p2 = tessellator.getPoint(face_idx[1]);
                Point2D p3 = tessellator.getPoint(face_idx[2]);
                int i1 =  mesh.addVertex(new Point3D(p1.x, p1.y, minHeight) );
                int i2 =  mesh.addVertex(new Point3D(p2.x, p2.y, minHeight) );
                int i3 =  mesh.addVertex(new Point3D(p3.x, p3.y, minHeight) );

                mesh.addBottomFace(new int[]{i3, i2, i1});

                /* flat roof for testing purposes
                i1 =  mesh.addVertex(new Point3D(p1.x, p1.y, wallHeight) );
                i2 =  mesh.addVertex(new Point3D(p2.x, p2.y, wallHeight) );
                i3 =  mesh.addVertex(new Point3D(p3.x, p3.y, wallHeight) );
                mesh.addRoofFace(new int[]{i1, i2, i3});
                */
            }
        }

        //Initialize skeleton input data, from the building outline
        LoopL<Edge> loopl = new LoopL<>();

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
        loopl.add(loop);

        for (var basePoints2: building.getContourInnerRings()){

            loop = new Loop<>();
            cornerMap = new HashMap<>();
            for (Point2D p : basePoints2) {
                cornerMap.put(p, new Corner(p.x, p.y));
            }

            for (int i = 0; i < basePoints2.size(); i++) {
                Point2D p1 = basePoints2.get(i);
                Point2D p2 = basePoints2.get((i + 1) % basePoints2.size());
                Edge edge = new Edge(cornerMap.get(p2), cornerMap.get(p1));
                edge.machine = new Machine(Math.PI / 4);
                loop.append(edge);
            }
            loopl.add(loop);
        }

        //Request skeleton generation
        Skeleton skel = new Skeleton(loopl, true);
        skel.skeleton();

        //we need to find min and max Z, to proper scale our roof.
        // we need to respect our roofHeight

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

        //Check created faces/vertices and add them to our mesh

        for (Output.Face face : skel.output.faces.values()) {
            for (Loop<Point3d> lp3 : face.points) {
                List<Integer> faceIndices = new ArrayList<>();
                for (Point3d pt : lp3) {
                    double scaledZ = (pt.z - minZ) * roofScale;
                    int newIndex = createNewRoofVertex(mesh, pt, wallHeight, scaledZ);
                    faceIndices.add(newIndex);
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
        //For some reason the Skeleton produces artifacts, we need to fix them
        //  It's obviously much like bailing out the boat without plugging the leak,
        //  but we cannot fix the camp-skeleton library right now.
        mesh.fixDuplicateFaces();
        mesh.fixTJunctions();
        mesh.fixDegeneratedFaceParts();
        return mesh;
    }

    private Mesh generateRectangular(BuildingRecipe building) {
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
