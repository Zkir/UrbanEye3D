package ru.zkir.urbaneye3d.utils;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.strtree.STRtree;
import org.openstreetmap.josm.data.coor.LatLon;
import ru.zkir.urbaneye3d.RenderableElement;

import java.util.ArrayList;
import java.util.List;

/**
 * A spatial index of the mesh of every rendered building of the scene,
 * which allows to find the height (z) of the roof surface above a given point.
 *
 * <p>It is used for point objects standing on a roof ({@code location=roof}, e.g. a chimney
 * or a solar panel). If such an object does not have an explicit {@code min_height} tag,
 * its vertical position is taken from the roof of the building below it.
 *
 * <p>The height is the topmost intersection of the vertical line going through the given
 * point with the building mesh. So, for a building consisting of several parts (a tower
 * on a lower base, for example) the object is placed on the highest surface.
 */
public class BuildingSpatialIndex {

    /** Padding (degrees) of the search envelope. It is used for the coarse search only. */
    private static final double ENVELOPE_PADDING_DEG = 1e-9;


    /**  This is a younger brother of the RenderableBuildingElement class
     *   It has Envelope required by STRtree
     * */
    private static final class Element {
        /** Origin (LatLon) of the local cartesian system in which the mesh is defined */
        private final LatLon origin;
        private final Mesh mesh;
        /** Bounding box of the mesh footprint, in degrees (lon/lat) */
        private final Envelope bounds;

        private Element(LatLon origin, Mesh mesh, Envelope bounds) {
            this.origin = origin;
            this.mesh = mesh;
            this.bounds = bounds;
        }
    }

    private final List<Element> buildingElements = new ArrayList<>();
    private STRtree index = null;

    /**
     * Adds a building mesh to the index.
     * @param element -- renderable element which should be added (we are interested in mesh and origin)
     */
    public void add(RenderableElement element) {
        LatLon origin = element.origin;
        Mesh mesh = element.getMesh();
        if (origin == null || mesh == null || mesh.verts.isEmpty()) {
            return;
        }
        Point3D min = mesh.getMinBounds();
        Point3D max = mesh.getMaxBounds();

        LatLon corner1 = FlatEarth.fromLocalCoords(min.x, min.y, origin);
        LatLon corner2 = FlatEarth.fromLocalCoords(max.x, max.y, origin);

        Envelope bounds = new Envelope(
                Math.min(corner1.lon(), corner2.lon()) - ENVELOPE_PADDING_DEG,
                Math.max(corner1.lon(), corner2.lon()) + ENVELOPE_PADDING_DEG,
                Math.min(corner1.lat(), corner2.lat()) - ENVELOPE_PADDING_DEG,
                Math.max(corner1.lat(), corner2.lat()) + ENVELOPE_PADDING_DEG);

        buildingElements.add(new Element(origin, mesh, bounds));
        index = null; //the previously built index is not valid anymore
    }

    public boolean isEmpty() {
        return buildingElements.isEmpty();
    }

    /**
     * Finds the height of the topmost roof surface above the given point.
     * @param coor geographical coordinate of the query point
     * @return z of the roof in meters above ground, or null if there is no roof above this point
     */
    public Double getRoofHeight(LatLon coor) {
        if (coor == null || buildingElements.isEmpty()) {
            return null;
        }
        if (index == null) {
            STRtree newIndex = new STRtree();
            for (Element surface : buildingElements) {
                newIndex.insert(surface.bounds, surface);
            }
            index = newIndex;
        }

        Envelope query = new Envelope(coor.lon(), coor.lon(), coor.lat(), coor.lat());
        List<?> candidates = index.query(query);

        double topmost = Double.NaN;
        for (Object candidate : candidates) {
            Element surface = (Element) candidate;
            Point2D local = FlatEarth.getLocalCoords(coor, surface.origin);
            double z = surface.mesh.getTopmostVerticalRayIntersection(local.x, local.y);
            if (!Double.isNaN(z) && (Double.isNaN(topmost) || z > topmost)) {
                topmost = z;
            }
        }

        //min_height=0 means "on the ground", there is nothing to infer
        if (Double.isNaN(topmost) || topmost <= 0) {
            return null;
        }
        return topmost;
    }

}
