package ru.zkir.urbaneye3d.validator;

import static org.openstreetmap.josm.tools.I18n.tr;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 *  This class contains checks for building and building part spatial validity.
 *  It is used together with josm validator system.
 */
public class SpatialConsistencyChecks extends Test {

    private final GeometryFactory geometryFactory = new GeometryFactory();
    public static final int BUILDING_NOT_COVERED_BY_PARTS = 1001;
    public static final int ORPHANED_BUILDING_PART = 1002;
    public static final int BUILDING_HEIGHT_MISMATCH = 1003;
    private HashMap<OsmPrimitive, OsmPrimitive> partParents;

    public SpatialConsistencyChecks() {
        super(tr("[UrbanEye3D] Building and building parts spatial consistency check"), tr("Building outline should be fully covered by parts. Parts should not be orphans. Note this test may show false positives on incomplete data"));
    }

    /**
     * This method is invoked by JOSM (surprise!) before test starts, and we have a chance to perform some global
     * calculations, considering all the primitives in the current layer.
     * Since we are going to do geometric containment checks, we need to determine parent buildings and child parts
     */
    @Override
    public void startTest(ProgressMonitor monitor) {
        super.startTest(monitor);
        DataSet ds = OsmDataManager.getInstance().getActiveDataSet();
        if (ds == null) return;
        partParents = new HashMap<>();
        HashMap<OsmPrimitive, Contour> primitiveContours = new HashMap<>();
        ArrayList<OsmPrimitive> buildingParts = new ArrayList<>();
        ArrayList<OsmPrimitive> buildings = new ArrayList<>();

        for (OsmPrimitive primitive : ds.allPrimitives()) {
            if (primitive instanceof Node) {
                continue;
            }

            if (primitive.isUsable()) {
                if (primitive.hasKey("building:part") && !primitive.get("building:part").equals("no")) {
                    buildingParts.add(primitive);
                    primitiveContours.put(primitive, new Contour(primitive, null));
                } else if (primitive.hasKey("building")) {
                    buildings.add(primitive);
                    primitiveContours.put(primitive, new Contour(primitive, null));
                }
            }
        }

        for (OsmPrimitive building : buildings) {
            Contour buildingContour = primitiveContours.get(building);
            for (OsmPrimitive part : buildingParts) {
                if (building.getBBox().bounds(part.getBBox())) {
                    Contour partContour = primitiveContours.get(part);
                    if (buildingContour.contains(partContour)) {
                        partParents.put(part, building);
                    }
                }
            }
        }
    }

    @Override
    public void endTest() {
        super.endTest();
        partParents = null;
    }

    private double getPrimitiveHeight(OsmPrimitive p) {
        String heightStr = p.get("height");
        if (heightStr != null) {
            try {
                // Handle comma as decimal separator
                heightStr = heightStr.replace(',', '.');
                return Double.parseDouble(heightStr);
            } catch (NumberFormatException e) {
                // Ignore invalid height values
            }
        }

        String levelsStr = p.get("building:levels");
        if (levelsStr != null) {
            try {
                int levels = Integer.parseInt(levelsStr);
                return levels * 3.0; // Assume 3 meters per level
            } catch (NumberFormatException e) {
                // Ignore invalid levels values
            }
        }

        return 0.0; // No height information
    }


    private Polygon toJtsPolygon(Contour contour) {
        if (contour.outerRings.isEmpty()) {
            return null;
        }

        LinearRing shell = toLinearRing(contour.outerRings.get(0));
        if (shell == null) {
            return null;
        }

        List<LinearRing> holes = new ArrayList<>();
        for (List<Point2D> innerRing : contour.innerRings) {
            LinearRing hole = toLinearRing(innerRing);
            if (hole != null) {
                holes.add(hole);
            }
        }

        return geometryFactory.createPolygon(shell, holes.toArray(new LinearRing[0]));
    }

    private LinearRing toLinearRing(List<Point2D> points) {
        if (points.size() < 4) {
            // A linear ring must have at least 4 points (the first and last must be the same)
            return null;
        }
        Coordinate[] coordinates = new Coordinate[points.size()];
        for (int i = 0; i < points.size(); i++) {
            Point2D p = points.get(i);
            coordinates[i] = new Coordinate(p.x, p.y);
        }
        
        // Ensure the ring is closed
        if (!coordinates[0].equals(coordinates[coordinates.length - 1])) {
            Coordinate[] closedCoordinates = new Coordinate[coordinates.length + 1];
            System.arraycopy(coordinates, 0, closedCoordinates, 0, coordinates.length);
            closedCoordinates[coordinates.length] = coordinates[0];
            coordinates = closedCoordinates;
        }

        return geometryFactory.createLinearRing(coordinates);
    }

