package ru.zkir.urbaneye3d;

import com.drew.lang.annotations.Nullable;
import org.locationtech.jts.geom.Geometry;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import ru.zkir.urbaneye3d.meshers.custom.MesherBillboard;
import ru.zkir.urbaneye3d.utils.BuildingPassage;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;
import ru.zkir.urbaneye3d.meshers.s3db.RoofShapes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openstreetmap.josm.data.osm.PrimitiveId;

import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_LEVELS_NUMBER;
import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_LEVEL_HEIGHT;
import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_CHIMNEY_HEIGHT;
import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_ROOF_THICKNESS;
import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.INHERIT_HEIGHT_FROM_PARENT;
import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getTagD;
import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getTagStr;

public class RenderableElement {

    public final PrimitiveId primitiveId;
    public final LatLon origin; // since we do not have stable global Cartesian coordinate system, origin is latlon
    public final double direction; // mesh can be rotated.
    public final double zOffset; //mesh can be shifted up, due to min_height tag.
    private final Mesh mesh;
    public boolean isSelected;


    public final double physicalArea;

    /**
     * Vertical range used for the fake ambient occlusion.
     * Normally it is the bounding box of this very mesh, but for the pieces of an object which was cut
     * by a building passage it is the range of the whole original object, so that the shading gradient
     * is not restarted on every piece (see {@link #setAmbientOcclusionRange}).
     */
    private double aoMinHeight;
    private double aoMaxHeight;

    /** Bottom of the vertical range used for the fake ambient occlusion, meters. */
    public double getAoMinHeight() {
        return aoMinHeight;
    }

    /** Top of the vertical range used for the fake ambient occlusion, meters. */
    public double getAoMaxHeight() {
        return aoMaxHeight;
    }

    /**
     * Tells the renderer to calculate the fake ambient occlusion from the given vertical range
     * instead of the bounds of this mesh. It is used by the pieces of a building cut by a building
     * passage: the lower blocks and the part above the passage then share one shading gradient,
     * computed from the ground (or from the original min_height) up to the original roof,
     * so the cut is no longer visible as a change of brightness.
     */
    public void setAmbientOcclusionRange(double minHeight, double maxHeight) {
        if (maxHeight > minHeight) {
            this.aoMinHeight = minHeight;
            this.aoMaxHeight = maxHeight;
        }
    }

