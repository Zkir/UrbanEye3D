package ru.zkir.urbaneye3d.validator;

import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.Point2D;

import java.util.*;

import static org.openstreetmap.josm.tools.I18n.tr;

public class OverlappingWallsCheck extends Test {
    public static final int OVERLAPPING_3D_WALLS = 3001;

    private List<OsmPrimitive> primitivesToCheck;

    public OverlappingWallsCheck() {
        super(tr("[UrbanEye3D] Overlapping 3D walls check"),
              tr("Detects overlapping 3D walls that cause Z-fighting."));
    }

    @Override
    public void startTest(ProgressMonitor monitor) {
        super.startTest(monitor);
        primitivesToCheck = new ArrayList<>();
    }

    @Override
    public void visit(Way w) {
        if (isRenderable(w)) primitivesToCheck.add(w);
    }

    @Override
    public void visit(Relation r) {
        if (isRenderable(r)) primitivesToCheck.add(r);
    }

    private boolean isRenderable(OsmPrimitive p) {
        if (!p.isUsable()) return false;
        return p.hasKey("building") || p.hasKey("building:part");
    }

    @Override
    public void endTest() {
        if (primitivesToCheck == null || primitivesToCheck.isEmpty()) {
            super.endTest();
            return;
        }

        // 1. Identify building parts and candidate buildings, and cache their contours
        List<OsmPrimitive> buildingParts = new ArrayList<>();
        List<OsmPrimitive> buildingCandidates = new ArrayList<>();
        Map<OsmPrimitive, Contour> primitiveContours = new HashMap<>();

        for (OsmPrimitive p : primitivesToCheck) {
            if (!hasCompleteContour(p)){
                continue;
            }

            Contour contour = new Contour(p);
            if (contour.outerRings.isEmpty()) continue;
            primitiveContours.put(p, contour);

            if (p.hasKey("building:part") && !p.get("building:part").equals("no")) {
                buildingParts.add(p);
            } else if (p.hasKey("building") && !p.get("building").equals("no")) {
                buildingCandidates.add(p);
            }
        }

        // 2. Identify "suppressed" buildings (those that contain at least one part)
        Set<OsmPrimitive> suppressedBuildings = new HashSet<>();
        for (OsmPrimitive building : buildingCandidates) {
            Contour bContour = primitiveContours.get(building);
            for (OsmPrimitive part : buildingParts) {
                // Optimization: check BBox first
                if (building.getBBox().bounds(part.getBBox())) {
                    Contour pContour = primitiveContours.get(part);
                    if (bContour.contains(pContour)) {
                        suppressedBuildings.add(building);
                        break; // One part is enough to suppress the building
                    }
                }
            }
        }

        Map<OrientedSegment, List<PrimitiveZRange>> segmentMap = new HashMap<>();

        // 3. Process segments, skipping suppressed buildings
        for (OsmPrimitive p : primitivesToCheck) {
            if (suppressedBuildings.contains(p)) {
                continue;
            }

            Contour contour = primitiveContours.get(p);
            if (contour == null) continue;

            //roof is ignored, because if it is not flat, roof faces are located elsewhere
            double zMax = getPrimitiveHeight(p) - getPrimitiveRoofHeight(p);
            double zMin = getPrimitiveMinHeight(p);

            // Skip objects with zero or negative height
            if (zMax <= zMin) continue;

            PrimitiveZRange zRange = new PrimitiveZRange(p, zMin, zMax);

            // Process outer rings (Normalize to CCW)
            for (List<Point2D> ring : contour.outerRings) {
                addRingToMap(ring, zRange, segmentMap, false);
            }

            // Process inner rings (Normalize to CW)
            for (List<Point2D> ring : contour.innerRings) {
                addRingToMap(ring, zRange, segmentMap, true);
            }
        }

        Set<Set<OsmPrimitive>> conflictPairs = new HashSet<>();

        for (Map.Entry<OrientedSegment, List<PrimitiveZRange>> entry : segmentMap.entrySet()) {
            OrientedSegment seg = entry.getKey();
            List<PrimitiveZRange> ranges = entry.getValue();
            if (ranges.size() < 2) continue;

            // Find all opposing walls for this geometric segment
            OrientedSegment oppositeSeg = seg.getOpposite();
            List<PrimitiveZRange> oppositeRanges = segmentMap.get(oppositeSeg);
            ZIntervalList coveringShield = null;
            if (oppositeRanges != null) {
                coveringShield = new ZIntervalList();
                for (PrimitiveZRange oppR : oppositeRanges) {
                    coveringShield.add(oppR.zMin, oppR.zMax);
                }
            }

            for (int i = 0; i < ranges.size(); i++) {
                for (int j = i + 1; j < ranges.size(); j++) {
                    PrimitiveZRange r1 = ranges.get(i);
                    PrimitiveZRange r2 = ranges.get(j);

                    if (r1.primitive == r2.primitive) continue;

                    // Check Z overlap
                    double overlapMin = Math.max(r1.zMin, r2.zMin);
                    double overlapMax = Math.min(r1.zMax, r2.zMax);

                    if (overlapMin < overlapMax - 0.01) {
                        // Potential Z-fighting! But is it visible?
                        // If the overlap range is fully covered by opposing walls, ignore it.
                        if (coveringShield != null && coveringShield.covers(overlapMin, overlapMax)) {
                            continue;
                        }

                        Set<OsmPrimitive> pair = new HashSet<>();
                        pair.add(r1.primitive);
                        pair.add(r2.primitive);
                        conflictPairs.add(pair);
                    }
                }
            }
        }

        for (Set<OsmPrimitive> pair : conflictPairs) {
            errors.add(TestError.builder(this, Severity.WARNING, OVERLAPPING_3D_WALLS)
                    .message(tr("Overlapping 3D walls (potential Z-fighting)"))
                    .primitives(pair)
                    .build());
        }

        primitivesToCheck = null;
        super.endTest();
    }

