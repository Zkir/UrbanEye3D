package ru.zkir.urbaneye3d.utils;

import java.util.Objects;

public class Point3D {
    public double x, y, z;

   public Point3D(double x, double y, double z) {
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

    // vector module
    public double length(){
        return Math.sqrt(x*x + y*y+ z*z);
    }

    //vector summation
    public Point3D add(Point3D other){
        return new Point3D(this.x+other.x, this.y+other.y, this.z+other.z );
    }

    //vector subtraction
    public Point3D subtract(Point3D other){
        return new Point3D(this.x-other.x, this.y-other.y, this.z-other.z);
    }

    public double dot(Point3D other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }

    public Point3D cross(Point3D other) {
        return new Point3D(
            this.y * other.z - this.z * other.y,
            this.z * other.x - this.x * other.z,
            this.x * other.y - this.y * other.x
        );
    }

    public double distance(Point3D other) {
       // l(a,b) = |a-b|
       return this.subtract(other).length();
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