    /**
     * Creates Renderable Element from basic parameters. May return null if object is not creatable.
     * @return RenderableElement
     */
    @Nullable
    public static BuildingRecipe createBuildingOrPartRecipe(OsmPrimitive primitive, LatLon primitiveOrigin, Contour contour,
                                                            Map<String, String> primitiveTags, Map<String, String> parentTags ){

        String source_key="";
        if (primitiveTags.containsKey("building") && !primitiveTags.get("building").equals("no") ) {
            source_key = "building";
        } else if (primitiveTags.containsKey("building:part")  && !primitiveTags.get("building:part").equals("no") ) {
            source_key="building:part";
        } else {
            throw new RuntimeException("This is neither building nor building part");
        }

        Double height =  getTagD("height", primitiveTags, parentTags);
        if ( height==null ) {
            height = getTagD("building:height", primitiveTags, parentTags);
        }

        Double levels = getTagD("building:levels", primitiveTags, parentTags);
        Double minHeight = getTagD("min_height", primitiveTags, parentTags);
        Double minLevel = getTagD("building:min_level", primitiveTags, parentTags);
        Double roofHeight = getTagD("roof:height", primitiveTags, parentTags);
        Double roofLevels =  getTagD("roof:levels", primitiveTags, parentTags);
        String roofShape = getTagStr("roof:shape", primitiveTags, parentTags);
        String buildingShape = getTagStr("shape", primitiveTags, parentTags);

        Double stepHeight = getTagD("step:height", primitiveTags, parentTags);

        // Hyperboloid specific tags
        Double hyperboloidTopRate = getTagD("hyperboloid:top_rate", primitiveTags, parentTags);
        Double hyperboloidMiddleRate = getTagD("hyperboloid:middle_rate", primitiveTags, parentTags);

        // we ignore such underground buildings/parts for now.
        if (isPrimitiveUnderground(primitive, primitiveTags)) return null;

        // New: Prioritize building:shape over roof:shape for specific cases like hyperboloid
        if (!buildingShape.isEmpty()){
            if (buildingShape.equals("hyperboloid")) {
                roofShape = "hyperboloid";
            }
            // Add other building:shape types here as needed in the future
        }

        if (roofShape.isEmpty()){
            roofShape="flat";
        }
        if (roofShape.equals("cone")){
            roofShape="pyramidal";
        }

        if (roofShape.equals("equal_hipped")){
            roofShape="hipped";
        }

        if (roofShape.equals("many") && source_key.equals("building")){
            roofShape="hipped";
        }

        if (roofShape.equals("crosspitched")) {
            roofShape = "cross_gabled";
        }

        if (roofShape.equals("skillion") && getTagStr(source_key, primitiveTags, "").equals("steps")) {
            roofShape="steps";
        }

        //default values for minHeight. Tags order: min_height, minLevel
        if (minHeight ==null){
            if (minLevel!=null) {
                minHeight = minLevel * DEFAULT_LEVEL_HEIGHT;
            }else{
                minHeight=0.0;
            }
        }

        //default value for roof:height
        if (roofHeight == null ) {
            if (roofLevels != null) {
                if (roofLevels==0 && !roofShape.equals("flat")){
                    // roof:levels=0 does not mean that the roof is flat,
                    // especially in case it is explicitly specified that it does not.
                    // it rather means that roof does not constitute habitable level
                    roofHeight =  DEFAULT_LEVEL_HEIGHT * 0.5 ;
                }else {
                    roofHeight = roofLevels * DEFAULT_LEVEL_HEIGHT;
                }
            } else {
                if (roofShape.equals("flat")) {
                    roofHeight = 0.;
                } else {
                    //here we infer roof height from it's shape, because, say, gabled roof usually have some height.
                    //F4 map also does that.
                    roofHeight = 1.0 * DEFAULT_LEVEL_HEIGHT;
                }
            }
        }

        //default values for height. Tags order: height, building:levels+roof:levels, default height or parent height
        if (height==null) {
            if(INHERIT_HEIGHT_FROM_PARENT) {
                //throw new RuntimeException("Inheriting height from parent is not currently supported.");
                //to return it back, probably tag inheritance should be adjusted somehow
                if (source_key.equals("building") && levels == null) {
                    levels = DEFAULT_LEVELS_NUMBER;
                }
                if (levels != null) {
                    height = levels * DEFAULT_LEVEL_HEIGHT;
                    height += roofHeight; //roof:levels are not included into levels, so we can do this increment
                } else {
                    //This is a very controversial feature. There are a lot of building parts without height,
                    //which are not rendered in any 3D renderer. So they can look strange.
                    height = null;//buildingHeights.get(parent);
                    if (height == null) {
                        //this situation is possible in 2 cases:
                        // * Building part is orphan
                        // * Spatial containment check failed
                        height = 0.0;
                        //System.out.println("Height could not be determined for "+ primitive.getPrimitiveId()+ " (" + source_key+")");
                    }
                }
            }else{
                //buildings and buildings parts are processed uniformly:
                //Tags order: height, building:levels, default_level.
                if (levels == null) {
                    levels = DEFAULT_LEVELS_NUMBER;
                }
                height = levels * DEFAULT_LEVEL_HEIGHT;
                height += roofHeight; //roof:levels are not included into levels, so we can do this increment
            }
        }

        if(height<minHeight){
            // this it not a defined behaviour, so we can do anything.
            // disappearing buildings are not nice, so let's limit height.
            height=minHeight;
        }

        // we have the proper support for building:part=roof now,
        // in such case walls and bottom are extruded downwards, for certain roof shapes
        boolean noWalls = false;
        if (source_key.equals("building:part")) {
            // walls are not generated if wall=no OR building:part=roof. wall=yes overrides building:part=roof
            if (("roof".equals(primitiveTags.get(source_key)) && !"yes".equals(primitiveTags.get("wall"))) ||
                    "no".equals(primitiveTags.get("wall"))) {
                minHeight = height - roofHeight - DEFAULT_ROOF_THICKNESS;
                noWalls = true;
            }
        }

        if (height <= 0) {
            return null;
        }

        String wallColor = getTagStr("building:colour", primitiveTags, parentTags);
        String roofColor = getTagStr("roof:colour", primitiveTags, parentTags);

        String roofDirection = getTagStr("roof:direction", primitiveTags, parentTags);
        String roofOrientation = getTagStr("roof:orientation", primitiveTags, parentTags);

        //TODO: probably we need to create material, and inherit diffuse colour from xxx:colour tag
        var roofMaterial = Materials.fromString(getTagStr("roof:material",     primitiveTags, parentTags));
        var wallMaterial = Materials.fromString(getTagStr("building:material", primitiveTags, parentTags));

        // get default values if material is specified
        if (roofMaterial!=null && roofColor.isEmpty()){
            roofColor = roofMaterial.defaultColour;
        }
        if (wallMaterial!=null && wallColor.isEmpty()){
            wallColor = wallMaterial.defaultColour;
        }

        if (contour == null || contour.outerRings.isEmpty()) {
            return null;
        }

        contour.toLocalCoords(primitiveOrigin);
        contour.removeRedundantNodes();
        BuildingRecipe buildingRecipe = new BuildingRecipe(primitive.getPrimitiveId(), contour,
                height, minHeight, roofHeight, wallColor, roofColor, roofShape, roofDirection, roofOrientation,
                stepHeight, noWalls, hyperboloidTopRate, hyperboloidMiddleRate);

        return buildingRecipe;
    }

