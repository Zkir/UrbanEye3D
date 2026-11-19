package ru.zkir.urbaneye3d.meshers.buildingpassage;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.PrimitiveId;
import org.openstreetmap.josm.data.osm.Way;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.FlatEarth;
import ru.zkir.urbaneye3d.utils.OsmDataWasher;
import ru.zkir.urbaneye3d.utils.Point2D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getTagD;
import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getTagStr;
import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_LEVEL_HEIGHT;

/**
 * A way tagged {@code tunnel=building_passage}: a road, footway or railway going THROUGH a building.  Described
 * by its axis (in local metres of the building), a width and an optional height ({@code maxheight},
 * {@code maxheight:physical}, or the structure gauge of a railway).  See {@link PassageCutter} for the way the
 * opening is cut out of the building mesh.
 */
public class BuildingPassage {

    /** Default arch height when maxheight is not tagged, metres: the height of the first floor. */
    public static final double DEFAULT_ARCH_HEIGHT = DEFAULT_LEVEL_HEIGHT;
    /** The strip is prolonged past the ends of the way so that it goes through the wall, metres. */
    public static final double OVERSHOOT = 1.0;
    /** The wall above the arch is kept at least this thick, metres. */
    public static final double MIN_LINTEL = 0.3;
    /** An arch shorter than this is not cut at all, metres. */
    public static final double MIN_ARCH_HEIGHT = 1.0;
    /** Outside these limits a "width" tag value is a mistake, metres. */
    public static final double MIN_WIDTH = 0.5;
    public static final double MAX_WIDTH = 30.0;
    /** Outside these limits a "maxheight" tag value is a mistake, metres. */
    public static final double MIN_HEIGHT = 1.0;
    public static final double MAX_HEIGHT = 20.0;
    /** Ends of two axes nearer than this are one road mapped as several ways, metres. */
    public static final double JOIN_TOLERANCE = 0.05;
    /** Axes turning at their common end by more than this (tan) are two passages, not one corridor. */
    private static final double JOIN_TAN_LIMIT = 0.6;

    /** Way types that get a narrow opening. */
    private static final Set<String> NARROW_WAYS =
            Set.of("footway", "path", "pedestrian", "cycleway", "steps", "bridleway", "corridor");

    /** railway=... -> {width, height} of the structure gauge of a single track, metres. */
    private static final Map<String, double[]> RAIL_GAUGE = new HashMap<>();

    static {
        RAIL_GAUGE.put("rail", new double[]{5.0, 6.5});
        RAIL_GAUGE.put("narrow_gauge", new double[]{4.0, 5.0});
        RAIL_GAUGE.put("preserved", new double[]{5.0, 5.5});
        RAIL_GAUGE.put("light_rail", new double[]{4.0, 5.0});
        RAIL_GAUGE.put("subway", new double[]{4.0, 4.5});
        RAIL_GAUGE.put("tram", new double[]{4.0, 5.0});
        RAIL_GAUGE.put("monorail", new double[]{4.0, 5.0});
        RAIL_GAUGE.put("funicular", new double[]{4.0, 4.5});
        RAIL_GAUGE.put("miniature", new double[]{2.0, 2.5});
    }

    public final PrimitiveId primitiveId;
    /** Axis of the passage in local metres (same origin as the mesh of the building). */
    public final List<Point2D> axis;
    /** Width of the opening, metres. */
    public final double width;
    /** Height of the opening above the ground, metres; null means the first floor height is used. */
    public final double height;

    public BuildingPassage(PrimitiveId primitiveId, List<Point2D> axis, double width, double height) {
        this.primitiveId = primitiveId;
        this.axis = axis;
        this.width = width;
        this.height = height;
    }

    /** Does this primitive describe a way going through a building? */
    public static boolean isPassage(OsmPrimitive primitive) {
        if (!(primitive instanceof Way)) {
            return false;
        }
        if (!primitive.hasTag("tunnel", "building_passage")) {
            return false;
        }
        if (primitive.hasTag("area", "yes")) {
            return false; //an area tagged as a tunnel is not a way going through a building
        }
        //A way deeper than one storey underground is a real tunnel, not a passage through a building.
        double layer = getTagD("layer", primitive,0.0);
        if (layer < -1) {
            return false;
        }
        return primitive.hasKey("highway") || primitive.hasKey("railway");
    }

