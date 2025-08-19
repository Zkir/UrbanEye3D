package ru.zkir.urbaneye3d.roofgenerators;

import ru.zkir.urbaneye3d.RenderableBuildingElement;
import ru.zkir.urbaneye3d.UrbanEye3dPlugin;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * This mesher is somewhat similar to MesherSkillion, but unlike it,
 *  it creates not just one inclined surface for the roof,
 * but a ladder: alternating vertical and horizontal surfaces.
 *
 * The walls are vertical as usual, but the base is recreated because,
 *  given that the wall tops vertices are created based on parallel cuts,
 *  the vertices of the original contour do not always lie on these cuts.
 *
 * @see MesherSkillion
 */
public class MesherSteps extends  RoofGenerator {

    private static class Intersection {
        final Point3D point;
        final int edgeIndex;

        Intersection(Point3D point, int edgeIndex) {
            this.point = point;
            this.edgeIndex = edgeIndex;
        }
    }

    @Override
    public Mesh generate(RenderableBuildingElement building) {
        if (building.hasComplexContour()) {
            UrbanEye3dPlugin.debugMsg("Steps generation for complex contours is not yet supported. Building: " + building.primitiveId);
            return null;
        }
        List<Point2D> contour = building.getContour();
        if (contour.isEmpty()) return new Mesh();

        double minHeight = building.minHeight;
        double roofHeight = building.roofHeight;
        double wallHeight = building.height - roofHeight;
        double roofDirection = building.roofDirection;

        Mesh mesh = new Mesh();
        List<Point3D> verts = mesh.verts;

        Point2D slopeVector = calculateSlopeVector(contour, roofDirection);

        double maxProj = -Double.MAX_VALUE, minProj = Double.MAX_VALUE;
        for (Point2D p : contour) {
            double proj = p.x * slopeVector.x + p.y * slopeVector.y;
            maxProj = Math.max(maxProj, proj);
            minProj = Math.min(minProj, proj);
        }

        final double STEP_HEIGHT = 0.16;
        int numSteps = (int) Math.max(1, Math.floor(roofHeight / STEP_HEIGHT));
        double actualStepHeight = roofHeight / numSteps;
        double projDiff = maxProj - minProj;
        double stepDepth = (projDiff > 1e-9) ? projDiff / numSteps : 0;

        // Add base vertices and bottom face
        int baseStartIndex = verts.size();
        contour.forEach(p -> verts.add(new Point3D(p.x, p.y, minHeight)));
        int[] bottomFace = new int[contour.size()];
        for (int i = 0; i < contour.size(); i++) bottomFace[i] = baseStartIndex + (contour.size() - 1 - i);
        //mesh.bottomFaces.add(bottomFace);

        // --- Generate Steps (Risers and Treads) for potentially multiple segments ---
        List<Intersection> frontIntersectionPoints = getIntersectionPoints(contour, slopeVector, minProj);

        // List of indices for the previous cut.
        List<Integer> prev_cut_indices = new ArrayList<>();
        List<Integer> prev_cut_edges = new ArrayList<>();
        for (int i = 0; i < frontIntersectionPoints.size(); i++ ) {
            int v1_idx = verts.size();
            verts.add(new Point3D(frontIntersectionPoints.get(i).point.x, frontIntersectionPoints.get(i).point.y, wallHeight));
            prev_cut_indices.add(v1_idx);
            prev_cut_edges.add(frontIntersectionPoints.get(i).edgeIndex);
        }

        //steps
        UrbanEye3dPlugin.debugMsg("-- steps!!");
        for (int s = 0; s < numSteps; s++) {
            double z_top = wallHeight + (s + 1) * actualStepHeight;
            double proj_back = minProj + (s + 1) * stepDepth;
            List<Intersection> currentIntersectionPoints = getIntersectionPoints(contour, slopeVector, proj_back);

            List<Integer> current_cut_indices = new ArrayList<>();
            List<Integer> current_cut_edges = new ArrayList<>();
            int back_point_idx = 0;
            //we need to create vertices for each intersection.
            for (int i = 0; i < currentIntersectionPoints.size(); i++ ) {
                int v_idx = verts.size();
                verts.add(new Point3D(currentIntersectionPoints.get(i).point.x, currentIntersectionPoints.get(i).point.y, z_top));
                current_cut_indices.add(v_idx);
                current_cut_edges.add(currentIntersectionPoints.get(i).edgeIndex);
            }
            if (current_cut_indices.size()==prev_cut_indices.size() && current_cut_indices.size() %2 ==0 ){
                //there are pairs of vertices - simple case
                for (int i=0; i<prev_cut_indices.size(); i+=2){
                    var face = new int[]{prev_cut_indices.get(i+1), prev_cut_indices.get(i), current_cut_indices.get(i), current_cut_indices.get(i+1)  };
                    mesh.roofFaces.add(face);
                };
            } else{
                if (Math.abs(current_cut_indices.size()-prev_cut_indices.size())==1){
                    List<Integer> face_idxs = new ArrayList<>();
                    Collections.reverse(prev_cut_indices);
                    face_idxs.addAll(prev_cut_indices);
                    face_idxs.addAll(current_cut_indices);
                    int[] face = face_idxs.stream()
                            .mapToInt(i -> i)
                            .toArray();
                    mesh.roofFaces.add(face);

                } else if (Math.abs(current_cut_indices.size()-prev_cut_indices.size())%2==0){

                    // let's do another trick, since we have 2 less vertices in one edge, let's create only possible faces
                    // we can join nodes which lie on the same edge.

                    if  (current_cut_indices.size()>prev_cut_indices.size()){
                        UrbanEye3dPlugin.debugMsg("increase: "+ prev_cut_indices.size() + " " + current_cut_indices.size());
                        for (int i=0; i<prev_cut_indices.size(); i+=2) {
                            var face = processChange2(i, prev_cut_indices, prev_cut_edges, current_cut_indices, current_cut_edges);
                            mesh.roofFaces.add(face);
                        }

                    } else{
                        UrbanEye3dPlugin.debugMsg("decrease: "+ prev_cut_indices.size() + " " + current_cut_indices.size());
                        for (int i=0; i<current_cut_indices.size(); i+=2) {
                            var face = processChange2(i, current_cut_indices, current_cut_edges, prev_cut_indices, prev_cut_edges);
                            mesh.roofFaces.add(face);
                        }
                    };
                } else{
                    UrbanEye3dPlugin.debugMsg("strange case: "+ prev_cut_indices.size() + " " + current_cut_indices.size());
                }
            }
            prev_cut_indices = current_cut_indices;
            prev_cut_edges   = current_cut_edges;
        }
        //TODO: create bottom face(s)
        //The base should be recreated because,
        //  given that the wall tops vertices are created based on parallel cuts (see above),
        //  the vertices of the original contour do not always lie on these cuts.


        // Wall generation is intentionally omitted.
        //TODO: create wall faces.


        return mesh;
    }


