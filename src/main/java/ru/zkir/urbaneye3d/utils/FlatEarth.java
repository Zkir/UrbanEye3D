package ru.zkir.urbaneye3d.utils;

import org.openstreetmap.josm.data.coor.LatLon;

/** Everybody knows that the Earth is flat. Especially, in Computer Graphics.
 *  Jokes aside, it's our projection from geographical LatLon to Cartesian XY
 * */
public class FlatEarth {
    public final static double RADIUS = 6378137.;
    public final static double GRAD_LENGTH_M = RADIUS*2*Math.PI/360.;
    public static final double EQUATOR_LENGTH_M = 2 * Math.PI * RADIUS;

    public static Point2D getLocalCoords(LatLon point, LatLon center) {
        return getLocalCoords(point.lat(), point.lon(), center) ;
    }

    /**
     *   Calculates local coords (xy) from the given geographical coords (lat lon) and center.
     */
    public static Point2D getLocalCoords(double lat, double lon, LatLon center) {
        double dx = lon - center.lon();
        double dy = lat - center.lat();
        return new Point2D(dx * Math.cos(Math.toRadians(center.lat())) * GRAD_LENGTH_M,
                dy * GRAD_LENGTH_M);
    }

    /**
     *   Calculates geographical coords (lat lon) from the given local coords (xy) and center.
     */
    public static LatLon fromLocalCoords(double x, double y, LatLon center) {
        double dlat = y / GRAD_LENGTH_M;
        double dlon = x / (Math.cos(Math.toRadians(center.lat())) * GRAD_LENGTH_M);
        return new LatLon(center.lat() + dlat, center.lon() + dlon);
    }

}
