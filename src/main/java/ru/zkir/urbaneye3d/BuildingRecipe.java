package ru.zkir.urbaneye3d;

import com.drew.lang.annotations.NotNull;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.PrimitiveId;
import ru.zkir.urbaneye3d.roofgenerators.RoofShapes;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.OsmDataWasher;
import ru.zkir.urbaneye3d.utils.Point2D;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_STEP_HEIGHT;

/**
 * Data necessary to produce building\part mesh according to S3DB specification */
public class BuildingRecipe {
    public final PrimitiveId primitiveId; //potentially for random seed
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
    public final double stepHeight;
    public final Double hyperboloidTopRate;
    public final Double hyperboloidMiddleRate;

    public BuildingRecipe(PrimitiveId primitiveId, Contour contour,
                              double height, double minHeight, double roofHeight, String wallColor, String roofColor,
                              String roofShape, String roofDirectionStr, String roofOrientation, Double stepHeight,
                              boolean noWalls, Double hyperboloidTopRate, Double hyperboloidMiddleRate ) {

        this.primitiveId = primitiveId;

        if (contour == null) {
            throw new RuntimeException("contour must be specified");
        }

        if (contour.outerRings.isEmpty()) {
            throw new RuntimeException("There can be empty multipolygon relations, broken or not fully downloaded. " +
                    "However, renderable building cannot be created without outer ring. " +
                    "This condition should be checked outside this constructor. (" + this.primitiveId + ")"
            );
        }
        if (!contour.mode.equals("XY")){
            throw new RuntimeException("To create a building mesh, contour should be in local coords");
        }

        this.contour = contour;
        this.height = height;
        this.minHeight = minHeight;

        //default value for roofHeight
        if (roofShape.isEmpty()) {
            roofShape = "flat";
        }

        if (roofHeight > height - minHeight) {
            roofHeight = height - minHeight;
        }


        //in case outline has inner rings, we cannot construct any other roof, but FLAT and SKILLION
        // also, if roof's height is zero, it's flat!
        if ((roofHeight == 0) || (contour.isComplex() && !roofShape.equals(RoofShapes.SKILLION.toString()))) {
            this.roofShape = RoofShapes.FLAT;
        } else {
            this.roofShape = RoofShapes.fromString(roofShape);
        }

        this.roofDirection = OsmDataWasher.parseDirection(roofDirectionStr);
        if (roofOrientation == null) {
            roofOrientation = "";
        }
        this.roofOrientation = roofOrientation;

        this.roofHeight = roofHeight;
        this.wallHeight = height - roofHeight;

        this.color = OsmDataWasher.parseColor(wallColor, new Color(204, 204, 204));
        this.roofColor = OsmDataWasher.parseColor(roofColor, new Color(150, 150, 150));
        this.bottomColor = this.color.darker().darker(); //Fake AO LOL!

        if (stepHeight == null || stepHeight == 0) {
            this.stepHeight = DEFAULT_STEP_HEIGHT;
        } else {
            this.stepHeight = stepHeight;
        }

        this.noWalls = noWalls;
        this.hyperboloidTopRate = hyperboloidTopRate != null ? hyperboloidTopRate : 0.6;
        this.hyperboloidMiddleRate = hyperboloidMiddleRate != null ? hyperboloidMiddleRate : this.hyperboloidTopRate;
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

    public boolean hasComplexContour() {
        return contour.isComplex();
    }
}
