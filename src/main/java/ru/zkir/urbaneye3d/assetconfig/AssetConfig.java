package ru.zkir.urbaneye3d.assetconfig;

import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.mappaint.Cascade;
import org.openstreetmap.josm.gui.mappaint.MultiCascade;
import org.openstreetmap.josm.gui.mappaint.mapcss.MapCSSStyleSource;

import java.util.HashMap;
import java.util.Map;

public class AssetConfig {
    private final MapCSSStyleSource styleSource;

    public AssetConfig(MapCSSStyleSource styleSource) {
        this.styleSource = styleSource;
    }

    public AssetRule findBestMatch(OsmPrimitive primitive) {
        MultiCascade mc = new MultiCascade();
        // Applying rules with scale 1.0 and pretendWayIsClosed = false
        styleSource.apply(mc, primitive, 1.0, false);
        Cascade cascade = mc.getCascade("default");

        if (cascade == null) {
            return null;
        }

        Map<String, String> properties = new HashMap<>();
        
        // Extract known properties from the cascade
        extractString(cascade, properties, "procedure");
        extractString(cascade, properties, "model");
        extractString(cascade, properties, "billboard");
        extractString(cascade, properties, "rotatable");
        extractString(cascade, properties, "scalable");
        extractString(cascade, properties, "height");
        extractString(cascade, properties, "width");
        extractString(cascade, properties, "snap_to_roads");
        extractString(cascade, properties, "orientation");
        extractString(cascade, properties, "display");



        if (properties.isEmpty()) {
            return null;
        }

        return new AssetRule(properties);
    }

    private void extractString(Cascade cascade, Map<String, String> properties, String key) {
        String val = cascade.get(key, null, String.class);
        if (val != null) {
            properties.put(key, val);
        }
    }
}
