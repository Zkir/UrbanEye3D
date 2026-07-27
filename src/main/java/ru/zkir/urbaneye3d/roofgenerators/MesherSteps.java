package ru.zkir.urbaneye3d.roofgenerators;

import ru.zkir.urbaneye3d.BuildingRecipe;
import ru.zkir.urbaneye3d.RenderableElement;
//import ru.zkir.urbaneye3d.UrbanEye3dPlugin;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;

import java.util.*;

import static java.lang.Math.abs;

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
public class MesherSteps extends RoofGenerator {

    @Override
    public Mesh generate(BuildingRecipe building) {
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
    public Mesh generateQuadrangular(BuildingRecipe building) {
        if (building.getContour().size() != 4) {
            throw new RuntimeException("generateQuadrangular() supports only quadrangular bases. This call should never occur");
        }

        List<Point2D> contour = building.getContour();
        double minHeight = building.minHeight;
        double roofHeight = building.roofHeight;
        double wallHeight = building.height - roofHeight;
        Double roofDirection = building.roofDirection;

        Mesh mesh = new Mesh(building.bottomColor, building.color, building.roofColor);

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

        int numSteps = (int) Math.max(1, Math.floor(roofHeight / building.stepHeight));
        double actualStepHeight = roofHeight / numSteps;
        double projDiff = maxProj - minProj;
        double stepDepth = (projDiff > 1e-9) ? projDiff / numSteps : 0;
        double tan = (projDiff > 1e-9) ? roofHeight / projDiff : 0;

        // Part 2: Generate Base
        int[] baseIndices = new int[4];
        for (int i = 0; i < 4; i++) {
            baseIndices[i] = mesh.addVertex(new Point3D(contour.get(i).x, contour.get(i).y, minHeight));
        }

        mesh.addBottomFace(new int[]{baseIndices[0], baseIndices[3], baseIndices[2], baseIndices[1]});

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
                        double t = (abs(proj2 - proj1) < 1e-9) ? 0 : (projS - proj1) / (proj2 - proj1);
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
                        double t = (abs(proj2 - proj1) < 1e-9) ? 0 : (projS - proj1) / (proj2 - proj1);
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

                mesh.addWallFace(wallFaceR.stream().mapToInt(Integer::intValue).toArray());

            }else{
                mesh.addWallFace(wallFace.stream().mapToInt(Integer::intValue).toArray());
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
                mesh.addRoofFace(face);
                ii += 2;
                continue;
            }
            if (vj0==vj1){
                //we need to create additional face
                int vj2 = rail2.get(jj + 2);
                var face = new int[]{vj2, vj1, vi0 };
                mesh.addRoofFace(face);
                jj +=2;
                continue;
            }

            if (vi0==vj0){ // only expected at the start
                var face = new int[]{vi0, vi1, vj1};
                mesh.addRoofFace(face);
            } else if(vi1==vj1){// only expected at the end
                var face = new int[]{vi0, vi1, vj0};
                mesh.addRoofFace(face);
            }else {
                var face =new int[]{vi1, vj0, vi0};
                mesh.addRoofFace(face);
                face =new int[]{vi1, vj1, vj0};
                mesh.addRoofFace(face);
            }
            ii++; jj++;
        }
        return mesh;
    }

    /**
    * Generate steps for an arbitrary non-convex base. Complex algorithm and still some bugs here.
    */
    public Mesh generateNonConvex(BuildingRecipe building) {

        List<Point2D> contour = building.getContour();
        if (contour.isEmpty()) return null;

        double minHeight = building.minHeight;
        double roofHeight = building.roofHeight;
        double wallHeight = building.height - roofHeight;
        Double roofDirection = building.roofDirection;

        Mesh mesh = new Mesh(building.bottomColor, building.color, building.roofColor);

        Point2D slopeVector = calculateSlopeVector(contour, roofDirection);
        final Point2D normal = new Point2D(-slopeVector.y, slopeVector.x);

        double maxProj = -Double.MAX_VALUE, minProj = Double.MAX_VALUE;
        for (Point2D p : contour) {
            double proj = p.x * slopeVector.x + p.y * slopeVector.y;
            maxProj = Math.max(maxProj, proj);
            minProj = Math.min(minProj, proj);
        }

        int numSteps = (int) Math.max(1, Math.floor(roofHeight / building.stepHeight));
        double actualStepHeight = roofHeight / numSteps;
        double projDiff = maxProj - minProj;
        double stepDepth = (projDiff > 1e-9) ? projDiff / numSteps : 0;

        // --- Generate Steps (Risers and Treads) for potentially multiple segments ---
        List<Intersection> frontIntersectionPoints = getIntersectionPoints(contour, slopeVector, minProj);
        if ( frontIntersectionPoints.size() == 1 ) {
            addNearestContourVertexToIntersection(frontIntersectionPoints, contour, slopeVector, normal, stepDepth);
        }

        //debugMsg("frontIntersectionPoints: " + frontIntersectionPoints);

        List<Integer> prev_cut_indices = new ArrayList<>();
        List<Integer> prev_cut_edges = new ArrayList<>();
        List<Intersection> prevIntersectionPoints=null;
        for (int i = 0; i < frontIntersectionPoints.size(); i++ ) {
            int v1_idx = mesh.addVertex(new Point3D(frontIntersectionPoints.get(i).point.x, frontIntersectionPoints.get(i).point.y, wallHeight));
            prev_cut_indices.add(v1_idx);
            prev_cut_edges.add(frontIntersectionPoints.get(i).edgeIndex);
        }

        for (int s = 0; s < numSteps; s++) {
            double z_bottom = wallHeight + s * actualStepHeight;
            double z_top = wallHeight + (s + 1) * actualStepHeight;

            double proj_back = minProj + (s + 1) * stepDepth;
            List<Intersection> currentIntersectionPoints = getIntersectionPoints(contour, slopeVector, proj_back);

            if ((currentIntersectionPoints.size() == 1) && (s == 0 || s == numSteps - 1)) {
                addNearestContourVertexToIntersection(currentIntersectionPoints, contour, slopeVector, normal, stepDepth);
            }

            if (s==numSteps-1){
                //debugMsg("prevIntersectionPoints: "+ prevIntersectionPoints);
                //debugMsg("currentIntersectionPoints: "+currentIntersectionPoints);
            }

            // 1. Create vertices for the Riser
            List<Integer> riser_bottom_indices = new ArrayList<>();
            List<Integer> riser_top_indices = new ArrayList<>();
            //if (currentIntersectionPoints.size()>1) { //TODO: Do we need any condition here???
            if (!((s==numSteps-1) && (currentIntersectionPoints.size() % 2 != 0))) { //TODO: this is some kind of remaining problem
                //for the last (topmost) step we can create raisers, but we cannot create tread face, in case geometry differs,
                //especially if one edge is created via intersections, and another just by single(?) contour vertex
                for (int idx : prev_cut_indices) {
                    Point3D p = mesh.verts.get(idx);
                    riser_bottom_indices.add(mesh.addVertex(new Point3D(p.x, p.y, z_bottom)));
                }

                for (int idx : prev_cut_indices) {
                    Point3D p_bottom = mesh.verts.get(idx);
                    riser_top_indices.add(mesh.addVertex(new Point3D(p_bottom.x, p_bottom.y, z_top)));
                }

                // 2. Create Riser faces
                for (int i = 0; i < riser_bottom_indices.size() / 2; i++) {
                    var face = new int[]{  riser_top_indices.get(i * 2),
                                           riser_top_indices.get(i * 2 + 1),
                                           riser_bottom_indices.get(i * 2 + 1),
                                           riser_bottom_indices.get(i * 2)
                                        };
                    mesh.addRoofFace(face);
                }
            }

            // 3. Create vertices for the Tread

            List<Integer> tread_back_indices = new ArrayList<>();
            List<Integer> tread_back_edges = new ArrayList<>();
            for (Intersection inter : currentIntersectionPoints) {
                tread_back_indices.add(mesh.addVertex(new Point3D(inter.point.x, inter.point.y, z_top)));
                tread_back_edges.add(inter.edgeIndex);
            }

            // 4. Create Tread faces
            if (riser_top_indices.size()==tread_back_indices.size() && riser_top_indices.size() %2 ==0 ){
                for (int i=0; i<riser_top_indices.size(); i+=2){
                    var face = new int[]{riser_top_indices.get(i+1), riser_top_indices.get(i), tread_back_indices.get(i), tread_back_indices.get(i+1)  };
                    mesh.addRoofFace(face);
                }
            } else {
                 if (abs(riser_top_indices.size()-tread_back_indices.size())%2==0){
                    if  (riser_top_indices.size() > tread_back_indices.size()){
                        for (int i=0; i<tread_back_indices.size(); i+=2) {
                            var face = processChange2(i, tread_back_indices, tread_back_edges, riser_top_indices, prev_cut_edges);
                            mesh.addRoofFace(face);
                        }

                    } else{
                        for (int i=0; i<riser_top_indices.size(); i+=2) {
                            var face = processChange2(i, riser_top_indices, prev_cut_edges, tread_back_indices, tread_back_edges);
                            mesh.addRoofFace(face);
                        }
                    }
                } else{
                    // special case, we can't do much here
                }
            }

            // 5. Update state for next iteration
            prev_cut_indices = tread_back_indices;
            prev_cut_edges = tread_back_edges;
            prevIntersectionPoints = currentIntersectionPoints;
        }

        createWallsAndBottom(mesh, minHeight, actualStepHeight);
        return mesh;
    }

    private static void addNearestContourVertexToIntersection(List<Intersection> currentIntersectionPoints, List<Point2D> contour, Point2D slopeVector, Point2D normal, double stepDepth) {
        // This is a special case where the cutting line for the step's back edge results in a single intersection,
        // likely at a tip of the building outline. We try to find a nearby contour vertex to form a pair,
        // creating a very small edge for the step tread.
        Intersection singleIntersection = currentIntersectionPoints.get(0);
        Point2D intersectionPoint2D = new Point2D(singleIntersection.point.x, singleIntersection.point.y);

        Point2D nearestContourVertex = null;
        double minProjDistance = Double.MAX_VALUE;
        int n = contour.size();

        // Find the closest adjacent vertex on the main building contour.
        for (int i=0; i<n; i++) {
            Point2D v1 = contour.get((i+n-1)%n);
            Point2D v2 = contour.get((i+1)%n );
            if (contour.get(i).distance(intersectionPoint2D) <1e-6 ){
                //we've found this vertex in a contour.
                //check the ADJACENT vertices
                double d1 = abs(v1.dot(slopeVector)-intersectionPoint2D.dot(slopeVector));
                double d2 = abs(v2.dot(slopeVector)-intersectionPoint2D.dot(slopeVector));
                if (d1<d2){
                    nearestContourVertex = v1;
                    minProjDistance = d1;
                }else{
                    nearestContourVertex = v2;
                    minProjDistance = d2;
                }
                break;
            }
        }

        // If a close-enough vertex is found, create a new intersection point from it.
        if (nearestContourVertex != null && minProjDistance < stepDepth) {
            int edgeIndex = -1;
            for (int i = 0; i < contour.size(); i++) {
                if (contour.get(i) == nearestContourVertex) {
                    edgeIndex = i; // The edge is considered to start at this vertex.
                    break;
                }
            }
            if (edgeIndex != -1) {
                Intersection newIntersection = new Intersection(new Point3D(nearestContourVertex.x, nearestContourVertex.y, 0), edgeIndex);
                currentIntersectionPoints.add(newIntersection);
                // Re-sort the intersections list as the order is important.
                currentIntersectionPoints.sort(Comparator.comparingDouble(p -> p.point.x * normal.x + p.point.y * normal.y));
            }
        }
    }

    /**
     * The part of the algorithm that stitches together the vertices of the front and back cuts if both cuts have an even number of vertices.
    */
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

            if (curr_edge == prev_edge) {
                if(!face_idxs.contains(current_cut_indices.get(j))) {
                    face_idxs.add(current_cut_indices.get(j));
                }
                break;
            }
            j++;
        }
        if (j>=current_cut_indices.size()){
            //debugMsg("   unable to find matching node!");
            j=0;
            if(!face_idxs.contains(current_cut_indices.get(j))) {
                face_idxs.add(current_cut_indices.get(j));
            }
        }

        //process the remaining nodes of the opposite edge.
        while (j<current_cut_indices.size()){
            int prev_edge = prev_cut_edges.get(i+1);
            int curr_edge = current_cut_edges.get(j);

            if(!face_idxs.contains(current_cut_indices.get(j))) {
                face_idxs.add(current_cut_indices.get(j)); //here the node is added unconditionally.
            }
            if (curr_edge == prev_edge) {
                break; //it means that we gave found proper node.
            }

            j++;
        }
        //close the loop
        if(!face_idxs.contains(prev_cut_indices.get(i+1))) {
            face_idxs.add(prev_cut_indices.get(i + 1));
        }

        //for some reason we need to reverse the windings
        //Collections.reverse(face_idxs);
        return face_idxs.stream()
                .mapToInt(k -> k)
                .toArray();

    }

    private void createWallsAndBottom(Mesh mesh, double minHeight, double actualStepHeight) {
        Map<String, Integer> edgeCounts = new HashMap<>();

        for (int[] face : mesh.faces) {
            for (int i = 0; i < face.length; i++) {
                int v1 = face[i];
                int v2 = face[(i + 1) % face.length];
                String edge =  v1 + "-" + v2;
                edgeCounts.put(edge, edgeCounts.getOrDefault(edge,  0) + 1);
            }
        }

        Map<Integer, Integer> topToBottomMap = new HashMap<>();

        for (String edgeKey : edgeCounts.keySet() ) {
            String[] vertices = edgeKey.split("-");
            int v1_top_idx = Integer.parseInt(vertices[0]);
            int v2_top_idx = Integer.parseInt(vertices[1]);
            int countF= edgeCounts.get(edgeKey);
            int countR= edgeCounts.getOrDefault(v2_top_idx + "-" + v1_top_idx ,0);
            int count=  countF + countR;
            if (count == 1) { // This is a boundary edge
                boolean reverseRequired=false;
                if (v1_top_idx>v2_top_idx){
                    v1_top_idx = Integer.parseInt(vertices[1]);
                    v2_top_idx = Integer.parseInt(vertices[0]);
                    reverseRequired = true;
                }
                Point3D p1_top = mesh.verts.get(v1_top_idx);
                Point3D p2_top = mesh.verts.get(v2_top_idx);

                int raiser_idx = mesh.getVertexId( new Point3D(p1_top.x, p1_top.y, p1_top.z - actualStepHeight));

                int v1_bottom_idx = topToBottomMap.computeIfAbsent(v1_top_idx, k -> mesh.addVertex(new Point3D(p1_top.x, p1_top.y, minHeight)));
                int v2_bottom_idx = topToBottomMap.computeIfAbsent(v2_top_idx, k -> mesh.addVertex(new Point3D(p2_top.x, p2_top.y, minHeight)));

                int[] wallFace;
                if (raiser_idx!=-1 && raiser_idx!=v1_bottom_idx) {
                    if(!reverseRequired) {
                        wallFace = new int[]{v1_bottom_idx, v2_bottom_idx, v2_top_idx, v1_top_idx, raiser_idx};
                    }else {
                        wallFace = new int[]{raiser_idx, v1_top_idx,v2_top_idx, v2_bottom_idx, v1_bottom_idx};
                    }
                }else {
                    if(!reverseRequired) {
                        wallFace = new int[]{v1_bottom_idx, v2_bottom_idx, v2_top_idx, v1_top_idx};
                    }else{
                        wallFace = new int[]{v1_top_idx, v2_top_idx, v2_bottom_idx,v1_bottom_idx};
                    }
                }
                if (wallFace[0] != wallFace[1] && wallFace[0] != wallFace[2] && wallFace[0] != wallFace[3] &&
                    wallFace[1] != wallFace[2] && wallFace[1] != wallFace[3] &&
                    wallFace[2] != wallFace[3]) {
                    mesh.addWallFace(wallFace);
                }
            }
        }

        // Create bottom faces by projecting horizontal roof faces (treads) down.
        for (int[] roofFace : mesh.getRoofFaces()) {
            boolean isTread = true;
            if (roofFace.length == 0) continue;
            double z = mesh.verts.get(roofFace[0]).z;
            if (z <= minHeight + 1e-6) continue; // Don't create bottom faces for risers on the ground

            for (int i = 1; i < roofFace.length; i++) {
                if (abs(mesh.verts.get(roofFace[i]).z - z) > 1e-6) {
                    isTread = false;
                    break;
                }
            }

            if (isTread) {
                int[] bottomFace = new int[roofFace.length];
                for (int i = 0; i < roofFace.length; i++) {
                    Point3D topVert = mesh.verts.get(roofFace[i]);
                    bottomFace[i] = topToBottomMap.computeIfAbsent(roofFace[i], k -> mesh.addVertex(new Point3D(topVert.x, topVert.y, minHeight)));
                }
                // Reverse winding for bottom face
                int[] reversedBottomFace = new int[bottomFace.length];
                for (int i = 0; i < bottomFace.length; i++) {
                    reversedBottomFace[i] = bottomFace[bottomFace.length - 1 - i];
                }
                mesh.addBottomFace(reversedBottomFace);
            }
        }
    }


    private Point2D calculateSlopeVector(List<Point2D> contour, Double roofDirection) {
        Point2D slopeVector;
        if (roofDirection != null) {
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

        @Override
        public String toString(){
            return "("+point +", "+edgeIndex+")";
        }
    }

    /**
     * Finds intersection points of the contour edges and the current cut, specified via proj
     * @return The list of intersection points, with coordinates and number of the edge
     */
    private List<Intersection> getIntersectionPoints(List<Point2D> contour, Point2D slopeVector, double proj) {
        final double EPSILON = 1e-6;
        List<Intersection> intersections = new ArrayList<>();
        Point2D normal = new Point2D(-slopeVector.y, slopeVector.x);
        int n = contour.size();

        for (int i = 0; i < n; i++) {
            Point2D p1 = contour.get(i);
            Point2D p2 = contour.get((i + 1) % n);

            double a1 = p2.y - p1.y, b1 = p1.x - p2.x, c1 = a1 * p1.x + b1 * p1.y;
            double a2 = slopeVector.x, b2 = slopeVector.y, c2 = proj;
            double det = a1 * b2 - a2 * b1;

            if (abs(det) > EPSILON) {
                //intersection point
                Point2D pi = new Point2D((b2 * c1 - b1 * c2) / det,(a1 * c2 - a2 * c1) / det);
                if (pi.distance(p1) < EPSILON ){
                    //this is vertex exactly, we need no make more wise choice of the edge.
                    //let's choose edge with biggest projection difference.
                    int edge_idx;
                    Point2D p0 = contour.get((i-1+n) % n);
                    double d0 = abs(p0.dot(slopeVector) - p1.dot(slopeVector));
                    double d2 = abs(p2.dot(slopeVector) - p1.dot(slopeVector));
                    if (d0<d2) {
                        edge_idx = (i-1+n) % n;
                    } else{
                        edge_idx = i;
                    }
                    intersections.add(new Intersection(new Point3D(pi, 0), edge_idx)); // Z is set later
                }else if(pi.distance(p2)<EPSILON){
                    int edge_idx;
                    Point2D p3 = contour.get( (i+2) % n );
                    double d1 = abs(p1.dot(slopeVector) - p2.dot(slopeVector));
                    double d3 = abs(p3.dot(slopeVector) - p2.dot(slopeVector));
                    if (d1<d3) {
                        edge_idx = i;
                    } else{
                        edge_idx = (i+2) % n;
                    }
                    intersections.add(new Intersection(new Point3D(pi, 0), edge_idx)); // Z is set later
                } else
                    // Check if the intersection point is within the segment p1-p2 with a small tolerance
                    if (pi.x >= Math.min(p1.x, p2.x) - EPSILON && pi.x <= Math.max(p1.x, p2.x) + EPSILON &&
                            pi.y >= Math.min(p1.y, p2.y) - EPSILON && pi.y <= Math.max(p1.y, p2.y) + EPSILON) {
                        intersections.add(new Intersection(new Point3D(pi, 0), i)); // Z is set later
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