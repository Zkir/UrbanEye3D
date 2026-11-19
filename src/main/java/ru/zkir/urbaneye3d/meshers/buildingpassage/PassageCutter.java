package ru.zkir.urbaneye3d.meshers.buildingpassage;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUtessellator;
import com.jogamp.opengl.glu.GLUtessellatorCallbackAdapter;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.TopologyException;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.openstreetmap.josm.data.osm.PrimitiveId;
import ru.zkir.urbaneye3d.UrbanEye3dPlugin;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point3D;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_LEVEL_HEIGHT;

/**
 * Cuts a building passage (a way tagged {@code tunnel=building_passage}) out of a generated building mesh, so
 * that the road or railway goes through the building instead of into a blind wall.
 *
 * <p>A building is a "2.5D" object: vertical walls extruded from the contour, horizontal floor and roof, so 2D
 * boolean operations (JTS) are enough.  The strip around the axis ({@link BuildingPassage#strip}) intersected
 * with the footprint gives the plan of the opening; its edges on the contour become holes clipped in the wall
 * faces, which stay single non-convex faces and need no triangulation; the other edges become vertical walls of
 * the passage; the plan becomes the ceiling of the arch, and the strip cuts the floor.  Every new edge is shared
 * by two faces, so the body stays watertight.
 *
 * <p>All passages of one building share the height of the arch, so the ceilings form one consistent surface.
 */
public class PassageCutter {

    /** An edge of an opening is considered lying on the contour of the building if it is closer than this, metres. */
    private static final double ON_CONTOUR_EPS = 1e-3;
    /** A part of the contour of an opening shorter than this is ignored, metres. */
    private static final double MIN_EDGE = 1e-3;
    /** An opening or a face smaller than this is ignored, square metres. */
    private static final double MIN_AREA = 1e-4;
    /** How far a vertex of a "wall" face may stick out of the plane of the wall, metres.  Faces that are not
     *  flat vertical quads are not cut. */
    private static final double MAX_FACE_SKEW = 0.05;
    /** Two vertices closer than this are the same vertex of the mesh, metres.  The grid that recognises a vertex
     *  uses exactly this step, so such a point is always found in a neighbouring cell.  It must stay far below
     *  the smallest distance between two different vertices, or two corners would merge. */
    private static final double WELD_EPS = 2e-5;
    /** An opening that disappears when shrunk by this much is a hairline, not an opening, metres. */
    private static final double HAIRLINE_WIDTH = 0.05;
    /** An edge of an opening shorter than this, next to a corner lying on the straight line between its two
     *  neighbours, is merged into one edge (see {@link #dropHairlineCorners(Coordinate[])}), metres.  JTS leaves
     *  such corners where the corridor touches the outline or where a mitre spikes over a wall; the wall would
     *  then be cut along two edges a hair apart.  A short edge at a real corner stays. */
    private static final double COLLINEAR_EDGE = 0.05;
    /** Two openings of the passages of one object closer than this are the cut of the same corner, metres. */
    private static final double MIN_OPENING_GAP = 0.01;
    /** The mapped way of a passage must reach this far inside the contour to be a passage of the object, metres:
     *  a strip as wide as the road can cover a piece of the contour even when the way passes outside.  The
     *  tolerance also saves ways digitised a few millimetres over a neighbouring outline. */
    private static final double MIN_WAY_INSIDE = 0.05;
    /** A corner of a corridor standing this close to the outline of the object, inside it, is moved onto that
     *  outline (see {@link #snapCornersToTheOutline}), metres.  Enough for the corners that would otherwise leave
     *  a sliver of a wall, and far below the distances at which the cuts in one wall face start crossing. */
    private static final double CORNER_SNAP = 0.1;

    /**
     * Cuts the passages out of the mesh; everything is in local metres of the object.
     *
     * @param contour    contour of the object the mesh belongs to
     * @param minHeight  z of the floor of the object
     * @param wallHeight z of the top of the walls
     * @return the number of cut passages; 0 if none crosses the object, negative if they cross it but the
     *         opening cannot be made without breaking the mesh (the mesh is then left as it was)
     */
    public static int apply(Mesh mesh, Contour contour, double minHeight, double wallHeight,
                            List<BuildingPassage> passages) {
        return apply(null, mesh, contour, minHeight, wallHeight, passages);
    }

    /** The same, naming the object the passages are cut in, so that a refusal is reported with the object. */
    public static int apply(PrimitiveId host, Mesh mesh, Contour contour, double minHeight, double wallHeight,
                            List<BuildingPassage> passages) {
        if (mesh == null || contour == null || passages == null || passages.isEmpty()) {
            return 0;
        }
        //objects without walls (building:part=roof, wall=no) and objects without a floor cannot have a passage
        if (mesh.getWallFaces().isEmpty() || mesh.getBottomFaces().isEmpty()) {
            return 0;
        }
        if (wallHeight - minHeight < BuildingPassage.MIN_ARCH_HEIGHT + BuildingPassage.MIN_LINTEL) {
            return 0;
        }

        Geometry footprint = contour.toJTSGeometry();
        if (!(footprint instanceof Polygon) && !(footprint instanceof MultiPolygon)) {
            return 0;
        }

        //An arch is not higher than the first floor of the building unless maxheight says otherwise.
        double levels = Math.max(1, Math.round((wallHeight - minHeight) / DEFAULT_LEVEL_HEIGHT));
        double firstFloorTop = minHeight + Math.min(BuildingPassage.DEFAULT_ARCH_HEIGHT, (wallHeight - minHeight) / levels);
        double archZ = firstFloorTop;

        List<BuildingPassage> cuts = new ArrayList<>();
        boolean raised = minHeight > 0.5;
        for (BuildingPassage passage : passages) {
            //a part standing above the ground gets the passage only if its declared height reaches it
            if (raised && (passage.height <= minHeight + BuildingPassage.MIN_ARCH_HEIGHT)) {
                continue;
            }
            if (passage.height > archZ) {
                archZ = passage.height;
            }
            cuts.add(passage);
        }
        //some wall above the arch must remain, otherwise the roof of the building floats in the air
        archZ = Math.min(archZ, wallHeight - BuildingPassage.MIN_LINTEL);
        if (cuts.isEmpty() || archZ < minHeight + BuildingPassage.MIN_ARCH_HEIGHT) {
            if (!cuts.isEmpty() && archZ < minHeight + BuildingPassage.MIN_ARCH_HEIGHT) {
                UrbanEye3dPlugin.debugMsg(refusal(host, "the object is too low for an arch"
                        + " of at least " + BuildingPassage.MIN_ARCH_HEIGHT + " m (its walls are "
                        + (wallHeight - minHeight) + " m high)"));
            }
            return 0;
        }
        String unclosed = firstUnclosedEdge(mesh);
        if (unclosed != null) {
            //the body is not closed even before it is pierced; piercing it would only add more holes
            UrbanEye3dPlugin.debugMsg(refusal(host, "the mesh of the object is not closed"
                    + " before cutting: " + unclosed));
            return 0;
        }

        return new PassageCutter(host, mesh, footprint, minHeight, archZ).cut(cuts);
    }

    /** One message telling why the passages of one object were refused. */
    private static String refusal(PrimitiveId host, String reason) {
        return "Passages of " + (host == null ? "the building" : host) + " are not cut: " + reason;
    }

