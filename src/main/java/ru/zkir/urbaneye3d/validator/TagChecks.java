package ru.zkir.urbaneye3d.validator;

import static org.openstreetmap.josm.tools.I18n.tr;
import static ru.zkir.urbaneye3d.utils.Contour.isClockwise;
import static ru.zkir.urbaneye3d.utils.OsmDataWasher.parseDirection;

import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import ru.zkir.urbaneye3d.roofgenerators.RoofGenerator;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.Point2D;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Simple tests for tag validity, related to buildings and parts
 */
public class TagChecks extends Test {
    public static final int NO_HEIGHT_OR_LEVELS_SPECIFIED = 2001;
    public static final int ROOF_DIRECTION_MISSING = 2002;
    public static final int INVALID_ROOF_DIRECTION = 2003;
    public static final int INVALID_ROOF_ORIENTATION = 2004;
    public static final int ROOF_SHAPE_MANY_NOT_ALLOWED_FOR_PARTS = 2005;

    private static final List<String> VALID_ROOF_ORIENTATIONS = Arrays.asList("along", "across");

    public TagChecks() {
        super(tr("[UrbanEye3D] Tag validity checks"), tr("Checks for tag validity for buildings and building parts."));
    }

    @Override
    public void visit(Way w) {
        checkPrimitive(w);
    }

    @Override
    public void visit(Relation r) {
        checkPrimitive(r);
    }

    private void checkPrimitive(OsmPrimitive p) {
        if (!p.isUsable() || !(p.hasKey("building") || p.hasKey("building:part"))) {
            return;
        }

        String roofShape = p.get("roof:shape");
        if ("skillion".equals(roofShape) || "side_hipped".equals(roofShape)) {
            if (!p.hasKey("roof:direction")) {
                errors.add(TestError.builder(this, Severity.WARNING, ROOF_DIRECTION_MISSING)
                        .message(tr("Missing tag: roof:shape={0} without ''roof:direction''", roofShape))
                        .primitives(p)
                        .build());
            }
        }

        if (p.hasKey("roof:direction")) {
            String direction = p.get("roof:direction");
            if (parseDirection(direction).isNaN()) {
                errors.add(TestError.builder(this, Severity.WARNING, INVALID_ROOF_DIRECTION)
                        .message(tr("Invalid value for roof:direction: {0}", direction))
                        .primitives(p)
                        .build());
            }
        }

        if (p.hasKey("roof:orientation")) {
            String orientation = p.get("roof:orientation");
            if (!VALID_ROOF_ORIENTATIONS.contains(orientation)) {
                errors.add(TestError.builder(this, Severity.WARNING, INVALID_ROOF_ORIENTATION)
                        .message(tr("Invalid value for roof:orientation: {0}", orientation))
                        .primitives(p)
                        .build());
            }
        }

        if (p.hasKey("building:part") && !p.get("building:part").equals("no")) {
            if (!p.hasKey("height") && !p.hasKey("building:levels")) {
                errors.add(TestError.builder(this, Severity.WARNING, NO_HEIGHT_OR_LEVELS_SPECIFIED)
                        .message(tr("Building part without height"))
                        .primitives(p)
                        .build());
            }
            if ("many".equals(p.get("roof:shape"))) {
                errors.add(TestError.builder(this, Severity.WARNING, ROOF_SHAPE_MANY_NOT_ALLOWED_FOR_PARTS)
                        .message(tr("roof:shape=many is not allowed for building parts"))
                        .primitives(p)
                        .build());
            }
        }
    }

    @Override
    public boolean isFixable(TestError testError) {
        return testError.getCode() == ROOF_DIRECTION_MISSING;
    }

    @Override
    public Command fixError(TestError testError) {
        if (!isFixable(testError)) {
            return null;
        }

        OsmPrimitive p = testError.getPrimitives().iterator().next();
        if (!(p instanceof Way) && !(p instanceof Relation)) {
            return null;
        }

        Contour contour = new Contour(p);
        if (contour.outerRings.isEmpty()) {
            return null;
        }

        LatLon center = p.getBBox().getCenter();
        contour.toLocalCoords(center);

        ArrayList<Point2D> outerRing = contour.outerRings.get(0);
        if (outerRing.size() < 2) {
            return null;
        }

        int[] longestEdgeIndices = RoofGenerator.findLongestEdge(outerRing);
        Point2D p1 = outerRing.get(longestEdgeIndices[0]);
        Point2D p2 = outerRing.get(longestEdgeIndices[1]);

        Point2D slopeVector = new Point2D(-(p2.y - p1.y), p2.x - p1.x);

        if (!isClockwise(outerRing)) {
            slopeVector.x *= -1;
            slopeVector.y *= -1;
        }

        slopeVector.normalize();

        double angleRad = Math.atan2(slopeVector.x, slopeVector.y);
        double angleDeg = Math.toDegrees(angleRad);
        if (angleDeg < 0) {
            angleDeg += 360;
        }

        String direction = String.format(java.util.Locale.US, "%.2f", angleDeg);

        return new ChangePropertyCommand(Collections.singleton(p), "roof:direction", direction);
    }
}
