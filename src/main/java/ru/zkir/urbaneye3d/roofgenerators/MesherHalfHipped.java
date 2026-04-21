package ru.zkir.urbaneye3d.roofgenerators;

import ru.zkir.urbaneye3d.BuildingRecipe;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;

import java.util.ArrayList;
import java.util.List;

public class MesherHalfHipped extends RoofGenerator {
    @Override
    public Mesh generate(BuildingRecipe building) {

        List<Point2D> basePoints = building.getContour();
        double minHeight = building.minHeight;
        double wallHeight = building.wallHeight;
        double height = building.height;
        double roofHeight = building.roofHeight;

        Mesh mesh = new Mesh(building.bottomColor, building.color, building.roofColor);
        if (basePoints.size() != 4) {
            // Fallback to flat roof for non-quadrilaterals
            return null;
        }

        // 1. Let's find the longest side.
        int longestSideIndex = 0;
        double maxLen = -1;
        for (int i = 0; i < 4; i++) {
            double len = basePoints.get(i).distance(basePoints.get((i + 1) % 4));
            if (len > maxLen) {
                maxLen = len;
                longestSideIndex = i;
            }
        }

        // 2. Determine effective orientation.
        boolean isAcross = "across".equals(building.roofOrientation);
        if (building.roofOrientation.isEmpty() && building.roofDirection != null && !Double.isNaN(building.roofDirection)) {
            Point2D vLong = basePoints.get(longestSideIndex).subtract(basePoints.get((longestSideIndex + 1) % 4)).normalized();
            Point2D vShort = basePoints.get((longestSideIndex + 1) % 4).subtract(basePoints.get((longestSideIndex + 2) % 4)).normalized();
            double d = Math.toRadians(building.roofDirection);
            Point2D dirAcrossRidge = new Point2D(Math.sin(d), Math.cos(d));
            if (Math.abs(dirAcrossRidge.dot(vLong)) > Math.abs(dirAcrossRidge.dot(vShort))) {
                isAcross = true;
            }
        }
        if (isAcross) {
            longestSideIndex = (longestSideIndex + 1) % 4;
        }

        // 3. Reorder vertices : AB and CD are parallel to the ridge, BC и DA - are gabled ends
        List<Point2D> p = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            p.add(basePoints.get((longestSideIndex + i) % 4));
        }
        Point2D A = p.get(0);
        Point2D B = p.get(1);
        Point2D C = p.get(2);
        Point2D D = p.get(3);

        // 4. Calculate ridge parameters
        Point2D midBC = B.add(C).mult(0.5);
        Point2D midDA = D.add(A).mult(0.5);
        double a = A.distance(B);
        double b = B.distance(C);
        double ridgeLen = Math.max(0.1, a - b / 2);
        if (isAcross) {
            ridgeLen = Math.max(ridgeLen, a / 3);
        }
        Point2D[] ridge = shortenSegment(midBC, midDA, ridgeLen / a);

        // 5. Create vertices
        int baseIdx = 0;
        for (Point2D pt : p) {
            mesh.verts.add(new Point3D(pt.x, pt.y, minHeight));
        }

        int wallIdx = baseIdx;
        if (wallHeight > minHeight) {
            wallIdx = mesh.verts.size();
            for (Point2D pt : p) {
                mesh.verts.add(new Point3D(pt.x, pt.y, wallHeight));
            }
        }

        int r1 = mesh.verts.size();
        mesh.verts.add(new Point3D(ridge[0].x, ridge[0].y, height));
        int r2 = mesh.verts.size();
        mesh.verts.add(new Point3D(ridge[1].x, ridge[1].y, height));

        double zMid = wallHeight + roofHeight / 2;
        int mB1 = mesh.verts.size();
        mesh.verts.add(new Point3D(B.add(midBC).mult(0.5).x, B.add(midBC).mult(0.5).y, zMid));
        int mC1 = mesh.verts.size();
        mesh.verts.add(new Point3D(C.add(midBC).mult(0.5).x, C.add(midBC).mult(0.5).y, zMid));
        int mD2 = mesh.verts.size();
        mesh.verts.add(new Point3D(D.add(midDA).mult(0.5).x, D.add(midDA).mult(0.5).y, zMid));
        int mA2 = mesh.verts.size();
        mesh.verts.add(new Point3D(A.add(midDA).mult(0.5).x, A.add(midDA).mult(0.5).y, zMid));

        // 6. Create faces
        // Create Walls only if they have height
        if (wallHeight > minHeight) {
            // Create walls (Quads)
            mesh.addWallFace(new int[]{baseIdx + 0, baseIdx + 1, wallIdx + 1, wallIdx + 0});
            mesh.addWallFace(new int[]{baseIdx + 1, baseIdx + 2, wallIdx + 2, wallIdx + 1});
            mesh.addWallFace(new int[]{baseIdx + 2, baseIdx + 3, wallIdx + 3, wallIdx + 2});
            mesh.addWallFace(new int[]{baseIdx + 3, baseIdx + 0, wallIdx + 0, wallIdx + 3});
        }

        // And one more pair of walls -- trapezoids. those walls are above z1=wallHeight, so they are created always.
        mesh.addWallFace(new int[]{wallIdx + 1, wallIdx + 2, mC1, mB1}); // BC "gabled" side
        mesh.addWallFace(new int[]{wallIdx + 3, wallIdx + 0, mA2, mD2}); // DA "gabled" side

        //Create Roof Planes (Triangles)
        mesh.addRoofFace(new int[]{mC1, r1, mB1});
        mesh.addRoofFace(new int[]{mA2, r2, mD2});

        // Create Roof Planes (Hexagons!)
        mesh.addRoofFace(new int[]{wallIdx + 0, wallIdx + 1, mB1, r1, r2, mA2}); // plane AB
        mesh.addRoofFace(new int[]{wallIdx + 2, wallIdx + 3, mD2, r2, r1, mC1}); // plane CD

        // Create bottom face
        mesh.addBottomFace(new int[]{baseIdx + 0, baseIdx + 3, baseIdx + 2, baseIdx + 1});
        return mesh;
    }
}
