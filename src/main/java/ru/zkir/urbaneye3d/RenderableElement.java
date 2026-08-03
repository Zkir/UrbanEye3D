package ru.zkir.urbaneye3d;

import com.drew.lang.annotations.Nullable;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import ru.zkir.urbaneye3d.generators.MesherTree;

import ru.zkir.urbaneye3d.utils.ColorUtils;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.FlagColorInference;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;
import ru.zkir.urbaneye3d.roofgenerators.RoofShapes;

import ru.zkir.urbaneye3d.utils.TreeSpeciesDatabase;

import java.awt.Color;
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
import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_CHIMNEY_HEIGHT;
import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_ROOF_THICKNESS;
import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_TREE_HEIGHT;
import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.INHERIT_HEIGHT_FROM_PARENT;
import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_STEP_HEIGHT;
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

        return new RenderableElement(primitive, primitiveOrigin, mesh,0,0);

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
        String color =  getTagStr("colour", primitive, "");
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
    public static RenderableElement createBillboard(OsmPrimitive primitive, LatLon origin, String texturePath, double width, double height) {
        if (primitive.isDeleted() || origin == null || texturePath == null) {
            return null;
        }

        Mesh mesh = MesherTree.generate(width, height);
        mesh.textureName = texturePath;
        return new RenderableElement(primitive, origin, mesh, 0, 0);
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

        return new RenderableElement(primitive, origin, mesh, 0, 0);
    }

    public static RenderableElement createFlagpole(OsmPrimitive primitive, LatLon origin, Map<String, String> tags, Random random) {
        final int poleSegments = 8;
        final int flagSegments = 8;
        final double DEFAULT_FLAGPOLE_HEIGHT = 10.0;

        if (primitive.isDeleted()) return null;
        if (isPrimitiveUnderground(primitive)) return null;

        double height = getTagD("height", primitive, DEFAULT_FLAGPOLE_HEIGHT);
        double flagHeight = Math.pow(height, 0.5) / 1.9;
        double flagWidth = flagHeight * 1.5;


        double estimatedPolyRadius = Math.pow(height, 0.5) / 63.0;
        double polyRadius = getTagD("diameter", primitive, estimatedPolyRadius*2*1000)/2/1000; //NOTE: default unit for diameter tag is MILLIMETER!
        polyRadius = Math.max(polyRadius, 0.02); // pole should not be too narrow, even if units have messed up, e.g. diameter=1
        double finialRadius = polyRadius * 1.5;
        double finialHeight = finialRadius * 2;

        String mastColorStr = getTagStr("colour", primitive, "#C0C0C0");
        String flagColorStr = getTagStr("flag:colour", primitive, null);
        
        // If explicit color is missing, try data-driven inference
        if (flagColorStr == null) {
            flagColorStr = FlagColorInference.getInstance().getInferredColor(primitive);
        }
        // If still null, use the default
        if (flagColorStr == null) {
            flagColorStr = "#AFA0A0";
        }
        
        java.awt.Color mastColor = ColorUtils.parseColor(mastColorStr);
        java.awt.Color flagColor = ColorUtils.parseColor(flagColorStr);
        java.awt.Color finialColor = ColorUtils.parseColor("#FFD700"); // Gold

        Mesh mesh = new Mesh();
        // Materials: 0: mast, 1: finial, 2: flag
        mesh.materials.add(mastColor);
        mesh.materials.add(finialColor);
        mesh.materials.add(flagColor);

        // 1. Mast
        int[] bottomIndices = new int[poleSegments];
        int[] topIndices = new int[poleSegments];
        for (int i = 0; i < poleSegments; i++) {
            double angle = (2 * Math.PI / poleSegments) * i;
            double x = polyRadius * Math.cos(angle);
            double y = polyRadius * Math.sin(angle);
            bottomIndices[i] = mesh.addVertex(new Point3D(x, y, 0));
            topIndices[i] = mesh.addVertex(new Point3D(x, y, height));
        }
        // Walls of the mast
        for (int i = 0; i < poleSegments; i++) {
            int next = (i + 1) % poleSegments;
            mesh.addFace(new int[]{bottomIndices[i], bottomIndices[next], topIndices[next], topIndices[i]}, 0);
        }
        // Top cap of the mast (under the finial)
        int[] topCap = new int[poleSegments];
        for (int i = 0; i < poleSegments; i++) topCap[i] = topIndices[poleSegments - 1 - i];
        mesh.addFace(topCap, 0);

        // 2. Finial (A small diamond/octahedron at the top)
        Point3D pTop = new Point3D(0, 0, height + finialHeight);
        Point3D pBottom = new Point3D(0, 0, height);
        int vTop = mesh.addVertex(pTop);
        int vBottom = mesh.addVertex(pBottom);
        int[] midRing = new int[4];
        midRing[0] = mesh.addVertex(new Point3D(finialRadius, 0, height + finialHeight / 2.0));
        midRing[1] = mesh.addVertex(new Point3D(0, finialRadius, height + finialHeight / 2.0));
        midRing[2] = mesh.addVertex(new Point3D(-finialRadius, 0, height + finialHeight / 2.0));
        midRing[3] = mesh.addVertex(new Point3D(0, -finialRadius, height + finialHeight / 2.0));

        for (int i = 0; i < 4; i++) {
            int next = (i + 1) % 4;
            mesh.addFace(new int[]{vTop, midRing[i], midRing[next]}, 1);
            mesh.addFace(new int[]{vBottom, midRing[next], midRing[i]}, 1);
        }

        // 3. Flag (Waving strip with thickness)
        double windAngle = 90 * Math.PI / 180.0; // Global wind direction (same for all flags)
        double phaseOffset =  10 * random.nextDouble() ;//Math.abs(primitive.getUniqueId()) % 100) / 10.0; // Random phase start for variety
        double cosW = Math.cos(windAngle);
        double sinW = Math.sin(windAngle);

        double flagTopZ = height - 0.15; // slightly below finial
        double flagBottomZ = flagTopZ - flagHeight;
        double thickness = 0.01; // 1cm thick

        int[] topFront = new int[flagSegments + 1];
        int[] bottomFront = new int[flagSegments + 1];
        int[] topBack = new int[flagSegments + 1];
        int[] bottomBack = new int[flagSegments + 1];

        for (int i = 0; i <= flagSegments; i++) {
            double progress = (double) i / flagSegments;
            double x = progress * flagWidth;
            // The wave amplitude must be zero at the attachment point (x=0)
            double damping = progress; 
            double waveH = damping * 0.1 * flagWidth * Math.sin(phaseOffset + progress * 1.5 * Math.PI); // Horizontal wave
            double waveV = damping * 0.04 * flagHeight * Math.sin(phaseOffset * 1.3 + progress * 2.0 * Math.PI); // Vertical wave
            double drop = x * Math.tan(Math.toRadians(20.0)); // 20-degree downward tilt

            // Surface normal to the flag (perpendicular to wind)
            double nx = -sinW;
            double ny = cosW;

            // Front vertices
            double fx = x * cosW - waveH * sinW + nx * (thickness / 2.0);
            double fy = x * sinW + waveH * cosW + ny * (thickness / 2.0);
            topFront[i] = mesh.addVertex(new Point3D(fx, fy, flagTopZ + waveV - drop));
            bottomFront[i] = mesh.addVertex(new Point3D(fx, fy, flagBottomZ + waveV - drop));

            // Back vertices
            double bx = x * cosW - waveH * sinW - nx * (thickness / 2.0);
            double by = x * sinW + waveH * cosW - ny * (thickness / 2.0);
            topBack[i] = mesh.addVertex(new Point3D(bx, by, flagTopZ + waveV - drop));
            bottomBack[i] = mesh.addVertex(new Point3D(bx, by, flagBottomZ + waveV - drop));
        }

        // Faces for the flag (watertight mesh)
        for (int i = 0; i < flagSegments; i++) {
            // Front face
            mesh.addFace(new int[]{topFront[i], topFront[i + 1], bottomFront[i + 1], bottomFront[i]}, 2);
            // Back face
            mesh.addFace(new int[]{topBack[i], bottomBack[i], bottomBack[i + 1], topBack[i + 1]}, 2);
            // Top edge
            mesh.addFace(new int[]{topFront[i], topBack[i], topBack[i + 1], topFront[i + 1]}, 2);
            // Bottom edge
            mesh.addFace(new int[]{bottomFront[i], bottomFront[i + 1], bottomBack[i + 1], bottomBack[i]}, 2);
        }
        // Left edge (near mast)
        mesh.addFace(new int[]{topFront[0], bottomFront[0], bottomBack[0], topBack[0]}, 2);
        // Right edge (far end)
        mesh.addFace(new int[]{topFront[flagSegments], topBack[flagSegments], bottomBack[flagSegments], bottomFront[flagSegments]}, 2);

        return new RenderableElement(primitive, origin, mesh, 0, 0);
    }

    public static RenderableElement createChimney(OsmPrimitive primitive, LatLon origin, Random random) {
        if (primitive.isDeleted()) return null;

        if (isPrimitiveUnderground(primitive)) return null;

        double diameter = getTagD("diameter", primitive, 2000.0)/1000; //Default unit for diameter tag is MILLIMETER!
        double height = getTagD("height", primitive, DEFAULT_CHIMNEY_HEIGHT);
        double min_height = getTagD("min_height", primitive, 0.0);
        var colour = getTagStr("colour", primitive, ""); // Unified default (BuildingRecipe will handle it)

        // Points only for this procedural generator
        if (primitive instanceof Way || primitive instanceof Relation) return null;

        // Support for shape and tapering rates, same as polygon chimneys
        String buildingShape = getTagStr("shape", primitive, "frustum");
        Double topRate = getTagD("hyperboloid:top_rate", primitive.getInterestingTags(), null);
        Double middleRate = getTagD("hyperboloid:middle_rate", primitive.getInterestingTags(), null);

        int segments = 12;
        ArrayList<Point2D> circle = new ArrayList<Point2D>();

        for (int i = 0; i < segments; i++) {
            double angle = (2 * Math.PI / segments) * i;
            double x = diameter / 2 * Math.cos(angle);
            double y = diameter / 2 * Math.sin(angle);
            circle.add(new Point2D(x, y));
        }

        Contour contour = new Contour(circle, "XY");
        contour.removeRedundantNodes();

        BuildingRecipe buildingRecipe = new BuildingRecipe(primitive.getPrimitiveId(), contour, height, min_height, 0.1, colour, colour, buildingShape, "", "", null, false, topRate, middleRate);
        Mesh mesh = composeMesh(buildingRecipe);

        return new RenderableElement(primitive, origin, mesh, 0,0);
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
    public static RenderableElement createFromModel(Node node, Mesh modelMesh, double direction, double translationZ) {
        if (node.isDeleted() || node.getCoor() == null || modelMesh == null) {
            return null;
        }
        // If the mesh has a model-defined texture, we use it.
        return new RenderableElement(node, node.getBBox().getCenter(), modelMesh,  direction, translationZ);
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