    /**
     * Creates renderable elements for a building or a building part.
     * <p>
     * Normally it is a single element. But if the object is crossed by a building passage
     * ({@code tunnel=building_passage}), the object is split into:
     * <ul>
     *     <li>one or more pieces up to the height of the passage (they form the walls of the passage),</li>
     *     <li>the part of the building lying above the passage, which keeps its original roof shape.</li>
     * </ul>
     * All those elements share the same primitiveId, so they are selected and highlighted together.
     * @param passageZones building passages of the scene, already filtered by this primitive
     */
    public static List<RenderableElement> createBuildingOrPart(OsmPrimitive primitive, LatLon primitiveOrigin, Contour contour,
                                                         Map<String, String> primitiveTags, Map<String, String> parentTags,
                                                         List<BuildingPassage.Zone> passageZones ){

        if (primitive.isDeleted()){
            return List.of();
        }

        //The contour is still in LatLon coords here, so we can cheaply skip the passages which are far away.
        List<BuildingPassage.Zone> zones = filterPassageZones(contour, passageZones);

        BuildingRecipe baseRecipe = createBuildingOrPartRecipe(primitive, primitiveOrigin, contour,  primitiveTags,  parentTags);
        if (baseRecipe==null){
            return List.of();
        }

        List<BuildingPiece> pieces = new ArrayList<>();
        pieces.add(new BuildingPiece(contour, new HashMap<>(primitiveTags)));

        boolean wasCut = false;
        if (!zones.isEmpty() && !baseRecipe.noWalls) {
            //Passages of different heights are applied one by one, from the lowest one to the highest one.
            zones.sort(Comparator.comparingDouble(zone -> zone.height));
            for (BuildingPassage.Zone zone : zones) {
                CutResult cut = cutByPassage(primitive, primitiveOrigin, parentTags, pieces, zone);
                pieces = cut.pieces;
                wasCut |= cut.wasCut;
            }
        }

        List<RenderableElement> result = new ArrayList<>();
        for (BuildingPiece piece : pieces) {
            BuildingRecipe recipe = createBuildingOrPartRecipe(primitive, primitiveOrigin, piece.contour, piece.tags, parentTags);
            if (recipe == null) {
                continue;
            }
            Mesh mesh = composeMesh(recipe);
            RenderableElement element = new RenderableElement(primitive, primitiveOrigin, mesh, 0, 0);
            if (wasCut) {
                //All pieces of the cut object are shaded as one body: from the original bottom to the original top.
                element.setAmbientOcclusionRange(baseRecipe.minHeight, baseRecipe.height);
            }
            result.add(element);
        }
        return result;
    }

    /** The pieces of an object after one more passage has been applied, and whether it cut anything. */
    private static final class CutResult {
        private final List<BuildingPiece> pieces;
        private final boolean wasCut;

        private CutResult(List<BuildingPiece> pieces, boolean wasCut) {
            this.pieces = pieces;
            this.wasCut = wasCut;
        }
    }

    /** A solid to render: a footprint (in local coords) and the tags which define its vertical dimensions. */
    private static final class BuildingPiece {
        private final Contour contour;
        private final Map<String, String> tags;

        private BuildingPiece(Contour contour, Map<String, String> tags) {
            this.contour = contour;
            this.tags = tags;
        }
    }

