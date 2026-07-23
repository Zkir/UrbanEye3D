package ru.zkir.urbaneye3d.assetconfig;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AssetRuleParser {

    public List<AssetRule> parse(InputStream is) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String content = reader.lines().collect(Collectors.joining("\n"));
            return parseString(content);
        }
    }

    public List<AssetRule> parseString(String content) {
        List<AssetRule> rules = new ArrayList<>();
        // Remove C-style comments /* ... */
        content = content.replaceAll("(?s)/\\*.*?\\*/", "");
        // Remove line comments // ... or # ...
        content = content.replaceAll("//.*|#.*", "");

        Pattern blockPattern = Pattern.compile("(?s)([^{]+)\\{([^}]*)\\}");
        Matcher blockMatcher = blockPattern.matcher(content);

        while (blockMatcher.find()) {
            String header = blockMatcher.group(1).trim();
            String body = blockMatcher.group(2).trim();
            if (header.isEmpty()) continue;

            // Extract tags
            Selector selector = new Selector();
            Pattern tagPattern = Pattern.compile("\\[\\s*([^\\]=]+)\\s*(?:=\\s*(?:\"([^\"]+)\"|'([^']+)'|([^\\]]+))\\s*)?\\]");
            Matcher tagMatcher = tagPattern.matcher(header);
            while (tagMatcher.find()) {
                String key = tagMatcher.group(1).trim();
                String value = null;
                if (tagMatcher.group(2) != null) value = tagMatcher.group(2);
                else if (tagMatcher.group(3) != null) value = tagMatcher.group(3);
                else if (tagMatcher.group(4) != null) value = tagMatcher.group(4).trim();
                selector.addTag(key, value);
            }

            // Extract target and DistanceRange
            AssetRule.TargetType targetType = AssetRule.TargetType.ALL;
            DistanceRange distanceRange = new DistanceRange(0, Double.MAX_VALUE);
            String targetAndLod = header.replaceAll("\\[.*?\\]", "").trim();
            if (!targetAndLod.isEmpty()) {
                String[] parts = targetAndLod.split("\\|");
                String targetStr = parts[0].trim().toLowerCase();
                if (targetStr.equals("node")) targetType = AssetRule.TargetType.NODE;
                else if (targetStr.equals("way")) targetType = AssetRule.TargetType.WAY;
                else if (targetStr.equals("area")) targetType = AssetRule.TargetType.AREA;

                if (parts.length > 1) {
                    String dStr = parts[1].trim();
                    if (dStr.startsWith("d")) {
                        dStr = dStr.substring(1);
                        if (dStr.contains("-")) {
                            String[] dParts = dStr.split("-", -1);
                            double minD = dParts[0].isEmpty() ? 0 : Double.parseDouble(dParts[0]);
                            double maxD = dParts.length > 1 && !dParts[1].isEmpty() ? Double.parseDouble(dParts[1]) : Double.MAX_VALUE;
                            distanceRange = new DistanceRange(minD, maxD);
                        } else {
                            double dist = Double.parseDouble(dStr);
                            distanceRange = new DistanceRange(dist, dist);
                        }
                    }
                }
            }

            // Parse body
            Map<String, String> properties = new HashMap<>();
            String[] lines = body.split(";");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] kv = line.split(":", 2);
                if (kv.length == 2) {
                    String k = kv[0].trim();
                    String v = kv[1].trim();
                    if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                        v = v.substring(1, v.length() - 1);
                    }
                    properties.put(k, v);
                }
            }

            rules.add(new AssetRule(targetType, distanceRange, selector, properties));
        }
        return rules;
    }
}