    /**
     * The passage of the way, in the local coordinate system of the building it passes through.
     * @param origin origin of that coordinate system
     * @return the passage, or null if the way has too few nodes or is too narrow
     */
    public static BuildingPassage fromWay(Way way, LatLon origin, double minHeight, double wallHeight) {
        List<Point2D> axis = new ArrayList<>();
        for (Node node : way.getNodes()) {
            if (node.isIncomplete() || node.getCoor() == null) {
                return null; //the geometry of the passage is not fully downloaded
            }
            Point2D p = FlatEarth.getLocalCoords(node.getCoor(), origin);
            Point2D prev = axis.isEmpty() ? null : axis.get(axis.size() - 1);
            if (prev != null && prev.distance(p) < 1e-6) {
                continue; //duplicated nodes of the way
            }
            axis.add(p);
        }
        if (axis.size() < 2) {
            return null;
        }
        double width = passageWidth(way.getInterestingTags());
        if (width < MIN_WIDTH) {
            return null;
        }

        double height = passageHeight(way.getInterestingTags(), minHeight, wallHeight);
        return new BuildingPassage(way.getPrimitiveId(), axis, width, height );
    }

    /** Width of the opening: from the width tags, the number of lanes, or the type of the way; railways use the gauge. */
    private static double passageWidth(Map<String, String> tags) {
        Double tagged = getTagD("width", tags, null);
        if (tagged == null) {
            tagged = getTagD("maxwidth:physical", tags, null );
        }
        if (tagged == null) {
            tagged = getTagD("maxwidth", tags, null);
        }
        if (tagged != null && tagged > MIN_WIDTH && tagged < MAX_WIDTH) {
            return tagged;
        }
        String railway = getTagStr("railway", tags, (String) null);
        double[] gauge = railway == null ? null : RAIL_GAUGE.get(railway);
        if (gauge != null) {
            return gauge[0];
        }
        Double lanes = getTagD("lanes", tags, null);
        String highway = tags.get("highway");
        if (lanes != null && lanes >= 1 && lanes <= 8 && !NARROW_WAYS.contains(highway)) {
            return lanes * 3.0;
        }
        if (NARROW_WAYS.contains(highway)) {
            return 2.5;
        }
        //Without highway=* the way is treated like a service road
        return highway == null || "service".equals(highway) ? 3.5 : 5.0;
    }

    /** Height of the opening from the tags; null if not specified, then the first floor height is used. */
    private static double passageHeight(Map<String, String> tags, double minHeight, double wallHeight) {
        Double tagged = getTagD("maxheight:physical", tags, null);
        if (tagged == null) {
            tagged = getTagD("maxheight", tags, null);
        }
        if (tagged != null && tagged > MIN_HEIGHT && tagged < MAX_HEIGHT) {
            return tagged;
        }
        String railway = tags.get("railway");
        double[] gauge = railway == null ? null : RAIL_GAUGE.get(railway);
        if (gauge == null) {
            return Math.min(DEFAULT_ARCH_HEIGHT, wallHeight - minHeight);
        }
        //Overhead contact line of a non-electrified railway is lower than the structure gauge
        return "rail".equals(railway)  ? 5.5 : gauge[1];
    }

    /**
     * The passage as a polygon in plan: the axis buffered by half of the width, with flat caps, mitred joins at
     * the bends and the ends prolonged by {@link #OVERSHOOT}, because the axis usually ends on the contour while
     * the strip has to go through the wall.
     */
    public Geometry strip(GeometryFactory factory) {
        if (axis.size() < 2) {
            return null;
        }
        Coordinate[] coords = stretchedAxis();
        if (coords.length < 2) {
            return null;
        }
        LineString line = factory.createLineString(coords);
        BufferParameters parameters = new BufferParameters();
        parameters.setQuadrantSegments(1);
        parameters.setEndCapStyle(BufferParameters.CAP_FLAT);
        //a sharp bend must not leave a wedge-shaped gap between the rectangles of the parts
        parameters.setJoinStyle(BufferParameters.JOIN_MITRE);
        parameters.setMitreLimit(10.0);
        return BufferOp.bufferOp(line, width / 2.0, parameters);
    }

    /**
     * The axis prolonged at both ends: the end points move outward by {@link #OVERSHOOT}; no points are added.
     * A point added in the middle of a straight stretch would leave a redundant corner a hair away from the spot
     * where the strip really crosses the wall: the wall is cut at one of them and the arch built on the other, so
     * the faces stop sharing an edge and the body of the building is left open.
     */
    public Coordinate[] stretchedAxis() {
        List<Point2D> straight = straighten(axis);
        Coordinate[] coords = new Coordinate[straight.size()];
        for (int i = 0; i < straight.size(); i++) {
            coords[i] = new Coordinate(straight.get(i).x, straight.get(i).y);
        }
        if (coords.length < 2) {
            return coords;
        }
        double dx = coords[0].x - coords[1].x;
        double dy = coords[0].y - coords[1].y;
        double len = Math.hypot(dx, dy);
        if (len > 1e-9) {
            //the first point moves away from the second one, along the first segment of the axis
            coords[0].x += dx / len * OVERSHOOT;
            coords[0].y += dy / len * OVERSHOOT;
        }
        int last = coords.length - 1;
        dx = coords[last].x - coords[last - 1].x;
        dy = coords[last].y - coords[last - 1].y;
        len = Math.hypot(dx, dy);
        if (len > 1e-9) {
            //the last point moves away from the previous one, along the last segment of the axis
            coords[last].x += dx / len * OVERSHOOT;
            coords[last].y += dy / len * OVERSHOOT;
        }
        return coords;
    }

