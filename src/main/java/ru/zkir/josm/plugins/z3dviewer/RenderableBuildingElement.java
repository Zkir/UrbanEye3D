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
    public static class Point3D {
        double x, y, z;
        Point3D(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }

        public Point3D normalize() {
            double length = Math.sqrt(x * x + y * y + z * z);
            if (length > 0) {
                return new Point3D(x / length, y / length, z / length);
            }
            return new Point3D(0, 0, 0);
        }

        public double dot(Point3D other) {
            return this.x * other.x + this.y * other.y + this.z * other.z;
        }
    }

    public static class Contour{
        ArrayList<Point3D> contour;
        Contour(Way way, LatLon center){
            ArrayList<Point3D> contour = new ArrayList<>();
            for (Node node : way.getNodes()) {
                contour.add(getNodeLocalCoords(node, center));
            }
            this.contour = contour;
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

            this.contour = new ArrayList<>();
            for (Node node : assembledNodes) {
                contour.add(getNodeLocalCoords(node, center));
            }
        }

        static Point3D getNodeLocalCoords(Node node, LatLon center){
            LatLon ll = node.getCoor();
            double dx = ll.lon() - center.lon();
            double dy = ll.lat() - center.lat();
            return new Point3D(dx * Math.cos(Math.toRadians(center.lat())) * 111320.0,
                    dy * 111320.0, 0);
        }
    }

    public final double height;
    public final double minHeight;
    public final @NotNull Color color;
    public final @NotNull Color roofColor;
    private final Contour contour;

    public RenderableBuildingElement(Contour contour, double height, double minHeight, String wallColor, String roofColor) {
        this.contour = contour;
        this.height = height;
        this.minHeight = minHeight;

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
