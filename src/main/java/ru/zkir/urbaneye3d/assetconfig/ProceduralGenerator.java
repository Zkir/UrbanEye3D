package ru.zkir.urbaneye3d.assetconfig;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import ru.zkir.urbaneye3d.RenderableElement;
import java.util.Random;

@FunctionalInterface
public interface ProceduralGenerator {
    RenderableElement generate(OsmPrimitive primitive, LatLon origin, AssetRule rule, Random random);
}
