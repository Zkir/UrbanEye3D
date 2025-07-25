package ru.zkir.josm.plugins.z3dviewer;

public class Point3D {
    double x, y, z;

    Point3D(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Point3D normalize() {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length > 0) {
            return new Point3D(x / length, y / length, z / length);
        }
        return new Point3D(0, 0, 0);
    }

    public double dot(Point3D other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }
}
