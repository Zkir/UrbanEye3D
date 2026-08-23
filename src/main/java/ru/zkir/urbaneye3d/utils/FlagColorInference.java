package ru.zkir.urbaneye3d.utils;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import ru.zkir.urbaneye3d.UrbanEye3dPlugin;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class FlagColorInference {
    private static FlagColorInference instance;
    private final Map<String, Map<String, FlagRule>> flagRules = new HashMap<>();

    private static class FlagRule {
        final String colour;
        final double prob;
        final int count;

        FlagRule(String colour, double prob, int count) {
            this.colour = colour;
            this.prob = prob;
            this.count = count;
        }
    }

    private FlagColorInference() {
        loadRules();
    }

    public static synchronized FlagColorInference getInstance() {
        if (instance == null) {
            instance = new FlagColorInference();
        }
        return instance;
    }

    private void loadRules() {
        String RULES_FILE_NAME= "/data/flag_rules_colour.json";
        try (InputStream is = getClass().getResourceAsStream(RULES_FILE_NAME)) {
            if (is == null) {
               throw new RuntimeException("Resource not found: " + RULES_FILE_NAME);
            }
            try (JsonReader reader = Json.createReader(is)) {
                JsonObject root = reader.readObject();

                for (String predictorTag : root.keySet()) {
                    JsonObject valuesObj = root.getJsonObject(predictorTag);
                    Map<String, FlagRule> valueMap = new HashMap<>();

                    for (String value : valuesObj.keySet()) {
                        JsonObject ruleObj = valuesObj.getJsonObject(value);
                        valueMap.put(value, new FlagRule(
                                ruleObj.getString("flag:colour"),
                                ruleObj.getJsonNumber("prob").doubleValue(),
                                ruleObj.getInt("count")
                        ));
                    }
                    flagRules.put(predictorTag, valueMap);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException ("Error reading flag rules file "+RULES_FILE_NAME + " "+ e.getMessage());
        }
    }

    public String getInferredColor(OsmPrimitive primitive) {
        FlagRule bestRule = null;
        Map<String, String> tags = primitive.getInterestingTags();

        for (Map.Entry<String, String> entry : tags.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            Map<String, FlagRule> valueMap = flagRules.get(key);
            if (valueMap != null) {
                FlagRule candidate = valueMap.get(value);
                if (candidate != null) {
                    // Maximum Likelihood Logic:
                    // 1. Higher probability wins
                    // 2. If probabilities are equal, higher count wins
                    if (bestRule == null ||
                        candidate.prob > bestRule.prob ||
                        (candidate.prob == bestRule.prob && candidate.count > bestRule.count)) {
                        bestRule = candidate;
                    }
                }
            }
        }

        return (bestRule != null) ? bestRule.colour : null;
    }
}
