package ru.zkir.urbaneye3d.utils;

public class Point2D {
    public double x;
    public double y;

    public Point2D(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void normalize() {
        double length = Math.sqrt(x * x + y * y);
        if (length != 0) {
            x /= length;
            y /= length;
        }
    }

    public Point2D normalized() {
        double length = Math.sqrt(x * x + y * y);
        if (length != 0) {
            return new Point2D(x /= length, y /= length);
        }else{
            return this;
        }
    }

    public double length(){
        return Math.sqrt(x * x + y * y);
    }
    //vector summation
    public Point2D add(Point2D other){
        return new Point2D(this.x+other.x, this.y+other.y);
    }
    //vector subtraction
    public Point2D subtract(Point2D other){
        return new Point2D(this.x-other.x, this.y-other.y);
    }

    //scalar multiplication
    public Point2D mult(double scalar) {
        return new Point2D(x * scalar, y * scalar);
    }

    public double distance(Point2D other) {
        return this.subtract(other).length();
    }

    public double dot(Point2D other) {
        return this.x * other.x + this.y * other.y ;
    }

}