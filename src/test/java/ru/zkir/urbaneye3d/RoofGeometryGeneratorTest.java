package ru.zkir.urbaneye3d;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.coor.LatLon;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;
import ru.zkir.urbaneye3d.roofgenerators.RoofShapes;

import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.SimplePrimitiveId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openstreetmap.josm.spi.preferences.Config;

import static org.junit.jupiter.api.Assertions.*;

class RoofGeometryGeneratorTest {

    static {
        Config.setPreferencesInstance(new org.openstreetmap.josm.data.Preferences());
    }

    private ArrayList<Point2D> createRectangularBase(double width, double depth) {
        ArrayList<Point2D> base = new ArrayList<>();
        base.add(new Point2D(-width / 2, -depth / 2));
        base.add(new Point2D(width / 2, -depth / 2));
        base.add(new Point2D(width / 2, depth / 2));
        base.add(new Point2D(-width / 2, depth / 2));
        return base;
    }

    private ArrayList<Point2D> createPentagonalBase() {
        ArrayList<Point2D> base = new ArrayList<>();
        base.add(new Point2D(120.70860290527344, -225.74282836914062));
        base.add(new Point2D(64.7934341430664, 38.12529373168945));
        base.add(new Point2D(129.06674194335938, 323.88177490234375));
        base.add(new Point2D(-43.17829132080078, 325.7801513671875));
        base.add(new Point2D(-51.55766296386719, -223.84390258789062));
        return base;
    }

    private Contour createRectangularBaseWithHole(double outerWidth, double outerDepth, double innerWidth, double innerDepth) {
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

        Contour contour = new Contour(outer);
        contour.innerRings.add(inner);

        return contour;
    }
    private RenderableBuildingElement createTestBuilding(ArrayList<Point2D> basePoints, RoofShapes roofShape, double minHeight, double roofHeight, double height) {
        LatLon origin = new LatLon(55,37);
        Contour contour = new Contour(basePoints);
        return new RenderableBuildingElement(new SimplePrimitiveId(-1, OsmPrimitiveType.WAY), origin, contour,  height, minHeight, roofHeight,
                "", "", roofShape.toString(), "", "" );
    }

