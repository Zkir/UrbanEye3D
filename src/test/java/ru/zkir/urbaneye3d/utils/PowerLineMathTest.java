package ru.zkir.urbaneye3d.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

public class PowerLineMathTest {

    @Test
    public void testGenerateSaggingWire() {
        Point3D p1 = new Point3D(0, 0, 10);
        Point3D p2 = new Point3D(10, 0, 10);
        double sag = 2.0;
        int segments = 2;

        List<Point3D> wire = PowerLineMath.generateSaggingWire(p1, p2, sag, segments);
        
        assertEquals(3, wire.size());
        
        // P(0)
        assertEquals(0.0, wire.get(0).x, 1e-6);
        assertEquals(0.0, wire.get(0).y, 1e-6);
        assertEquals(10.0, wire.get(0).z, 1e-6);
        
        // P(0.5)
        assertEquals(5.0, wire.get(1).x, 1e-6);
        assertEquals(0.0, wire.get(1).y, 1e-6);
        assertEquals(8.0, wire.get(1).z, 1e-6); // 10 - 2.0
        
        // P(1.0)
        assertEquals(10.0, wire.get(2).x, 1e-6);
        assertEquals(0.0, wire.get(2).y, 1e-6);
        assertEquals(10.0, wire.get(2).z, 1e-6);
    }

    @Test
    public void testCalculateLineAngle() {
        // Horizontal line
        Point2D A = new Point2D(0, 0);
        Point2D B = new Point2D(1, 0);
        Point2D C = new Point2D(2, 0);
        
        double angle = PowerLineMath.calculateLineAngle(A, B, C);
        assertEquals(0.0, angle, 1e-6);
        
        // Vertical line (North)
        A = new Point2D(0, 0);
        B = new Point2D(0, 1);
        C = new Point2D(0, 2);
        angle = PowerLineMath.calculateLineAngle(A, B, C);
        assertEquals(90.0, angle, 1e-6);
        
        // 90 degree turn
        A = new Point2D(0, 0);
        B = new Point2D(1, 0);
        C = new Point2D(1, 1);
        angle = PowerLineMath.calculateLineAngle(A, B, C);
        // vIn = (1, 0), vOut = (0, 1) -> direction = (1, 1) -> 45 degrees
        assertEquals(45.0, angle, 1e-6);
    }
}
