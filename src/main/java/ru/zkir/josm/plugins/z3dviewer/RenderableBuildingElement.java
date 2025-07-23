package ru.zkir.josm.plugins.z3dviewer;

import com.drew.lang.annotations.NotNull;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class RenderableBuildingElement {
    public static class Point3D {
        double x, y, z;
        Point3D(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    }

    public static class Contour{
        ArrayList<Point3D> contour;
        Contour(Way way, EastNorth center){
            ArrayList<Point3D> contour = new ArrayList<>();
            for (Node node : way.getNodes()) {
                EastNorth en = node.getEastNorth();
                contour.add(new Point3D(en.east() - center.east(), en.north() - center.north(), 0));
            }
            this.contour = contour;
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
