package ru.zkir.josm.plugins.z3dviewer;

import com.drew.lang.annotations.NotNull;
import org.openstreetmap.josm.data.osm.Way;
import java.awt.Color;

public class RenderableBuildingElement {
    public final Way way;
    public final double height;
    public final double minHeight;
    public final @NotNull Color color;
    public final @NotNull Color  roofColor;

    public RenderableBuildingElement(Way way, double height, double minHeight, String wallColor, String roofColor) {
        this.way = way;
        this.height = height;
        this.minHeight = minHeight;

        this.color = parseColor(wallColor, new Color(204, 204, 204));
        this.roofColor = parseColor(roofColor, new Color(150, 150, 150));
    }

    private Color parseColor(String color, Color default_color){
        Color rgb_color = ColorUtils.parseColor(color);
        if (rgb_color == null) {
            rgb_color = default_color;
        }
        return rgb_color;
    }

}
