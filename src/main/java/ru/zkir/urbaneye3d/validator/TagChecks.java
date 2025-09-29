package ru.zkir.urbaneye3d.validator;

import static org.openstreetmap.josm.tools.I18n.tr;
import static ru.zkir.urbaneye3d.RenderableBuildingElement.parseDirection;

import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;

import java.util.Arrays;
import java.util.List;

/**
 * Simple tests for tag validity, related to buildings and parts
 */
public class TagChecks extends Test {
    public static final int NO_HEIGHT_OR_LEVELS_SPECIFIED = 2001;
    public static final int ROOF_DIRECTION_MISSING = 2002;
    public static final int INVALID_ROOF_DIRECTION = 2003;
    public static final int INVALID_ROOF_ORIENTATION = 2004;

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
        }
    }
}