    @Override
    public void visit(Way w) {
        checkPrimitive(w);
    }

    @Override
    public void visit(Relation r) {
        checkPrimitive(r);
    }

    /**
     * Josm validator system invokes visit() method one by one per primitive.
     * Luckily, we have parent-children relationship hashmap prepared already in startTest()
     */
    private void checkPrimitive(OsmPrimitive p) {

        if (!p.isUsable() || !(p.hasKey("building") || p.hasKey("building:part"))) {
            return;
        }
        if (!hasCompleteContour(p)){
            return;
        }

        // the most complex check is whether parts fully cover buildings.
        // we invoke JTS (java topology suite) to do the job for us.
        if (p.hasKey("building") && (!p.hasKey("building:part")  || p.get("building:part").equals("no"))) {
            List<OsmPrimitive> parts = new ArrayList<>();
            //here we do some kind of reverse search and find the list of parts of the particular building.
            for (HashMap.Entry<OsmPrimitive, OsmPrimitive> entry : partParents.entrySet()) {
                if (entry.getValue().equals(p)) {
                    parts.add(entry.getKey());
                }
            }

            if (!parts.isEmpty()) {
                // Coverage check
                List<Polygon> partPolygons = new ArrayList<>();
                for(OsmPrimitive part : parts) {
                    if (!hasCompleteContour(part)){
                        //it happened somehow, that a child part is not fully loaded.
                        //probably we should have identified that earlier, but anyway
                        //we cannot continue test of this building
                        return;
                    }
                    Contour partContour = new Contour(part, null);
                    if (partContour.outerRings.size() > 1){
                        //TODO: we do not know how to deal with multipolygons with multiple outer rings.
                        //The only thing we can do is skip them for now.
                        return;
                    }
                    Polygon partPolygon = toJtsPolygon(partContour);
                    if (partPolygon != null && !partPolygon.isEmpty()) {
                        partPolygons.add(partPolygon);
                    }
                }

                if (!partPolygons.isEmpty()) {
                    Contour buildingContour = new Contour(p, null);
                    Polygon buildingPolygon = toJtsPolygon(buildingContour);
                    if (buildingPolygon != null && !buildingPolygon.isEmpty()) {
                        Geometry partsUnion = UnaryUnionOp.union(partPolygons);
                        if (!partsUnion.covers(buildingPolygon)) {
                            errors.add(TestError.builder(this, Severity.WARNING, BUILDING_NOT_COVERED_BY_PARTS)
                                    .message(tr("Building is not fully covered by its parts"))
                                    .primitives(p)
                                    .build());
                        }
                    }
                }

                // Height check
                double maxPartHeight = 0.0;
                for (OsmPrimitive part : parts) {
                    double partHeight = getPrimitiveHeight(part);
                    if (partHeight > maxPartHeight) {
                        maxPartHeight = partHeight;
                    }
                }

                if (maxPartHeight > 0) {
                    double buildingHeight = getPrimitiveHeight(p);
                    if (buildingHeight > 0 && Math.abs(buildingHeight - maxPartHeight) / Math.max(buildingHeight, maxPartHeight) > 0.1) {
                        errors.add(TestError.builder(this, Severity.WARNING, BUILDING_HEIGHT_MISMATCH)
                                .message(tr("Building height ({0}m) and calculated part height ({1}m) differ significantly",
                                        String.format("%.1f", buildingHeight), String.format("%.1f", maxPartHeight)))
                                .primitives(p)
                                .build());
                    }
                }
            }
        }

        //the other check is just a value comparisons.
        if (p.hasKey("building:part") && !p.get("building:part").equals("no")) {
            if (!partParents.containsKey(p)) {
                errors.add(TestError.builder(this, Severity.WARNING, ORPHANED_BUILDING_PART)
                        .message(tr("Orphaned building part"))
                        .primitives(p)
                        .build());
            }

        }
    }

    /**
     * Check that primitive has complete contour. We need to implement this check ourselves,
     * because JOSM interprets completeness of multipolygons in it's own way
     */
    private boolean hasCompleteContour(OsmPrimitive primitive) {
        if (primitive instanceof Way) {
            return !primitive.isIncomplete();
        }else if (primitive instanceof Relation) {
            for (RelationMember member : ((Relation) primitive).getMembers()) {
                if (member.getMember().isIncomplete()) {
                    return false;
                }
            }
            return true;
        }else {
            return false; // always false for nodes
        }

    }

    /* It seems that this hack is no longer necessary. DO NOT UNCOMMENT!!!
    @Override
    public void removeIrrelevantErrors(java.util.Collection<? extends OsmPrimitive> given) {
        // Do nothing to prevent JOSM from filtering our errors
    }
    */
}