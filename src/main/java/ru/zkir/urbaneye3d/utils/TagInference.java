package ru.zkir.urbaneye3d.utils;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.openstreetmap.josm.data.osm.OsmPrimitive;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class TagInference {
    private final Map<String, Map<String, Map<String, InferenceRule>>> inferenceRules; //predictor_tag, predictor_value, target_tag

    private static class InferenceRule {
        final String value;
        final double prob;
        final int count;

        InferenceRule(String value, double prob, int count) {
            this.value = value;
            this.prob = prob;
            this.count = count;
        }
    }
    TagInference(String fileName){
        inferenceRules = loadRules(fileName);
    }

    private HashMap<String, Map<String, Map<String, InferenceRule>>> loadRules(String fileName) {
        var flagRules = new HashMap<String, Map<String, Map<String, InferenceRule>>>();

        try (InputStream is = getClass().getResourceAsStream(fileName)) {
            if (is == null) {
                throw new RuntimeException("Resource not found: " + fileName);
            }
            try (JsonReader reader = Json.createReader(is)) {
                JsonObject root = reader.readObject();
                var rules = root.getJsonObject("rules");

                for (String predictorTag : rules.keySet()) {
                    JsonObject valuesObj = rules.getJsonObject(predictorTag);
                    Map<String, Map<String, InferenceRule>> valueMap1 = new HashMap<>();

                    for (String predictorValue : valuesObj.keySet()) {
                        JsonObject ruleObj1 = valuesObj.getJsonObject(predictorValue).getJsonObject("targets");
                        Map<String, InferenceRule> valueMap = new HashMap<>();
                        for (String target: ruleObj1.keySet()) {
                            var ruleObj = ruleObj1.getJsonObject(target);
                            valueMap.put(target, new InferenceRule(
                                    ruleObj.getString("value"),
                                    ruleObj.getJsonNumber("prob").doubleValue(),
                                    ruleObj.getInt("count")
                            ));

                        }
                        valueMap1.put(predictorValue.toLowerCase(), valueMap);
                    }
                    flagRules.put(predictorTag, valueMap1);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException ("Error reading flag rules file "+ fileName + " "+ e.getMessage());
        }
        return flagRules;
    }

    public String getInferredValue(Map<String, String> tags, String targetTag) {
        InferenceRule bestRule = null;

        for (Map.Entry<String, String> entry : tags.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue().toLowerCase();

            var valueMap = inferenceRules.get(key);
            if (valueMap != null && valueMap.get(value) !=null) {
                InferenceRule candidate = valueMap.get(value).get(targetTag);
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
}
