package ru.zkir.urbaneye3d.utils;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;

import java.util.Map;

public class WindTurbineDatabase {
    private final String RULES_FILE_NAME = "/data/turbine_rules.json";
    private static WindTurbineDatabase instance;
    private final TagInference inferenceRules;

    private WindTurbineDatabase() {
        inferenceRules = new TagInference(RULES_FILE_NAME);
    }

    public static synchronized WindTurbineDatabase getInstance() {
        if (instance == null) {
            instance = new WindTurbineDatabase();
        }
        return instance;
    }

    public String getInferredRotorDiameter(OsmPrimitive node) {
        Map<String, String> tags = node.getInterestingTags();
        //We need to construct virtual tag "manufacturer+model", because it is used in this ruleset.
        String manufacturer = tags.getOrDefault("manufacturer","");
        String model = tags.getOrDefault("model","");
        tags.put("manufacturer+model", manufacturer+ " " + model);

        return inferenceRules.getInferredValue(tags, "rotor:diameter");
    }

    public String getInferredHeight(OsmPrimitive node) {
        Map<String, String> tags = node.getInterestingTags();
        String manufacturer = tags.getOrDefault("manufacturer","");
        String model = tags.getOrDefault("model","");
        tags.put("manufacturer+model", manufacturer+ " " + model);
        return inferenceRules.getInferredValue(tags, "height");
    }
}
