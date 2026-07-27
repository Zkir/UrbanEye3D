package ru.zkir.urbaneye3d.utils;

import java.util.ArrayList;
import java.util.List;

public class PowerLineMath {

    /**
     * Generates points for a sagging wire between two points using a parabola.
     * @param p1 Start point
     * @param p2 End point
     * @param sag Sag value at the midpoint (positive meters)
     * @param segments Number of segments to divide the wire into
     * @return List of points along the wire
     */
    public static List<Point3D> generateSaggingWire(Point3D p1, Point3D p2, double sag, int segments) {
        List<Point3D> wirePoints = new ArrayList<>();
        if (segments < 1) segments = 1;

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            // Linear interpolation
            double x = p1.x * (1 - t) + p2.x * t;
            double y = p1.y * (1 - t) + p2.y * t;
            double z = p1.z * (1 - t) + p2.z * t;
            
            // Parabolic sag (Z is up, so we subtract from Z)
            // The formula 4 * t * (1 - t) gives 0 at t=0 and t=1, and 1 at t=0.5
            double currentSag = sag * 4 * t * (1 - t);
            wirePoints.add(new Point3D(x, y, z - currentSag));
        }
        return wirePoints;
    }

    /**
     * Calculates the rotation angle (in degrees) for an object at point B, 
     * based on incoming line segment AB and outgoing segment BC.
     * The rotation aligns with the average direction of the line.
     * @param A Previous point on the line (LatLon mode points)
     * @param B Current point (where the object is)
     * @param C Next point on the line
     * @return Angle in degrees (Counter-Clockwise from East/X+)
     */
    public static double calculateLineAngle(Point2D A, Point2D B, Point2D C) {
        Point2D vIn = (A != null) ? new Point2D(B.x - A.x, B.y - A.y).normalized() : null;
        Point2D vOut = (C != null) ? new Point2D(C.x - B.x, C.y - B.y).normalized() : null;
        
        Point2D direction;
        if (vIn != null && vOut != null) {
            direction = new Point2D(vIn.x + vOut.x, vIn.y + vOut.y);
            if (direction.length() < 1e-6) {
                // 180 degree turn or straight line? 
                // If they cancel out, it's 180 turn, use vIn
                direction = vIn;
            }
        } else if (vIn != null) {
            direction = vIn;
        } else if (vOut != null) {
            direction = vOut;
        } else {
            return 0;
        }
        
        // Atan2 returns radians. We want degrees.
        return Math.toDegrees(Math.atan2(direction.y, direction.x));
    }
}
