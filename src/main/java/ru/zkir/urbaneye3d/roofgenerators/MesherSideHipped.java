package ru.zkir.urbaneye3d.roofgenerators;

import ru.zkir.urbaneye3d.RenderableBuildingElement;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;

import java.util.ArrayList;
import java.util.List;

/**
 *  Roof shape which is half of a hipped roof, not the same as half-hipped.
 *  One vertical gabled side, one 'hip' with three sloped faced.
 *  This is common on semi-detached properties where the whole building has a hipped roof,
 *  but each side parts have a side_hipped roof.
 */
public class MesherSideHipped extends RoofGenerator {
    @Override
    public Mesh generate(RenderableBuildingElement building) {
        if (building.getContour().size() <= 4) {
            return generateRectangular(building);
        } else{
            return RoofShapes.HIPPED.getMesher().generate(building);
        }
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

        Point2D mid1 = new Point2D((g1_p0.x + g1_p1.x) / 2, (g1_p0.y + g1_p1.y) / 2);
        Point2D mid2 = new Point2D((g2_p0.x + g2_p1.x) / 2, (g2_p0.y + g2_p1.y) / 2);

        double a = g1_p0.subtract(g2_p1).length();
        double b = g1_p0.subtract(g1_p1).length();

        double ridge_length = a-b/2;
        if (ridge_length<0.1){
            ridge_length = 0.1;
        }
        double c = a - ridge_length;

        Point2D[] shortened_ridge = {mid1.add(mid2.subtract(mid1).normalized().mult(c)), mid2}; //shortenSegment(mid1, mid2,ridge_length/a);

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
