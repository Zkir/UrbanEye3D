package ru.zkir.urbaneye3d;

import com.drew.lang.annotations.Nullable;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import ru.zkir.urbaneye3d.generators.MesherTree;

import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;
import ru.zkir.urbaneye3d.roofgenerators.RoofShapes;

import ru.zkir.urbaneye3d.utils.TreeSpeciesDatabase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.openstreetmap.josm.data.osm.PrimitiveId;
import org.openstreetmap.josm.data.osm.Relation;

import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_LEVELS_NUMBER;
import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_LEVEL_HEIGHT;
import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_ROOF_THICKNESS;
import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_TREE_HEIGHT;
import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.INHERIT_HEIGHT_FROM_PARENT;
import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_STEP_HEIGHT;
import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getTagD;
import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getTagStr;

public class RenderableElement {

    public final PrimitiveId primitiveId;
    public final LatLon origin;
    private final Mesh mesh;
    public final String textureName;
    public boolean isSelected;

    public final double height;
    public final double minHeight;

    public final double physicalArea;

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

    public static RenderableElement createBuildingOrPart(OsmPrimitive primitive, LatLon primitiveOrigin, Contour contour,
                                                         Map<String, String> primitiveTags, Map<String, String> parentTags ){

        if (primitive.isDeleted()){
            return null;
        }
        BuildingRecipe buildingRecipe = createBuildingOrPartRecipe(primitive, primitiveOrigin, contour,  primitiveTags,  parentTags);
        if (buildingRecipe==null){
            return  null;
        }
        Mesh mesh = composeMesh(buildingRecipe);

        return new RenderableElement(primitive, primitiveOrigin, mesh,null);

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

        return new RenderableElement(primitive, origin, mesh, null);
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
            if (!List.of("tower", "water_tower", "communications_tower", "cooling_tower").contains(tag)) {
                return null;
            }
        }

        LatLon origin = primitive.getBBox().getCenter();
        String color =  getTagStr("colour", primitive, "");
        double minHeight = getTagD("min_height", primitive, 0);
        //TODO: here we got value  for height should be different for towers!
        double height = getTagD("height", primitive, DEFAULT_LEVEL_HEIGHT*DEFAULT_LEVELS_NUMBER*2 );
        // Hyperboloid specific tags
        Double hyperboloidTopRate = getTagD("hyperboloid:top_rate", primitive.getInterestingTags(), null);
        Double hyperboloidMiddleRate = getTagD("hyperboloid:middle_rate", primitive.getInterestingTags(), null);
        String roofShape="flat";
        double roofHeight =0;
        if ("hyperboloid".equals(primitive.get("shape"))){
            roofShape="hyperboloid";
            roofHeight = 0.1; //hack: otherwise roof becomes flat and shape is not applied!!
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

        return new RenderableElement(primitive, origin, mesh, null);
    }

    /** Create a tree*/
    public static RenderableElement createTree(Node node, String texturePath) {
        var random = new Random(node.getId());
        return createTree(node, node.getCoor(), node.getInterestingTags(), random, texturePath);
    }

    /**
     * Creates a tree RenderableElement from common parameters.
     * @param primitive - OSM primitive for this tree
     * @param origin - geographical coordinate (LatLon)
     * @param tags - tags to be used for height calculation
     * @param random - random object
     * @param texturePath - The path to the texture file
     * @return RenderableElement or null if tree cannot be created
     */
    public static RenderableElement createTree(OsmPrimitive primitive, LatLon origin, Map<String, String> tags, Random random, String texturePath) {
        if (primitive.isDeleted()){
            return null;
        }

        if (origin == null) {
            return null;
        }

        double treeHeight = 0;
        if (tags.containsKey("height")){
            treeHeight = getTagD("height", tags, 0);
        }
        if ((treeHeight == 0) && tags.containsKey("circumference")){
            double treeCircumference = getTagD("circumference", tags, 1);
            treeHeight = Math.pow((Math.log(treeCircumference)/Math.log(2) * 0.33 + 3), 2);
        }
        if (treeHeight == 0){
            treeHeight = DEFAULT_TREE_HEIGHT;
        }

        double treeWidth = treeHeight * 0.9; // Make width proportional to height

        if (texturePath == null) {
            UrbanEye3dPlugin.debugMsg("failed to assign texture to object with tags " + tags);
            return null;
        }

        // The origin of the tree object is the node itself.
        // The mesher creates geometry around (0,0,0).
        // The renderer will translate it to the correct world position.
        Mesh treeMesh = MesherTree.generate(treeWidth, treeHeight);

        return new RenderableElement(primitive, origin, treeMesh, texturePath);
    }

