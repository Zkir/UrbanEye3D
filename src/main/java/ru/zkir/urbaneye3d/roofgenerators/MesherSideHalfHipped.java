package ru.zkir.urbaneye3d.roofgenerators;

import ru.zkir.urbaneye3d.BuildingRecipe;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;

import java.util.List;

/**
 * Roof shape which is half of a roof:shape=half-hipped roof,
 * not the same as roof:shape=half-hipped nor roof:shape=side_hipped.
 * One vertical gabled side, one 'hip' with three sloped faced.
 * This is common on semi-detached properties where the whole building has a roof:shape=half-hipped roof,
 * but each house has a side_half-hipped roof.
 */
public class MesherSideHalfHipped extends RoofGenerator {
    @Override
    public Mesh generate(BuildingRecipe building) {
        if (building.getContour().size() <= 4) {
            return generateRectangular(building);
        } else{
            return RoofShapes.HIPPED.getMesher().generate(building);
        }
    }

    private Mesh generateRectangular(BuildingRecipe building) {
        List<Point2D> basePoints = building.getContour();
        double minHeight = building.minHeight;
        double wallHeight = building.wallHeight;
        double height = building.height;
        double roofHeight = building.roofHeight;
        String roofOrientation = building.roofOrientation;

        Mesh mesh = new Mesh(building.bottomColor, building.color, building.roofColor);
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

        //here we need to interpret direction
        //if direction is specified, we need to determine what our mid1 and mid2 points are
        if ( building.roofDirection!=null && !Double.isNaN(building.roofDirection)){
            double d = Math.toRadians(building.roofDirection);
            var direction_vec = new Point2D(Math.sin(d), Math.cos(d));
            gableEdgeIndices = findEdgeIndicesForRidge(basePoints, direction_vec);
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

        Point2D mid1 = g1_p0.add(g1_p1).mult(0.5);
        Point2D mid1A = g1_p0.add(mid1).mult(0.5);
        Point2D mid1B = g1_p1.add(mid1).mult(0.5);
        Point2D mid2 = g2_p0.add(g2_p1).mult(0.5);

        double a = g1_p0.subtract(g2_p1).length();
        double b = g1_p0.subtract(g1_p1).length();

        // For half-hipped, setback is b/4 instead of b/2.
        double c = b / 4.0;
        if (c > a - 0.1) {
            c = a - 0.1;
        }

        // ridge1 is set back from mid1 by distance c. ridge2 is mid2.
        Point2D[] shortened_ridge = {mid1.add(mid2.subtract(mid1).normalized().mult(c)), mid2};

        int ridge1Idx = mesh.verts.size();
        mesh.verts.add(new Point3D(shortened_ridge[0].x, shortened_ridge[0].y, height));
        int ridge2Idx = mesh.verts.size();
        mesh.verts.add(new Point3D(shortened_ridge[1].x, shortened_ridge[1].y, height));

        int mid1A_idx = mesh.verts.size();
        mesh.verts.add(new Point3D(mid1A.x, mid1A.y, wallHeight + roofHeight / 2));

        int mid1B_idx = mesh.verts.size();
        mesh.verts.add(new Point3D(mid1B.x, mid1B.y, wallHeight + roofHeight / 2));

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

        // Side 1 (half-hipped) trapezoid wall:
        mesh.addWallFace(ra(new int[]{ wallIdx + g1_idx1, wallIdx + g1_idx0, mid1A_idx, mid1B_idx}));

        // Side 2 (vertical gable) triangle wall:
        mesh.addWallFace(ra(new int[]{ wallIdx + g2_idx1, wallIdx + g2_idx0, ridge2Idx}));

        // Side 1 (half-hipped) roof face (triangle):
        mesh.addRoofFace(new int[]{ mid1B_idx, ridge1Idx, mid1A_idx});

        // Eaves roof faces (pentagons):
        mesh.addRoofFace(new int[]{wallIdx + eave1_idx0, wallIdx + eave1_idx1, ridge2Idx, ridge1Idx, mid1B_idx});
        mesh.addRoofFace(new int[]{wallIdx + eave2_idx0, wallIdx + eave2_idx1, mid1A_idx, ridge1Idx, ridge2Idx});

        int[] bottomFaceRect = new int[n];
        for (int i = 0; i < n; i++) {
            bottomFaceRect[i] = baseIdx + n - 1 - i;
        }
        mesh.addBottomFace(bottomFaceRect);

        return mesh;
    }

    private int[] findEdgeIndicesForRidge(List<Point2D> basePoints, Point2D direction) {
        int index=-1;
        double min_x =Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            Point2D p1 = basePoints.get(i);
            Point2D p2 = basePoints.get((i + 1) % 4);
            var edge_vec= p2.subtract(p1);
            double x = edge_vec.normalized().cross(direction);
            //bet: let's try min cross_product
            if (x<min_x){
               min_x=x;
               index=i;
            }
        }
        return new int[]{index,(index+2)%4};
    }


}
