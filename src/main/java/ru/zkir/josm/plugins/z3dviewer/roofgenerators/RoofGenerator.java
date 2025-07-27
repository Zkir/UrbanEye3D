package ru.zkir.josm.plugins.z3dviewer.roofgenerators;

import ru.zkir.josm.plugins.z3dviewer.RenderableBuildingElement;
import ru.zkir.josm.plugins.z3dviewer.utils.Mesh;
import ru.zkir.josm.plugins.z3dviewer.utils.Point2D;
import ru.zkir.josm.plugins.z3dviewer.utils.Point3D;

import java.util.List;

public abstract class RoofGenerator {

    // main method for mesh creation
    public abstract Mesh generate(RenderableBuildingElement building);

    //auxiliary functions, used by descendants are implemented here.
    public static Point3D calculateCentroid(List<Point2D> points) {
        double signedArea = 0.0;
        double cx = 0.0;
        double cy = 0.0;

        for (int i = 0; i < points.size(); i++) {
            Point2D p1 = points.get(i);
            Point2D p2 = points.get((i + 1) % points.size());
            double crossProduct = (p1.x * p2.y) - (p2.x * p1.y);
            signedArea += crossProduct;
            cx += (p1.x + p2.x) * crossProduct;
            cy += (p1.y + p2.y) * crossProduct;
        }
        signedArea *= 0.5;

        if (Math.abs(signedArea) < 1e-9) {
            // Fallback for zero-area polygons (collinear points) to simple average
            cx = 0;
            cy = 0;
            for (Point2D p : points) {
                cx += p.x;
                cy += p.y;
            }
            cx /= points.size();
            cy /= points.size();
        } else {
            cx /= (6.0 * signedArea);
            cy /= (6.0 * signedArea);
        }
        return new Point3D(cx, cy, 0);
    }


    public static Point3D calculateMidpointOfLongestEdge(List<Point2D> points) {
        if (points == null || points.size() < 2) {
            return null; // Or throw an exception
        }

        double maxDistSq = -1;
        Point3D midpoint = null;

        for (int i = 0; i < points.size(); i++) {
            Point2D p1 = points.get(i);
            Point2D p2 = points.get((i + 1) % points.size()); // Wrap around for the last segment

            double dx = p2.x - p1.x;
            double dy = p2.y - p1.y;
            double distSq = dx * dx + dy * dy;

            if (distSq > maxDistSq) {
                maxDistSq = distSq;
                midpoint = new Point3D((p1.x + p2.x) / 2.0, (p1.y + p2.y) / 2.0, 0);
            }
        }
        return midpoint;
    }

    static int[] findLongestOppositeEdges(List<Point2D> points) {
        if (points.size() != 4) {
            return new int[]{-1, -1};
        }

        double[] lengthsSq = new double[4];
        for (int i = 0; i < 4; i++) {
            Point2D p1 = points.get(i);
            Point2D p2 = points.get((i + 1) % 4);
            lengthsSq[i] = (p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y);
        }

        if (lengthsSq[0] + lengthsSq[2] > lengthsSq[1] + lengthsSq[3]) {
            return new int[]{0, 2};
        } else {
            return new int[]{1, 3};
        }
    }

    static int[] findShortestEdges(List<Point2D> points) {
        if (points.size() != 4) {
            return new int[]{-1, -1}; // Should not happen with the check in generateGabledRoof
        }

        double[] lengthsSq = new double[4];
        for (int i = 0; i < 4; i++) {
            Point2D p1 = points.get(i);
            Point2D p2 = points.get((i + 1) % 4);
            lengthsSq[i] = (p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y);
        }

        // In a quad, if we sort edges by length, the two shortest are not necessarily opposite.
        // We need to find the two shortest edges that don't share a vertex.
        int[] indices = {0, 1, 2, 3};

        // Find the indices of the two shortest edges
        int shortest1 = -1, shortest2 = -1;
        double minLength1 = Double.MAX_VALUE, minLength2 = Double.MAX_VALUE;

        for (int i = 0; i < 4; i++) {
            if (lengthsSq[i] < minLength1) {
                minLength2 = minLength1;
                shortest2 = shortest1;
                minLength1 = lengthsSq[i];
                shortest1 = i;
            } else if (lengthsSq[i] < minLength2) {
                minLength2 = lengthsSq[i];
                shortest2 = i;
            }
        }

        // Check if the two shortest edges are adjacent. If so, they can't form the gables.
        // In a typical rectangular building, the two shortest sides will be opposite.
        // If the building is not rectangular, we must decide which pair of opposite sides to use.
        // We'll choose the pair with the shorter average length.
        if (Math.abs(shortest1 - shortest2) == 1 || Math.abs(shortest1 - shortest2) == 3) { // Adjacent
            // Edges 0 and 2 vs 1 and 3
            if (lengthsSq[0] + lengthsSq[2] < lengthsSq[1] + lengthsSq[3]) {
                return new int[]{0, 2};
            } else {
                return new int[]{1, 3};
            }
        } else { // Opposite
            return new int[]{shortest1, shortest2};
        }
    }

    public static Point2D[] shortenSegment(Point2D p1, Point2D p2, double k) {

        // Вычисляем координаты центра отрезка
        Point2D c = p1.add(p2).mult(1/2.0);

        // Рассчитываем новые координаты точек
        Point2D new1 = p1.mult(k).add(c.mult(1-k));
        Point2D new2 = p2.mult(k).add(c.mult(1-k));

        return new Point2D[]{new1,new2};
    }

}