    private void addRingToMap(List<Point2D> ring, PrimitiveZRange zRange, Map<OrientedSegment, List<PrimitiveZRange>> segmentMap, boolean inner) {
        if (ring.size() < 3) return;

        List<Point2D> points = new ArrayList<>(ring);
        // Remove duplicate last point if present
        if (points.get(0).equals(points.get(points.size() - 1))) {
            points.remove(points.size() - 1);
        }

        boolean clockwise = Contour.isClockwise(points);
        // Outer rings (inner=false) should be CCW (clockwise=false)
        // Inner rings (inner=true) should be CW (clockwise=true)
        if (inner != clockwise) {
            Collections.reverse(points);
        }

        for (int i = 0; i < points.size(); i++) {
            Point2D p1 = points.get(i);
            Point2D p2 = points.get((i + 1) % points.size());

            // Skip zero-length segments
            if (p1.x == p2.x && p1.y == p2.y) continue;

            OrientedSegment seg = new OrientedSegment(p1, p2);
            segmentMap.computeIfAbsent(seg, k -> new ArrayList<>()).add(zRange);
        }
    }

    private double getPrimitiveHeight(OsmPrimitive p) {
        String heightStr = p.get("height");
        if (heightStr != null) {
            try {
                return Double.parseDouble(heightStr.replace(',', '.').split(" ")[0]);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) { /* ignore */ }
        }

        String levelsStr = p.get("building:levels");
        if (levelsStr != null) {
            try {
                return Double.parseDouble(levelsStr.replace(',', '.').split(" ")[0]) * 3.0;
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) { /* ignore */ }
        }

        return 3.0; // Default height
    }

    private double getPrimitiveRoofHeight(OsmPrimitive p) {
        String heightStr = p.get("roof:height");
        if (heightStr != null) {
            try {
                return Double.parseDouble(heightStr.replace(',', '.').split(" ")[0]);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) { /* ignore */ }
        }

        String levelsStr = p.get("roof:levels");
        if (levelsStr != null) {
            try {
                return Double.parseDouble(levelsStr.replace(',', '.').split(" ")[0]) * 3.0;
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) { /* ignore */ }
        }

        return 0.0; // Default height
    }

    private double getPrimitiveMinHeight(OsmPrimitive p) {
        String minHeightStr = p.get("min_height");

        if (minHeightStr != null) {
            try {
                return Double.parseDouble(minHeightStr.replace(',', '.').split(" ")[0]);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) { /* ignore */ }
        }

        String minLevelsStr = p.get("building:min_level");
        if (minLevelsStr != null) {
            try {
                return Double.parseDouble(minLevelsStr.replace(',', '.').split(" ")[0]) * 3.0;
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) { /* ignore */ }
        }

        return 0.0;
    }

    private static class OrientedSegment {
        final double x1, y1, x2, y2;

        OrientedSegment(Point2D p1, Point2D p2) {
            this.x1 = p1.x;
            this.y1 = p1.y;
            this.x2 = p2.x;
            this.y2 = p2.y;
        }

        public OrientedSegment getOpposite() {
            return new OrientedSegment(new Point2D(x2, y2), new Point2D(x1, y1));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OrientedSegment that = (OrientedSegment) o;
            // Use exact comparison because points come from the same Node objects in JOSM
            return Double.compare(that.x1, x1) == 0 &&
                    Double.compare(that.y1, y1) == 0 &&
                    Double.compare(that.x2, x2) == 0 &&
                    Double.compare(that.y2, y2) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x1, y1, x2, y2);
        }
    }

    private static class PrimitiveZRange {
        final OsmPrimitive primitive;
        final double zMin;
        final double zMax;

        PrimitiveZRange(OsmPrimitive primitive, double zMin, double zMax) {
            this.primitive = primitive;
            this.zMin = zMin;
            this.zMax = zMax;
        }
    }

    /**
     * Helper class to check if a Z-range is covered by a union of other Z-ranges.
     */
    private static class ZIntervalList {
        private final List<double[]> intervals = new ArrayList<>();

        void add(double min, double max) {
            if (max > min) {
                intervals.add(new double[]{min, max});
            }
        }

        boolean covers(double min, double max) {
            if (min >= max - 0.001) return true;
            if (intervals.isEmpty()) return false;

            // Sort intervals by start point
            intervals.sort(Comparator.comparingDouble(a -> a[0]));

            double currentMax = min;
            for (double[] interval : intervals) {
                if (interval[0] > currentMax + 0.001) {
                    // There is a gap between currentMax and the start of this interval
                    return false;
                }
                currentMax = Math.max(currentMax, interval[1]);
                if (currentMax >= max - 0.001) {
                    return true;
                }
            }
            return currentMax >= max - 0.001;
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
}
