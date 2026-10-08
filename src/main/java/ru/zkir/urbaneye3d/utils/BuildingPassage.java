package ru.zkir.urbaneye3d.utils;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import ru.zkir.urbaneye3d.UrbanEye3dPlugin;

import java.util.ArrayList;
import java.util.List;

import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getTagD;

/**
 * Support for {@code tunnel=building_passage} -- a way going through a building, with walls on both sides.
 * <p>
 * According to the OSM wiki such a way is the centre line of the passage: it is split at the entry and exit
 * points of the building, and its end nodes are shared with the building outline. There is no tag defining
 * the physical size of the opening, so the width of the passage is taken from the {@code width} tag (if any)
 * and the height -- from the {@code height} tag, with reasonable defaults for the rest.
 * <p>
 * The passage itself is not rendered as a road. Instead, the zone occupied by the passage is subtracted
 * from the footprint of every building (or building part) it crosses: the resulting shape is extruded
 * up to the passage height, and the part of the building lying above the passage keeps its own (untouched)
 * roof. That's why no 3D boolean operations are required -- everything is done on 2D footprints with JTS,
 * in the local coordinate system of the building.
 */
public final class BuildingPassage {

    /** Default height of a building passage, meters. */
    public static final double DEFAULT_HEIGHT = 3.0;
    /** Default width of a building passage, meters. Narrow enough for gates and doorways. */
    public static final double DEFAULT_WIDTH = 3.5;

    /**
     * Corridors are extended beyond the ends of the way by this distance (meters).
     * The wiki says that the way shares nodes with the building outline, so the ends are exactly on the walls,
     * and this margin makes the subtraction numerically robust. It is also harmless for ways which go on
     * beyond the building.
     */
    private static final double END_MARGIN = 1.0;

    /** Footprints (and pieces of footprints) smaller than this area (m2) are considered empty. */
    public static final double MIN_AREA = 0.01;

    private BuildingPassage() {
    }

    /**
     * Checks whether the given way is a building passage which should be cut out of buildings.
     * Underground passages ({@code layer<0}, e.g. underground crossings) are not supported.
     */
    public static boolean isBuildingPassage(Way way) {
        if (way == null || way.isDeleted() || way.isIncomplete()) {
            return false;
        }
        return "building_passage".equals(way.get("tunnel"))
                && way.getNodesCount() >= 2
                && getTagD("layer", way, 0) >= 0;
    }

    /**
     * A building passage, prepared to be cut out of buildings.
     * <p>
     * Keeps the parameters of the passage and its bounding box (used to skip obviously non-intersecting
     * buildings cheaply). {@link #corridor(LatLon)} builds the actual polygon in the local coordinate
     * system of a building -- that operation is much more expensive, so it is done lazily.
     */
    public static final class Zone {
        private final Way way;
        /** Height of the passage (top of the opening), meters. */
        public final double height;
        /** Width of the passage (distance between its walls), meters. */
        public final double width;

        private final double minLat, maxLat, minLon, maxLon;

        private Zone(Way way) {
            this.way = way;
            this.height = getTagD("height", way, DEFAULT_HEIGHT);
            double w = getTagD("width", way, DEFAULT_WIDTH);
            this.width = w > 0 ? w : DEFAULT_WIDTH;

            double minLat = Double.POSITIVE_INFINITY;
            double maxLat = Double.NEGATIVE_INFINITY;
            double minLon = Double.POSITIVE_INFINITY;
            double maxLon = Double.NEGATIVE_INFINITY;
            for (Node node : way.getNodes()) {
                minLat = Math.min(minLat, node.lat());
                maxLat = Math.max(maxLat, node.lat());
                minLon = Math.min(minLon, node.lon());
                maxLon = Math.max(maxLon, node.lon());
            }
            //expand the bbox by half of the passage width plus the end margin
            double marginM = width / 2.0 + END_MARGIN;
            double dLat = marginM / FlatEarth.GRAD_LENGTH_M;
            double cosLat = Math.cos(Math.toRadians((minLat + maxLat) / 2.0));
            double dLon = marginM / (Math.max(cosLat, 1e-6) * FlatEarth.GRAD_LENGTH_M);
            this.minLat = minLat - dLat;
            this.maxLat = maxLat + dLat;
            this.minLon = minLon - dLon;
            this.maxLon = maxLon + dLon;
        }

