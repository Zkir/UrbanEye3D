package ru.zkir.urbaneye3d.validator;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.io.OsmWriter;
import org.openstreetmap.josm.io.OsmWriterFactory;
import ru.zkir.urbaneye3d.UrbanEye3dPlugin;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.FlatEarth;
import ru.zkir.urbaneye3d.utils.Point2D;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.openstreetmap.josm.tools.I18n.tr;

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
                    primitiveContours.put(primitive, new Contour(primitive));
                } else if (primitive.hasKey("building") && !primitive.get("building").equals("no")) {
                    buildings.add(primitive);
                    primitiveContours.put(primitive, new Contour(primitive));
                }else if (primitive.hasKey("man_made") && hasOutlineRole(primitive)) {
                    //we cannot accept any object as parent, because there is a bad practice of adding just unclosed was as building relation members
                    buildings.add(primitive);
                    primitiveContours.put(primitive, new Contour(primitive));
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
                    Contour partContour = new Contour(part);
                    if (partContour.outerRings.size() > 1){
                        //TODO: we do not know how to deal with multipolygons with multiple outer rings.
                        //The only thing we can do is skip them for now.
                        return;
                    }
                    Polygon partPolygon = toJtsPolygon(partContour);
                    if (partPolygon != null && !partPolygon.isEmpty()) {
                        Geometry partGeom = partPolygon.buffer(0); // zero buffer heals broken geometry.
                        if (!(partGeom instanceof Polygon) ) {
                            UrbanEye3dPlugin.debugMsg("Unexpected topology problem with " + part.getOsmPrimitiveId() );
                            /* we can just exit here, because topology problems like way self intersections and duplicated segments
                               are checked by other JOSM validation rules. We do not need to worry about that here.
                            */
                            return;
                        }
                        partPolygons.add((Polygon)partGeom);
                    }
                }

                if (!partPolygons.isEmpty()) {
                    Contour buildingContour = new Contour(p);
                    Polygon buildingPolygon = toJtsPolygon(buildingContour);
                    if (buildingPolygon != null && !buildingPolygon.isEmpty()) {
                        Geometry partsUnion = UnaryUnionOp.union(partPolygons); // unite parts.
                        partsUnion = partsUnion.buffer(0.001/ FlatEarth.GRAD_LENGTH_M,1); // we need to do small buffer, to avoid JTS bugs.
                        if (!partsUnion.covers(buildingPolygon)) {
                            errors.add(TestError.builder(this, Severity.WARNING, BUILDING_NOT_COVERED_BY_PARTS)
                                    .message(tr("Building is not fully covered by its parts"))
                                    .primitives(p)
                                    .build());
                            // DO NOT REMOVE: this useful for debug purposes.
                            //saveAsOSMFile(buildingPolygon.difference(partsUnion));
                            //saveAsOSMFile(p.getOsmPrimitiveId(), partsUnion);
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
     * Saves jts polygon as osm file, for debug purposes.
     * @param geometry JTS geometry to be saved
     */
    private void saveAsOSMFile(PrimitiveId id, Geometry geometry) {
        try (FileOutputStream fos = new FileOutputStream("tests\\validator\\"+id.toString()+".osm")) {
            DataSet dataSet = new DataSet();

            for (int i = 0; i < geometry.getNumGeometries(); i++) {
                Geometry subGeometry = geometry.getGeometryN(i);
                if (subGeometry instanceof Polygon) {
                    Polygon polygon = (Polygon) subGeometry;

                    if (polygon.getNumInteriorRing() > 0) {
                        Relation multipolygon = new Relation();
                        multipolygon.setKeys(new TagMap("type", "multipolygon"));
                        dataSet.addPrimitive(multipolygon);

                        // Outer ring
                        Way outerWay = createWayFromLinearRing((LinearRing) polygon.getExteriorRing(), dataSet);
                        multipolygon.addMember(new RelationMember("outer", outerWay));

                        // Inner rings
                        for (int j = 0; j < polygon.getNumInteriorRing(); j++) {
                            Way innerWay = createWayFromLinearRing((LinearRing) polygon.getInteriorRingN(j), dataSet);
                            multipolygon.addMember(new RelationMember("inner", innerWay));
                        }
                    } else {
                        Way way = createWayFromLinearRing((LinearRing) polygon.getExteriorRing(), dataSet);
                        way.setKeys(new TagMap("debug", "polygon"));
                    }
                }
            }

            PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8));
            OsmWriter writer = OsmWriterFactory.createOsmWriter(printWriter, true, dataSet.getVersion());
            writer.write(dataSet);
            writer.flush();
            printWriter.flush();
        } catch (IOException e) {
            // It's a debug method, so just print the stack trace
            e.printStackTrace();
        }
    }

    private Way createWayFromLinearRing(LinearRing ring, DataSet dataSet) {
        Way way = new Way();
        List<Node> nodes = new ArrayList<>();
        for (Coordinate coord : ring.getCoordinates()) {
            Node node = new Node(new LatLon(coord.y, coord.x));
            // Let's not try to find duplicates for now, it will make things complicated.
            // JOSM will merge nodes on read.
            dataSet.addPrimitive(node);
            nodes.add(node);
        }
        way.setNodes(nodes);
        dataSet.addPrimitive(way);
        return way;
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

    private boolean hasOutlineRole(OsmPrimitive primitive) {
        boolean member_of_building_relation = false;
        for (var r: primitive.getReferrers()) {
            if (r instanceof  Relation) {
                var rr = (Relation)r;
                if ("building".equals(rr.get("type"))) {
                    var outlines=rr.findRelationMembers("outline");
                    if (outlines.contains(primitive)) {
                        member_of_building_relation = true;
                        break;
                    }
                }
            }
        }
        return member_of_building_relation;
    }
}