    /** The first edge of the mesh without exactly one partner edge going the other way, or null if it is closed. */
    private static String firstUnclosedEdge(Mesh mesh) {
        return firstUnclosedEdge(mesh.faces);
    }

    /** The same check for a list of faces that is not in the mesh yet. */
    private static String firstUnclosedEdge(List<int[]> faces) {
        Map<String, Integer> uses = new HashMap<>();
        for (int[] face : faces) {
            for (int i = 0; i < face.length; i++) {
                int a = face[i];
                int b = face[(i + 1) % face.length];
                String key = Math.min(a, b) + "-" + Math.max(a, b);
                uses.merge(key, 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> edge : uses.entrySet()) {
            if (edge.getValue() != 2) {
                return "edge " + edge.getKey() + " belongs to " + edge.getValue() + " faces";
            }
        }
        return null;
    }

    /** The faces the mesh would have after the cut, without changing the mesh. */
    private List<int[]> prospectiveFaces() {
        List<int[]> faces = new ArrayList<>();
        for (int i = 0; i < mesh.faces.size(); i++) {
            List<int[]> replacement = replacements.get(i);
            if (replacement == null) {
                faces.add(mesh.faces.get(i));
            } else {
                faces.addAll(replacement);
            }
        }
        faces.addAll(addedFaces);
        return faces;
    }

    private final Mesh mesh;
    private final PrimitiveId host;
    private final Geometry footprint;
    private final Geometry contourBoundary;
    /** The contour shrunk by {@link #MIN_WAY_INSIDE}; null when the object is too narrow for a way to be inside it. */
    private final Geometry innerFootprint;
    private final GeometryFactory factory = new GeometryFactory();
    private final double floorZ;
    private final double archZ;

    /** Faces to be replaced (index in mesh.faces -> new faces; an empty list means "delete this face"). */
    private final Map<Integer, List<int[]>> replacements = new HashMap<>();
    /** Faces added on top of the existing ones. */
    private final List<int[]> addedFaces = new ArrayList<>();
    private final List<Integer> addedMaterials = new ArrayList<>();
    /** Existing and new vertices of the mesh, to not create duplicated vertices. */
    private final Map<String, Integer> vertexIds = new HashMap<>();
    /** Which of the opening edges that lie on the contour were matched with a wall face. */
    private boolean[] chordMatched;
    /** Set when the opening cannot be cut without breaking the mesh; then nothing is changed in the mesh. */
    private boolean broken;
    /** Why the opening turned out impossible to cut, for the debug message. */
    private String brokenReason;

    /** Remembers the first reason why this opening cannot be cut. */
    private void markBroken(String reason) {
        if (!broken) {
            broken = true;
            brokenReason = reason;
        }
    }

    private PassageCutter(PrimitiveId host, Mesh mesh, Geometry footprint, double floorZ, double archZ) {
        this.host = host;
        this.mesh = mesh;
        this.footprint = footprint;
        this.contourBoundary = footprint.getBoundary();
        this.innerFootprint = shrinkInside(footprint);
        this.floorZ = floorZ;
        this.archZ = archZ;
        //the new vertices must be the existing ones where they coincide, or the mesh is not watertight
        for (int i = 0; i < mesh.verts.size(); i++) {
            Point3D p = mesh.verts.get(i);
            vertexIds.putIfAbsent(vertexKey(p.x, p.y, p.z), i);
        }
    }

    /** The same contour, shrunk by {@link #MIN_WAY_INSIDE}; null if it disappears or cannot be computed. */
    private static Geometry shrinkInside(Geometry footprint) {
        try {
            Geometry inner = footprint.buffer(-MIN_WAY_INSIDE);
            return inner.isEmpty() ? null : inner;
        } catch (TopologyException | IllegalArgumentException e) {
            return null; //cannot tell; no way is counted as going inside such an object
        }
    }

    /**
     * Does the mapped way of the passage come inside the contour of this object?  Only such a way is a passage of
     * this object: the strip of a way passing outside or along the facade can cover a piece of the contour, but
     * cutting that piece would make an opening that leads nowhere.
     */
    private boolean wayGoesInsideTheContour(BuildingPassage passage) {
        if (innerFootprint == null) {
            return false;
        }
        LineString axis = axisLine(passage);
        if (axis == null) {
            return false;
        }
        try {
            return !axis.intersection(innerFootprint).isEmpty();
        } catch (TopologyException | IllegalArgumentException e) {
            return false; //cannot tell, better leave the object alone
        }
    }

    /** The way of the passage as it is mapped, without the ends prolonged, as a line in the local metres. */
    private LineString axisLine(BuildingPassage passage) {
        if (passage.axis.size() < 2) {
            return null;
        }
        Coordinate[] axis = new Coordinate[passage.axis.size()];
        for (int i = 0; i < axis.length; i++) {
            axis[i] = new Coordinate(passage.axis.get(i).x, passage.axis.get(i).y);
        }
        try {
            return factory.createLineString(axis);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Moves the corners of a corridor that stand inside the object a hair off its outline onto that outline.
     * Those corners are ours: the prolonged ends of the axis and the mitre joints of a bending way.  A wall that
     * cuts such a corner off leaves two corners a hair apart, and the wall and the arch stop sharing an edge, so
     * the body is not closed.  One corner instead of two costs the corridor no more than {@link #CORNER_SNAP};
     * only corners inside the object are moved.
     *
     * @return the corridor with its corners moved, or itself if they cannot be moved
     */
    private Geometry snapCornersToTheOutline(Geometry corridor) {
        try {
            if (corridor instanceof Polygon) {
                return snapPolygon((Polygon) corridor);
            }
            if (corridor instanceof GeometryCollection) {
                //two passages that do not touch one another give one corridor of several pieces
                Geometry[] pieces = new Geometry[corridor.getNumGeometries()];
                for (int i = 0; i < pieces.length; i++) {
                    pieces[i] = snapCornersToTheOutline(corridor.getGeometryN(i));
                }
                return factory.buildGeometry(Arrays.asList(pieces));
            }
            return corridor;
        } catch (IllegalArgumentException | TopologyException e) {
            return corridor; //cannot tell, leave the corridor exactly as it was
        }
    }

    /** Applies {@link #snapRing(LinearRing)} to the outer ring and to the holes of a corridor. */
    private Polygon snapPolygon(Polygon corridor) {
        LinearRing outer = snapRing(corridor.getExteriorRing());
        LinearRing[] holes = new LinearRing[corridor.getNumInteriorRing()];
        for (int i = 0; i < holes.length; i++) {
            holes[i] = snapRing(corridor.getInteriorRingN(i));
        }
        Polygon moved = factory.createPolygon(outer, holes);
        //two corners of a corridor running along the wall would both land on it and make a slit, so such a
        //corridor is left as it was
        return moved.isValid() && moved.getArea() > MIN_AREA ? moved : corridor;
    }

    private LinearRing snapRing(LinearRing ring) {
        Coordinate[] corners = ring.getCoordinates();
        Coordinate[] moved = new Coordinate[corners.length];
        for (int i = 0; i < corners.length; i++) {
            Coordinate on = pointOfTheOutlineFor(corners[i]);
            moved[i] = on == null ? new Coordinate(corners[i]) : on;
        }
        return factory.createLinearRing(moved);
    }

    /**
     * The point of the outline of the object that the given corner of the corridor has to be moved onto, or null
     * when the corner must stay where it is.
     */
    private Coordinate pointOfTheOutlineFor(Coordinate corner) {
        Point p = factory.createPoint(corner);
        if (!footprint.contains(p)) {
            return null; //this corner is outside the object, so its wall does not cut this corner of the corridor
        }
        double d = p.distance(contourBoundary);
        if (d <= 1e-9 || d > CORNER_SNAP) {
            return null; //the corner is on the outline already, or far enough from it to be a corner on its own
        }
        Coordinate on = DistanceOp.nearestPoints(p, contourBoundary)[1];
        return new Coordinate(on);
    }

    private int cut(List<BuildingPassage> passages) {
        int vertexMark = mesh.verts.size();
        try {
            return examinePassages(passages, vertexMark);
        } catch (RuntimeException e) {
            //JTS and the tessellator may fail on a pathological geometry; this runs during a scene rebuild,
            //so nothing may escape and the building is left as it was
            markBroken(e.toString());
            return abort(vertexMark, passages.size());
        }
    }

    private int examinePassages(List<BuildingPassage> passages, int vertexMark) {
        //new vertices are appended as they are created, and removed again if the passage cannot be cut
        List<BuildingPassage> accepted = new ArrayList<>();
        //the part of the contour that each single passage crosses, kept to not compute it twice
        List<Geometry> crossings = new ArrayList<>();
        int relevant = 0;
        for (BuildingPassage passage : passages) {
            Geometry strip = snapCornersToTheOutline(passage.strip(factory));
            if (strip == null || strip.isEmpty()) {
                continue;
            }
            if (hasHoles(strip)) {
                //a closed or self-crossing axis gives a strip with a hole, and these faces have no holes
                UrbanEye3dPlugin.debugMsg("Building passage " + passage.primitiveId +
                        " is skipped: its axis is closed or self-crossing");
                continue;
            }
            if (!wayGoesInsideTheContour(passage)) {
                //only the strip of this way, as wide as the road, may cover a piece of the contour; cutting it
                //would open the body through a corridor that ends on one of its walls for no reason at all
                continue;
            }
            Geometry crossing;
            try {
                crossing = footprint.intersection(strip);
            } catch (TopologyException | IllegalArgumentException e) {
                UrbanEye3dPlugin.debugMsg("Cannot intersect the building contour with a passage: " + e);
                continue;
            }
            if (crossing.getArea() < MIN_AREA) {
                continue; //this passage does not cross the object
            }
            //the pieces of the contour covered by the strip: the ones with a real area, and the hairline traces
            //of the strip touching the contour
            List<Polygon> solid = new ArrayList<>();
            List<Polygon> hairline = new ArrayList<>();
            for (Polygon piece : polygonsOf(crossing)) {
                if (piece.getArea() < MIN_AREA || isHairline(piece)) {
                    hairline.add(piece);
                } else {
                    solid.add(piece);
                }
            }
            if (solid.isEmpty()) {
                //the strip lies outside the object or touches it along a hairline: there is no wall to cut
                continue;
            }
            if (!hairline.isEmpty()) {
                //the passage crosses the object, but its strip also touches the contour along a hairline
                //elsewhere, which would open the body: a passage is cut as a whole or not at all
                UrbanEye3dPlugin.debugMsg("Building passage " + passage.primitiveId + " is not cut in "
                        + (host == null ? "the building" : host) + ": its strip touches the contour of the object"
                        + " along a hairline, leaving a piece of an opening " + hairline.get(0));
                continue;
            }
            relevant++;
            accepted.add(passage);
            crossings.add(crossing);
        }
        if (accepted.isEmpty()) {
            return 0;
        }
        //the strips are built from the corridors, not the passages: a road mapped as several ways is one
        //corridor (see BuildingPassage.joinChains)
        Geometry strip;
        try {
            List<BuildingPassage> corridors = BuildingPassage.joinChains(accepted);
            List<Geometry> strips = new ArrayList<>();
            for (BuildingPassage corridor : corridors) {
                Geometry corridorStrip = snapCornersToTheOutline(corridor.strip(factory));
                if (corridorStrip != null && !corridorStrip.isEmpty()) {
                    strips.add(corridorStrip);
                }
            }
            if (strips.isEmpty()) {
                return 0;
            }
            strip = strips.size() == 1 ? strips.get(0) : UnaryUnionOp.union(strips);
        } catch (TopologyException | IllegalArgumentException e) {
            UrbanEye3dPlugin.debugMsg("Cannot join the passages of a building: " + e);
            return 0;
        }
        if (hasHoles(strip)) {
            //two passages that cross twice enclose a pocket between them; leave the building alone
            markBroken("their strips enclose a pocket between them");
            return abort(vertexMark, relevant);
        }

        List<Polygon> openings;
        try {
            //for a single passage its intersection with the contour was already computed above
            openings = polygonsOf(crossings.size() == 1 ? crossings.get(0) : footprint.intersection(strip));
        } catch (TopologyException | IllegalArgumentException e) {
            UrbanEye3dPlugin.debugMsg("Cannot cut a passage out of the building contour: " + e);
            return 0;
        }
        //the strips cover the whole contour: the object is smaller than the passage and stands inside it, so
        //there is no wall to cut an opening in, and nothing is refused
        double covered = 0;
        for (Polygon piece : openings) {
            covered += piece.getArea();
        }
        if (covered > footprint.getArea() - MIN_AREA) {
            return 0;
        }
        //a hairline opening is the trace of the strip grazing the contour: the floor is cut there although no
        //wall is, which opens the body.  Half an opening is worse than none, so the passage is refused.
        List<Polygon> grazed = new ArrayList<>();
        List<Polygon> kept = new ArrayList<>();
        for (Polygon opening : openings) {
            if (opening.getArea() < MIN_AREA || isHairline(opening)) {
                grazed.add(opening);
            } else {
                kept.add(opening);
            }
        }
        openings = kept;
        if (openings.isEmpty()) {
            return 0; //the passage does not cross this object, or only touches it along a hairline
        }
        if (!grazed.isEmpty()) {
            markBroken("the strip of the passage leaves a hairline piece of the opening " + grazed.get(0));
            return abort(vertexMark, relevant);
        }
        //JTS keeps the orientation of the input contours, while every opening needs the same one, so it is made
        //canonical here
        List<Polygon> canonical = new ArrayList<>();
        for (Polygon opening : openings) {
            canonical.add(canonicalRings(opening));
        }
        openings = canonical;

        //the tiny collinear corners JTS leaves are removed from the openings, so that the chords, the ceiling
        //and the passage walls share one boundary
        List<Polygon> simplified = new ArrayList<>();
        for (Polygon opening : openings) {
            Polygon simple = dropHairlineCorners(opening);
            if (simple == null) {
                markBroken("the opening of the passage collapses into a line: " + opening);
                return abort(vertexMark, relevant);
            }
            simplified.add(simple);
        }
        openings = simplified;

        //An opening pinching into a hairline neck inside itself means the strip crosses the contour right at a
        //corner of the building: the wall would be cut twice, a hairline apart.
        for (Polygon opening : openings) {
            if (isPinched(opening)) {
                markBroken("their opening pinches into a hairline neck, the passage crosses the contour of the"
                        + " building at a corner: " + opening);
                return abort(vertexMark, relevant);
            }
        }

        //two pieces of the opening almost on top of each other mean the same: the strip crosses the contour at
        //a corner and the wall would be cut twice, a hairline apart
        for (int i = 0; i < openings.size(); i++) {
            for (int j = i + 1; j < openings.size(); j++) {
                double gap = openings.get(i).distance(openings.get(j));
                if (gap < MIN_OPENING_GAP) {
                    markBroken("two of their openings are " + gap + " m apart, so the passage crosses the contour"
                            + " of the building at a corner and the walls of the passage would stand on top of each"
                            + " other");
                    return abort(vertexMark, relevant);
                }
            }
        }
        //the edges of an opening on the contour are holes in the walls ("chords"), the others are the walls
        //of the passage
        List<LineString> chords = new ArrayList<>();
        List<Coordinate[]> passageWallEdges = new ArrayList<>();
        for (Polygon opening : openings) {
            classifyRing(opening.getExteriorRing().getCoordinates(), chords, passageWallEdges);
            for (int i = 0; i < opening.getNumInteriorRing(); i++) {
                classifyRing(opening.getInteriorRingN(i).getCoordinates(), chords, passageWallEdges);
            }
        }
        if (broken) {
            return abort(vertexMark, relevant);
        }
        if (chords.isEmpty()) {
            return 0; //the passage is completely inside the building, there is nothing to come out through
        }

        chordMatched = new boolean[chords.size()];
        cutWallFaces(chords);
        for (int i = 0; i < chordMatched.length; i++) {
            if (!chordMatched[i]) {
                //a wall face is missing on the contour of this opening (another object draws that part of the
                //building, or the walls do not follow the contour exactly); cutting only part would tear the mesh
                markBroken("the edge of the opening " + chords.get(i) + " is not on a wall of the object");
                return abort(vertexMark, relevant);
            }
        }
        if (broken) {
            return abort(vertexMark, relevant);
        }
        if (replacements.isEmpty()) {
            return 0;
        }

        cutBottomFaces(strip);
        addPassageWalls(passageWallEdges);
        addArchCeilings(openings);
        if (broken) {
            //the floor or the ceiling could not be cut, and leaving them out would open the body
            return abort(vertexMark, relevant);
        }

        String open = firstUnclosedEdge(prospectiveFaces());
        if (open != null) {
            //a hairline difference between a corner of the opening and a corner of a wall face leaves an edge
            //without a partner, and a torn envelope is worse than no arch, so the passage is refused.
            markBroken("the cut would leave the body of the object open: " + open);
            return abort(vertexMark, relevant);
        }

        applyChanges();
        return relevant;
    }

    /**
     * The passage is refused: the mesh is left unchanged and the vertices created while examining the opening
     * are removed again.  The reason remembered by {@link #markBroken} is reported here.
     *
     * @param refused how many passages cross the object but could not be cut (returned as a negative number)
     */
    private int abort(int vertexMark, int refused) {
        if (broken) {
            UrbanEye3dPlugin.debugMsg(refusal(host, brokenReason));
        }
        replacements.clear();
        addedFaces.clear();
        addedMaterials.clear();
        while (mesh.verts.size() > vertexMark) {
            mesh.verts.remove(mesh.verts.size() - 1);
        }
        return -refused;
    }

    /**
     * Is the opening only a hairline, like the trace of a passage grazing the contour?  A real opening is at least
     * {@link BuildingPassage#MIN_WIDTH} wide, so one that disappears when shrunk by a few centimetres is a
     * numerical artifact and must not be cut.
     */
    private static boolean isHairline(Polygon opening) {
        try {
            return opening.buffer(-HAIRLINE_WIDTH).isEmpty();
        } catch (TopologyException | IllegalArgumentException e) {
            return true; //cannot tell, better leave the building alone
        }
    }

    /**
     * Does the opening pinch into a hairline neck inside itself?  A small negative buffer splits such an opening
     * into pieces or leaves lines next to it, while an ordinary opening stays one piece.
     */
    private static boolean isPinched(Polygon opening) {
        Geometry shrunk;
        try {
            shrunk = opening.buffer(-MIN_OPENING_GAP);
        } catch (TopologyException | IllegalArgumentException e) {
            return true; //cannot tell, better leave the building alone
        }
        if (shrunk.isEmpty()) {
            return false; //this is a hairline opening, and it is recognised as such elsewhere
        }
        int pieces = 0;
        for (int i = 0; i < shrunk.getNumGeometries(); i++) {
            if (shrunk.getGeometryN(i) instanceof Polygon) {
                pieces++;
            } else {
                return true; //a line or a point left by the neck of the opening: it is degenerate
            }
        }
        return pieces > 1;
    }

    /** Does the geometry have a ring inside (a closed or a self-crossing axis, or an enclosed pocket)? */
    private static boolean hasHoles(Geometry geometry) {
        if (geometry instanceof Polygon) {
            return ((Polygon) geometry).getNumInteriorRing() > 0;
        }
        if (geometry instanceof GeometryCollection) {
            for (int i = 0; i < geometry.getNumGeometries(); i++) {
                if (hasHoles(geometry.getGeometryN(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Splits the boundary of an opening into the chords in the walls and the walls of the passage. */
    private void classifyRing(Coordinate[] ring, List<LineString> chords, List<Coordinate[]> passageWallEdges) {
        for (int i = 0; i + 1 < ring.length; i++) {
            Coordinate a = ring[i];
            Coordinate b = ring[i + 1];
            //the corners of an opening always touch the contour, so the middle of an edge shows whether it runs
            //along the wall or across the building.  Every edge is classified: skipping a hairline one would
            //leave the wall over it uncut while the faces around it keep their edges
            Coordinate middle = new Coordinate((a.x + b.x) / 2, (a.y + b.y) / 2);
            if (contourBoundary.distance(factory.createPoint(middle)) < ON_CONTOUR_EPS) {
                chords.add(factory.createLineString(new Coordinate[]{a, b}));
            } else {
                //the walls of the passage must look into the passage, so the ring is traversed the other way
                //round: the interior of the opening is on its left for an exterior ring, on its right for a hole
                passageWallEdges.add(new Coordinate[]{b, a});
            }
        }
    }

    /** Applies {@link #dropHairlineCorners(Coordinate[])} to the outer ring and to the holes of an opening. */
    private Polygon dropHairlineCorners(Polygon opening) {
        Coordinate[] outer = dropHairlineCorners(opening.getExteriorRing().getCoordinates());
        if (outer == null) {
            return null;
        }
        LinearRing[] holes = new LinearRing[opening.getNumInteriorRing()];
        for (int i = 0; i < holes.length; i++) {
            Coordinate[] ring = dropHairlineCorners(opening.getInteriorRingN(i).getCoordinates());
            if (ring == null) {
                return null;
            }
            holes[i] = factory.createLinearRing(ring);
        }
        return factory.createPolygon(factory.createLinearRing(outer), holes);
    }

    /**
     * Removes the tiny collinear corners that JTS leaves where the strip touches the contour or where a mitre
     * spikes over a wall: the two edges around the corner become one edge that can be cut in a wall, and the
     * shape of the opening does not change.  A hairline edge cannot just be skipped: the wall over it would
     * stay uncut while the faces around it keep their edges.
     *
     * @return the ring without such corners, or null if the ring collapses into a line
     */
    private Coordinate[] dropHairlineCorners(Coordinate[] ring) {
        List<Coordinate> corners = new ArrayList<>();
        for (int i = 0; i + 1 < ring.length; i++) {
            corners.add(ring[i]);
        }
        boolean changed;
        do {
            changed = false;
            int n = corners.size();
            if (n < 3) {
                return null;
            }
            //of two removable corners in a row, the one farther from the contour goes: the corner on the contour
            //is where the wall is crossed and where the floor is cut, so it must survive
            int worst = -1;
            double worstDistance = -1;
            for (int i = 0; i < n; i++) {
                Coordinate c = corners.get(i);
                Coordinate a = corners.get((i - 1 + n) % n);
                Coordinate b = corners.get((i + 1) % n);
                if (Math.min(a.distance(c), c.distance(b)) >= COLLINEAR_EDGE) {
                    continue; //both edges around the corner are long enough to be cut as they are
                }
                if (distanceToSegment(c, a, b) > ON_CONTOUR_EPS) {
                    continue; //the corner sticks out of the line of its neighbours, so it is a real corner
                }
                double distance = contourBoundary.distance(factory.createPoint(c));
                if (distance > worstDistance) {
                    worstDistance = distance;
                    worst = i;
                }
            }
            if (worst >= 0) {
                corners.remove(worst);
                changed = true;
            }
        } while (changed);
        if (corners.size() < 3) {
            return null;
        }
        Coordinate[] result = new Coordinate[corners.size() + 1];
        for (int i = 0; i < corners.size(); i++) {
            result[i] = corners.get(i);
        }
        result[corners.size()] = new Coordinate(result[0]);
        return result;
    }

    /** The distance from a point to the segment drawn between two other points, in the floor plane. */
    private static double distanceToSegment(Coordinate p, Coordinate a, Coordinate b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double length2 = dx * dx + dy * dy;
        if (length2 < MIN_EDGE * MIN_EDGE) {
            return p.distance(a);
        }
        double t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / length2;
        if (t <= 0) {
            return p.distance(a);
        }
        if (t >= 1) {
            return p.distance(b);
        }
        return Math.hypot(p.x - a.x - t * dx, p.y - a.y - t * dy);
    }

    /** Brings the rings of an opening to the canonical orientation: the exterior one counter-clockwise. */
    private Polygon canonicalRings(Polygon polygon) {
        LinearRing outer = factory.createLinearRing(ensureCounterClockwise(polygon.getExteriorRing().getCoordinates()));
        LinearRing[] holes = new LinearRing[polygon.getNumInteriorRing()];
        for (int i = 0; i < holes.length; i++) {
            holes[i] = factory.createLinearRing(ensureClockwise(polygon.getInteriorRingN(i).getCoordinates()));
        }
        return factory.createPolygon(outer, holes);
    }

    private static Coordinate[] ensureCounterClockwise(Coordinate[] ring) {
        return shoelace(ring) > 0 ? ring : reverse(ring);
    }

    private static Coordinate[] ensureClockwise(Coordinate[] ring) {
        return shoelace(ring) < 0 ? ring : reverse(ring);
    }

    /** Cuts a hole in every wall face crossed by an opening. */
    private void cutWallFaces(List<LineString> chords) {
        for (int faceIndex = 0; faceIndex < mesh.faces.size(); faceIndex++) {
            if (mesh.faceMaterials.get(faceIndex) != Mesh.WALL_COLOUR_IDX) {
                continue;
            }
            WallLine wall = wallLine(mesh.faces.get(faceIndex));
            if (wall == null) {
                continue; //not a flat vertical wall (a roof slope, a conical or hyperboloid wall)
            }
            if (wall.faceMinZ > archZ) {
                continue; //this band of the wall stands above the top of the arch, there is nothing to cut in it
            }
            List<double[]> intervals = new ArrayList<>();
            for (int c = 0; c < chords.size(); c++) {
                double[] interval = wall.intervalOf(chords.get(c));
                if (interval != null) {
                    intervals.add(interval);
                    chordMatched[c] = true;
                }
            }
            if (!intervals.isEmpty()) {
                List<int[]> pieces = cutFaceInItsPlane(faceIndex, wall, intervals);
                if (pieces == null) {
                    return; //markBroken was called: the whole passage must be skipped
                }
                replacements.put(faceIndex, pieces);
            }
        }
    }

    /**
     * The wall face is clipped in its own plane: it is a polygon in a vertical plane, described by s (along the
     * wall) and z only.  The holes are rectangles from the floor to the top of the arch and always touch the
     * lower edge of the face, so the result is still one face, non-convex.
     *
     * @return the faces replacing the wall face, or null if it cannot be cut: half a cut would open the body
     */
    private List<int[]> cutFaceInItsPlane(int faceIndex, WallLine wall, List<double[]> intervals) {
        int[] face = mesh.faces.get(faceIndex);
        Coordinate[] ring = new Coordinate[face.length + 1];
        for (int i = 0; i < face.length; i++) {
            Point3D p = mesh.verts.get(face[i]);
            ring[i] = new Coordinate(wall.s(p.x, p.y), p.z);
        }
        ring[face.length] = new Coordinate(ring[0]);
        Polygon original = factory.createPolygon(ring);
        if (!original.isValid()) {
            markBroken("a wall face of the opening is not a valid polygon in its plane " + Arrays.toString(ring));
            return null;
        }
        List<Geometry> holes = new ArrayList<>();
        for (double[] interval : intervals) {
            holes.add(rectangle(interval[0], interval[1], wall.faceMinZ - 1.0, archZ));
        }
        Geometry rest;
        try {
            Geometry hole = holes.size() == 1 ? holes.get(0) : UnaryUnionOp.union(holes);
            rest = original.difference(hole);
        } catch (TopologyException | IllegalArgumentException e) {
            markBroken("a hole cannot be cut in a wall face: " + e);
            return null;
        }
        List<Polygon> parts = polygonsOf(rest);
        if (parts.isEmpty()) {
            //the whole wall face is inside the opening (a wall lower than the arch, e.g. of steps): removing
            //it would leave a gap
            markBroken("a wall of the building is entirely inside the opening of the passage");
            return null;
        }
        List<int[]> pieces = new ArrayList<>();
        for (Polygon piece : parts) {
            if (piece.getNumInteriorRing() > 0) {
                markBroken("a piece of a wall face would need a hole in it");
                return null;
            }
            //the orientation (and so the normal) of the face must not change
            Coordinate[] pieceRing = dropHairlineCorners(piece.getExteriorRing().getCoordinates());
            if (pieceRing == null) {
                markBroken("a piece of a wall face collapsed into a line: " + piece);
                return null;
            }
            int[] pieceFace = ringToFace(pieceRing,
                    shoelace(ring) > 0, (s, z) -> wall.point3D(s, z));
            if (pieceFace == null) {
                markBroken("a piece of a wall face collapsed into a line: " + piece);
                return null;
            }
            pieces.add(pieceFace);
        }
        return pieces;
    }

    /** Cuts the floor of the building, so that the passage is not closed by it and the body stays watertight. */
    private void cutBottomFaces(Geometry strip) {
        for (int faceIndex = 0; faceIndex < mesh.faces.size(); faceIndex++) {
            if (mesh.faceMaterials.get(faceIndex) != Mesh.BOTTOM_COLOUR_IDX) {
                continue;
            }
            int[] face = mesh.faces.get(faceIndex);
            double z = mesh.verts.get(face[0]).z;
            Coordinate[] ring = new Coordinate[face.length + 1];
            boolean horizontal = true;
            for (int i = 0; i < face.length; i++) {
                Point3D p = mesh.verts.get(face[i]);
                if (Math.abs(p.z - z) > 1e-3) {
                    horizontal = false;
                }
                ring[i] = new Coordinate(p.x, p.y);
            }
            ring[face.length] = new Coordinate(ring[0]);
            if (!horizontal) {
                //a "floor" that is not horizontal cannot be cut in the XY plane, so a passage through it
                //opens the body
                if (crosses(ring, strip)) {
                    markBroken("the bottom of the object is not horizontal " + Arrays.toString(ring));
                }
                continue;
            }
            if (z > archZ) {
                continue; //a floor above the top of the arch is not touched
            }
            Polygon original = factory.createPolygon(ring);
            if (!original.isValid()) {
                markBroken("the bottom of the object is not a valid polygon " + Arrays.toString(ring));
                continue;
            }
            Geometry rest;
            try {
                rest = original.difference(strip);
            } catch (TopologyException | IllegalArgumentException e) {
                markBroken("the bottom of the object cannot be cut: " + e);
                continue;
            }
            if (rest.getArea() > original.getArea() - MIN_AREA && polygonsOf(rest).size() == 1) {
                continue; //the passage does not touch this part of the floor
            }
            List<Polygon> parts = polygonsOf(rest);
            List<int[]> pieces = new ArrayList<>();
            int tessellated = 0;
            for (Polygon piece : parts) {
                if (piece.getNumInteriorRing() > 0) {
                    //the piece wraps around the courtyard, so it has a hole and cannot be one face: it is
                    //tessellated, as MesherFlat does
                    tessellateLookingDown(piece, z, Mesh.BOTTOM_COLOUR_IDX, "the bottom of the object");
                    tessellated++;
                    continue;
                }
                Coordinate[] pieceRing = dropHairlineCorners(piece.getExteriorRing().getCoordinates());
                if (pieceRing == null) {
                    markBroken("a piece of the bottom of the object collapsed into a line: " + piece);
                    break;
                }
                int[] pieceFace = ringToFace(pieceRing, shoelace(ring) > 0, (s, zz) -> new Point3D(s, zz, z));
                if (pieceFace == null) {
                    markBroken("a piece of the bottom of the object collapsed into a line: " + piece);
                    break;
                }
                pieces.add(pieceFace);
            }
            if (broken) {
                return;
            }
            if (pieces.isEmpty() && tessellated == 0) {
                markBroken("the bottom of the object is entirely inside the opening of the passage");
                return;
            }
            replacements.put(faceIndex, pieces);
        }
    }

    /** Is the area of a face (given by its ring in the floor plane) crossed by the strip of a passage? */
    private boolean crosses(Coordinate[] ring, Geometry strip) {
        try {
            Polygon polygon = factory.createPolygon(ring);
            return polygon.isValid() && polygon.intersection(strip).getArea() > MIN_AREA;
        } catch (TopologyException | IllegalArgumentException e) {
            return true; //cannot tell, better leave the building alone
        }
    }

    /**
     * Vertical walls of the passage, floor to top of the arch.  The lower edge of such a wall lies on the
     * boundary of the cut floor, which may have a vertex in the middle of that edge, at a seam between the
     * pieces of the floor: such vertices are inserted into the wall face so that wall and floor share edges.
     */
    private void addPassageWalls(List<Coordinate[]> edges) {
        for (Coordinate[] edge : edges) {
            if (edge[0].distance(edge[1]) < MIN_EDGE) {
                continue;
            }
            List<Coordinate> bottom = new ArrayList<>();
            bottom.add(edge[0]);
            bottom.addAll(verticesOn(edge[0], edge[1]));
            bottom.add(edge[1]);
            int n = bottom.size();
            int[] face = new int[n + 2];
            for (int i = 0; i < n; i++) {
                Coordinate c = bottom.get(i);
                face[i] = vertexId(c.x, c.y, floorZ);
            }
            //the upper edge is not split, because the ceiling of the arch has a single edge there
            Coordinate last = bottom.get(n - 1);
            Coordinate first = bottom.get(0);
            face[n] = vertexId(last.x, last.y, archZ);
            face[n + 1] = vertexId(first.x, first.y, archZ);
            addFace(face, Mesh.WALL_COLOUR_IDX);
        }
    }

    /** Vertices of the floor strictly inside the segment from a to b, ordered from a to b, only those exactly
     *  on its line. */
    private List<Coordinate> verticesOn(Coordinate a, Coordinate b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double length = a.distance(b);
        List<Coordinate> found = new ArrayList<>();
        for (Point3D p : mesh.verts) {
            if (Math.abs(p.z - floorZ) > WELD_EPS) {
                continue; //not a vertex of the floor of the building
            }
            double along = ((p.x - a.x) * dx + (p.y - a.y) * dy) / length;
            if (along < MIN_EDGE || length - along < MIN_EDGE) {
                continue; //not strictly inside the segment
            }
            double ox = a.x + along * dx / length - p.x;
            double oy = a.y + along * dy / length - p.y;
            if (ox * ox + oy * oy > WELD_EPS * WELD_EPS) {
                continue; //not on the line of the wall of the passage
            }
            found.add(new Coordinate(p.x, p.y));
        }
        found.sort(Comparator.comparingDouble(c -> (c.x - a.x) * dx + (c.y - a.y) * dy));
        return found;
    }

    /** The ceiling of the arch: the plan of the opening at the height of the arch, looking down. */
    private void addArchCeilings(List<Polygon> openings) {
        for (Polygon opening : openings) {
            Coordinate[] outer = opening.getExteriorRing().getCoordinates();
            if (opening.getNumInteriorRing() == 0) {
                //reversed, so that the normal of the horizontal face points down
                int[] face = ringToFace(reverse(outer), false, (s, z) -> new Point3D(s, z, archZ));
                if (face == null) {
                    markBroken("the ceiling of the arch collapsed into a line: " + opening);
                    return;
                }
                addFace(face, Mesh.WALL_COLOUR_IDX);
            } else {
                //an opening with a hole cannot be a single face, so it is tessellated, as MesherFlat does
                tessellateLookingDown(opening, archZ, Mesh.WALL_COLOUR_IDX, "the ceiling of the arch");
            }
        }
    }

    /**
     * A horizontal polygon with a hole cannot be one face of our mesh, so it is tessellated the way MesherFlat
     * tessellates the floor and the roof of a building with a courtyard.
     *
     * @param what what this polygon is, for the message about a failure
     */
    private void tessellateLookingDown(Polygon polygon, double z, int material, String what) {
        //JTS keeps the orientation of the rings of its inputs, while the faces must all look the same way,
        //so the rings are made canonical first
        Polygon canonical = canonicalRings(polygon);
        List<Coordinate[]> rings = new ArrayList<>();
        rings.add(reverse(canonical.getExteriorRing().getCoordinates()));
        for (int i = 0; i < canonical.getNumInteriorRing(); i++) {
            rings.add(canonical.getInteriorRingN(i).getCoordinates());
        }

        GLU glu = new GLU();
        GLUtessellator tess = glu.gluNewTess();
        try {
            HorizontalCallback callback = new HorizontalCallback(what);
            glu.gluTessCallback(tess, GLU.GLU_TESS_VERTEX_DATA, callback);
            glu.gluTessCallback(tess, GLU.GLU_TESS_BEGIN_DATA, callback);
            glu.gluTessCallback(tess, GLU.GLU_TESS_END_DATA, callback);
            glu.gluTessCallback(tess, GLU.GLU_TESS_ERROR, callback);
            glu.gluTessCallback(tess, GLU.GLU_TESS_COMBINE_DATA, callback);
            glu.gluTessProperty(tess, GLU.GLU_TESS_WINDING_RULE, GLU.GLU_TESS_WINDING_ODD);

            glu.gluTessBeginPolygon(tess, null);
            for (Coordinate[] ring : rings) {
                glu.gluTessBeginContour(tess);
                for (Coordinate p : ring) {
                    double[] coords = {p.x, p.y, z};
                    glu.gluTessVertex(tess, coords, 0, vertexId(p.x, p.y, z));
                }
                glu.gluTessEndContour(tess);
            }
            glu.gluTessEndPolygon(tess);

            for (int[] face : callback.faces) {
                int[] cleaned = dedup(face);
                if (cleaned != null && !broken) {
                    addFace(cleaned, material);
                }
            }
            if (callback.failed || callback.faces.isEmpty()) {
                markBroken(what + " with a hole cannot be tessellated: " + polygon);
            }
        } finally {
            glu.gluDeleteTess(tess);
        }
    }

    /** Callback that collects the faces of a tessellated horizontal face (same primitives as MesherFlat gets). */
    private class HorizontalCallback extends GLUtessellatorCallbackAdapter {
        private final List<int[]> faces = new ArrayList<>();
        private final List<Integer> current = new ArrayList<>();
        private final String what;
        private int primitiveType;
        private boolean failed;

        HorizontalCallback(String what) {
            this.what = what;
        }

        @Override
        public void beginData(int type, Object polygonData) {
            primitiveType = type;
            current.clear();
        }

        @Override
        public void vertexData(Object vertexData, Object polygonData) {
            if (vertexData == null) {
                failed = true; //a triangle without a vertex cannot be a face of the mesh
            } else {
                current.add((Integer) vertexData);
            }
        }

        @Override
        public void combine(double[] coords, Object[] data, float[] weight, Object[] outData) {
            //where two edges cross, or where the tessellator makes a vertex out of the rings: it must become a
            //real vertex of the mesh, or the triangles do not fit.  This is the only flavour JOGL has.
            outData[0] = vertexId(coords[0], coords[1], coords[2]);
        }

        @Override
        public void endData(Object polygonData) {
            int n = current.size();
            if (primitiveType == GL2.GL_TRIANGLE_FAN) {
                for (int i = 1; i + 1 < n; i++) {
                    faces.add(new int[]{current.get(0), current.get(i), current.get(i + 1)});
                }
            } else if (primitiveType == GL2.GL_TRIANGLE_STRIP) {
                for (int i = 0; i + 2 < n; i++) {
                    if (i % 2 == 0) {
                        faces.add(new int[]{current.get(i), current.get(i + 1), current.get(i + 2)});
                    } else {
                        faces.add(new int[]{current.get(i + 1), current.get(i), current.get(i + 2)});
                    }
                }
            } else if (primitiveType == GL2.GL_TRIANGLES) {
                for (int i = 0; i + 2 < n; i += 3) {
                    faces.add(new int[]{current.get(i), current.get(i + 1), current.get(i + 2)});
                }
            } else {
                //The tessellator asked for lines or points, which cannot become faces of the mesh.
                failed = true;
                UrbanEye3dPlugin.debugMsg(what + " produces " + primitiveType + " instead of triangles");
            }
            current.clear();
        }

        @Override
        public void error(int errnum) {
            failed = true;
            UrbanEye3dPlugin.debugMsg("Cannot tessellate " + what + " (" + errnum + ")");
        }
    }

    /** A vertical line of a wall face: the face is described by s (along the wall) and z. */
    private class WallLine {
        private final double x0, y0, dx, dy;
        /** z-coordinate of the lowest vertex of the face, and the s-range of the face. */
        double faceMinZ;
        double sMin, sMax;
        /** s-coordinates of the vertices of the face, to snap the edges of the hole to them. */
        private double[] vertexS = new double[0];

        WallLine(Coordinate a, Coordinate b) {
            this.x0 = a.x;
            this.y0 = a.y;
            double len = a.distance(b);
            this.dx = (b.x - a.x) / len;
            this.dy = (b.y - a.y) / len;
        }

        double s(double x, double y) {
            return (x - x0) * dx + (y - y0) * dy;
        }

        double skew(double x, double y) {
            return Math.abs((x - x0) * dy - (y - y0) * dx);
        }

        Point3D point3D(double s, double z) {
            return new Point3D(x0 + s * dx, y0 + s * dy, z);
        }

        /** The part of this wall covered by the given opening edge, in s-coordinates, or null if it is elsewhere. */
        double[] intervalOf(LineString chord) {
            Coordinate a = chord.getCoordinateN(0);
            Coordinate b = chord.getCoordinateN(chord.getNumPoints() - 1);
            if (skew(a.x, a.y) > ON_CONTOUR_EPS || skew(b.x, b.y) > ON_CONTOUR_EPS) {
                return null; //the edge of the opening does not lie in the plane of this wall
            }
            double s0 = snap(s(a.x, a.y));
            double s1 = snap(s(b.x, b.y));
            if (s0 > s1) {
                double t = s0;
                s0 = s1;
                s1 = t;
            }
            if (s1 - s0 < MIN_EDGE || s1 < sMin + MIN_EDGE || s0 > sMax - MIN_EDGE) {
                return null; //the opening is elsewhere on the same line, but not on this face of the wall
            }
            return new double[]{s0, s1};
        }

        private double snap(double s) {
            //the edge of the hole snaps onto a vertex of this wall face only when they are really the same point
            for (double v : vertexS) {
                if (Math.abs(v - s) < WELD_EPS) {
                    return v;
                }
            }
            return s;
        }
    }

    /** Builds the wall line of the face, or returns null if the face is not a flat vertical wall. */
    private WallLine wallLine(int[] face) {
        Coordinate[] xy = new Coordinate[face.length];
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < face.length; i++) {
            Point3D p = mesh.verts.get(face[i]);
            xy[i] = new Coordinate(p.x, p.y);
            minZ = Math.min(minZ, p.z);
            maxZ = Math.max(maxZ, p.z);
        }
        //the extremes of the projection of the face onto the floor plane define the wall
        int ia = 0, ib = 0;
        double best = -1;
        for (int i = 0; i < xy.length; i++) {
            for (int j = i + 1; j < xy.length; j++) {
                double d = xy[i].distance(xy[j]);
                if (d > best) {
                    best = d;
                    ia = i;
                    ib = j;
                }
            }
        }
        if (best < MIN_EDGE || maxZ - minZ < 1e-3) {
            return null;
        }
        WallLine wall = new WallLine(xy[ia], xy[ib]);
        double[] vertexS = new double[xy.length];
        for (int i = 0; i < xy.length; i++) {
            if (wall.skew(xy[i].x, xy[i].y) > MAX_FACE_SKEW) {
                return null; //the face is not in one vertical plane
            }
            vertexS[i] = wall.s(xy[i].x, xy[i].y);
        }
        wall.vertexS = vertexS;
        wall.faceMinZ = minZ;
        wall.sMin = vertexS[0];
        wall.sMax = vertexS[0];
        for (double s : vertexS) {
            wall.sMin = Math.min(wall.sMin, s);
            wall.sMax = Math.max(wall.sMax, s);
        }
        return wall;
    }

    /** Converts a 2D ring (in the plane of a face) back into a face of the mesh. */
    private int[] ringToFace(Coordinate[] ring, boolean wantCCW, Mapper mapper) {
        Coordinate[] closed = ring;
        boolean ccw = shoelace(ring) > 0;
        if (closed.length > 1 && wantCCW != ccw) {
            closed = reverse(ring);
        }
        int[] indices = new int[closed.length];
        int n = 0;
        for (Coordinate p : closed) {
            Point3D p3 = mapper.map(p.x, p.y);
            int id = vertexId(p3.x, p3.y, p3.z);
            if (n > 0 && indices[n - 1] == id) {
                continue; //duplicated corner of the ring
            }
            indices[n++] = id;
        }
        return dedup(Arrays.copyOf(indices, n));
    }

    /**
     * Removes the closing vertex of the ring and the vertices a face visits twice.  Such a face has an edge of
     * zero length and the body is not closed anymore; it comes from a piece of a face touching itself at a point
     * or from a degenerate triangle of the tessellator.  A degenerate triangle is left out, any other repeated
     * vertex makes the passage impossible.
     */
    private int[] dedup(int[] face) {
        if (face == null || face.length < 3) {
            return null;
        }
        int[] result = new int[face.length];
        int n = 0;
        for (int index : face) {
            if (n > 0 && result[n - 1] == index) {
                continue; //the same vertex twice in a row
            }
            int seen = firstIndexOf(result, n, index);
            if (seen == 0) {
                continue; //the ring of the face was closed in its input
            }
            if (seen > 0) {
                markBroken("a face of the opening visits the same vertex twice: " + Arrays.toString(face));
                return null;
            }
            result[n++] = index;
        }
        if (n < 3) {
            return null; //the face collapsed into a point or a line
        }
        return Arrays.copyOf(result, n);
    }

    private static int firstIndexOf(int[] array, int length, int value) {
        for (int i = 0; i < length; i++) {
            if (array[i] == value) {
                return i;
            }
        }
        return -1;
    }

    private interface Mapper {
        Point3D map(double s, double second);
    }

    private int vertexId(double x, double y, double z) {
        //the vertex cache of Mesh is not used here: meshers add vertices straight to the list, so it is empty
        Integer found = findNearbyVertex(x, y, z);
        if (found != null) {
            return found;
        }
        mesh.verts.add(new Point3D(x, y, z));
        int index = mesh.verts.size() - 1;
        vertexIds.put(vertexKey(x, y, z), index);
        return index;
    }

    /**
     * Looks for a mesh vertex at the same point as the given one.  The keys of the map are rounded to a grid the
     * size of the tolerance, so the neighbouring cells are checked as well.
     */
    private Integer findNearbyVertex(double x, double y, double z) {
        long rx = Math.round(x / WELD_EPS);
        long ry = Math.round(y / WELD_EPS);
        long rz = Math.round(z / WELD_EPS);
        for (long dx = -1; dx <= 1; dx++) {
            for (long dy = -1; dy <= 1; dy++) {
                for (long dz = -1; dz <= 1; dz++) {
                    Integer id = vertexIds.get((rx + dx) + "/" + (ry + dy) + "/" + (rz + dz));
                    if (id != null) {
                        Point3D p = mesh.verts.get(id);
                        double ddx = p.x - x;
                        double ddy = p.y - y;
                        double ddz = p.z - z;
                        if (ddx * ddx + ddy * ddy + ddz * ddz < WELD_EPS * WELD_EPS) {
                            return id;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static String vertexKey(double x, double y, double z) {
        long rx = Math.round(x / WELD_EPS);
        long ry = Math.round(y / WELD_EPS);
        long rz = Math.round(z / WELD_EPS);
        return rx + "/" + ry + "/" + rz;
    }

    private void addFace(int[] face, int material) {
        addedFaces.add(face);
        addedMaterials.add(material);
    }

    private void applyChanges() {
        List<int[]> faces = new ArrayList<>();
        List<Integer> materials = new ArrayList<>();
        List<int[]> uvs = new ArrayList<>();
        for (int i = 0; i < mesh.faces.size(); i++) {
            List<int[]> replacement = replacements.get(i);
            if (replacement == null) {
                faces.add(mesh.faces.get(i));
                materials.add(mesh.faceMaterials.get(i));
                uvs.add(mesh.faceUVs.get(i));
            } else {
                for (int[] face : replacement) {
                    faces.add(face);
                    materials.add(mesh.faceMaterials.get(i));
                    int[] uv = mesh.faceUVs.get(i);
                    //UV coordinates belong to the vertices of the face, so a piece of the face, which has
                    //different vertices, cannot inherit them
                    uvs.add(uv != null && uv.length == face.length ? uv : null);
                }
            }
        }
        for (int i = 0; i < addedFaces.size(); i++) {
            faces.add(addedFaces.get(i));
            materials.add(addedMaterials.get(i));
            uvs.add(null);
        }
        mesh.faces.clear();
        mesh.faces.addAll(faces);
        mesh.faceMaterials.clear();
        mesh.faceMaterials.addAll(materials);
        mesh.faceUVs.clear();
        mesh.faceUVs.addAll(uvs);
        //The new vertices were added to the list directly, so the cached bounds and the vertex cache of the
        //mesh do not know about them.
        mesh.invalidateBBOX();
    }

    // ---------------------------------------------------------------- helpers

    private Polygon rectangle(double s0, double s1, double z0, double z1) {
        Coordinate[] ring = new Coordinate[]{
                new Coordinate(s0, z0), new Coordinate(s1, z0), new Coordinate(s1, z1),
                new Coordinate(s0, z1), new Coordinate(s0, z0)};
        return factory.createPolygon(ring);
    }

    private static List<Polygon> polygonsOf(Geometry geometry) {
        List<Polygon> result = new ArrayList<>();
        if (geometry instanceof Polygon) {
            Polygon polygon = (Polygon) geometry;
            if (polygon.getArea() >= MIN_AREA) {
                //an empty or a hairline piece of JTS is not a piece at all
                result.add(polygon);
            }
        } else if (geometry instanceof MultiPolygon) {
            for (int i = 0; i < geometry.getNumGeometries(); i++) {
                result.addAll(polygonsOf(geometry.getGeometryN(i)));
            }
        } else if (geometry instanceof GeometryCollection) {
            for (int i = 0; i < geometry.getNumGeometries(); i++) {
                result.addAll(polygonsOf(geometry.getGeometryN(i)));
            }
        }
        return result;
    }

    private static Coordinate[] reverse(Coordinate[] ring) {
        Coordinate[] result = new Coordinate[ring.length];
        for (int i = 0; i < ring.length; i++) {
            result[i] = ring[ring.length - 1 - i];
        }
        return result;
    }

    /** Twice the signed area of a 2D ring; positive for a counter-clockwise one. */
    private static double shoelace(Coordinate[] ring) {
        double sum = 0;
        int n = ring.length - 1;
        for (int i = 0; i < n; i++) {
            sum += ring[i].x * ring[i + 1].y - ring[i + 1].x * ring[i].y;
        }
        return sum;
    }
}