        /** Cheap bbox test: can this passage touch the given (LatLon) bbox at all? */
        public boolean mayIntersect(double otherMinLat, double otherMaxLat, double otherMinLon, double otherMaxLon) {
            return minLat <= otherMaxLat && maxLat >= otherMinLat
                    && minLon <= otherMaxLon && maxLon >= otherMinLon;
        }

        /**
         * Builds the polygon occupied by the passage, in the local cartesian coordinate system
         * of the building with the given origin.
         * @return corridor geometry, or null if it cannot be built
         */
        public Geometry corridor(LatLon origin) {
            List<Coordinate> coords = new ArrayList<>();
            for (Node node : way.getNodes()) {
                Point2D p = FlatEarth.getLocalCoords(node.lat(), node.lon(), origin);
                coords.add(new Coordinate(p.x, p.y));
            }
            if (coords.size() < 2) {
                return null;
            }
            extendEnds(coords, END_MARGIN);
            try {
                GeometryFactory factory = new GeometryFactory();
                LineString line = factory.createLineString(coords.toArray(new Coordinate[0]));
                return line.buffer(width / 2.0, 1, BufferParameters.CAP_FLAT);
            } catch (Exception e) {
                UrbanEye3dPlugin.debugMsg("Unable to build building passage corridor: " + e.getMessage());
                return null;
            }
        }
    }

    /** Prepares a zone for the given building passage way. */
    public static Zone zone(Way way) {
        return new Zone(way);
    }

    /**
     * Subtracts the passage corridor from the footprint of a building part.
     * The contour must be in local (XY) coordinates; the result also is.
     * @return one contour per remaining piece of the footprint, or null if the operation failed
     *         (in that case the caller should keep the original, uncut geometry)
     */
    public static List<Contour> subtract(Contour contour, Geometry corridor) {
        Geometry footprint = contour.toJTSGeometry();
        if (footprint == null || footprint.isEmpty()) {
            return null;
        }
        Geometry difference;
        try {
            difference = footprint.difference(corridor);
        } catch (Exception e) {
            //JTS may fail on the invalid input. It is not a reason to lose the building.
            UrbanEye3dPlugin.debugMsg("Unable to subtract building passage: " + e.getMessage());
            return null;
        }

        List<Contour> result = new ArrayList<>();
        for (int i = 0; i < difference.getNumGeometries(); i++) {
            Geometry geometry = difference.getGeometryN(i);
            if (!(geometry instanceof Polygon)) {
                continue;
            }
            Polygon polygon = (Polygon) geometry;
            if (polygon.getArea() < MIN_AREA) {
                continue;
            }
            Contour piece = new Contour(toRing(polygon.getExteriorRing()), "XY");
            for (int j = 0; j < polygon.getNumInteriorRing(); j++) {
                piece.innerRings.add(toRing(polygon.getInteriorRingN(j)));
            }
            result.add(piece);
        }
        return result;
    }

    private static ArrayList<Point2D> toRing(LineString ring) {
        ArrayList<Point2D> points = new ArrayList<>();
        for (Coordinate coordinate : ring.getCoordinates()) {
            points.add(new Point2D(coordinate.x, coordinate.y));
        }
        return points;
    }

    /** Moves the ends of the polyline outwards, along its own direction. */
    private static void extendEnds(List<Coordinate> coords, double margin) {
        Coordinate first = coords.get(0);
        Coordinate second = coords.get(1);
        double dx = first.x - second.x;
        double dy = first.y - second.y;
        double length = Math.hypot(dx, dy);
        if (length > 1e-9) {
            coords.set(0, new Coordinate(first.x + dx / length * margin, first.y + dy / length * margin));
        }

        int lastIndex = coords.size() - 1;
        Coordinate last = coords.get(lastIndex);
        Coordinate beforeLast = coords.get(lastIndex - 1);
        dx = last.x - beforeLast.x;
        dy = last.y - beforeLast.y;
        length = Math.hypot(dx, dy);
        if (length > 1e-9) {
            coords.set(lastIndex, new Coordinate(last.x + dx / length * margin, last.y + dy / length * margin));
        }
    }
}
