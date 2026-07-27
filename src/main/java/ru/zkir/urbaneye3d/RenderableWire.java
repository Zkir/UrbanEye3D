package ru.zkir.urbaneye3d;

import org.openstreetmap.josm.data.osm.OsmPrimitive;
import ru.zkir.urbaneye3d.utils.Point3D;
import java.util.List;

/**
 * A lightweight class to represent a sagging power line wire in the 3D scene.
 */
public class RenderableWire {
    public final List<Point3D> points;
    public final float lineWidth;
    public final OsmPrimitive primitive;
    public final org.openstreetmap.josm.data.coor.LatLon origin;

    public RenderableWire(OsmPrimitive primitive, org.openstreetmap.josm.data.coor.LatLon origin, List<Point3D> points, float lineWidth) {
        this.primitive = primitive;
        this.origin = origin;
        this.points = points;
        this.lineWidth = lineWidth;
    }
}