    /**
     * Cheap bbox-based pre-filtering of the passages. The corridor itself is built with JTS,
     * which is much more expensive, so we do it only for the buildings which may actually be crossed.
     */
    private static List<BuildingPassage.Zone> filterPassageZones(Contour contour, List<BuildingPassage.Zone> passageZones) {
        if (passageZones == null || passageZones.isEmpty()) {
            return List.of();
        }
        if (!"LatLon".equals(contour.mode)) {
            //We cannot tell anything about the coordinates anymore, let the exact check decide.
            return new ArrayList<>(passageZones);
        }
        double minLat = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
        double minLon = Double.POSITIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;
        for (List<Point2D> ring : contour.outerRings) {
            for (Point2D point : ring) {
                minLat = Math.min(minLat, point.y);
                maxLat = Math.max(maxLat, point.y);
                minLon = Math.min(minLon, point.x);
                maxLon = Math.max(maxLon, point.x);
            }
        }
        List<BuildingPassage.Zone> result = new ArrayList<>();
        for (BuildingPassage.Zone zone : passageZones) {
            if (zone.mayIntersect(minLat, maxLat, minLon, maxLon)) {
                result.add(zone);
            }
        }
        return result;
    }

    /**
     * Cuts the given pieces by one building passage.
     * Everything below the passage ceiling is replaced by the same solid without the passage corridor,
     * and the rest of the building (if it is higher than the passage) becomes a piece with
     * {@code min_height} equal to the height of the passage, keeping its original roof.
     */
    private static CutResult cutByPassage(OsmPrimitive primitive, LatLon origin, Map<String, String> parentTags,
                                                    List<BuildingPiece> pieces, BuildingPassage.Zone zone) {
        final double EPS = 1e-6;
        List<BuildingPiece> result = new ArrayList<>();
        boolean wasCut = false;
        Geometry corridor = null;
        for (BuildingPiece piece : pieces) {
            BuildingRecipe recipe = createBuildingOrPartRecipe(primitive, origin, piece.contour, piece.tags, parentTags);
            if (recipe == null) {
                result.add(piece);
                continue;
            }
            //The piece is completely above the passage, or has no vertical extent at all.
            if (recipe.minHeight >= zone.height - EPS || recipe.height <= recipe.minHeight + EPS) {
                result.add(piece);
                continue;
            }

            if (corridor == null) {
                corridor = zone.corridor(origin);
            }
            if (corridor == null || !intersects(piece.contour, corridor)) {
                result.add(piece);
                continue;
            }

            List<Contour> below = BuildingPassage.subtract(piece.contour, corridor);
            if (below == null) {
                //The boolean operation failed. Keep the object as it is, otherwise the upper piece would duplicate it.
                result.add(piece);
                continue;
            }

            //The building below the passage becomes a set of flat-roofed blocks, the passage walls are their outer walls.
            wasCut = true;
            double passageHeight = Math.min(zone.height, recipe.height);
            for (Contour pieceContour : below) {
                Map<String, String> tags = new HashMap<>(piece.tags);
                tags.put("height", Double.toString(passageHeight));
                tags.put("roof:height", "0");
                tags.put("roof:shape", "flat");
                tags.remove("shape");
                tags.remove("building:shape");
                result.add(new BuildingPiece(pieceContour, tags));
            }

            //The rest of the building stays as it is, but starts at the height of the passage.
            if (recipe.height > zone.height + EPS) {
                Map<String, String> tags = new HashMap<>(piece.tags);
                tags.put("min_height", Double.toString(zone.height));
                result.add(new BuildingPiece(piece.contour, tags));
            }
        }
        return new CutResult(result, wasCut);
    }

    /** Checks whether the footprint of a building part really intersects the passage corridor. */
    private static boolean intersects(Contour contour, Geometry corridor) {
        Geometry footprint = contour.toJTSGeometry();
        if (footprint == null || footprint.isEmpty()) {
            return false;
        }
        try {
            return footprint.intersection(corridor).getArea() >= BuildingPassage.MIN_AREA;
        } catch (Exception e) {
            UrbanEye3dPlugin.debugMsg("Unable to intersect a building with a building passage: " + e.getMessage());
            return false;
        }
    }

