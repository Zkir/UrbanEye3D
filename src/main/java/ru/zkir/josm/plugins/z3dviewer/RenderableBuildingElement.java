package ru.zkir.josm.plugins.z3dviewer;

import com.drew.lang.annotations.NotNull;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.*;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RenderableBuildingElement {

    public static class Contour {
        // Define a tolerance for the tangent of the angle. For example, 0.08 corresponds to ~175.5 degrees.
        // This allows for slight deviations in manually placed points.
        static final double STRAIGHT_ANGLE_TAN_TOLERANCE = 0.08;
        List<ArrayList<Point2D>> outerRings;
        List<ArrayList<Point2D>> innerRings;

        Contour(Way way, LatLon center){
            this.outerRings = new ArrayList<>();
            this.innerRings = new ArrayList<>();
            ArrayList<Point2D> tempContour = new ArrayList<>();
            for (Node node : way.getNodes()) {
                tempContour.add(getNodeLocalCoords(node, center));
            }
            this.outerRings.add(simplifyContour(tempContour));
        }

        Contour(Relation relation, LatLon center) {
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
                    pointRing.add(getNodeLocalCoords(node, center));
                }
                this.outerRings.add(simplifyContour(pointRing));
            }

            List<List<Node>> innerNodeRings = assembleRings(innerWays);
            for (List<Node> nodeRing : innerNodeRings) {
                ArrayList<Point2D> pointRing = new ArrayList<>();
                for (Node node : nodeRing) {
                    pointRing.add(getNodeLocalCoords(node, center));
                }
                this.innerRings.add(simplifyContour(pointRing));
            }
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
            LatLon ll = node.getCoor();
            double dx = ll.lon() - center.lon();
            double dy = ll.lat() - center.lat();
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
                simplifiedContour.add(simplifiedContour.get(0));
            }

            return simplifiedContour;
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
    }

    public final double height;
    public final double minHeight;
    public final double roofHeight;
    public final @NotNull Color color;
    public final @NotNull Color roofColor;
    public final RoofShapes roofShape;
    public final double roofDirection;
    private final Contour contour;
    public final LatLon origin;

    public RenderableBuildingElement(OsmPrimitive primitive, double height, double minHeight, double roofHeight, String wallColor, String roofColor, String roofShape, String roofDirectionStr) {
        this.origin = primitive.getBBox().getCenter();

        if (primitive instanceof Way) {
            this.contour = new Contour( (Way) primitive, this.origin);

        } else if (primitive instanceof Relation) {
            this.contour = new Contour((Relation)primitive, this.origin);
        } else {
            this.contour = null;
        }


        this.height = height;
        this.minHeight = minHeight;
        this.roofShape = RoofShapes.fromString(roofShape);
        this.roofDirection = parseDirection(roofDirectionStr);

        //default value for roofHeight
        if (this.roofShape!=RoofShapes.FLAT && roofHeight==0){
            roofHeight=3.0;
        }
        if (roofHeight>height-minHeight){
            roofHeight=height-minHeight;
        }
        this.roofHeight = roofHeight;

        this.color = parseColor(wallColor, new Color(204, 204, 204));
        this.roofColor = parseColor(roofColor, new Color(150, 150, 150));
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