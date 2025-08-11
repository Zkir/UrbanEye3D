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

class RoofGeneratorTopologyTest {

    static {
        Config.setPreferencesInstance(new org.openstreetmap.josm.data.Preferences());
    }

    public static ArrayList<Point2D> createRectangularBase(double width, double depth) {
        ArrayList<Point2D> base = new ArrayList<>();
        base.add(new Point2D(-width / 2, -depth / 2));
        base.add(new Point2D(width / 2, -depth / 2));
        base.add(new Point2D(width / 2, depth / 2));
        base.add(new Point2D(-width / 2, depth / 2));
        return base;
    }

    public static ArrayList<Point2D> createPentagonalBase() {
        ArrayList<Point2D> base = new ArrayList<>();
        base.add(new Point2D(120, -225));
        base.add(new Point2D(64, 38));
        base.add(new Point2D(129, 323));
        base.add(new Point2D(-43, 325));
        base.add(new Point2D(-51, -223));
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
    public static RenderableBuildingElement createTestBuilding(ArrayList<Point2D> basePoints, RoofShapes roofShape, double minHeight, double roofHeight, double height) {
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

    @Test
    void testAllRoofShapesNonRectangular() {
        ArrayList<Point2D> base = createPentagonalBase();
        for (RoofShapes roof_shape: RoofShapes.values()) {
            //some roofs still do not support arbitrary base
            if (roof_shape == RoofShapes.HIPPED || roof_shape == RoofShapes.HALF_HIPPED ||
                   roof_shape == RoofShapes.MANSARD|| roof_shape == RoofShapes.CROSS_GABLED ){
                continue;
            }
            //if (roof_shape == RoofShapes.SALTBOX) continue;
            RenderableBuildingElement test_building = createTestBuilding(base, roof_shape, 0, 5, 10);
            Mesh mesh = roof_shape.getMesher().generate(test_building);
            AssertMeshTopology(mesh, test_building.minHeight, test_building.height, roof_shape + ", pentagonal base");
        }
    }

    @Test
    void testAllRoofShapesNonRectangularNoWalls() {
        ArrayList<Point2D> base = createPentagonalBase();
        for (RoofShapes roof_shape: RoofShapes.values()) {
            //some roofs still do not support arbitrary base
            if (roof_shape == RoofShapes.HIPPED || roof_shape == RoofShapes.HALF_HIPPED ||
                    roof_shape == RoofShapes.MANSARD|| roof_shape == RoofShapes.CROSS_GABLED ){
                continue;
            }
            RenderableBuildingElement test_building = createTestBuilding(base, roof_shape, 0, 10, 10);
            Mesh mesh = roof_shape.getMesher().generate(test_building);
            AssertMeshTopology(mesh, test_building.minHeight, test_building.height, roof_shape + ", pentagonal base");
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

}