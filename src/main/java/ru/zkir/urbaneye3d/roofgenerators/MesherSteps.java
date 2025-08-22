package ru.zkir.urbaneye3d.roofgenerators;

import ru.zkir.urbaneye3d.RenderableBuildingElement;
//import ru.zkir.urbaneye3d.UrbanEye3dPlugin;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;

import java.util.*;

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
    final double STEP_HEIGHT = 0.16;

    @Override
    public Mesh generate(RenderableBuildingElement building) {
        if (building.hasComplexContour()) {
            return null;
        }
        if (building.getContour().size() == 4){
            return generateQuadrangular(building);
        }else{
            return generateNonConvex(building);
        }
    }

    /**
    * Generate steps for quadrangular base. Simple algorithm
    */
    public Mesh generateQuadrangular(RenderableBuildingElement building) {
        if (building.getContour().size() != 4) {
            throw new RuntimeException("generateQuadrangular() supports only quadrangular bases. This call should never occur");
        }

        List<Point2D> contour = building.getContour();
        double minHeight = building.minHeight;
        double roofHeight = building.roofHeight;
        double wallHeight = building.height - roofHeight;
        double roofDirection = building.roofDirection;

        Mesh mesh = new Mesh();

        // Part 1: Initial calculations
        Point2D slopeVector = calculateSlopeVector(contour, roofDirection);
        double maxProj = -Double.MAX_VALUE, minProj = Double.MAX_VALUE;
        int start_node_idx = -1, end_node_idx = -1;

        for (int i = 0; i < contour.size(); i++) {
            Point2D p = contour.get(i);
            double proj = p.x * slopeVector.x + p.y * slopeVector.y;
            if (proj < minProj) {
                minProj = proj;
                start_node_idx = i;
            }
            if (proj > maxProj) {
                maxProj = proj;
                end_node_idx = i;
            }
        }

        int numSteps = (int) Math.max(1, Math.floor(roofHeight / STEP_HEIGHT));
        double actualStepHeight = roofHeight / numSteps;
        double projDiff = maxProj - minProj;
        double stepDepth = (projDiff > 1e-9) ? projDiff / numSteps : 0;
        double tan = (projDiff > 1e-9) ? roofHeight / projDiff : 0;

        // Part 2: Generate Base
        int[] baseIndices = new int[4];
        for (int i = 0; i < 4; i++) {
            baseIndices[i] = mesh.addVertex(new Point3D(contour.get(i).x, contour.get(i).y, minHeight));
        }

        mesh.bottomFaces.add(new int[]{baseIndices[0], baseIndices[3], baseIndices[2], baseIndices[1]});

        // Part 3: Generate complex walls that follow the roof profile
        List<List<Integer>> allWallFaces = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Point2D p1 = contour.get(i);
            Point2D p2 = contour.get((i + 1) % 4);

            double proj1 = p1.x * slopeVector.x + p1.y * slopeVector.y;
            double proj2 = p2.x * slopeVector.x + p2.y * slopeVector.y;
            boolean reversed_edge=false;
            if (proj1>proj2){
                //"reverse" edge
                var tmp_proj=proj1;
                var tmp_p = p1;
                proj1=proj2;
                p1=p2;
                proj2=tmp_proj;
                p2=tmp_p;
                reversed_edge=true;
            }

            List<Point3D> topProfile = new ArrayList<>();

            topProfile.add((new Point3D(p1, minHeight)));
            double z = wallHeight + (proj1 - minProj) * tan;
            z = Math.max(z, actualStepHeight);
            topProfile.add((new Point3D(p1,  z )));

           // Add all step-corners along the wall edge
            if (proj1<proj2){
                for (int s = 0; s <= numSteps; s++) {
                    double projS = minProj + s * stepDepth;
                    if (projS >= Math.min(proj1, proj2) - 1e-9 && projS <= Math.max(proj1, proj2) + 1e-9) {
                        double t = (Math.abs(proj2 - proj1) < 1e-9) ? 0 : (projS - proj1) / (proj2 - proj1);
                        t = Math.max(0, Math.min(1, t)); // Clamp t to [0,1]
                        Point2D ip = new Point2D(p1.x + t * (p2.x - p1.x), p1.y + t * (p2.y - p1.y));
                        if (s > 0) {
                            topProfile.add(new Point3D(ip.x, ip.y, wallHeight + s * actualStepHeight));
                        }
                        if (s < numSteps) { // Add point for the top of the riser, prevent going over max height
                            topProfile.add(new Point3D(ip.x, ip.y, wallHeight + (s + 1) * actualStepHeight));
                        }
                    }
                }

            }else{
                for (int s = numSteps; s >= 0; s--) {
                    double projS = minProj + s * stepDepth;
                    if (projS >= Math.min(proj1, proj2) - 1e-9 && projS <= Math.max(proj1, proj2) + 1e-9) {
                        double t = (Math.abs(proj2 - proj1) < 1e-9) ? 0 : (projS - proj1) / (proj2 - proj1);
                        t = Math.max(0, Math.min(1, t)); // Clamp t to [0,1]
                        Point2D ip = new Point2D(p1.x + t * (p2.x - p1.x), p1.y + t * (p2.y - p1.y));
                        if (s < numSteps) { // Add point for the top of the riser, prevent going over max height
                            topProfile.add(new Point3D(ip.x, ip.y, wallHeight + (s + 1) * actualStepHeight));
                        }
                        if (s > 0) {
                            topProfile.add(new Point3D(ip.x, ip.y, wallHeight + s * actualStepHeight));
                        }
                    }
                }
            }
            z =wallHeight + (proj2 - minProj) * tan;
            z = Math.max(z, actualStepHeight);
            topProfile.add((new Point3D(p2, z)));
            topProfile.add((new Point3D(p2, minHeight)));

            List<Integer> topProfileIndices = new ArrayList<>();

            Point3D lastPoint = null;
            for (Point3D p : topProfile) {
                 if (lastPoint == null || p.distance(lastPoint) > 1e-6){
                     topProfileIndices.add(mesh.addVertex(p));
                     lastPoint = p;
                 }
            }

            List<Integer> wallFace = new ArrayList<>();

            // Add profile vertices to mesh and face list
            wallFace.addAll(topProfileIndices);

            if (!reversed_edge){
                List<Integer> wallFaceR = new ArrayList<>();
                for (int k=wallFace.size()-1; k>=0; k--){
                    wallFaceR.add(wallFace.get(k));
                }

                mesh.wallFaces.add(wallFaceR.stream().mapToInt(Integer::intValue).toArray());

            }else{
                mesh.wallFaces.add(wallFace.stream().mapToInt(Integer::intValue).toArray());
            }

            allWallFaces.add(wallFace);
        }

        // Part 4: Generate Roof Faces by stitching wall profiles
        List<List<Integer>> topEdges = new ArrayList<>();
        for (List<Integer> wallFace : allWallFaces) {

            List<Integer> topEdge = new ArrayList<>();
            for (int index : wallFace) {
                if (mesh.verts.get(index).z > minHeight ) {
                    topEdge.add(index);
                }
            }
            topEdges.add(topEdge);
        }

        List<Integer> rail1 = new ArrayList<>();
        int i = start_node_idx;
        while (i != end_node_idx) {
            rail1.addAll(topEdges.get(i));
            i = (i + 1) % 4;
        }

        List<Integer> rail2 = new ArrayList<>();
        i = start_node_idx;
        while (i != end_node_idx) {
            int wall_idx = (i - 1 + 4) % 4;
            rail2.addAll(topEdges.get(wall_idx));
            i = (i - 1 + 4) % 4;
        }

        int ii=0;
        int jj=0;

        while (ii<rail1.size()-1 && jj<rail2.size()-1 ) {

            int vi0 = rail1.get(ii);
            int vi1 = rail1.get(ii + 1);
            int vj0 = rail2.get(jj);
            int vj1 = rail2.get(jj + 1);

            if(vi0==vj0 && vi1==vj1 ){ //this is some strange glitch of wall profile creation algorithm. first raiser is included twice
                ii++; jj++;
                continue;
            }

            if (vi0==vi1){
                //we need to create additional face
                int vi2 = rail1.get(ii + 2);
                var face = new int[]{vj0, vi1, vi2};
                mesh.roofFaces.add(face);
                ii += 2;
                continue;
            }
            if (vj0==vj1){
                //we need to create additional face
                int vj2 = rail2.get(jj + 2);
                var face = new int[]{vj2, vj1, vi0 };
                mesh.roofFaces.add(face);
                jj +=2;
                continue;
            }

            if (vi0==vj0){ // only expected at the start
                var face = new int[]{vi0, vi1, vj1};
                mesh.roofFaces.add(face);
            } else if(vi1==vj1){// only expected at the end
                var face = new int[]{vi0, vi1, vj0};
                mesh.roofFaces.add(face);
            }else {
                var face =new int[]{vi1, vj0, vi0};
                mesh.roofFaces.add(face);
                face =new int[]{vi1, vj1, vj0};
                mesh.roofFaces.add(face);
            }
            ii++; jj++;
        }
        return mesh;
    }

    /**
    *  Generates steps mesh for non-convex base (not finished yet)
    */
    public Mesh generateNonConvex(RenderableBuildingElement building) {

        List<Point2D> contour = building.getContour();
        if (contour.isEmpty()) return new Mesh();

        double minHeight = building.minHeight;
        double roofHeight = building.roofHeight;
        double wallHeight = building.height - roofHeight;
        double roofDirection = building.roofDirection;

        Mesh mesh = new Mesh();

        Point2D slopeVector = calculateSlopeVector(contour, roofDirection);

        double maxProj = -Double.MAX_VALUE, minProj = Double.MAX_VALUE;
        for (Point2D p : contour) {
            double proj = p.x * slopeVector.x + p.y * slopeVector.y;
            maxProj = Math.max(maxProj, proj);
            minProj = Math.min(minProj, proj);
        }

        int numSteps = (int) Math.max(1, Math.floor(roofHeight / STEP_HEIGHT));
        double actualStepHeight = roofHeight / numSteps;
        double projDiff = maxProj - minProj;
        double stepDepth = (projDiff > 1e-9) ? projDiff / numSteps : 0;

        // Add base vertices and bottom face
        int baseStartIndex = mesh.verts.size();
        for (Point2D p : contour) {
            mesh.addVertex(new Point3D(p.x, p.y, minHeight));
        }
        int[] bottomFace = new int[contour.size()];
        for (int i = 0; i < contour.size(); i++) bottomFace[i] = baseStartIndex + (contour.size() - 1 - i);
        //mesh.bottomFaces.add(bottomFace);

        // --- Generate Steps (Risers and Treads) for potentially multiple segments ---
        List<Intersection> frontIntersectionPoints = getIntersectionPoints(contour, slopeVector, minProj);

        // List of indices for the previous cut.
        List<Integer> prev_cut_indices = new ArrayList<>();
        List<Integer> prev_cut_edges = new ArrayList<>();
        for (int i = 0; i < frontIntersectionPoints.size(); i++ ) {
            int v1_idx = mesh.addVertex(new Point3D(frontIntersectionPoints.get(i).point.x, frontIntersectionPoints.get(i).point.y, wallHeight));
            prev_cut_indices.add(v1_idx);
            prev_cut_edges.add(frontIntersectionPoints.get(i).edgeIndex);
        }

        //steps
        //debugMsg("-- steps!!");
        for (int s = 0; s < numSteps; s++) {
            double z_top = wallHeight + (s + 1) * actualStepHeight;
            double proj_back = minProj + (s + 1) * stepDepth;
            List<Intersection> currentIntersectionPoints = getIntersectionPoints(contour, slopeVector, proj_back);

            List<Integer> current_cut_indices = new ArrayList<>();
            List<Integer> current_cut_edges = new ArrayList<>();

            //we need to create vertices for each intersection.
            for (int i = 0; i < currentIntersectionPoints.size(); i++ ) {
                int v_idx = mesh.addVertex(new Point3D(currentIntersectionPoints.get(i).point.x, currentIntersectionPoints.get(i).point.y, z_top));
                current_cut_indices.add(v_idx);
                current_cut_edges.add(currentIntersectionPoints.get(i).edgeIndex);
            }
            if (current_cut_indices.size()==prev_cut_indices.size() && current_cut_indices.size() %2 ==0 ){
                //there are pairs of vertices - simple case
                for (int i=0; i<prev_cut_indices.size(); i+=2){
                    var face = new int[]{prev_cut_indices.get(i+1), prev_cut_indices.get(i), current_cut_indices.get(i), current_cut_indices.get(i+1)  };
                    mesh.roofFaces.add(face);
                }
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
                        //debugMsg("increase: "+ prev_cut_indices.size() + " " + current_cut_indices.size());
                        for (int i=0; i<prev_cut_indices.size(); i+=2) {
                            var face = processChange2(i, prev_cut_indices, prev_cut_edges, current_cut_indices, current_cut_edges);
                            mesh.roofFaces.add(face);
                        }

                    } else{
                        //debugMsg("decrease: "+ prev_cut_indices.size() + " " + current_cut_indices.size());
                        for (int i=0; i<current_cut_indices.size(); i+=2) {
                            var face = processChange2(i, current_cut_indices, current_cut_edges, prev_cut_indices, prev_cut_edges);
                            mesh.roofFaces.add(face);
                        }
                    }
                } else{
                    //debugMsg("strange case: "+ prev_cut_indices.size() + " " + current_cut_indices.size());
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
            //debugMsg("   unable to find matching node!");
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

    }


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
            //The existing algorithm does not work really well for collinear slope
            // so let's add some nice distortion.
            slopeVector=slopeVector.add(new Point2D(1e-4, -1e-4));
        }
        slopeVector.normalize();
        return slopeVector;
    }

    /**
     *  Result data structure for getIntersectionPoints() method
     */
    private static class Intersection {
        final Point3D point;
        final int edgeIndex;

        Intersection(Point3D point, int edgeIndex) {
            this.point = point;
            this.edgeIndex = edgeIndex;
        }
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