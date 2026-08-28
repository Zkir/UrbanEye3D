package ru.zkir.urbaneye3d.utils;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.openstreetmap.josm.data.osm.OsmPrimitive;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class FlagsDatabase {
    private static final int FLAG_TEXTURE_SIZE = 128;
    String COLOUR_RULES_FILE_NAME = "/data/flag_rules_colour.json";
    String QID_RULES_FILE_NAME = "/data/flag_rules_wd.json";
    private static FlagsDatabase instance;
    private final Map<String, Map<String, FlagRule>> flagRulesColour1;
    private final Map<String, Map<String, FlagRule>> flagRulesQID;

    private static class FlagRule {
        final String value;
        final double prob;
        final int count;

        FlagRule(String value, double prob, int count) {
            this.value = value;
            this.prob = prob;
            this.count = count;
        }
    }

    private FlagsDatabase() {
        flagRulesColour1     = loadRules(COLOUR_RULES_FILE_NAME);
        flagRulesQID = loadRules(QID_RULES_FILE_NAME);
    }

    public static synchronized FlagsDatabase getInstance() {
        if (instance == null) {
            instance = new FlagsDatabase();
        }
        return instance;
    }

    private HashMap<String, Map<String, FlagRule>> loadRules(String fileName) {
        var flagRules = new HashMap<String, Map<String, FlagRule>>();

        try (InputStream is = getClass().getResourceAsStream(fileName)) {
            if (is == null) {
               throw new RuntimeException("Resource not found: " + fileName);
            }
            try (JsonReader reader = Json.createReader(is)) {
                JsonObject root = reader.readObject();

                for (String predictorTag : root.keySet()) {
                    JsonObject valuesObj = root.getJsonObject(predictorTag);
                    Map<String, FlagRule> valueMap = new HashMap<>();

                    for (String value : valuesObj.keySet()) {
                        JsonObject ruleObj = valuesObj.getJsonObject(value);
                        valueMap.put(value.toLowerCase(), new FlagRule(
                                ruleObj.getString("value"),
                                ruleObj.getJsonNumber("prob").doubleValue(),
                                ruleObj.getInt("count")
                        ));
                    }
                    flagRules.put(predictorTag, valueMap);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException ("Error reading flag rules file "+ fileName + " "+ e.getMessage());
        }
        return flagRules;
    }

    public String getInferredColor(OsmPrimitive primitive) {
        return getInferredValue(primitive, flagRulesColour1);
    }
    public String getInferredQID(OsmPrimitive primitive) {
        return getInferredValue(primitive, flagRulesQID);
    }

    private String getInferredValue(OsmPrimitive primitive, Map<String, Map<String, FlagRule>> flagRules) {
        FlagRule bestRule = null;
        Map<String, String> tags = primitive.getInterestingTags();

        for (Map.Entry<String, String> entry : tags.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue().toLowerCase();

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
        return (bestRule != null) ? bestRule.value : "";
    }


    public boolean checkQID(String flagQID){
        String resourcePath = "/textures/flags/" + flagQID + ".png";
        URL svgUrl = FlagsDatabase.class.getResource(resourcePath);
        if (svgUrl == null) {
            return false;
        }
        return true;
    }

    public String getFlagTextureName(String qid) {
        //unlike checkQID, we just need relative path for resources/textures. TextureManager will handle that
        String resourcePath = "flags/" + qid + ".png";

        return resourcePath;
    }
}