    private void assertNoZeroLengthEdges(Mesh mesh, String mesherName) {
        // A small tolerance for floating point comparisons
        final double Epsilon = 1e-6;
        List<Point3D> vertices = mesh.verts;
        for (int i = 0; i < vertices.size(); i++) {
            for (int j = i + 1; j < vertices.size(); j++) {
                assertTrue(vertices.get(i).distance(vertices.get(j)) > Epsilon,
                        "Roof shape " + mesherName + ": Vertices " + i + " and " + j + " are too close, effectively a zero-length edge.");
            }
        }
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

    private void assertNormalsAndConsistency(Mesh mesh, String mesherName) {
        // This map stores the first vertex of an edge traversal for the first face that uses it.
        Map<String, Integer> edgeTraversal = new HashMap<>();
        List<int[]> allFaces = new ArrayList<>();
        allFaces.addAll(mesh.wallFaces);
        allFaces.addAll(mesh.roofFaces);
        allFaces.addAll(mesh.bottomFaces);

        for (int[] face : allFaces) {
            for (int i = 0; i < face.length; i++) {
                int v1 = face[i];
                int v2 = face[(i + 1) % face.length];
                String edgeKey = Math.min(v1, v2) + "-" + Math.max(v1, v2);

                if (edgeTraversal.containsKey(edgeKey)) {
                    // This is the second face sharing the edge. Check for opposite traversal.
                    int firstFaceV1 = edgeTraversal.get(edgeKey);
                    // If the first face traversed v1 -> v2, this face must traverse v2 -> v1.
                    // So, the stored v1 should be the current face's v2.
                    assertEquals(firstFaceV1, v2, "Roof shape " + mesherName + ": Inconsistent winding for edge " + edgeKey + ". Normals are not consistent.");
                } else {
                    // First time seeing this edge, store its traversal direction.
                    edgeTraversal.put(edgeKey, v1);
                }
            }
        }

        // After confirming consistency, check the absolute orientation of one face.
        // A bottom face normal must point down (negative Z).
        assertTrue(mesh.bottomFaces.size() > 0, "Roof shape " + mesherName + ": Mesh has no bottom faces to check for orientation.");

        int[] anyBottomFace = mesh.bottomFaces.get(0);
        Point3D v0 = mesh.verts.get(anyBottomFace[0]);
        Point3D v1 = mesh.verts.get(anyBottomFace[1]);
        Point3D v2 = mesh.verts.get(anyBottomFace[2]);
        Point3D normal = calculateNormal(v0, v1, v2);

        assertTrue(normal.z < 0, "Roof shape " + mesherName + ": Bottom face normal is not pointing downwards. Overall mesh orientation is likely incorrect.");
    }

    private Point3D calculateNormal(Point3D p1, Point3D p2, Point3D p3) {
        Point3D u = new Point3D(p2.x - p1.x, p2.y - p1.y, p2.z - p1.z);
        Point3D v = new Point3D(p3.x - p1.x, p3.y - p1.y, p3.z - p1.z);
        return new Point3D(
            u.y * v.z - u.z * v.y,
            u.z * v.x - u.x * v.z,
            u.x * v.y - u.y * v.x
        ).normalize();
    }

    private void assertHeightConstraints(Mesh mesh, double minHeight, double height, String mesherName) {
        assertFalse(mesh.verts.isEmpty(), "Mesh has no vertices for " + mesherName);

        double minZ = Double.MAX_VALUE;
        double maxZ = Double.MIN_VALUE;

        for (Point3D vert : mesh.verts) {
            if (vert.z < minZ) minZ = vert.z;
            if (vert.z > maxZ) maxZ = vert.z;
        }
        assertEquals(minHeight, minZ, 0.001, "Roof shape " + mesherName + ": Minimum Z does not match minHeight.");
        assertEquals(height, maxZ, 0.001, "Roof shape " + mesherName + ": Maximum Z does not match height.");
    }

    void AssertMeshTopology(Mesh mesh, double minHeight, double height, String roofShape){
        assertNotNull(mesh, "Mesh is null for the roof shape " + roofShape);
        assertHeightConstraints(mesh,  minHeight, height, roofShape);
        assertNoZeroLengthEdges(mesh, roofShape);
        assertWatertight(mesh, roofShape);
        assertNormalsAndConsistency(mesh, roofShape);
    }

    // all defined roof shapes are tested automatically for a typical building.
    // so we should not even worry about extending autotests, they are extended automatically.
    @Test
    void testAllRoofShapesGeneral(){
        ArrayList<Point2D> base = createRectangularBase(25, 10);
        for (RoofShapes roof_shape: RoofShapes.values()){
            RenderableBuildingElement test_building = createTestBuilding(base, roof_shape, 0, 15, 40);
            Mesh mesh = roof_shape.getMesher().generate(test_building);
            AssertMeshTopology(mesh, test_building.minHeight, test_building.height,  roof_shape.toString());
        }
    }

    //the same test as above, for all known roof shapes, but with wallHeight=0, i.e. no walls (roof only) case.
    @Test
    void testAllRoofShapesNoWalls(){
        ArrayList<Point2D> base = createRectangularBase(25, 10);
        for (RoofShapes roof_shape: RoofShapes.values()){
            RenderableBuildingElement test_building = createTestBuilding(base, roof_shape, 2, 9, 11);
            Mesh mesh = roof_shape.getMesher().generate(test_building);
            AssertMeshTopology(mesh, test_building.minHeight, test_building.height, roof_shape.toString());
        }
    }

    //only SPECIAL cases should be added below.
    // For example, some specific parameter values different from default ones. roof:orientation=across, multipolygons with holes or smth like this.
    @Test
    void testGabledRoofAcross() {
        ArrayList<Point2D> basePoints = createRectangularBase(10, 20);
        LatLon origin = new LatLon(55,37);
        Contour contour = new Contour(basePoints);

        RenderableBuildingElement test_building = new RenderableBuildingElement(new SimplePrimitiveId(-1, OsmPrimitiveType.WAY), origin, contour,  10, 0, 4,
                "", "", RoofShapes.GABLED.toString(), "", "across" );

        Mesh mesh = RoofShapes.GABLED.getMesher().generate(test_building);
        AssertMeshTopology(mesh, test_building.minHeight, test_building.height, RoofShapes.GABLED.toString());
    }


    @Test
    void testHippedRoofAcross() {
        ArrayList<Point2D> basePoints = createRectangularBase(10, 20);
        LatLon origin = new LatLon(55,37);
        Contour contour = new Contour(basePoints);
        RenderableBuildingElement test_building = new RenderableBuildingElement(new SimplePrimitiveId(-1, OsmPrimitiveType.WAY), origin, contour,  10, 0, 6,
                "", "", RoofShapes.HIPPED.toString(), "", "across" );

        Mesh mesh = RoofShapes.HIPPED.getMesher().generate(test_building);

        AssertMeshTopology(mesh, test_building.minHeight, test_building.height, RoofShapes.HIPPED.toString());
    }


    // Additional test for skillion roof to test different direction
    @Test
    void testSkillionRoof() {
        ArrayList<Point2D> basePoints = createRectangularBase(14, 10);
        LatLon origin = new LatLon(55,37);
        Contour contour = new Contour(basePoints);
        RenderableBuildingElement test_building = new RenderableBuildingElement(new SimplePrimitiveId(-1, OsmPrimitiveType.WAY), origin, contour,  10, 0, 6,
                "", "", RoofShapes.SKILLION.toString(), "45", "" );

        Mesh mesh = RoofShapes.SKILLION.getMesher().generate(test_building);

        AssertMeshTopology(mesh, test_building.minHeight, test_building.height, RoofShapes.SKILLION.toString());
    }

    @Test
    void testFlatRoofWithHole() {
        Contour contour = createRectangularBaseWithHole(10, 10, 2, 2);
        LatLon origin = new LatLon(55,37);
        RenderableBuildingElement test_building = new RenderableBuildingElement(new SimplePrimitiveId(-1, OsmPrimitiveType.WAY), origin, contour,  10, 0, 3,
                "", "", RoofShapes.FLAT.toString(), "", "" );

        Mesh mesh = RoofShapes.FLAT.getMesher().generate(test_building); 

        //common set of topology checks for a mesh.
        AssertMeshTopology(mesh, test_building.minHeight, test_building.height, RoofShapes.FLAT.toString());
    }

    @Test
    void testSkillionRoofWithHole() {
        Contour contour = createRectangularBaseWithHole(12, 12, 4, 4);
        LatLon origin = new LatLon(55, 37);
        RenderableBuildingElement test_building = new RenderableBuildingElement(new SimplePrimitiveId(-1, OsmPrimitiveType.WAY), origin, contour, 10, 0, 5,
                "", "", RoofShapes.SKILLION.toString(), "30", "");

        Mesh mesh = RoofShapes.SKILLION.getMesher().generate(test_building);
        AssertMeshTopology(mesh, test_building.minHeight, test_building.height, RoofShapes.SKILLION.toString() + " with hole");
    }


    @Test
    //to do: test all the roofs with pentagonal base
    void testRoundRoofNonRectangular() {
        ArrayList<Point2D> base = createPentagonalBase();

        RenderableBuildingElement test_building = createTestBuilding(base,  RoofShapes.ROUND, 0, 5, 10);

        Mesh mesh = RoofShapes.ROUND.getMesher().generate(test_building);

        // Common set of topology checks for a mesh.
        AssertMeshTopology(mesh, test_building.minHeight, test_building.height, RoofShapes.ROUND.toString() + ", pentagonal base");
    }

    @Test
    //to do: test all the roofs with pentagonal base
    void testRoundRoofNonRectangularNoWalls() {
        ArrayList<Point2D> base = createPentagonalBase();

        RenderableBuildingElement test_building = createTestBuilding(base,  RoofShapes.ROUND, 0, 10, 10);

        Mesh mesh = RoofShapes.ROUND.getMesher().generate(test_building);

        // Common set of topology checks for a mesh.
        AssertMeshTopology(mesh, test_building.minHeight, test_building.height, RoofShapes.ROUND.toString() + ", pentagonal base");
    }

    /* temporary disabled. DO NOT REMOVE!
    @Test
    void testGabledRoof_GoldenMaster() {
        ArrayList<Point2D> base = createRectangularBase(20, 10);
        RenderableBuildingElement test_building = createTestBuilding(base, RoofShapes.GABLED, 0, 5, 10);
        Mesh mesh = RoofShapes.GABLED.getMesher().generate(test_building);
        String result = ru.zkir.urbaneye3d.utils.ObjExporter.meshToString(mesh);
        String expected = "# Blender-compatible OBJ\n" +
                "mtllib default.mtl\n\n" +
                "v -10.000000 0.000000 -5.000000\n" +
                "v 10.000000 0.000000 -5.000000\n" +
                "v 10.000000 0.000000 5.000000\n" +
                "v -10.000000 0.000000 5.000000\n" +
                "v -10.000000 5.000000 -5.000000\n" +
                "v 10.000000 5.000000 -5.000000\n" +
                "v 10.000000 5.000000 5.000000\n" +
                "v -10.000000 5.000000 5.000000\n" +
                "v 10.000000 10.000000 0.000000\n" +
                "v -10.000000 10.000000 0.000000\n" +
                "\ng object_default\n" +
                "usemtl default\n" +
                "\n# Roof\n" +
                "f 7 8 10 9\n" +
                "f 5 6 9 10\n" +
                "\n# Walls \n" +
                "f 3 4 8 7\n" +
                "f 1 2 6 5\n" +
                "f 2 3 7 9 6\n" +
                "f 4 1 5 10 8\n" +
                "\n# Base\n" +
                "f 4 3 2 1\n";

        assertEquals(expected.trim().replaceAll("\\s+", " "), result.trim().replaceAll("\\s+", " "));
    }
    */

    @Test
    void testRoundRoof_GoldenMaster() {
        ArrayList<Point2D> base = createRectangularBase(20, 10);
        RenderableBuildingElement test_building = createTestBuilding(base, RoofShapes.ROUND, 0, 5, 10);
        Mesh mesh = RoofShapes.ROUND.getMesher().generate(test_building);
        String result = ru.zkir.urbaneye3d.utils.ObjExporter.meshToString(mesh);
        String expected = "# Blender-compatible OBJ\n" +
                "mtllib default.mtl\n\n" +
                "v -10.000000 0.000000 5.000000\n" +
                "v 10.000000 0.000000 5.000000\n" +
                "v 10.000000 0.000000 -5.000000\n" +
                "v -10.000000 0.000000 -5.000000\n" +
                "v 10.000000 5.000000 5.000000\n" +
                "v -10.000000 5.000000 5.000000\n" +
                "v -10.000000 5.000000 -5.000000\n" +
                "v -10.000000 5.975000 -4.900000\n" +
                "v -10.000000 6.915000 -4.620000\n" +
                "v -10.000000 7.780000 -4.160000\n" +
                "v -10.000000 8.535000 -3.540000\n" +
                "v -10.000000 9.155000 -2.780000\n" +
                "v -10.000000 9.620000 -1.910000\n" +
                "v -10.000000 9.905000 -0.980000\n" +
                "v -10.000000 10.000000 0.000000\n" +
                "v -10.000000 9.905000 0.980000\n" +
                "v -10.000000 9.620000 1.910000\n" +
                "v -10.000000 9.155000 2.780000\n" +
                "v -10.000000 8.535000 3.540000\n" +
                "v -10.000000 7.780000 4.160000\n" +
                "v -10.000000 6.915000 4.620000\n" +
                "v -10.000000 5.975000 4.900000\n" +
                "v 10.000000 5.000000 -5.000000\n" +
                "v 10.000000 5.975000 4.900000\n" +
                "v 10.000000 6.915000 4.620000\n" +
                "v 10.000000 7.780000 4.160000\n" +
                "v 10.000000 8.535000 3.540000\n" +
                "v 10.000000 9.155000 2.780000\n" +
                "v 10.000000 9.620000 1.910000\n" +
                "v 10.000000 9.905000 0.980000\n" +
                "v 10.000000 10.000000 0.000000\n" +
                "v 10.000000 9.905000 -0.980000\n" +
                "v 10.000000 9.620000 -1.910000\n" +
                "v 10.000000 9.155000 -2.780000\n" +
                "v 10.000000 8.535000 -3.540000\n" +
                "v 10.000000 7.780000 -4.160000\n" +
                "v 10.000000 6.915000 -4.620000\n" +
                "v 10.000000 5.975000 -4.900000\n" +
                "\ng object_default\n" +
                "usemtl default\n" +
                "\n# Roof\n" +
                "f 24 5 6 22\n" +
                "f 22 21 25 24\n" +
                "f 21 20 26 25\n" +
                "f 20 19 27 26\n" +
                "f 19 18 28 27\n" +
                "f 18 17 29 28\n" +
                "f 17 16 30 29\n" +
                "f 16 15 31 30\n" +
                "f 15 14 32 31\n" +
                "f 14 13 33 32\n" +
                "f 13 12 34 33\n" +
                "f 12 11 35 34\n" +
                "f 11 10 36 35\n" +
                "f 10 9 37 36\n" +
                "f 9 8 38 37\n" +
                "f 8 7 23 38\n" +
                "\n# Walls \n" +
                "f 2 1 6 5\n" +
                "f 1 4 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 6\n" +
                "f 4 3 23 7\n" +
                "f 3 2 5 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 23\n" +
                "\n# Base\n" +
                "f 1 2 3 4\n";

        assertEquals(expected.trim().replaceAll("\\s+", " "), result.trim().replaceAll("\\s+", " "));
    }

}