    /**
     * The axis with the points where the way goes almost straight removed.  The contour of a building drops such
     * nodes (see {@link Contour#STRAIGHT_ANGLE_TAN_TOLERANCE}), so a wall goes straight through them while the
     * corridor bends: the strip gets a corner the wall does not know about, a hair away from the crossing point,
     * and the wall and the arch stop sharing an edge.  A straight passage stays a rectangle of four corners.
     */
    private static List<Point2D> straighten(List<Point2D> axis) {
        List<Point2D> result = new ArrayList<>(axis);
        boolean changed;
        do {
            changed = false;
            for (int i = 1; i + 1 < result.size(); i++) {
                if (goesStraightOn(result.get(i - 1), result.get(i), result.get(i + 1))) {
                    result.remove(i);
                    changed = true;
                    break;
                }
            }
        } while (changed && result.size() > 2);
        return result;
    }

    /** Does the way go on almost straight through the middle of the three points?  The same test as in Contour. */
    private static boolean goesStraightOn(Point2D before, Point2D middle, Point2D after) {
        double v1x = before.x - middle.x;
        double v1y = before.y - middle.y;
        double v2x = after.x - middle.x;
        double v2y = after.y - middle.y;
        double cross = v1x * v2y - v1y * v2x;
        double dot = v1x * v2x + v1y * v2y;
        return dot < 0 && Math.abs(cross / dot) < Contour.STRAIGHT_ANGLE_TAN_TOLERANCE;
    }

    /**
     * Passages that go on through the end of one another — one road mapped as several ways — become one corridor,
     * because it is the corridor of a road, not its pieces, that the building is cut with.  Buffered one by one,
     * the strips overlap: the prolonged end of each of them sticks out of the side of the other and leaves a
     * hairline edge right where the corridor crosses the wall, which would leave the body of the building open,
     * so such a passage is refused.  Buffered as one polyline there is no such edge, because {@link #straighten}
     * drops the straight node.
     *
     * @return the passages joined into corridors; the rest stay as they were
     */
    public static List<BuildingPassage> joinChains(List<BuildingPassage> passages) {
        List<BuildingPassage> corridors = new ArrayList<>();
        for (BuildingPassage passage : passages) {
            BuildingPassage chain = null;
            List<Point2D> merged = null;
            for (BuildingPassage candidate : corridors) {
                merged = joinedAxis(candidate.axis, passage.axis);
                if (merged != null) {
                    chain = candidate;
                    break;
                }
            }
            if (chain == null) {
                corridors.add(passage);
            } else {
                //after the join only the shape matters: the corridor takes the widest of its ways, while the
                //height of the arch was decided for every way earlier
                corridors.set(corridors.indexOf(chain),
                        new BuildingPassage(chain.primitiveId, merged, Math.max(chain.width, passage.width),
                                Math.max(chain.height, passage.height)));
            }
        }
        return corridors;
    }

    /**
     * The axis of two passages that go on through each other, or null if their ends do not touch or the way turns
     * too sharply at their common end to be one corridor.  The axes are turned around as the join needs.
     */
    private static List<Point2D> joinedAxis(List<Point2D> first, List<Point2D> second) {
        if (first.size() < 2 || second.size() < 2) {
            return null;
        }
        for (int flip = 0; flip < 4; flip++) {
            List<Point2D> a = (flip & 1) == 0 ? first : reversed(first);
            List<Point2D> b = (flip & 2) == 0 ? second : reversed(second);
            Point2D joint = a.get(a.size() - 1);
            if (joint.distance(b.get(0)) > JOIN_TOLERANCE) {
                continue;
            }
            Point2D before = a.get(a.size() - 2);
            Point2D after = b.get(1);
            double ux = joint.x - before.x;
            double uy = joint.y - before.y;
            double vx = after.x - joint.x;
            double vy = after.y - joint.y;
            double cross = ux * vy - uy * vx;
            double dot = ux * vx + uy * vy;
            if (dot <= 0 || Math.abs(cross / dot) > JOIN_TAN_LIMIT) {
                //the way would turn back on itself, or turn by more than about 30 degrees: two passages
                //crossing the building in different directions, not one corridor
                continue;
            }
            List<Point2D> merged = new ArrayList<>(a);
            merged.addAll(b.subList(1, b.size()));
            return merged;
        }
        return null;
    }

    /** A copy of the axis, the other way round. */
    private static List<Point2D> reversed(List<Point2D> axis) {
        List<Point2D> result = new ArrayList<>(axis);
        Collections.reverse(result);
        return result;
    }
}
