package ru.zkir.josm.plugins.z3dviewer;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Point3D point3D = (Point3D) o;
        return Double.compare(point3D.x, x) == 0 &&
               Double.compare(point3D.y, y) == 0 &&
               Double.compare(point3D.z, z) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }
}