    int[] processChange2(int i,
                         List<Integer> prev_cut_indices, List<Integer> prev_cut_edges,
                         List<Integer> current_cut_indices, List<Integer> current_cut_edges  ){

        List<Integer> face_idxs = new ArrayList<>();
        face_idxs.add(prev_cut_indices.get(i));
        //let's find the first node on opposite edge,
        // we are sure it exists
        int j=0; //index of vertex in  opposite edge.

        while (j<current_cut_indices.size()) {
            int prev_edge = prev_cut_edges.get(i);
            int curr_edge = current_cut_edges.get(j);

            if (curr_edge== prev_edge) {
                face_idxs.add(current_cut_indices.get(j));
                break;
            }
            j++;
        }
        if (j>=current_cut_indices.size()){
            UrbanEye3dPlugin.debugMsg("   unable to find matching node!");
            j=0;
            face_idxs.add(current_cut_indices.get(j));
        }

        //process the remaining nodes of the opposite edge.
        while (j<current_cut_indices.size()){
            int prev_edge = prev_cut_edges.get(i+1);
            int curr_edge = current_cut_edges.get(j);

            face_idxs.add(current_cut_indices.get(j)); //here the node is added unconditionally.
            if (curr_edge== prev_edge) {
                break; //it means that we gave found proper node.
            }

            j++;
        }
        //close the loop
        face_idxs.add(prev_cut_indices.get(i+1));

        //for some reason we need to reverse the windings
        Collections.reverse(face_idxs);
        return face_idxs.stream()
                .mapToInt(k -> k)
                .toArray();

    };