    /**
     * Creates Renderable Element for barrier from OsmPrimitive.
     * Normally, barrier in OSM is a linear object, and its width depends on tags,
     * so we cannot create contour beforehand.
     * May return null if object is not creatable
     * @param  primitive - OSM primitive (should be way)
     * @return RenderableElement
     */
    @Nullable
    public static RenderableElement createBarrier(OsmPrimitive primitive){

        if (primitive.isDeleted()){
            return null;
        }

        // we ignore such underground barriers for now.
        if(isPrimitiveUnderground(primitive)) return null;

        String barrierType = primitive.get("barrier");

        double width;
        double height;
        String color =  getTagStr("colour", primitive, "");
        //unlike buildings, for barrier just colour=* and material=* tags are used.
        var material = Materials.fromString(getTagStr("material", primitive, (String) null));

        // get default values if material is specified
        if (material!=null && color.isEmpty()){
            color = material.defaultColour;
        }
        if (color.isEmpty()){
            if(barrierType.equals("hedge")){
                color = "#308030";
            }else {
                color = "lightgray";
            }
        }

        switch (barrierType) {
            case "wall":
                width = getTagD("width", primitive, 0.25);
                height = getTagD("height",primitive, 1.5);
                break;
            case "hedge":
                width = getTagD("width", primitive, 0.5);
                height = getTagD("height", primitive, 1.5);
                break;
            case "fence":
                width = getTagD("width", primitive, 0.1);
                height = getTagD("height", primitive,  1.5);
                break;
            case "city_wall":
                width = getTagD("width", primitive, 1.0);
                height = getTagD("height", primitive, 5.0);
                break;
            default:
                return null; // Skip unsupported barrier types
        }
        Double minHeight = getTagD("min_height", primitive, 0);

        Contour contour;
        LatLon origin = primitive.getBBox().getCenter();
        if ("yes".equals(primitive.get("area"))) {
            contour = new Contour(primitive);
            contour.toLocalCoords(origin);
        } else {
            if (width > 0) {
                contour = new Contour((Way)primitive, width, origin);
            } else {
                return null;
            }
        }

        if (contour.outerRings.isEmpty()) {
            return null;
        }
        contour.removeRedundantNodes();
        BuildingRecipe buildingRecipe = new BuildingRecipe(primitive.getPrimitiveId(),  contour,
                height, minHeight, 0, color, color, "flat", "", "", null,
                false, null, null);

        Mesh mesh = composeMesh(buildingRecipe);

        return new RenderableElement(primitive, origin, mesh, 0, 0);
    }



    //similar to buildings, but with fewer options
    public static RenderableElement createManMade(OsmPrimitive primitive,  Contour contour){
        if (primitive.isDeleted()){
            return null;
        }
        var tag = primitive.get("man_made");
        if (tag==null){
            throw new RuntimeException("The object should have man_made tag to be processed by this method");
        }
        //We gladly accept any object, as long as it has height.
        //if it does not, we accept it if it of the known type
        if (!primitive.hasKey("height")) {
            if (!List.of("tower", "water_tower", "communications_tower", "cooling_tower", "chimney").contains(tag)) {
                return null;
            }
        }

        LatLon origin = primitive.getBBox().getCenter();
        var default_colour = ""; // Unified default (BuildingRecipe will handle it)
        var material = Materials.fromString(getTagStr("material", primitive, ""));
        if (material!=null){
            default_colour = material.defaultColour;
        }
        String color =  getTagStr("colour", primitive, default_colour);
        double minHeight = getTagD("min_height", primitive, 0);
        
        double defaultHeight = DEFAULT_LEVEL_HEIGHT * DEFAULT_LEVELS_NUMBER * 2;
        if ("chimney".equals(tag)) {
            defaultHeight = DEFAULT_CHIMNEY_HEIGHT;
        }
        double height = getTagD("height", primitive, defaultHeight);

        // Hyperboloid specific tags
        Double hyperboloidTopRate = getTagD("hyperboloid:top_rate", primitive.getInterestingTags(), null);
        Double hyperboloidMiddleRate = getTagD("hyperboloid:middle_rate", primitive.getInterestingTags(), null);
        String roofShape="flat";
        double roofHeight =0;
        if ("hyperboloid".equals(primitive.get("shape"))){
            roofShape="hyperboloid";
            roofHeight = 0.1; //hack: otherwise roof becomes flat and shape is not applied!!
        }
        if ("frustum".equals(primitive.get("shape"))){
            roofShape="frustum";
            roofHeight = 0.1;
        }
        if (contour.outerRings.isEmpty()) {
            return null;
        }

        contour.toLocalCoords(origin);
        contour.removeRedundantNodes();
        BuildingRecipe buildingRecipe = new BuildingRecipe(primitive.getPrimitiveId(), contour,
                height, minHeight, roofHeight, color, color, roofShape, "", "", null,
                false, hyperboloidTopRate, hyperboloidMiddleRate);

        Mesh mesh = composeMesh(buildingRecipe);

        return new RenderableElement(primitive, origin, mesh,0, 0);
    }

