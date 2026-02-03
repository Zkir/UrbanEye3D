package ru.zkir.urbaneye3d.utils;

import org.openstreetmap.josm.data.coor.LatLon;

/** Everybody knows that the Earth is flat. Especially, in Computer Graphics*/
public class FlatEarth {
    public final static double RADIUS = 6378137.;
    public final static double GRAD_LENGTH_M = RADIUS*2*Math.PI/360.;
    public static final double EQUATOR_LENGTH_M = 2 * Math.PI * RADIUS;

    public static Point2D getLocalCoords(LatLon point, LatLon center) {

        return getLocalCoords(point.lat(), point.lon(), center) ;
    }

    public static Point2D getLocalCoords(double lat, double lon, LatLon center) {
        double dx = lon - center.lon();
        double dy = lat - center.lat();
        return new Point2D(dx * Math.cos(Math.toRadians(center.lat())) * GRAD_LENGTH_M,
                dy * GRAD_LENGTH_M);
    }
}
