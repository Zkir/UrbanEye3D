package ru.zkir.urbaneye3d.meshers.custom;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import ru.zkir.urbaneye3d.BuildingRecipe;
import ru.zkir.urbaneye3d.Materials;
import ru.zkir.urbaneye3d.RenderableElement;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;

import java.util.ArrayList;
import java.util.SplittableRandom;

import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_CHIMNEY_HEIGHT;
import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getTagD;
import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getTagStr;

public class MesherChimney {
    public static Mesh generate(OsmPrimitive primitive, SplittableRandom random) {

        if (primitive instanceof Way || primitive instanceof Relation) {
            return null;
        }

        double diameter = getTagD("diameter", primitive, 2000.0)/1000; //Default unit for diameter tag is MILLIMETER!
        double min_height = getTagD("min_height", primitive, 0.0);
        double height = getTagD("height", primitive, DEFAULT_CHIMNEY_HEIGHT + min_height) - min_height;

        var material = Materials.fromString(getTagStr("material", primitive, ""));
        var default_colour = ""; // Unified default (BuildingRecipe will handle it)
        if (material!=null){
            default_colour=material.getColor();
        }
        var colour = getTagStr("colour", primitive, default_colour);

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

        BuildingRecipe buildingRecipe = new BuildingRecipe(primitive.getPrimitiveId(), contour, height, 0, 0.1, colour, colour, buildingShape, "", "", null, false, topRate, middleRate);
        Mesh mesh = RenderableElement.composeMesh(buildingRecipe);

        return mesh;
    }
}
