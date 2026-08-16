package ru.zkir.urbaneye3d.utils;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.*;

import org.locationtech.jts.operation.buffer.BufferParameters;
import ru.zkir.urbaneye3d.UrbanEye3dPlugin;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Contour {
    // Define a tolerance for the tangent of the angle. For example, 0.08 corresponds to ~175.5 degrees.
    // This allows for slight deviations in manually placed points.
    static final double STRAIGHT_ANGLE_TAN_TOLERANCE = 0.08;
    public String mode = "";
    public List<ArrayList<Point2D>> outerRings;
    public List<ArrayList<Point2D>> innerRings;
    
    /**
     * This constructor creates contour from POLYGONAL primitive (e.g. closed way or multipolygon relation)
     */
    public Contour(OsmPrimitive primitive) {
        this.mode = "LatLon";
        if (primitive instanceof Way) {
            Way way = (Way) primitive;
            this.outerRings = new ArrayList<>();
            this.innerRings = new ArrayList<>();
            ArrayList<Point2D> tempContour = new ArrayList<>();
            for (Node node : way.getNodes()) {
                tempContour.add(new Point2D(node.lon(), node.lat()));
            }
            this.outerRings.add(tempContour);
        } else { //relation
            Relation relation = (Relation) primitive;
            this.outerRings = new ArrayList<>();
            this.innerRings = new ArrayList<>();

            List<Way> outerWays = new ArrayList<>();
            List<Way> innerWays = new ArrayList<>();

            for (RelationMember member : relation.getMembers()) {
                if (!member.isWay() || member.getMember().isIncomplete()){
                    continue;
                }

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
                    pointRing.add(new Point2D(node.lon(), node.lat()));
                }
                this.outerRings.add(pointRing);
            }

            List<List<Node>> innerNodeRings = assembleRings(innerWays);
            for (List<Node> nodeRing : innerNodeRings) {
                ArrayList<Point2D> pointRing = new ArrayList<>();
                for (Node node : nodeRing) {
                    pointRing.add(new Point2D(node.lon(), node.lat()));
                }
                this.innerRings.add(pointRing);
            }
        }
    }

    /**
     * This constructor creates contour from LINEAR primitive (e.g. barrier) applying BUFFER.
     * Contour is created in local coords, because buffer operation should be done in local coords!
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

            // Gaps for gates and entrances
            Geometry finalGeom = polygon;
            for (int i = 0; i < way.getNodesCount(); i++) {
                Node node = way.getNode(i);
                if (node.hasTag("barrier", "gate") || node.hasTag("barrier", "lift_gate") || node.hasTag("barrier", "entrance")) {
                    Point2D localPos = getNodeLocalCoords(node, center);

                    // Calculate direction at this node index
                    Point2D dir = null;
                    if (way.getNodesCount() >= 2) {
                        if (i > 0 && i < way.getNodesCount() - 1) {
                            Point2D prev = getNodeLocalCoords(way.getNode(i - 1), center);
                            Point2D next = getNodeLocalCoords(way.getNode(i + 1), center);
                            dir = next.subtract(prev);
                        } else if (i == 0) {
                            Point2D next = getNodeLocalCoords(way.getNode(1), center);
                            dir = next.subtract(localPos);
                        } else {
                            Point2D prev = getNodeLocalCoords(way.getNode(i - 1), center);
                            dir = localPos.subtract(prev);
                        }
                    }

                    if (dir != null && dir.length() > 1e-6) {
                        double angle = Math.atan2(dir.y, dir.x);
                        double cosA = Math.cos(angle);
                        double sinA = Math.sin(angle);

                        // We want the gap to be ALONG the barrier.
                        // For entrances, we can respect the width tag.
                        // For gates, we MUST use a fixed width (3.5m) because the 3D models have fixed dimensions.
                        double gapWidth;
                        if (node.hasTag("barrier", "entrance")) {
                            double defaultEntranceWidth = 1.5;
                            gapWidth = OsmDataWasher.getTagD("width", node, 
                                          OsmDataWasher.getTagD("maxwidth:physical", node, defaultEntranceWidth));
                        } else {
                            // barrier=gate or barrier=lift_gate
                            gapWidth = 3.5;
                        }
                        
                        double hx = gapWidth / 2.0; 
                        double hy = Math.max(hx, width * 1.5); // enough to cover the barrier width

                        Coordinate[] gapCoords = new Coordinate[5];
                        double[][] offsets = {{-hx, -hy}, {hx, -hy}, {hx, hy}, {-hx, hy}, {-hx, -hy}};
                        for (int j = 0; j < 5; j++) {
                            double rx = offsets[j][0] * cosA - offsets[j][1] * sinA;
                            double ry = offsets[j][0] * sinA + offsets[j][1] * cosA;
                            gapCoords[j] = new Coordinate(localPos.x + rx, localPos.y + ry);
                        }
                        Polygon gap = geometryFactory.createPolygon(gapCoords);
                        finalGeom = finalGeom.difference(gap);
                    }
                }
            }

            if (finalGeom instanceof Polygon) {
                addPolygonToContour((Polygon) finalGeom);
            } else if (finalGeom instanceof MultiPolygon) {
                MultiPolygon mp = (MultiPolygon) finalGeom;
                for (int i = 0; i < mp.getNumGeometries(); i++) {
                    addPolygonToContour((Polygon) mp.getGeometryN(i));
                }
            }
        }
    }

    private void addPolygonToContour(Polygon p) {
        ArrayList<Point2D> polygonPoints1 = new ArrayList<>();
        for (Coordinate coord : p.getExteriorRing().getCoordinates()) {
            polygonPoints1.add(new Point2D(coord.x, coord.y));
        }
        this.outerRings.add(polygonPoints1);

        for (int i = 0; i < p.getNumInteriorRing(); i++) {
            ArrayList<Point2D> polygonPoints2 = new ArrayList<>();
            for (Coordinate coord : p.getInteriorRingN(i).getCoordinates()) {
                polygonPoints2.add(new Point2D(coord.x, coord.y));
            }
            this.innerRings.add(polygonPoints2);
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
        Geometry thisGeom = this.toJTSGeometry();
        Geometry otherGeom = other.toJTSGeometry();

        if (thisGeom == null || otherGeom == null) {
            return false;
        }

        Geometry thisGeomFixed = thisGeom.buffer(0);
        Geometry otherGeomFixed = otherGeom.buffer(0);

        // Add a tiny tolerance to 'thisGeom' so it robustly covers 'otherGeom'
        // even with slight precision issues or collinear points.
        thisGeomFixed = thisGeomFixed.buffer(0.05 / FlatEarth.GRAD_LENGTH_M, 1);

        return thisGeomFixed.covers(otherGeomFixed);
    }

    /**
     *  Returns JTS equivalent of the contour, correctly handling multiple outer and inner rings (multipolygons)
     */
    public Geometry toJTSGeometry() {
        GeometryFactory factory = new GeometryFactory();
        if (outerRings.isEmpty()) return null;

        List<Geometry> polyList = new ArrayList<>();
        for (ArrayList<Point2D> outerRing : outerRings) {
            Coordinate[] coords = toCoordinates(outerRing);
            if (coords.length < 4) continue;
            try {
                polyList.add(factory.createPolygon(factory.createLinearRing(coords), null).buffer(0));
            } catch (Exception e) {
                // Ignore invalid rings
            }
        }

        if (polyList.isEmpty()) return null;
        Geometry allOuters = UnaryUnionOp.union(polyList);

        if (innerRings.isEmpty()) return allOuters;

        List<Geometry> holeList = new ArrayList<>();
        for (ArrayList<Point2D> innerRing : innerRings) {
            Coordinate[] coords = toCoordinates(innerRing);
            if (coords.length < 4) continue;
            try {
                holeList.add(factory.createPolygon(factory.createLinearRing(coords), null).buffer(0));
            } catch (Exception e) {
                // Ignore
            }
        }

        if (holeList.isEmpty()) {
            return allOuters;
        }

        Geometry allInners = UnaryUnionOp.union(holeList);
        return allOuters.difference(allInners);
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
        return FlatEarth.getLocalCoords(node.lat(), node.lon(), center);
    }

    private static Point2D getLocalCoords(Point2D point, LatLon center) {
        return FlatEarth.getLocalCoords(point.y, point.x, center);
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

    public boolean isComplex() {
        return this.outerRings.size() > 1 || !this.innerRings.isEmpty();
    }

    private Coordinate[] toCoordinates(ArrayList<Point2D> ring) {
        Coordinate[] coords = new Coordinate[ring.size()];
        for (int i = 0; i < ring.size(); i++) {
            coords[i] = new Coordinate(ring.get(i).x, ring.get(i).y);
        }
        // Ensure the ring is closed for JTS
        if (coords.length > 0 && !coords[0].equals2D(coords[coords.length - 1])) {
            Coordinate[] closedCoords = new Coordinate[coords.length + 1];
            System.arraycopy(coords, 0, closedCoords, 0, coords.length);
            closedCoords[coords.length] = new Coordinate(coords[0].x, coords[0].y);
            return closedCoords;
        }
        return coords;
    }

    /**
     * Check that primitive has complete contour. We need to implement this check ourselves,
     * because JOSM interprets completeness of multipolygons in its own way
     */
    public static boolean hasCompleteContour(OsmPrimitive primitive) {
        if (primitive instanceof Way) {
            return !primitive.isIncomplete();
        }else if (primitive instanceof Relation) {
            for (RelationMember member : ((Relation) primitive).getMembers()) {
                if (member.getMember().isIncomplete()) {
                    return false;
                }
            }
            return true;
        }else {
            return false; // always false for nodes
        }
    }

    /**
     * Finds the largest inscribed axis-aligned rectangle within the first outer ring
     * oriented according to the given axis.
     */
    public Rectangle2D.Double findLargestInscribedRectangle(double ux, double uy) {
        if (outerRings.isEmpty()) return null;
        ArrayList<Point2D> ring = outerRings.get(0);
        int n = ring.size();
        if (n < 3) return null;

        // Normal vector nx, ny is perpendicular to ux, uy
        double nx = -uy;
        double ny = ux;

        List<Point2D> projected = new ArrayList<>();
        for (Point2D p : ring) {
            projected.add(new Point2D(p.x * ux + p.y * uy, p.x * nx + p.y * ny));
        }

        // 2. Find min/max indices to split into upper and lower chains
        int minIdx = 0, maxIdx = 0;
        for (int i = 1; i < projected.size(); i++) {
            if (projected.get(i).x < projected.get(minIdx).x) minIdx = i;
            if (projected.get(i).x > projected.get(maxIdx).x) maxIdx = i;
        }

        List<Point2D> chain1 = new ArrayList<>();
        List<Point2D> chain2 = new ArrayList<>();
        for (int i = minIdx; ; i = (i + 1) % n) {
            chain1.add(projected.get(i));
            if (i == maxIdx) break;
        }
        for (int i = maxIdx; ; i = (i + 1) % n) {
            chain2.add(projected.get(i));
            if (i == minIdx) break;
        }

        // Identify upper and lower chains
        double midX = (projected.get(minIdx).x + projected.get(maxIdx).x) / 2.0;
        double y1 = getYAt(chain1, midX);
        double y2 = getYAt(chain2, midX);
        List<Point2D> upper = y1 > y2 ? chain1 : chain2;
        List<Point2D> lower = y1 > y2 ? chain2 : chain1;

        // 3. Brute force pairs of X-coordinates
        List<Double> xCandidates = new ArrayList<>();
        for (var p : projected) xCandidates.add(p.x);
        Collections.sort(xCandidates);

        double maxArea = -1;
        Rectangle2D.Double bestRect = null;

        for (int i = 0; i < xCandidates.size(); i++) {
            for (int j = i + 1; j < xCandidates.size(); j++) {
                double x1 = xCandidates.get(i);
                double x2 = xCandidates.get(j);
                if (x2 - x1 < 0.1) continue;

                double minTop = Double.POSITIVE_INFINITY;
                double maxBottom = Double.NEGATIVE_INFINITY;

                for (double x : new double[]{x1, x2}) {
                    minTop = Math.min(minTop, getYAt(upper, x));
                    maxBottom = Math.max(maxBottom, getYAt(lower, x));
                }
                for (var p : upper) if (p.x > x1 && p.x < x2) minTop = Math.min(minTop, p.y);
                for (var p : lower) if (p.x > x1 && p.x < x2) maxBottom = Math.max(maxBottom, p.y);

                double h = minTop - maxBottom;
                if (h > 0.1) {
                    double area = (x2 - x1) * h;
                    if (area > maxArea) {
                        maxArea = area;
                        bestRect = new Rectangle2D.Double(x1, maxBottom, x2 - x1, h);
                    }
                }
            }
        }
        return bestRect;
    }

    private double getYAt(List<Point2D> chain, double x) {
        for (int i = 0; i < chain.size() - 1; i++) {
            var p1 = chain.get(i);
            var p2 = chain.get(i + 1);
            double xMin = Math.min(p1.x, p2.x);
            double xMax = Math.max(p1.x, p2.x);
            if (x >= xMin && x <= xMax) {
                if (Math.abs(p2.x - p1.x) < 1e-9) return (p1.y + p2.y) / 2.0;
                double t = (x - p1.x) / (p2.x - p1.x);
                return p1.y + t * (p2.y - p1.y);
            }
        }
        return chain.get(0).y;
    }



}
