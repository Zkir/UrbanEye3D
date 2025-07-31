package ru.zkir.urbaneye3d;

import com.drew.lang.annotations.NotNull;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.*;
import ru.zkir.urbaneye3d.utils.ColorUtils;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.roofgenerators.RoofShapes;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RenderableBuildingElement {

    public static class Contour {
        // Define a tolerance for the tangent of the angle. For example, 0.08 corresponds to ~175.5 degrees.
        // This allows for slight deviations in manually placed points.
        static final double STRAIGHT_ANGLE_TAN_TOLERANCE = 0.08;
        private String mode="XY";
        List<ArrayList<Point2D>> outerRings;
        List<ArrayList<Point2D>> innerRings;

        Contour(OsmPrimitive primitive, LatLon center){
            if (center==null) {
                this.mode = "LatLon";
            }else{
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
                    }else{
                        tempContour.add(new Point2D(node.lon(),node.lat()));
                    }
                }
                this.outerRings.add(simplifyContour(tempContour));
            }else { //relation
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
                        }else{
                            pointRing.add(new Point2D(node.lon() , node.lat()));
                        }

                    }
                    this.outerRings.add(simplifyContour(pointRing));
                }

                List<List<Node>> innerNodeRings = assembleRings(innerWays);
                for (List<Node> nodeRing : innerNodeRings) {
                    ArrayList<Point2D> pointRing = new ArrayList<>();
                    for (Node node : nodeRing) {
                        if ("XY".equals(this.mode)) {
                            pointRing.add(getNodeLocalCoords(node, center));
                        }else{
                            pointRing.add(new Point2D(node.lon() , node.lat()));
                        }
                    }
                    this.innerRings.add(simplifyContour(pointRing));
                }
            }
        }

        Contour(ArrayList<Point2D> outerRing) {
            this.outerRings = new ArrayList<>();
            this.outerRings.add(outerRing);
            this.innerRings = new ArrayList<>();
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
                        if (isPointInside(buildingInnerRing, point)) {
                            return false; // Part is inside a hole of the building.
                        }
                    }
                }
            }

            // If all checks pass, the part is considered to be inside the building.
            return true;
        }

        private boolean isPointInside(List<Point2D> polygon, Point2D point) {
            if (isPointOnBorder(polygon, point)) {
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

        static Point2D getNodeLocalCoords(Node node, LatLon center){
            return getLocalCoords(new Point2D(node.lon(),node.lat()), center);
        }

        static Point2D getLocalCoords(Point2D point, LatLon center){
            double dx = point.x - center.lon();
            double dy = point.y - center.lat();
            return new Point2D(dx * Math.cos(Math.toRadians(center.lat())) * 111320.0,
                    dy * 111320.0);
        }


        static ArrayList<Point2D> simplifyContour(ArrayList<Point2D> originalContour) {
            if (originalContour.size() < 3) {
                return originalContour; // Cannot simplify a line or a single point
            }

            ArrayList<Point2D> simplifiedContour = new ArrayList<>();
            boolean isClosed = originalContour.get(0).x == originalContour.get(originalContour.size() - 1).x &&
                               originalContour.get(0).y == originalContour.get(originalContour.size() - 1).y;


            int start_index=0;
            if (!isClosed) {
                start_index=1;
                simplifiedContour.add(originalContour.get(0));
            }
            int numPoints = originalContour.size()-1; // Don't process the  last point. In closed loop it's duplicate. in open way it cannot be removed.

            //special check for the first node

            for (int i = start_index; i < numPoints; i++) {
                //special check for the first (#0) node
                Point2D p_prev;
                if (i==0){
                    p_prev = originalContour.get(numPoints - 1); //for node 0 previous is second to last :)
                } else {
                    p_prev = originalContour.get(i - 1);
                }
                Point2D p_current = originalContour.get(i);
                Point2D p_next = originalContour.get(i + 1);

                if (!isAntiCollinear(p_prev, p_current, p_next)) { // If not anti-collinear, keep the point
                    simplifiedContour.add(p_current);
                }
            }

            if (!isClosed) {
                simplifiedContour.add(originalContour.get(originalContour.size()-1));
            }


            // If the simplified contour has less than 3 points, or if it's a closed contour that became open,
            // revert to the original contour to avoid invalid geometry.
            if (simplifiedContour.size() < 3)  {
                return originalContour;
            }

            // Ensure closed contour remains closed if it was originally closed
            if (isClosed  &&
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

    private static boolean isClockwise(List<Point2D> polygon) {
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
            this.mode = "XY";
            for (ArrayList<Point2D> ring:outerRings){
                for (int i=0;i< ring.size();i++){
                    ring.set(i, getLocalCoords(ring.get(i),origin));
                }

            }
            for (ArrayList<Point2D> ring:innerRings){
                for (int i=0;i< ring.size();i++){
                    ring.set(i, getLocalCoords(ring.get(i),origin));
                }
            }
        }
    }

    public final double roofHeight;
    public final double minHeight;  // z0 -- z-coordinate of building bottom
    public final double wallHeight; // z1 -- z coordinate of walls top
    public final double height;     // z2 -- z coordinate of roof top
    public final @NotNull Color color;
    public final @NotNull Color roofColor;
    public final @NotNull Color bottomColor;
    public final RoofShapes roofShape;
    public final double roofDirection;
    public final @NotNull String roofOrientation;
    private final Contour contour;
    public final LatLon origin;
    private Mesh mesh;

    public RenderableBuildingElement(LatLon origin, Contour contour, double height, double minHeight, double roofHeight, String wallColor, String roofColor, String roofShape, String roofDirectionStr, String roofOrientation) {
        if (contour==null){
            throw new RuntimeException("contour must be specified");
        }

        this.origin = origin;
        if (contour.outerRings.isEmpty()){
            throw new RuntimeException("There can be empty multipolygon relations, broken or not fully downloaded. " +
                                       "However, renderable building cannot be created without outer ring. " +
                                       "This condition should be checked outside this constructor."
                                    );
        }
        this.contour = contour;



        this.height = height;
        this.minHeight = minHeight;

        //default value for roofHeight
        if (roofShape.isEmpty()){
            roofShape="flat";
        }

        if (!roofShape.equals("flat") && roofHeight == 0) { //its a bug. original string value should be tested.
            roofHeight = 3.0;
        }

        if (roofHeight>height-minHeight){
            roofHeight=height-minHeight;
        }

        if (this.hasComplexContour() || roofHeight == 0){
            //in case outline has inner rings, we cannot construct any other roof, but FLAT
            // also, if roof's height is zero, it's flat!
            this.roofShape = RoofShapes.FLAT;
        }else{
            this.roofShape = RoofShapes.fromString(roofShape);
        }

        this.roofDirection = parseDirection(roofDirectionStr);
        if (roofOrientation==null){
            roofOrientation="";
        }
        this.roofOrientation = roofOrientation;

        this.roofHeight = roofHeight;
        this.wallHeight = height - roofHeight;

        this.color = parseColor(wallColor, new Color(204, 204, 204));
        this.roofColor = parseColor(roofColor, new Color(150, 150, 150));
        this.bottomColor = this.color.darker().darker(); //Fake AO LOL!
        
        //since we have all the data, we can compose building mesh right in constructor.
        composeMesh();
    }

    public boolean hasComplexContour() {
        return this.getContourOuterRings().size() > 1 || !this.getContourInnerRings().isEmpty();
    }

    public List<Point2D> getContour() {
        return contour.outerRings.isEmpty() ? new ArrayList<>() : contour.outerRings.get(0);
    }

    public List<ArrayList<Point2D>> getContourOuterRings() {
        return contour.outerRings;
    }

    public List<ArrayList<Point2D>> getContourInnerRings() {
        return contour.innerRings;
    }

    public Mesh getMesh() {
        return this.mesh;

    }
    public void composeMesh(){
        this.mesh = null;
        double wallHeight = height - roofHeight;

        // Always generate flat roof if roofShape is FLAT or if it's a complex contour
        if ( !hasComplexContour()) {
            // Existing logic for other roof shapes (only for simple contours)
            List<Point2D> basePoints = getContour(); // This will return the first outer ring
            this.mesh = roofShape.getMesher().generate(this);
        }

        //last chance! mesh can be null, in case specific roof shapes was not created due to limitations
        // for example, GABLED and HIPPED can be created for quadrangles only.
        if( this.mesh == null){
            // Collect all contours (outer and inner) for flat roof generation
            this.mesh = RoofShapes.FLAT.getMesher().generate(this);
        }
    }


    private double parseDirection(String direction) {
        if (direction == null || direction.isEmpty()) {
            return Double.NaN; // Return NaN if direction is not specified
        }
        try {
            return Double.parseDouble(direction);
        } catch (NumberFormatException e) {
            // Handle cardinal directions (N, S, E, W, etc.)
            switch (direction.toUpperCase()) {
                case "N": return 0;
                case "NNE": return 22.5;
                case "NE": return 45;
                case "ENE": return 67.5;
                case "E": return 90;
                case "ESE": return 112.5;
                case "SE": return 135;
                case "SSE": return 157.5;
                case "S": return 180;
                case "SSW": return 202.5;
                case "SW": return 225;
                case "WSW": return 247.5;
                case "W": return 270;
                case "WNW": return 292.5;
                case "NW": return 315;
                case "NNW": return 337.5;
                default: return Double.NaN;
            }
        }
    }

    private Color parseColor(String color, Color default_color){
        Color rgb_color = ColorUtils.parseColor(color);
        if (rgb_color == null) {
            rgb_color = default_color;
        }
        return rgb_color;
    }
}