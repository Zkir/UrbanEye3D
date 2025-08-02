package ru.zkir.urbaneye3d;

import com.drew.lang.annotations.NotNull;
import org.openstreetmap.josm.data.coor.LatLon;
import ru.zkir.urbaneye3d.osm2world.Osm2WoldProxy;
import ru.zkir.urbaneye3d.utils.ColorUtils;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.roofgenerators.RoofShapes;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class RenderableBuildingElement {

    public final double roofHeight;
    public final double minHeight;  // z0 -- z-coordinate of building bottom
    public final double wallHeight; // z1 -- z coordinate of walls top
    public final double height;     // z2 -- z coordinate of roof top
    public final @NotNull Color color;
    public final @NotNull Color roofColor;
    public final @NotNull Color bottomColor;
    public final RoofShapes roofShape;
    public final double roofDirection;
    public final @NotNull String roofOrientation;
    private final Contour contour;
    public final LatLon origin;
    public HashMap<String, String> tags;
    private Mesh mesh;

    public RenderableBuildingElement(LatLon origin, Contour contour, double height, double minHeight, double roofHeight, String wallColor, String roofColor, String roofShape, String roofDirectionStr, String roofOrientation, HashMap<String, String> tags) {
        if (contour==null){
            throw new RuntimeException("contour must be specified");
        }
        this.tags = tags;

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

        if (!roofShape.equals("flat") && roofHeight == 0) { //its a bug. original string value should be tested.
            roofHeight = 3.0;
        }

        if (roofHeight>height-minHeight){
            roofHeight=height-minHeight;
        }

        if (this.hasComplexContour() || roofHeight == 0){
            //in case outline has inner rings, we cannot construct any other roof, but FLAT
            // also, if roof's height is zero, it's flat!
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

        String renderingEngine = org.openstreetmap.josm.spi.preferences.Config.getPref().get("urbaneye3d.rendering.engine", "Urban Eye");
        if (renderingEngine.equals("Osm2World")) {
            composeMeshViaOsm2World();
        } else {
            composeMesh();
        }
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

        // Always generate flat roof if roofShape is FLAT or if it's a complex contour
        if ( !hasComplexContour()) {
            // Existing logic for other roof shapes (only for simple contours)
            List<Point2D> basePoints = getContour(); // This will return the first outer ring
            this.mesh = roofShape.getMesher().generate(this);
        }

        //last chance! mesh can be null, in case specific roof shapes was not created due to limitations
        // for example, GABLED and HIPPED can be created for quadrangles only.
        if( this.mesh == null){
            // Collect all contours (outer and inner) for flat roof generation
            this.mesh = RoofShapes.FLAT.getMesher().generate(this);
        }
    }

    public void composeMeshViaOsm2World(){
        this.mesh = Osm2WoldProxy.composeMesh(this);
    }



    private double parseDirection(String direction) {
        if (direction == null || direction.isEmpty()) {
            return Double.NaN; // Return NaN if direction is not specified
        }
        try {
            return Double.parseDouble(direction);
        } catch (NumberFormatException e) {
            // Handle cardinal directions (N, S, E, W, etc.)
            switch (direction.toUpperCase()) {
                case "N": return 0;
                case "NNE": return 22.5;
                case "NE": return 45;
                case "ENE": return 67.5;
                case "E": return 90;
                case "ESE": return 112.5;
                case "SE": return 135;
                case "SSE": return 157.5;
                case "S": return 180;
                case "SSW": return 202.5;
                case "SW": return 225;
                case "WSW": return 247.5;
                case "W": return 270;
                case "WNW": return 292.5;
                case "NW": return 315;
                case "NNW": return 337.5;
                default: return Double.NaN;
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
}