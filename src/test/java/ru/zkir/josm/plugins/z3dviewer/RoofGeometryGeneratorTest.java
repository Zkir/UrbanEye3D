package ru.zkir.josm.plugins.z3dviewer;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.coor.LatLon;
import ru.zkir.josm.plugins.z3dviewer.utils.Mesh;
import ru.zkir.josm.plugins.z3dviewer.utils.Point2D;
import ru.zkir.josm.plugins.z3dviewer.utils.Point3D;
import ru.zkir.josm.plugins.z3dviewer.roofgenerators.RoofShapes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RoofGeometryGeneratorTest {

    private ArrayList<Point2D> createRectangularBase(double width, double depth) {
        ArrayList<Point2D> base = new ArrayList<>();
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

    private RenderableBuildingElement.Contour createRectangularBaseWithHole(double outerWidth, double outerDepth, double innerWidth, double innerDepth) {
        ArrayList<Point2D> outer = new ArrayList<>();
        outer.add(new Point2D(-outerWidth / 2, -outerDepth / 2));
        outer.add(new Point2D(outerWidth / 2, -outerDepth / 2));
        outer.add(new Point2D(outerWidth / 2, outerDepth / 2));
        outer.add(new Point2D(-outerWidth / 2, outerDepth / 2));

        ArrayList<Point2D> inner = new ArrayList<>();
        // Inner contour in clockwise order for hole
        inner.add(new Point2D(-innerWidth / 2, -innerDepth / 2));
        inner.add(new Point2D(innerWidth / 2, -innerDepth / 2));
        inner.add(new Point2D(innerWidth / 2, innerDepth / 2));
        inner.add(new Point2D(-innerWidth / 2, innerDepth / 2));

        RenderableBuildingElement.Contour contour = new RenderableBuildingElement.Contour(outer);
        contour.innerRings.add(inner);

        return contour;
    }
    private RenderableBuildingElement createTestBuilding(ArrayList<Point2D> basePoints, RoofShapes roofShape, double minHeight, double roofHeight, double height) {
        LatLon origin = new LatLon(55,37);
        RenderableBuildingElement.Contour contour = new RenderableBuildingElement.Contour(basePoints);
        RenderableBuildingElement building = new RenderableBuildingElement(origin, contour,  height, minHeight, roofHeight,
                "","", roofShape.toString(), "","" );

        return  building;
    }


    private void assertWatertight(Mesh mesh, String mesherName) {
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
            assertEquals(2, (int) entry.getValue(), "Roof shape " +mesherName+ ": edge " + entry.getKey() + " is not shared by exactly two faces.");
        }
    }

    private void assertNormalsOutward(Mesh mesh, String mesherName) {
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

            assertTrue(normal.dot(toCenter) < 0, "Roof shape " +mesherName+ ": normal of a face is pointing inwards.");
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

    // all defined roof shapes are tested automatically for a typical building.
    // so we should not even worry about extending autotests, they are extended automatically.
    @Test
    void testAllRoofShapesGeneral(){
        ArrayList<Point2D> base = createRectangularBase(25, 10);

        for (RoofShapes roof_shape: RoofShapes.values()){
            RenderableBuildingElement test_building = createTestBuilding(base, roof_shape, 1, 5, 10);
            Mesh mesh = roof_shape.getMesher().generate(test_building);
            assertNotNull(mesh, "Mesh is null for the roof shape " + roof_shape);
            assertWatertight(mesh, roof_shape.toString());
            assertNormalsOutward(mesh, roof_shape.toString());
        }
    }


    //the same test as above, for all known roof shapes, but with wallHeight=0, i.e. no walls (roof only) case.
    /*
    @Test
    void testAllRoofShapesNoWalls(){
        ArrayList<Point2D> base = createRectangularBase(25, 10);
        for (RoofShapes roof_shape: RoofShapes.values()){
            RenderableBuildingElement test_building = createTestBuilding(base, roof_shape, 2, 9, 11);
            Mesh mesh = roof_shape.getMesher().generate(test_building);
            assertNotNull(mesh, "Mesh is null for roof shape "+ roof_shape);
            assertWatertight(mesh, roof_shape.toString());
            assertNormalsOutward(mesh, roof_shape.toString());
        }
    }

     */


    //only SPECIAL cases should be added below.
    // For example, some specific parameter values different from default ones. roof:orientation=across, multipolygons with holes or smth like this.


    @Test
    void testGabledRoofAcross() {
        ArrayList<Point2D> basePoints = createRectangularBase(10, 20);
        LatLon origin = new LatLon(55,37);
        RenderableBuildingElement.Contour contour = new RenderableBuildingElement.Contour(basePoints);

        RenderableBuildingElement building = new RenderableBuildingElement(origin, contour,  10, 0, 4,
                "","", RoofShapes.GABLED.toString(), "","across" );

        Mesh mesh = RoofShapes.GABLED.getMesher().generate(building);

        assertNotNull(mesh,RoofShapes.GABLED.toString());
        assertWatertight(mesh,RoofShapes.GABLED.toString());
        assertNormalsOutward(mesh, RoofShapes.GABLED.toString());
    }


    @Test
    void testHippedRoofAcross() {
        ArrayList<Point2D> basePoints = createRectangularBase(10, 20);

        LatLon origin = new LatLon(55,37);
        RenderableBuildingElement.Contour contour = new RenderableBuildingElement.Contour(basePoints);
        RenderableBuildingElement building = new RenderableBuildingElement(origin, contour,  10, 0, 6,
                "","", RoofShapes.HIPPED.toString(), "","across" );

        Mesh mesh = RoofShapes.HIPPED.getMesher().generate(building);
        assertNotNull(mesh,RoofShapes.HIPPED.toString());
        assertWatertight(mesh, RoofShapes.HIPPED.toString());
        assertNormalsOutward(mesh, RoofShapes.HIPPED.toString());
    }


    // Additional test for skillion roof to test different direction
    @Test
    void testSkillionRoof() {
        ArrayList<Point2D> basePoints = createRectangularBase(14, 10);
        LatLon origin = new LatLon(55,37);
        RenderableBuildingElement.Contour contour = new RenderableBuildingElement.Contour(basePoints);
        RenderableBuildingElement building = new RenderableBuildingElement(origin, contour,  10, 0, 6,
                "","", RoofShapes.SKILLION.toString(), "45","" );

        Mesh mesh = RoofShapes.SKILLION.getMesher().generate(building);

        assertNotNull(mesh, RoofShapes.SKILLION.toString());
        assertWatertight(mesh, RoofShapes.SKILLION.toString());
        assertNormalsOutward(mesh, RoofShapes.SKILLION.toString());
    }


    @Test
    void testFlatRoofWithHole() {
        RenderableBuildingElement.Contour contour = createRectangularBaseWithHole(10, 10, 2, 2);

        LatLon origin = new LatLon(55,37);
        RenderableBuildingElement building = new RenderableBuildingElement(origin, contour,  10, 0, 3,
                "","", RoofShapes.FLAT.toString(), "","" );

        Mesh mesh = RoofShapes.FLAT.getMesher().generate(building); // height > wallHeight
        assertNotNull(mesh, "Mesh is null for roof shape "+ RoofShapes.FLAT);
        assertWatertight(mesh, RoofShapes.FLAT.toString());
        assertNormalsOutward(mesh, RoofShapes.FLAT.toString());
    }


}