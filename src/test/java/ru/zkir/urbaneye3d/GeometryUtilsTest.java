package ru.zkir.urbaneye3d.utils;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class GeometryUtilsTest {

    @Test
    public void testRayPolygonIntersectionTriangle() {
        // A simple triangle in XY plane
        List<Point3D> verts = Arrays.asList(
            new Point3D(0, 0, 0),
            new Point3D(1, 0, 0),
            new Point3D(0, 1, 0)
        );
        int[] indices = {0, 1, 2};

        GeometryUtils.Ray ray = new GeometryUtils.Ray(new Point3D(0.2, 0.2, 1), new Point3D(0, 0, -1));
        double dist = GeometryUtils.intersectRayPolygon(ray, verts, indices);
        assertFalse(Double.isNaN(dist), "Ray should intersect triangle");
        assertEquals(1.0, dist, 1e-6);

        GeometryUtils.Ray rayMiss = new GeometryUtils.Ray(new Point3D(0.8, 0.8, 1), new Point3D(0, 0, -1));
        dist = GeometryUtils.intersectRayPolygon(rayMiss, verts, indices);
        assertTrue(Double.isNaN(dist), "Ray should miss triangle");
    }

    @Test
    public void testRayPolygonIntersectionNonConvex() {
        // An L-shaped polygon (non-convex) in XY plane
        // (0,0) - (2,0) - (2,1) - (1,1) - (1,2) - (0,2)
        List<Point3D> verts = Arrays.asList(
            new Point3D(0, 0, 0),
            new Point3D(2, 0, 0),
            new Point3D(2, 1, 0),
            new Point3D(1, 1, 0),
            new Point3D(1, 2, 0),
            new Point3D(0, 2, 0)
        );
        int[] indices = {0, 1, 2, 3, 4, 5};

        // Hit one of the arms
        GeometryUtils.Ray rayHit = new GeometryUtils.Ray(new Point3D(1.5, 0.5, 1), new Point3D(0, 0, -1));
        double dist = GeometryUtils.intersectRayPolygon(rayHit, verts, indices);
        assertFalse(Double.isNaN(dist), "Ray should hit the L-shape arm");

        // Miss in the "empty corner" where a triangle fan would falsely report a hit
        // In an L-shape (0,0)-(2,0)-(2,1)-(1,1)-(1,2)-(0,2), the point (1.5, 1.5) is in the empty corner.
        GeometryUtils.Ray rayEmptyCorner = new GeometryUtils.Ray(new Point3D(1.5, 1.5, 1), new Point3D(0, 0, -1));
        dist = GeometryUtils.intersectRayPolygon(rayEmptyCorner, verts, indices);
        assertTrue(Double.isNaN(dist), "Ray should miss the empty corner of the L-shape");
    }

    @Test
    public void testRayAABBIntersection() {
        Point3D min = new Point3D(0, 0, 0);
        Point3D max = new Point3D(1, 1, 1);

        GeometryUtils.Ray ray = new GeometryUtils.Ray(new Point3D(0.5, 0.5, 2), new Point3D(0, 0, -1));
        assertTrue(GeometryUtils.intersectRayAABB(ray, min, max), "Ray should intersect AABB");

        GeometryUtils.Ray rayMiss = new GeometryUtils.Ray(new Point3D(2, 2, 2), new Point3D(1, 1, 1));
        assertFalse(GeometryUtils.intersectRayAABB(rayMiss, min, max), "Ray should miss AABB");
    }
}
