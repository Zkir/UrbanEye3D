package ru.zkir.urbaneye3d.assetconfig;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import ru.zkir.urbaneye3d.utils.Mesh;

import java.util.SplittableRandom;

@FunctionalInterface
public interface ProceduralGenerator {
    Mesh generate(OsmPrimitive primitive, SplittableRandom random);
}
