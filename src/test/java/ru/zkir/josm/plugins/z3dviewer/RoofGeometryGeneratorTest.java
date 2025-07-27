package ru.zkir.josm.plugins.z3dviewer;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RoofGeometryGeneratorTest {

    private List<Point2D> createRectangularBase(double width, double depth) {
        List<Point2D> base = new ArrayList<>();
        base.add(new Point2D(-width / 2, -depth / 2));
        base.add(new Point2D(width / 2, -depth / 2));
        base.add(new Point2D(width / 2, depth / 2));
        base.add(new Point2D(-width / 2, depth / 2));
        return base;
    }

    private List<List<Point2D>> createSingleContourList(List<Point2D> contour) {
        List<List<Point2D>> contours = new ArrayList<>();
        contours.add(contour);
        return contours;
    }

    private List<List<Point2D>> createRectangularBaseWithHole(double outerWidth, double outerDepth, double innerWidth, double innerDepth) {
        List<Point2D> outer = new ArrayList<>();
        outer.add(new Point2D(-outerWidth / 2, -outerDepth / 2));
        outer.add(new Point2D(outerWidth / 2, -outerDepth / 2));
        outer.add(new Point2D(outerWidth / 2, outerDepth / 2));
        outer.add(new Point2D(-outerWidth / 2, outerDepth / 2));

        List<Point2D> inner = new ArrayList<>();
        // Inner contour in clockwise order for hole
        inner.add(new Point2D(-innerWidth / 2, -innerDepth / 2));
        inner.add(new Point2D(innerWidth / 2, -innerDepth / 2));
        inner.add(new Point2D(innerWidth / 2, innerDepth / 2));
        inner.add(new Point2D(-innerWidth / 2, innerDepth / 2));

        List<List<Point2D>> contours = new ArrayList<>();
        contours.add(outer);
        contours.add(inner);
        return contours;
    }

    private void assertWatertight(RoofGeometryGenerator.Mesh mesh) {
        Map<String, Integer> edgeCounts = new HashMap<>();
        List<int[]> allFaces = new ArrayList<>();
        allFaces.addAll(mesh.wallFaces);
        allFaces.addAll(mesh.roofFaces);
        allFaces.addAll(mesh.bottomFaces);

        for (int[] face : allFaces) {
            for (int i = 0; i < face.length; i++) {
                int v1 = face[i];
                int v2 = face[(i + 1) % face.length];
                String edge = Math.min(v1, v2) + "-" + Math.max(v1, v2);
                edgeCounts.put(edge, edgeCounts.getOrDefault(edge, 0) + 1);
            }
        }

        for (Map.Entry<String, Integer> entry : edgeCounts.entrySet()) {
            assertEquals(2, (int) entry.getValue(), "Edge " + entry.getKey() + " is not shared by exactly two faces.");
        }
    }

    private void assertNormalsOutward(RoofGeometryGenerator.Mesh mesh) {
        Point3D geometricCenter = calculateGeometricCenter(mesh.verts);
        List<int[]> allFaces = new ArrayList<>();
        allFaces.addAll(mesh.wallFaces);
        allFaces.addAll(mesh.roofFaces);

        for (int[] face : allFaces) {
            Point3D v0 = mesh.verts.get(face[0]);
            Point3D v1 = mesh.verts.get(face[1]);
            Point3D v2 = mesh.verts.get(face[2]);

            Point3D normal = calculateNormal(v0, v1, v2);
            Point3D faceCenter = calculateFaceCenter(face, mesh.verts);
            Point3D toCenter = new Point3D(geometricCenter.x - faceCenter.x, geometricCenter.y - faceCenter.y, geometricCenter.z - faceCenter.z);

            assertTrue(normal.dot(toCenter) < 0, "Normal of a face is pointing inwards.");
        }
    }

    private Point3D calculateGeometricCenter(List<Point3D> vertices) {
        double x = 0, y = 0, z = 0;
        for (Point3D v : vertices) {
            x += v.x;
            y += v.y;
            z += v.z;
        }
        return new Point3D(x / vertices.size(), y / vertices.size(), z / vertices.size());
    }
    
    private Point3D calculateFaceCenter(int[] face, List<Point3D> vertices) {
        double x = 0, y = 0, z = 0;
        for (int index : face) {
            Point3D v = vertices.get(index);
            x += v.x;
            y += v.y;
            z += v.z;
        }
        return new Point3D(x / face.length, y / face.length, z / face.length);
    }

    private Point3D calculateNormal(Point3D p1, Point3D p2, Point3D p3) {
        Point3D u = new Point3D(p2.x - p1.x, p2.y - p1.y, p2.z - p1.z);
        Point3D v = new Point3D(p3.x - p1.x, p3.y - p1.y, p3.z - p1.z);
        return new Point3D(
            u.y * v.z - u.z * v.y,
            u.z * v.x - u.x * v.z,
            u.x * v.y - u.y * v.x
        );
    }

    @Test
    void testPyramidalRoof() {
        List<Point2D> base = createRectangularBase(10, 10);
        RoofGeometryGenerator.Mesh mesh = RoofGeometryGenerator.generateConicalRoof(RoofShapes.PYRAMIDAL, base, 0, 5, 10);
        assertNotNull(mesh);
        assertWatertight(mesh);
        assertNormalsOutward(mesh);
    }

    @Test
    void testGabledRoof() {
        List<Point2D> base = createRectangularBase(10, 20);
        RoofGeometryGenerator.Mesh mesh = RoofGeometryGenerator.generateGabledRoof(base, 0, 5, 10, "along");
        assertNotNull(mesh);
        assertWatertight(mesh);
        assertNormalsOutward(mesh);
    }

    @Test
    void testHippedRoof() {
        List<Point2D> base = createRectangularBase(10, 20);
        RoofGeometryGenerator.Mesh mesh = RoofGeometryGenerator.generateHippedRoof(base, 0, 5, 10, "along");
        assertNotNull(mesh);
        assertWatertight(mesh);
        assertNormalsOutward(mesh);
    }

    @Test
    void testSkillionRoof() {
        List<Point2D> base = createRectangularBase(10, 10);
        RoofGeometryGenerator.Mesh mesh = RoofGeometryGenerator.generateSkillionRoof(base, 0, 5, 10, 45);
        assertNotNull(mesh);
        assertWatertight(mesh);
        assertNormalsOutward(mesh);
    }

    @Test
    void testDomeRoof() {
        List<Point2D> base = createRectangularBase(10, 10);
        RoofGeometryGenerator.Mesh mesh = RoofGeometryGenerator.generateConicalRoof(RoofShapes.DOME, base, 0, 0, 10);
        assertNotNull(mesh);
        assertWatertight(mesh);
        assertNormalsOutward(mesh);
    }

    @Test
    void testOnionRoof() {
        List<Point2D> base = createRectangularBase(10, 10);
        RoofGeometryGenerator.Mesh mesh = RoofGeometryGenerator.generateConicalRoof(RoofShapes.ONION, base, 0, 0, 10);
        assertNotNull(mesh);
        assertWatertight(mesh);
        assertNormalsOutward(mesh);
    }

    @Test
    void testFlatRoof() {
        List<List<Point2D>> base = createSingleContourList(createRectangularBase(10, 10));
        RoofGeometryGenerator.Mesh mesh = RoofGeometryGenerator.generateFlatRoof(base, 0, 5, 10); // height > wallHeight
        assertNotNull(mesh);
        assertWatertight(mesh);
        assertNormalsOutward(mesh);
    }

    @Test
    void testFlatRoofWithHole() {
        List<List<Point2D>> base = createRectangularBaseWithHole(10, 10, 2, 2);
        RoofGeometryGenerator.Mesh mesh = RoofGeometryGenerator.generateFlatRoof(base, 0, 5, 10); // height > wallHeight
        assertNotNull(mesh);
        assertWatertight(mesh);
        assertNormalsOutward(mesh);
    }
}