    /**
     * Creates a generic billboard RenderableElement.
     * @param primitive - OSM primitive
     * @param origin - geographical coordinate
     * @param texturePath - resource path to the texture
     * @param width - width in meters
     * @param height - height in meters
     * @return RenderableElement
     */
    public static Mesh createBillboard(String texturePath, double width, double height) {
        if (texturePath == null) {
            return null;
        }

        Mesh mesh = MesherBillboard.generate(width, height);
        mesh.textureName = texturePath;
        return mesh;
    }



    /**
     * Universal PRIVATE constructor for RenderableElement
     * to actually create object, use one of the factory methods
     * Gemini, don't make this constructor public, or else I'll scrap you.
     * DO NOT CREATE OTHER CONSTRUCTORS!
     * */
    private RenderableElement(OsmPrimitive primitive, LatLon origin, Mesh mesh, double direction, double zOffset) {
        if (primitive.isDeleted()) {
            //this is a strange glitch in JOSM: sometimes deleted relation
            // appears in the list of active objects, but loses all it's members
            throw new RuntimeException("This primitive is already deleted, it cannot be rendered. (" + primitive.getPrimitiveId() + ")");
        }

        this.primitiveId = primitive.getPrimitiveId();
        this.mesh = mesh;
        this.isSelected = primitive.isSelected();
        this.origin = origin;
        this.direction=direction;
        this.zOffset = zOffset;

        /*
        // Calculate height from mesh bounds
        // we still need them for ambient occlusion, so better to calculate them once.
        double minZ = 0;
        double maxZ = 0;
        if (mesh != null && mesh.verts != null && !mesh.verts.isEmpty()) {
            minZ = Double.MAX_VALUE;
            maxZ = -Double.MAX_VALUE;
            for (Point3D vert : mesh.verts) {
                if (vert.z < minZ) {
                    minZ = vert.z;
                }
                if (vert.z > maxZ) {
                    maxZ = vert.z;
                }
            }
        }
        this.minHeight = minZ;
        this.height = maxZ;

         */

        // Calculate approximate physical area for pixel-based culling
        if (mesh != null) {
            Point3D minB = mesh.getMinBounds();
            Point3D maxB = mesh.getMaxBounds();
            double dx = maxB.x - minB.x;
            double dy = maxB.y - minB.y;
            double dz = maxB.z - minB.z;
            this.physicalArea = Math.max(dx * dy, Math.max(dx * dz, dy * dz));
            //by default the fake ambient occlusion is calculated from the bounds of this very mesh
            this.aoMinHeight = minB.z;
            this.aoMaxHeight = maxB.z;
        } else {
            this.physicalArea = 0.0;
            this.aoMinHeight = 0.0;
            this.aoMaxHeight = 0.0;
        }
    }

    /**
     * Create a RenderableElement from a pre-loaded mesh model.
     * @param node The OSM node.
     * @param modelMesh The mesh to use.
     * @return A new RenderableElement.
     */
    public static RenderableElement createFromModel(OsmPrimitive primitive, LatLon origin, Mesh modelMesh, double direction, double translationZ) {
        if (primitive.isDeleted() || primitive.getBBox().getCenter() == null || modelMesh == null) {
            return null;
        }
        // If the mesh has a model-defined texture, we use it.
        return new RenderableElement(primitive, origin, modelMesh,  direction, translationZ);
    }


    public Mesh getMesh() {
        return this.mesh;
    }
	
    public static Mesh composeMesh(BuildingRecipe buildingRecipe){
        Mesh mesh = null;

        mesh = buildingRecipe.roofShape.getMesher().generate(buildingRecipe);

        //last chance! mesh can be null, in case specific roof shapes was not created due to limitations
        // for example, GABLED and HIPPED can be created for quadrangles only.
        if( mesh == null){
            // Collect all contours (outer and inner) for flat roof generation
            mesh = RoofShapes.FLAT.getMesher().generate(buildingRecipe);
        }
        return mesh;
    }

    public static boolean isPrimitiveUnderground(OsmPrimitive primitive) {
        return isPrimitiveUnderground(primitive, primitive.getInterestingTags());
    }

    public static boolean isPrimitiveUnderground(OsmPrimitive primitive, Map<String, String> primitiveTags) {
        Double layer = getTagD("layer", primitiveTags, 0);
        String location = getTagStr("location", primitiveTags, "");

        if ((layer<0) || (location.equals("underground"))){
            return true;
        }
        return false;
    }


}