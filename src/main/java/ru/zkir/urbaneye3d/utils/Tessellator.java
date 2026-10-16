package ru.zkir.urbaneye3d.utils;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.triangulate.polygon.PolygonTriangulator;
import org.locationtech.jts.triangulate.polygon.ConstrainedDelaunayTriangulator;

import java.util.ArrayList;
import java.util.List;

/**
 * A utility class to perform polygon triangulation (tessellation) using JTS.
 * This replaces the JOGL GLUtessellator which is not available on Android.
 */
public class Tessellator {
    private final List<Point2D> outerRing;
    private final List<List<Point2D>> innerRings;
    private final List<Point2D> allPoints = new ArrayList<>();

    /**
     * Initialize triangulation input data.
     * @param outerRing The list of points defining the outer boundary.
     * @param innerRings A list of lists of points defining the holes.
     */
    public Tessellator(List<Point2D> outerRing, List<List<Point2D>> innerRings) {
        this.outerRing=outerRing;
        this.innerRings=innerRings;

        this.allPoints.addAll(outerRing);
        for(var innerRing:innerRings){
            this.allPoints.addAll(innerRing);
        }
    }

    /**
     *  Triangulates a polygon prepared via constructor call.
     *  @return A list of integer arrays, each representing a triangle (indices into the combined vertex list).
     * */
    public List<int[]> triangulate() {
        return triangulate(outerRing, innerRings);
    }

    /**
     * Triangulates a polygon with optional holes.
     *
     * @param outerRing The list of points defining the outer boundary.
     * @param innerRings A list of lists of points defining the holes.
     * @return A list of integer arrays, each representing a triangle (indices into the combined vertex list).
     */
    public static List<int[]> triangulate(List<Point2D> outerRing, List<List<Point2D>> innerRings) {
        return triangulate(outerRing, innerRings, "unknown polygon");
    }

    public static List<int[]> triangulate(List<Point2D> outerRing, List<List<Point2D>> innerRings, String debugLabel) {
        GeometryFactory factory = new GeometryFactory();

        Coordinate[] outerCoords = toCoordinates(outerRing, true);
        LinearRing shell = factory.createLinearRing(outerCoords);

        LinearRing[] holes = null;
        if (innerRings != null && !innerRings.isEmpty()) {
            holes = new LinearRing[innerRings.size()];
            for (int i = 0; i < innerRings.size(); i++) {
                holes[i] = factory.createLinearRing(toCoordinates(innerRings.get(i), true));
            }
        }

        Polygon polygon = factory.createPolygon(shell, holes);
        Geometry triangulation;
        try {
            triangulation = ConstrainedDelaunayTriangulator.triangulate(polygon);
            //triangulation = PolygonTriangulator.triangulate(polygon);

        } catch (Exception e) {
            try {
                // If standard triangulation fails (e.g. self-intersecting polygon), 
                // try to fix the geometry and triangulate again.
                Geometry fixed = org.locationtech.jts.geom.util.GeometryFixer.fix(polygon);
                triangulation = ConstrainedDelaunayTriangulator.triangulate(fixed);
            } catch (Exception e2) {
                StringBuilder sb = new StringBuilder();
                sb.append("Triangulation failed for ").append(debugLabel).append(" even after fix\n");
                // ... (rest of error logging can stay or be simplified)
                throw e; // Throw original exception if fix also fails
            }
        }

        List<int[]> triangles = new ArrayList<>();
        
        // Map JTS Coordinates back to original indices
        // We create a combined list of points to help mapping
        List<Point2D> allPoints = new ArrayList<>(outerRing);
        if (innerRings != null) {
            for (List<Point2D> hole : innerRings) {
                allPoints.addAll(hole);
            }
        }

        for (int i = 0; i < triangulation.getNumGeometries(); i++) {
            Geometry geom = triangulation.getGeometryN(i);
            if (geom instanceof Polygon) {
                Coordinate[] coords = geom.getCoordinates();
                // Triangulation should return triangles (4 coordinates: 3 vertices + 1 closing)
                if (coords.length >= 3) {
                    int[] triangle = new int[3];
                    for (int j = 0; j < 3; j++) {
                        triangle[j] = findClosestIndex(coords[j], allPoints);
                    }
                    
                    // JTS usually returns CCW triangles. 
                    // However, we want the output triangles to match the orientation of the input shell.
                    // This is crucial because for the bottom face, we pass a CW shell and expect CW triangles.
                    if (org.locationtech.jts.algorithm.Orientation.isCCW(outerCoords) != 
                        org.locationtech.jts.algorithm.Orientation.isCCW(coords)) {
                        int tmp = triangle[1];
                        triangle[1] = triangle[2];
                        triangle[2] = tmp;
                    }
                    
                    triangles.add(triangle);
                }
            }
        }

        return triangles;
    }

    private static Coordinate[] toCoordinates(List<Point2D> ring, boolean ensureClosed) {
        int size = ring.size();
        boolean isClosed = size > 0 && ring.get(0).x == ring.get(size - 1).x && ring.get(0).y == ring.get(size - 1).y;
        
        int count = isClosed || !ensureClosed ? size : size + 1;
        Coordinate[] coords = new Coordinate[count];
        for (int i = 0; i < size; i++) {
            coords[i] = new Coordinate(ring.get(i).x, ring.get(i).y);
        }
        if (ensureClosed && !isClosed) {
            coords[size] = new Coordinate(ring.get(0).x, ring.get(0).y);
        }
        return coords;
    }

    private static int findClosestIndex(Coordinate coord, List<Point2D> points) {
        int bestIdx = -1;
        double minDstSq = Double.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            Point2D p = points.get(i);
            double dx = p.x - coord.x;
            double dy = p.y - coord.y;
            double dstSq = dx * dx + dy * dy;
            if (dstSq < minDstSq) {
                minDstSq = dstSq;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    public Point2D getPoint(int pointIdx) {
        return allPoints.get(pointIdx);
    }

}
