package ru.zkir.urbaneye3d;

import com.drew.lang.annotations.NotNull;
import com.drew.lang.annotations.Nullable;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import ru.zkir.urbaneye3d.utils.ColorUtils;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.roofgenerators.RoofShapes;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.openstreetmap.josm.data.osm.PrimitiveId;

import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_LEVELS_NUMBER;
import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_LEVEL_HEIGHT;
import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_ROOF_THICKNESS;
import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.INHERIT_HEIGHT_FROM_PARENT;
import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_STEP_HEIGHT;

public class RenderableBuildingElement {

    public final PrimitiveId primitiveId;
    public boolean isSelected = false;
    public final double roofHeight;
    public final double minHeight;  // z0 -- z-coordinate of building bottom
    public final double wallHeight; // z1 -- z coordinate of walls top
    public final double height;     // z2 -- z coordinate of roof top
    public final @NotNull Color color;
    public final @NotNull Color roofColor;
    public final @NotNull Color bottomColor;
    public final RoofShapes roofShape;
    public final Double roofDirection;
    public final String roofOrientation;
    public final boolean noWalls;
    private final Contour contour;
    public final LatLon origin;
    public final double stepHeight;
    public final Double hyperboloidTopRate;
    public final Double hyperboloidMiddleRate;
    private Mesh mesh;

    /** Unlike F4, we inherit only some keys from building to parts, not all */
    final static List<String> inheritableKeys = Arrays.asList("building:colour", "building:material", "roof:colour", "roof:material");

    /**
     * Creates Renderable Element from basic parameters. May return null if object is not creatable.
     * @return RenderableBuildingElement
     */
    @Nullable
    public static RenderableBuildingElement createBuildingOrPart(OsmPrimitive primitive, LatLon primitiveOrigin, Contour contour,
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

        Double layer = getTagD("layer", primitiveTags, 0);
        String location = getTagStr("location", primitiveTags, "");

        if ((layer<0) || (location.equals("underground"))){
            // we ignore such underground buildings/parts for now.
            return null;
        }

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
        return new RenderableBuildingElement(primitive, primitiveOrigin, contour,
                height, minHeight, roofHeight, wallColor, roofColor, roofShape, roofDirection, roofOrientation,
                stepHeight, noWalls, hyperboloidTopRate, hyperboloidMiddleRate);
    }

    /**
     * Creates Renderable Element for barrier from OsmPrimitive.
     * Normally, barrier in OSM is a linear object, and its width depends on tags,
     * so we cannot create contour beforehand.
     * May return null if object is not creatable
     * @param  primitive - OSM primitive (should be way)
     * @return RenderableBuildingElement
     */
    @Nullable
    public static RenderableBuildingElement createBarrier(OsmPrimitive primitive){

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
            contour = new Contour(primitive, null);
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
        return new RenderableBuildingElement(primitive, origin, contour,
                height, minHeight, 0, color, color, "flat", "", "", null,
                false, null, null);
    }

    //similar to buildings, but with fewer options
    public static RenderableBuildingElement createManMade(OsmPrimitive primitive){
        var tag = primitive.get("man_made");
        if( !List.of("tower", "water_tower", "communications_tower", "cooling_tower").contains(tag)){
            return null;
        }
        Contour contour = new Contour(primitive, null);;
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

        contour.toLocalCoords(origin);
        contour.removeRedundantNodes();
        return new RenderableBuildingElement(primitive, origin, contour,
                height, minHeight, roofHeight, color, color, roofShape, "", "", null,
                false, hyperboloidTopRate, hyperboloidMiddleRate);
    }


    /**
     * This is a private constructor. createBuildingOrPart() or createBarrier() should be used outside, especially in autotests
     * */
    private RenderableBuildingElement(OsmPrimitive primitive, LatLon origin, Contour contour,
                                      double height, double minHeight, double roofHeight, String wallColor, String roofColor,
                                      String roofShape, String roofDirectionStr, String roofOrientation, Double stepHeight,
                                      boolean noWalls, Double hyperboloidTopRate, Double hyperboloidMiddleRate ) {
        this.primitiveId = primitive.getPrimitiveId();
        if (contour==null){
            throw new RuntimeException("contour must be specified");
        }

        this.origin = origin;
        if (contour.outerRings.isEmpty()){
            throw new RuntimeException("There can be empty multipolygon relations, broken or not fully downloaded. " +
                                       "However, renderable building cannot be created without outer ring. " +
                                       "This condition should be checked outside this constructor."
                                    );
        }
        this.contour = contour;

        this.height = height;
        this.minHeight = minHeight;

        //default value for roofHeight
        if (roofShape.isEmpty()){
            roofShape="flat";
        }

        if (roofHeight>height-minHeight){
            roofHeight=height-minHeight;
        }


        //in case outline has inner rings, we cannot construct any other roof, but FLAT and SKILLION
        // also, if roof's height is zero, it's flat!
        if( (roofHeight == 0) || (this.hasComplexContour() && !roofShape.equals(RoofShapes.SKILLION.toString()))){
            this.roofShape = RoofShapes.FLAT;
        }else{
            this.roofShape = RoofShapes.fromString(roofShape);
        }

        this.roofDirection = parseDirection(roofDirectionStr);
        if (roofOrientation==null){
            roofOrientation="";
        }
        this.roofOrientation = roofOrientation;

        this.roofHeight = roofHeight;
        this.wallHeight = height - roofHeight;

        this.color = parseColor(wallColor, new Color(204, 204, 204));
        this.roofColor = parseColor(roofColor, new Color(150, 150, 150));
        this.bottomColor = this.color.darker().darker(); //Fake AO LOL!

        if (stepHeight==null || stepHeight==0){
            this.stepHeight = DEFAULT_STEP_HEIGHT;
        }else{
            this.stepHeight = stepHeight;
        }

        this.noWalls = noWalls;
        this.hyperboloidTopRate = hyperboloidTopRate != null ? hyperboloidTopRate : 0.6;
        this.hyperboloidMiddleRate = hyperboloidMiddleRate != null ? hyperboloidMiddleRate : this.hyperboloidTopRate;

        //since we have all the data, we can compose building mesh right in constructor.
        composeMesh();
        this.isSelected = primitive.isSelected();
    }

