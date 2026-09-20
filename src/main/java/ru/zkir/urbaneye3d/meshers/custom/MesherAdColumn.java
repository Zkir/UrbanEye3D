package ru.zkir.urbaneye3d.meshers.custom;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import ru.zkir.urbaneye3d.assetconfig.ProceduralGenerator;
import ru.zkir.urbaneye3d.utils.ColorUtils;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.MeshOperations;

import java.awt.Color;
import java.util.Map;
import java.util.SplittableRandom;

import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getColorByColourAndMaterial;
import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getTagD;

public class MesherAdColumn  {
    public static Mesh generate(OsmPrimitive primitive, SplittableRandom random) {

        final double MODEL_HEIGHT = 4.8;

        double width = getTagD("width", primitive, 1.92);
        double min_height = getTagD("min_height", primitive, 0.0);
        double height = getTagD("height", primitive, MODEL_HEIGHT+min_height)-min_height;

        // Almost all columns are points
        // https://taginfo.openstreetmap.org/tags/advertising=column
        // TODO: support polygons, why not?

        //Color matColor = getTagC("colour", primitive, "#405040");
        Color matColor = getColorByColourAndMaterial(primitive, "#405040");
        Color matColor2 = ColorUtils.parseColor("#F0F0F0");
        Color matColor3 = ColorUtils.parseColor("#000000");

        var ctx = new MeshOperations();

        ctx.createCylinder(12);
        ctx.scale(2,2, MODEL_HEIGHT);
        //first of all insert vertex rings.
        ctx.insertHorizontalEdgeRing(0.27);
        ctx.insertHorizontalEdgeRing(0.44);
        ctx.insertHorizontalEdgeRing(3.16);
        ctx.insertHorizontalEdgeRing(3.25);
        ctx.insertHorizontalEdgeRing(3.60);
        ctx.insertHorizontalEdgeRing(3.70);
        ctx.insertHorizontalEdgeRing(3.74);
        ctx.insertHorizontalEdgeRing(3.82);
        ctx.insertHorizontalEdgeRing(3.90);
        ctx.insertHorizontalEdgeRing(4.03);
        ctx.insertHorizontalEdgeRing(4.21);
        ctx.insertHorizontalEdgeRing(4.35);
        ctx.insertHorizontalEdgeRing(4.45);
        ctx.insertHorizontalEdgeRing(4.47);
        ctx.insertHorizontalEdgeRing(4.48);
        ctx.insertHorizontalEdgeRing(4.49);
        ctx.insertHorizontalEdgeRing(4.60);

        // Now scale the newly-created rings!
        ctx.selectVerticesByZ( 0);
        ctx.scale( 0.75,0.75, 1);
        ctx.selectVerticesByZ( 0.27);
        ctx.scale(0.75,0.75,1);
        ctx.selectVerticesByZ( 0.44);
        ctx.scale(0.63,0.63,1);
        ctx.selectVerticesByZ( 3.16);
        ctx.scale(0.63,0.63,1);
        ctx.selectVerticesByZ( 3.25);
        ctx.scale(0.76,0.76,1);
        ctx.selectVerticesByZ( 3.60);
        ctx.scale(0.78,0.78,1);
        ctx.selectVerticesByZ( 3.70);
        ctx.scale(0.85,0.85,1);
        ctx.selectVerticesByZ( 3.74);
        ctx.scale(0.95,0.95,1);
        ctx.selectVerticesByZ( 3.82);
        ctx.scale(0.96,0.96,1);
        ctx.selectVerticesByZ( 3.90);
        ctx.scale(0.75,0.75,1);
        ctx.selectVerticesByZ( 4.03);
        ctx.scale(0.73,0.73,1);
        ctx.selectVerticesByZ( 4.21);
        ctx.scale(0.62,0.62,1);
        ctx.selectVerticesByZ( 4.35);
        ctx.scale(0.46,0.46,1);
        ctx.selectVerticesByZ( 4.45);
        ctx.scale(0.19,0.19,1);
        ctx.selectVerticesByZ(4.47);
        ctx.scale(0.04,0.04,1);
        ctx.selectVerticesByZ(4.48);
        ctx.scale(0.04,0.04,1);
        ctx.selectVerticesByZ( 4.49);
        ctx.scale(0.03,0.03,1);
        ctx.selectVerticesByZ(4.60);
        ctx.scale(0.07,0.07,1);

        ctx.selectVerticesByZ(MODEL_HEIGHT);
        ctx.scale(0.01, 0.01, 1);

        ctx.selectFacesByZ(0.44, 3.16);
        ctx.assignMaterial(2);

        //one more scaling, for actual height!
        // real procedural scaling means that the top of the column goes up by height difference.
        var ratio = width/(ctx.getMaxBounds().x - ctx.getMinBounds().x);

        //for width we need to scale the model uniformly.
        ctx.selectNone();
        ctx.scale(ratio, ratio, ratio);

        ctx.selectVerticesByZ(3.1*ratio, MODEL_HEIGHT*ratio);
        ctx.translate(0.0, 0.0, (height - MODEL_HEIGHT*ratio));

        // TODO:  some limits: objects cannot be stretched infinitely

        Mesh mesh = ctx.getMesh();

        mesh.materials.add(matColor3);
        mesh.materials.add(matColor);
        mesh.materials.add(matColor2);

        return mesh;
    }
}