    /* DO NOT REMOVE YET!
    private int[] getNearestPair(List<Point3D> verts, List<Integer> currentIndices, int index, List<Integer> otherIndices) {
        //let's find the pair of  vertices in other  nearest to current.
        var v1= verts.get(currentIndices.get(index));
        var v2= verts.get(currentIndices.get(index+1));
        var min_i=-1;
        var min_len = Double.MAX_VALUE;
        for (int i =0; i<otherIndices.size(); i+=2 ){
            var v3= verts.get(otherIndices.get(i));
            var v4= verts.get(otherIndices.get(i+1));
            var len = v1.add(v2).div(2.0).distance( v3.add(v4).div(2.0) );
            if (len<min_len){
                min_len = len;
                min_i = i;
            }
        }
        return new int[]{otherIndices.get(min_i),otherIndices.get(min_i+1) };
    }  */


    private Point2D calculateSlopeVector(List<Point2D> contour, double roofDirection) {
        Point2D slopeVector;
        if (!Double.isNaN(roofDirection)) {
            double angleRad = Math.toRadians(roofDirection);
            slopeVector = new Point2D(-Math.sin(angleRad), -Math.cos(angleRad));
        } else {
            int[] longestEdgeIndices = findLongestEdge(contour);
            Point2D p1 = contour.get(longestEdgeIndices[0]);
            Point2D p2 = contour.get(longestEdgeIndices[1]);
            slopeVector = new Point2D(-(p2.y - p1.y), p2.x - p1.x);
        }
        slopeVector.normalize();
        return slopeVector;
    }

    private List<Intersection> getIntersectionPoints(List<Point2D> contour, Point2D slopeVector, double proj) {
        List<Intersection> intersections = new ArrayList<>();
        Point2D normal = new Point2D(-slopeVector.y, slopeVector.x);

        for (int i = 0; i < contour.size(); i++) {
            Point2D p1 = contour.get(i);
            Point2D p2 = contour.get((i + 1) % contour.size());

            double a1 = p2.y - p1.y, b1 = p1.x - p2.x, c1 = a1 * p1.x + b1 * p1.y;
            double a2 = slopeVector.x, b2 = slopeVector.y, c2 = proj;
            double det = a1 * b2 - a2 * b1;

            if (Math.abs(det) > 1e-9) {
                double x = (b2 * c1 - b1 * c2) / det;
                double y = (a1 * c2 - a2 * c1) / det;
                // Check if the intersection point is within the segment p1-p2 with a small tolerance
                final double EPSILON = 1e-9;
                if (x >= Math.min(p1.x, p2.x) - EPSILON && x <= Math.max(p1.x, p2.x) + EPSILON &&
                        y >= Math.min(p1.y, p2.y) - EPSILON && y <= Math.max(p1.y, p2.y) + EPSILON) {
                    intersections.add(new Intersection(new Point3D(x, y, 0), i)); // Z is set later
                }
            }
        }
        intersections.sort(Comparator.comparingDouble(p -> p.point.x * normal.x + p.point.y * normal.y));

        // Remove duplicates that are too close to each other
        if (intersections.size() < 2) {
            return intersections;
        }
        final double DUPLICATE_EPSILON = 1e-6;
        List<Intersection> uniqueIntersections = new ArrayList<>();
        uniqueIntersections.add(intersections.get(0));
        for (int i = 1; i < intersections.size(); i++) {
            if (intersections.get(i).point.distance(intersections.get(i - 1).point) > DUPLICATE_EPSILON) {
                uniqueIntersections.add(intersections.get(i));
            }
        }

        return uniqueIntersections;
    }

}