    public static RenderableElement createAdColumn(OsmPrimitive primitive, LatLon origin, Map<String, String> tags, Random random) {
        if (primitive.isDeleted()) return null;

        // ignore underground ad columns
        if(isPrimitiveUnderground(primitive)) return null;

        double width = getTagD("width", primitive, 1.5);
        double height = getTagD("height", primitive, 4.0);
        double min_height = getTagD("min_height", primitive, 0.0);
        var colour = getTagStr("colour", primitive, null);
        

        // Almost all columns are points
        // https://taginfo.openstreetmap.org/tags/advertising=column
        if("yes".equals(primitive.get("area"))) return null;
        if(primitive instanceof Relation) return null;
        
        // I did not see a way to re-use the existing buffer mechanic, therefore we just create a countour directly
        int segments = 16;
        ArrayList<Point2D> circle = new ArrayList<Point2D>();

        
        for(int i = 0; i < segments; i++) {
            double angle = (2 * Math.PI / segments) * i;
            double x = width / 2 * Math.cos(angle);
            double y = width / 2 * Math.sin(angle);
            circle.add(new Point2D(x, y));
        }

        Contour contour = new Contour(circle, "XY");
        contour.removeRedundantNodes();
        double roofHeight = width / 2;

        BuildingRecipe buildingRecipe = new BuildingRecipe(primitive.getPrimitiveId(), contour, height, min_height, roofHeight, colour, colour, "dome", "", "", null, false, null, null);
        Mesh mesh = composeMesh(buildingRecipe);

        return new RenderableElement(primitive, origin, mesh, null);
    }


    /**
     * Universal PRIVATE constructor for RenderableElement
     * to actually create object, use one of the factory methods
     * Gemini, don't make this constructor public, or else I'll scrap you.
     * DO NOT CREATE OTHER CONSTRUCTORS!
     * */
    private RenderableElement(OsmPrimitive primitive, LatLon origin, Mesh mesh, String textureName) {
        if (primitive.isDeleted()) {
            //this is a strange glitch in JOSM: sometimes deleted relation
            // appears in the list of active objects, but loses all it's members
            throw new RuntimeException("This primitive is already deleted, it cannot be rendered. (" + primitive.getPrimitiveId() + ")");
        }

        this.primitiveId = primitive.getPrimitiveId();
        this.mesh = mesh;
        this.textureName = textureName;
        this.isSelected = primitive.isSelected();
        this.origin = origin;

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

        // Calculate approximate physical area for pixel-based culling
        if (mesh != null) {
            Point3D minB = mesh.getMinBounds();
            Point3D maxB = mesh.getMaxBounds();
            double dx = maxB.x - minB.x;
            double dy = maxB.y - minB.y;
            double dz = maxB.z - minB.z;
            this.physicalArea = Math.max(dx * dy, Math.max(dx * dz, dy * dz));
        } else {
            this.physicalArea = 0.0;
        }
    }

    /**
     * Create a RenderableElement from a pre-loaded mesh model.
     * @param node The OSM node.
     * @param modelMesh The mesh to use.
     * @return A new RenderableElement.
     */
    public static RenderableElement createFromModel(Node node, Mesh modelMesh) {
        if (node.isDeleted() || node.getCoor() == null || modelMesh == null) {
            return null;
        }
        // The color is now part of the mesh itself, so we just wrap it.
        return new RenderableElement(node, node.getBBox().getCenter(), modelMesh, null);
    }


    public Mesh getMesh() {
        return this.mesh;
    }
	
    private static Mesh composeMesh(BuildingRecipe buildingRecipe){
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

    private static boolean isPrimitiveUnderground(OsmPrimitive primitive) {
        return isPrimitiveUnderground(primitive, primitive.getInterestingTags());
    }

    private static boolean isPrimitiveUnderground(OsmPrimitive primitive, Map<String, String> primitiveTags) {
        Double layer = getTagD("layer", primitiveTags, 0);
        String location = getTagStr("location", primitiveTags, "");

        if ((layer<0) || (location.equals("underground"))){
            return true;
        }
        return false;
    }


}