    public boolean hasComplexContour() {
        return this.getContourOuterRings().size() > 1 || !this.getContourInnerRings().isEmpty();
    }

    public List<Point2D> getContour() {
        return contour.outerRings.isEmpty() ? new ArrayList<>() : contour.outerRings.get(0);
    }

    public List<ArrayList<Point2D>> getContourOuterRings() {
        return contour.outerRings;
    }

    public List<ArrayList<Point2D>> getContourInnerRings() {
        return contour.innerRings;
    }

    public Mesh getMesh() {
        return this.mesh;

    }
	
    public void composeMesh(){
        this.mesh = null;

        this.mesh = roofShape.getMesher().generate(this);

        //last chance! mesh can be null, in case specific roof shapes was not created due to limitations
        // for example, GABLED and HIPPED can be created for quadrangles only.
        if( this.mesh == null){
            // Collect all contours (outer and inner) for flat roof generation
            this.mesh = RoofShapes.FLAT.getMesher().generate(this);
        }
    }


    public  static Double parseDirection(String direction) {
        if (direction == null || direction.isEmpty()) {
            return Double.NaN; // Return NaN if direction is not specified
        }
        try {
            return Double.parseDouble(direction);
        } catch (NumberFormatException e) {
            // Handle cardinal directions (N, S, E, W, etc.)
            switch (direction.toUpperCase()) {
                case "N":   return   0.0;
                case "NNE": return  22.5;
                case "NE":  return  45.0;
                case "ENE": return  67.5;
                case "E":   return  90.0;
                case "ESE": return 112.5;
                case "SE":  return 135.0;
                case "SSE": return 157.5;
                case "S":   return 180.0;
                case "SSW": return 202.5;
                case "SW":  return 225.0;
                case "WSW": return 247.5;
                case "W":   return 270.0;
                case "WNW": return 292.5;
                case "NW":  return 315.0;
                case "NNW": return 337.5;
                default:    return Double.NaN;
            }
        }
    }

    private Color parseColor(String color, Color default_color){
        Color rgb_color = ColorUtils.parseColor(color);
        if (rgb_color == null) {
            rgb_color = default_color;
        }
        return rgb_color;
    }

    @NotNull
    public static String getTagStr(String key, OsmPrimitive primitive, String defaultValue ){
        return getTagStr(key, primitive.getInterestingTags(), defaultValue);
    }

    @NotNull
    private  static String getTagStr(String key, Map<String, String> primitive, String defaultValue ){
        String value = primitive.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    @NotNull
    private static String getTagStr(String key, Map<String, String> primitive, Map<String, String> parent ){

        String value=primitive.get(key);
        if ((value==null) && parent!=null && inheritableKeys.contains(key)){
            value=parent.get(key);
        }

        if (value==null){
            value="";
        }
        return value;
    }

    @NotNull
    private static Double getTagD(String key, OsmPrimitive primitive, double defaultValue) {
        return getTagD(key, primitive.getInterestingTags(), defaultValue);
    }

    @NotNull
    private static Double getTagD(String key, Map<String, String> primitive, double defaultValue) {
        String value = primitive.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.split(" ")[0]);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    //we need to get a floating point value from an osm tag
    // if tag is missing or cannot be parsed, the return value is null,
    // to let it possible to fallback to defaults.
    private static Double getTagD(String key, Map<String, String> primitive, Map<String, String> parent ){
        Double result;
        String tag_str = getTagStr(key, primitive, parent);

        if (tag_str.isEmpty()){
            return null;
        }

        try {
            result = Double.parseDouble(tag_str.split(" ")[0]);
        } catch (NumberFormatException e) {
            result = null;
        }
        return result;

    }
}