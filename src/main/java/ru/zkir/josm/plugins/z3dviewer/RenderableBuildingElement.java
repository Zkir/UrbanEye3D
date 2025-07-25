package ru.zkir.josm.plugins.z3dviewer;

import com.drew.lang.annotations.NotNull;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RenderableBuildingElement {

    public static class Contour {
        // Define a tolerance for the tangent of the angle. For example, 0.08 corresponds to ~175.5 degrees.
        // This allows for slight deviations in manually placed points.
        static final double STRAIGHT_ANGLE_TAN_TOLERANCE = 0.08;
        ArrayList<Point3D> contour;

        Contour(Way way, LatLon center){
            ArrayList<Point3D> tempContour = new ArrayList<>();
            for (Node node : way.getNodes()) {
                tempContour.add(getNodeLocalCoords(node, center));
            }
            this.contour = simplifyContour(tempContour);
        }

        Contour(Relation relation, LatLon center) {
            List<Way> ways = new ArrayList<>();
            for (RelationMember member : relation.getMembers()) {
                if ("outer".equals(member.getRole()) && member.isWay() && !member.getMember().isIncomplete()) {
                    ways.add(member.getWay());
                }
            }

            if (ways.isEmpty()) {
                this.contour = new ArrayList<>();
                return;
            }

            List<Node> assembledNodes = new ArrayList<>(ways.get(0).getNodes());
            ways.remove(0);

            while (!ways.isEmpty()) {
                boolean foundNext = false;
                for (int i = 0; i < ways.size(); i++) {
                    Way way = ways.get(i);
                    if (way.getNodesCount() < 2) continue; // Skip ways with less than 2 nodes

                    Node lastAssembledNode = assembledNodes.get(assembledNodes.size() - 1);
                    Node wayFirstNode = way.firstNode();
                    Node wayLastNode = way.lastNode();

                    if (wayFirstNode == null || wayLastNode == null) continue; // Skip incomplete ways

                    if (wayFirstNode.equals(lastAssembledNode)) {
                        assembledNodes.addAll(way.getNodes().subList(1, way.getNodesCount()));
                        ways.remove(i);
                        foundNext = true;
                        break;
                    } else if (wayLastNode.equals(lastAssembledNode)) {
                        List<Node> reversedNodes = new ArrayList<>(way.getNodes());
                        Collections.reverse(reversedNodes);
                        assembledNodes.addAll(reversedNodes.subList(1, reversedNodes.size()));
                        ways.remove(i);
                        foundNext = true;
                        break;
                    }
                }
                if (!foundNext) {
                    // Relation is broken
                    break;
                }
            }

            ArrayList<Point3D> tempContour = new ArrayList<>();
            for (Node node : assembledNodes) {
                tempContour.add(getNodeLocalCoords(node, center));
            }
            this.contour = simplifyContour(tempContour);
        }

        static Point3D getNodeLocalCoords(Node node, LatLon center){
            LatLon ll = node.getCoor();
            double dx = ll.lon() - center.lon();
            double dy = ll.lat() - center.lat();
            return new Point3D(dx * Math.cos(Math.toRadians(center.lat())) * 111320.0,
                    dy * 111320.0, 0);
        }


        static ArrayList<Point3D> simplifyContour(ArrayList<Point3D> originalContour) {
            if (originalContour.size() < 3) {
                return originalContour; // Cannot simplify a line or a single point
            }

            ArrayList<Point3D> simplifiedContour = new ArrayList<>();
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
                Point3D p_prev;
                if (i==0){
                    p_prev = originalContour.get(numPoints - 1); //for node 0 previous is second to last :)
                } else {
                    p_prev = originalContour.get(i - 1);
                }
                Point3D p_current = originalContour.get(i);
                Point3D p_next = originalContour.get(i + 1);

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
        private static boolean isAntiCollinear(Point3D p_prev, Point3D p_current, Point3D p_next) {
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
    private final Contour contour;

    public RenderableBuildingElement(Contour contour, double height, double minHeight, double roofHeight, String wallColor, String roofColor, String roofShape) {
        this.contour = contour;
        this.height = height;
        this.minHeight = minHeight;
        this.roofHeight = roofHeight;
        this.roofShape = RoofShapes.fromString(roofShape);

        this.color = parseColor(wallColor, new Color(204, 204, 204));
        this.roofColor = parseColor(roofColor, new Color(150, 150, 150));
    }

    public List<Point3D> getContour() {
        return contour.contour;
    }

    private Color parseColor(String color, Color default_color){
        Color rgb_color = ColorUtils.parseColor(color);
        if (rgb_color == null) {
            rgb_color = default_color;
        }
        return rgb_color;
    }
}