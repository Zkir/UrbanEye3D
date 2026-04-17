package ru.zkir.urbaneye3d.utils;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.prep.PreparedGeometry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Implements Bridson's algorithm for Poisson Disk Sampling in O(N) time.
 * Generates points that are at least 'r' distance apart.
 */
public class PoissonDiskSampler {
    private static final int K = 30; // Maximum number of attempts to find a new point around an existing one.
    private static final GeometryFactory factory = new GeometryFactory();

    public static List<Point2D> generatePoints(Envelope envelope, double r, PreparedGeometry filter, Random random) {
        List<Point2D> points = new ArrayList<>();
        if (r <= 0) return points;

        double minX = envelope.getMinX();
        double minY = envelope.getMinY();
        double maxX = envelope.getMaxX();
        double maxY = envelope.getMaxY();

        double cellSize = r / Math.sqrt(2);
        int cols = (int) Math.ceil((maxX - minX) / cellSize);
        int rows = (int) Math.ceil((maxY - minY) / cellSize);

        Point2D[][] grid = new Point2D[cols][rows];
        List<Point2D> activeList = new ArrayList<>();

        // 1. Initial point
        // Try to find a random starting point inside the geometry
        Point2D startPoint = null;
        for (int i = 0; i < 100; i++) {
            double rx = minX + random.nextDouble() * (maxX - minX);
            double ry = minY + random.nextDouble() * (maxY - minY);
            Point p = factory.createPoint(new Coordinate(rx, ry));
            if (filter.contains(p)) {
                startPoint = new Point2D(rx, ry);
                break;
            }
        }

        if (startPoint == null) return points;

        addPoint(startPoint, grid, activeList, points, minX, minY, cellSize);

        // 2. Iteratively add points
        while (!activeList.isEmpty()) {
            int index = random.nextInt(activeList.size());
            Point2D point = activeList.get(index);
            boolean found = false;

            for (int i = 0; i < K; i++) {
                double angle = 2 * Math.PI * random.nextDouble();
                double dist = r * (1 + random.nextDouble()); // Between r and 2r
                double nx = point.x + Math.cos(angle) * dist;
                double ny = point.y + Math.sin(angle) * dist;

                if (nx >= minX && nx < maxX && ny >= minY && ny < maxY) {
                    Point2D candidate = new Point2D(nx, ny);
                    Point p = factory.createPoint(new Coordinate(nx, ny));
                    
                    if (filter.contains(p) && isFarEnough(candidate, grid, minX, minY, cellSize, r)) {
                        addPoint(candidate, grid, activeList, points, minX, minY, cellSize);
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                activeList.remove(index);
            }
        }

        return points;
    }

    private static void addPoint(Point2D p, Point2D[][] grid, List<Point2D> activeList, List<Point2D> points, double minX, double minY, double cellSize) {
        int col = (int) ((p.x - minX) / cellSize);
        int row = (int) ((p.y - minY) / cellSize);
        grid[col][row] = p;
        activeList.add(p);
        points.add(p);
    }

    private static boolean isFarEnough(Point2D p, Point2D[][] grid, double minX, double minY, double cellSize, double r) {
        int col = (int) ((p.x - minX) / cellSize);
        int row = (int) ((p.y - minY) / cellSize);
        
        int rSquared = (int) (r * r);
        
        for (int i = Math.max(0, col - 2); i <= Math.min(grid.length - 1, col + 2); i++) {
            for (int j = Math.max(0, row - 2); j <= Math.min(grid[0].length - 1, row + 2); j++) {
                Point2D neighbor = grid[i][j];
                if (neighbor != null) {
                    double dx = p.x - neighbor.x;
                    double dy = p.y - neighbor.y;
                    if (dx * dx + dy * dy < r * r) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
