package ru.zkir.urbaneye3d.meshers.custom;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.MeshOperations;

import java.awt.Color;
import java.util.SplittableRandom;

import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getColorByColourAndMaterial;
import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getTagD;

public class MesherStreetCabinet {
    public static Mesh generate(OsmPrimitive primitive, SplittableRandom random) {
        // ignore polygon street_cabinets - processed by createManMade()
        if (primitive instanceof Way || primitive instanceof Relation){
            throw new RuntimeException("Only node objects are supported");
        }

        final double DELTA = 0.001;

        double length =     getTagD("length", primitive, 1.0);
        double width =      getTagD("width", primitive, 0.5);
        double min_height = getTagD("min_height",primitive,0);
        double height =     getTagD("height", primitive, 1.8 + min_height);

        // Defaults based on OSM typical values
        if (length <= 0) length = 1.0;
        if (width <= 0) width = Math.min(length, 0.5);
        if (height <= 0) height = 1.8;

        var ctx = new MeshOperations();

        ctx.createCube();
        ctx.insertHorizontalEdgeRing(0.1);  // первое кольцо
        ctx.insertHorizontalEdgeRing(0.2);  // первое кольцо
        ctx.insertHorizontalEdgeRing( 0.95);  // третье кольцо
        ctx.insertHorizontalEdgeRing( 0.96); // четвертое кольцо

        ctx.selectVerticesByZ(0-DELTA, 0.1+DELTA);
        ctx.scale( 1-0.15/width, 1-0.15/length, 1);

        ctx.selectVerticesByZ(0.96-DELTA, 1+DELTA);
        ctx.scale(1 + 0.05/width, 1+0.05/length, 1);

        ctx.selectNone();
        ctx.scale(width, length, height - min_height); //scale the whole object

        Mesh mesh = ctx.getMesh();

        Color matColor = getColorByColourAndMaterial(primitive, "#8B7355");
        mesh.materials.add(matColor);
        mesh.materials.add(matColor);
        mesh.materials.add(matColor);

        return mesh;
    }
}
