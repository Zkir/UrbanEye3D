package ru.zkir.urbaneye3d.assetconfig;

import org.openstreetmap.josm.data.osm.OsmPrimitive;

import java.util.HashMap;
import java.util.Map;

public class Selector {
    private final Map<String, String> tags = new HashMap<>();
    private int specificityScore = 0;

    public void addTag(String key, String value) {
        tags.put(key, value);
        specificityScore += calculateTagWeight(key);
    }

    public boolean matches(OsmPrimitive primitive) {
        if (tags.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (!primitive.hasKey(key)) {
                return false;
            }
            if (value != null && !value.equals(primitive.get(key))) {
                return false;
            }
        }
        return true;
    }

    public int getSpecificityScore() {
        return specificityScore;
    }

    private int calculateTagWeight(String key) {
        int baseScore = 10;
        switch (key) {
            case "species": return baseScore + 500;
            case "genus": return baseScore + 100;
            case "leaf_type": return baseScore + 50;
            case "leaf_cycle": return baseScore + 20;
            default: return baseScore;
        }
    }

    public Map<String, String> getTags() {
        return tags;
    }
}
