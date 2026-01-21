package ru.zkir.urbaneye3d.utils;

import org.locationtech.jts.geom.*;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.*;

import org.locationtech.jts.operation.buffer.BufferParameters;
import ru.zkir.urbaneye3d.UrbanEye3dPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Contour {
    // Define a tolerance for the tangent of the angle. For example, 0.08 corresponds to ~175.5 degrees.
    // This allows for slight deviations in manually placed points.
    static final double STRAIGHT_ANGLE_TAN_TOLERANCE = 0.08;
    public final static double GRAD_LENGTH_M = 6378137.*2*Math.PI/360.;
    public String mode = "";
    public List<ArrayList<Point2D>> outerRings;
    public List<ArrayList<Point2D>> innerRings;
    
    /**
     * This constructor creates contour from POLYGONAL primitive (e.g. closed way or multipolygon relation)
     */
    public Contour(OsmPrimitive primitive, LatLon center) {
        if (center == null) {
            this.mode = "LatLon";
        } else {
            this.mode = "XY";
            throw new RuntimeException("only latlon right now!");
        }
        if (primitive instanceof Way) {
            Way way = (Way) primitive;
            this.outerRings = new ArrayList<>();
            this.innerRings = new ArrayList<>();
            ArrayList<Point2D> tempContour = new ArrayList<>();
            for (Node node : way.getNodes()) {
                if ("XY".equals(this.mode)) {
                    tempContour.add(getNodeLocalCoords(node, center));
                } else {
                    tempContour.add(new Point2D(node.lon(), node.lat()));
                }
            }
            this.outerRings.add(tempContour);
        } else { //relation
            Relation relation = (Relation) primitive;
            this.outerRings = new ArrayList<>();
            this.innerRings = new ArrayList<>();

            List<Way> outerWays = new ArrayList<>();
            List<Way> innerWays = new ArrayList<>();

            for (RelationMember member : relation.getMembers()) {
                if (!member.isWay() || member.getMember().isIncomplete()) continue;

                if ("outer".equals(member.getRole())) {
                    outerWays.add(member.getWay());
                } else if ("inner".equals(member.getRole())) {
                    innerWays.add(member.getWay());
                }
            }

            List<List<Node>> outerNodeRings = assembleRings(outerWays);
            for (List<Node> nodeRing : outerNodeRings) {
                ArrayList<Point2D> pointRing = new ArrayList<>();
                for (Node node : nodeRing) {
                    if ("XY".equals(this.mode)) {
                        pointRing.add(getNodeLocalCoords(node, center));
                    } else {
                        pointRing.add(new Point2D(node.lon(), node.lat()));
                    }

                }
                this.outerRings.add(pointRing);
            }

            List<List<Node>> innerNodeRings = assembleRings(innerWays);
            for (List<Node> nodeRing : innerNodeRings) {
                ArrayList<Point2D> pointRing = new ArrayList<>();
                for (Node node : nodeRing) {
                    if ("XY".equals(this.mode)) {
                        pointRing.add(getNodeLocalCoords(node, center));
                    } else {
                        pointRing.add(new Point2D(node.lon(), node.lat()));
                    }
                }
                this.innerRings.add(pointRing);
            }
        }
    }

    /**
     * This constructor creates contour from LINEAR primitive (e.g. barrier) applying BUFFER
     */
    public Contour(Way way, double width, LatLon center) {
        this.mode = "XY";
        this.outerRings = new ArrayList<>();
        this.innerRings = new ArrayList<>();

        List<Point2D> points = new ArrayList<>();
        for (Node node : way.getNodes()) {
            points.add(getNodeLocalCoords(node, center));
        }

        if (points.size() >= 2) {
            GeometryFactory geometryFactory = new GeometryFactory();
            Coordinate[] coords = new Coordinate[points.size()];
            for (int i = 0; i < points.size(); i++) {
                coords[i] = new Coordinate(points.get(i).x, points.get(i).y);
            }

            LineString line = geometryFactory.createLineString(coords);

            Polygon polygon = (Polygon) line.buffer(width / 2, 1, BufferParameters.CAP_FLAT);

            ArrayList<Point2D> polygonPoints1 = new ArrayList<>();
            for (Coordinate coord : polygon.getExteriorRing().getCoordinates()) {
                polygonPoints1.add(new Point2D(coord.x, coord.y));
            }
            this.outerRings.add(polygonPoints1);

            for (int i=0; i<polygon.getNumInteriorRing(); i++){
                ArrayList<Point2D> polygonPoints2 = new ArrayList<>();
                for (Coordinate coord : polygon.getInteriorRingN(i).getCoordinates()) {
                    polygonPoints2.add(new Point2D(coord.x, coord.y));
                }
                this.innerRings.add(polygonPoints2);
            }
        }
    }

    public Contour(ArrayList<Point2D> outerRing, String mode) {
        this.outerRings = new ArrayList<>();
        this.outerRings.add(outerRing);
        this.innerRings = new ArrayList<>();
        this.mode = mode;
    }

    public boolean contains(Contour other) {
        // 'this' is the potential container (building), 'other' is the content (part).

        // A building must have an outer ring to contain anything.
        if (this.outerRings.isEmpty()) {
            return false;
        }
        // A part must have an outer ring to be contained.
        if (other.outerRings.isEmpty()) {
            return false;
        }

        // For simplicity, we assume a building is defined by its first outer ring for containment checks.
        // This is a reasonable simplification for most OSM data.
        List<Point2D> buildingOuterRing = this.outerRings.get(0);

        // Check every outer ring of the part.
        for (ArrayList<Point2D> partOuterRing : other.outerRings) {
            // Check every point of the part's outer ring.
            for (Point2D point : partOuterRing) {
                // 1. All points of the part must be inside the building's outer ring.
                if (!isPointInside(buildingOuterRing, point)) {
                    return false; // Part is not fully inside the building's boundary.
                }

                // 2. All points of the part must be outside all of the building's inner rings (holes).
                for (ArrayList<Point2D> buildingInnerRing : this.innerRings) {
                    if (isPointStrictlyInside(buildingInnerRing, point)) {
                        return false; // Part is inside a hole of the building.
                    }
                }
            }
        }

        // If all checks pass, the part is considered to be inside the building.
        return true;
    }

    private boolean isPointStrictlyInside(List<Point2D> polygon, Point2D point) {
        if (isPointOnBorder(polygon, point)) {
            return false;
        }
        return isPointInside(polygon, point, false);
    }

    private boolean isPointInside(List<Point2D> polygon, Point2D point) {
        return isPointInside(polygon, point, true);
    }


    private boolean isPointInside(List<Point2D> polygon, Point2D point, boolean includeBorder) {
        if (includeBorder && isPointOnBorder(polygon, point)) {
            return true; // for our purposes we consider borders as part of a polygon
        }

        int intersections = 0;
        for (int i = 0; i < polygon.size(); i++) {
            Point2D p1 = polygon.get(i);
            Point2D p2 = polygon.get((i + 1) % polygon.size());
            if (p1.y == p2.y) continue;
            if (point.y < Math.min(p1.y, p2.y) || point.y >= Math.max(p1.y, p2.y)) continue;
            double x = (point.y - p1.y) * (p2.x - p1.x) / (p2.y - p1.y) + p1.x;
            if (x > point.x) {
                intersections++;
            }
        }
        return (intersections % 2) == 1;
    }

    // Проверка, лежит ли точка на границе полигона
    private boolean isPointOnBorder(List<Point2D> polygon, Point2D point) {
        final double EPS = 1e-10;
        for (int i = 0; i < polygon.size(); i++) {
            Point2D p1 = polygon.get(i);
            Point2D p2 = polygon.get((i + 1) % polygon.size());

            // Проверка принадлежности точки ребру (p1, p2)
            if (pointOnSegment(p1, p2, point, EPS)) {
                return true;
            }
        }
        return false;
    }

    // Проверка, лежит ли точка на отрезке
    private boolean pointOnSegment(Point2D p1, Point2D p2, Point2D point, double eps) {
        // Расстояние от точки до концов отрезка
        double distToP1 = Math.hypot(point.x - p1.x, point.y - p1.y);
        double distToP2 = Math.hypot(point.x - p2.x, point.y - p2.y);
        double segLength = Math.hypot(p2.x - p1.x, p2.y - p1.y);

        // Если точка совпадает с вершиной
        if (distToP1 < eps || distToP2 < eps) return true;

        // Коллинеарность и нахождение на отрезке
        double cross = (point.x - p1.x) * (p2.y - p1.y) - (point.y - p1.y) * (p2.x - p1.x);
        boolean withinBoundingBox = point.x >= Math.min(p1.x, p2.x) - eps &&
                point.x <= Math.max(p1.x, p2.x) + eps &&
                point.y >= Math.min(p1.y, p2.y) - eps &&
                point.y <= Math.max(p1.y, p2.y) + eps;

        return Math.abs(cross) < eps && withinBoundingBox;
    }


    private List<List<Node>> assembleRings(List<Way> ways) {
        List<List<Node>> rings = new ArrayList<>();
        List<Way> remainingWays = new ArrayList<>(ways);

        while (!remainingWays.isEmpty()) {
            List<Node> currentRing = new ArrayList<>(remainingWays.get(0).getNodes());
            remainingWays.remove(0);

            boolean ringClosed = false;
            while (!ringClosed && !remainingWays.isEmpty()) {
                Node firstNode = currentRing.get(0);
                Node lastNode = currentRing.get(currentRing.size() - 1);

                if (firstNode.equals(lastNode)) {
                    ringClosed = true;
                    continue;
                }

                boolean foundNext = false;
                for (int i = 0; i < remainingWays.size(); i++) {
                    Way nextWay = remainingWays.get(i);
                    if (nextWay.getNodesCount() < 2) continue;

                    if (nextWay.firstNode().equals(lastNode)) {
                        currentRing.addAll(nextWay.getNodes().subList(1, nextWay.getNodesCount()));
                        remainingWays.remove(i);
                        foundNext = true;
                        break;
                    } else if (nextWay.lastNode().equals(lastNode)) {
                        List<Node> reversedNodes = new ArrayList<>(nextWay.getNodes());
                        Collections.reverse(reversedNodes);
                        currentRing.addAll(reversedNodes.subList(1, reversedNodes.size()));
                        remainingWays.remove(i);
                        foundNext = true;
                        break;
                    }
                }

                if (!foundNext) {
                    // Could not find a way to close the ring, break to avoid infinite loop
                    break;
                }
            }
            rings.add(currentRing);
        }
        return rings;
    }

    static Point2D getNodeLocalCoords(Node node, LatLon center) {
        return getLocalCoords(new Point2D(node.lon(), node.lat()), center);
    }

    public static Point2D getLocalCoords(Point2D point, LatLon center) {
        double dx = point.x - center.lon();
        double dy = point.y - center.lat();
        return new Point2D(dx * Math.cos(Math.toRadians(center.lat())) * GRAD_LENGTH_M,
                dy * GRAD_LENGTH_M);
    }


    ArrayList<Point2D> simplifyContour(ArrayList<Point2D> originalContour) {

        if (this.mode.equals("LatLon")){
            throw new RuntimeException("Contour simplification does not work correct in LatLon mode");
        }

        if (originalContour.size() < 3) {
            return originalContour; // Cannot simplify a line or a single point
        }

        ArrayList<Point2D> simplifiedContour = new ArrayList<>();
        boolean isClosed = originalContour.get(0).x == originalContour.get(originalContour.size() - 1).x &&
                originalContour.get(0).y == originalContour.get(originalContour.size() - 1).y;

        int start_index = 0;
        int numPoints = originalContour.size() - 1; // Don't process the  last point. In closed loop it's duplicate. in open way it cannot be removed.
        Point2D p_prev;
        if (!isClosed) {
            start_index = 1;
            p_prev = originalContour.get(0);
            simplifiedContour.add(p_prev); //in case of open way, we can add the first node immediately (since it cannot be removed)
        }else{
            p_prev = originalContour.get(numPoints - 1); //for node 0 previous is second to last :)
        }

        for (int i = start_index; i < numPoints; i++) {
            Point2D p_current = originalContour.get(i);
            Point2D p_next = originalContour.get(i + 1);

            if (!isAntiCollinear(p_prev, p_current, p_next)) { // If not anti-collinear, keep the point
                simplifiedContour.add(p_current);
                p_prev = p_current; //if we added a node, we can use it as previous on next step
            }
        }

        if (!isClosed) {
            simplifiedContour.add(originalContour.get(originalContour.size() - 1));
        }


        // If the simplified contour has less than 3 points, or if it's a closed contour that became open,
        // revert to the original contour to avoid invalid geometry.
        if (simplifiedContour.size() < 3) {
            return originalContour;
        }

        // Ensure closed contour remains closed if it was originally closed
        if (isClosed &&
                !(simplifiedContour.get(0).x == simplifiedContour.get(simplifiedContour.size() - 1).x &&
                        simplifiedContour.get(0).y == simplifiedContour.get(simplifiedContour.size() - 1).y)) {
            //Funny thing: it seems that all consequent logic does not expect "closed" ways, where first node is repeated as last one.
            // let's comment out and see what happens
            //simplifiedContour.add(simplifiedContour.get(0));
        }

        // Ensure the contour is counter-clockwise (CCW)
        if (isClosed && isClockwise(simplifiedContour)) {
            Collections.reverse(simplifiedContour);
        }

        return simplifiedContour;
    }

    public static boolean isClockwise(List<Point2D> polygon) {
        double sum = 0.0;
        for (int i = 0; i < polygon.size(); i++) {
            Point2D p1 = polygon.get(i);
            Point2D p2 = polygon.get((i + 1) % polygon.size());
            sum += (p2.x - p1.x) * (p2.y + p1.y);
        }
        return sum > 0;
    }

    // Calculate the 2D cross product and dot product of vectors ( p_prev-p_current) and (p_next - p_current)
    // If the tangent of the angle between them is close to zero, the points are collinear.
    private static boolean isAntiCollinear(Point2D p_prev, Point2D p_current, Point2D p_next) {
        double vec1_x = p_prev.x - p_current.x;
        double vec1_y = p_prev.y - p_current.y;
        double vec2_x = p_next.x - p_current.x;
        double vec2_y = p_next.y - p_current.y;

        double crossProduct = (vec1_x * vec2_y) - (vec1_y * vec2_x);
        double dotProduct = (vec1_x * vec2_x) + (vec1_y * vec2_y);

        boolean isAntiCollinear = false;
        if (dotProduct < 0) { //if dotProduct >=0, vectors are either perpendicular or sharp-angled.
            double tanAngle = crossProduct / dotProduct;
            if (Math.abs(tanAngle) < STRAIGHT_ANGLE_TAN_TOLERANCE) {
                isAntiCollinear = true;
            }
        }
        return isAntiCollinear;
    }

    public void toLocalCoords(LatLon origin) {
        if (this.mode.equals("XY")){
            //contour is already in local coords. nothing we can do
            return;
        }
        if (!this.mode.equals("LatLon")){
            throw new RuntimeException("Invalid coordinate system for conversion: '"+this.mode+"'");
        }

        for (ArrayList<Point2D> ring : outerRings) {
            for (int i = 0; i < ring.size(); i++) {
                ring.set(i, getLocalCoords(ring.get(i), origin));
            }

        }
        for (ArrayList<Point2D> ring : innerRings) {
            for (int i = 0; i < ring.size(); i++) {
                ring.set(i, getLocalCoords(ring.get(i), origin));
            }
        }
        this.mode = "XY";
    }

    public void removeRedundantNodes() {
        List<ArrayList<Point2D>> simplifiedOuterRings = new ArrayList<>();
        List<ArrayList<Point2D>> simplifiedInnerRings = new ArrayList<>();

        for (var ring: outerRings){
            simplifiedOuterRings.add( simplifyContour(ring));

        }

        for (var ring: innerRings){
            simplifiedInnerRings.add( simplifyContour(ring));
        }



        outerRings = simplifiedOuterRings;
        innerRings = simplifiedInnerRings;

    }
}
