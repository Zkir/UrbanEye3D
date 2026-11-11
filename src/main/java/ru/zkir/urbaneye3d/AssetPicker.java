package ru.zkir.urbaneye3d;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AssetPicker {
    /** List of assets*/
    private final List<AssetDefinition> assets = new ArrayList<>();

    public AssetPicker(String configFilePath) {
        parseConfig(configFilePath);
    }

    /**
     * Inner class to hold information about a single tree texture definition.
     */
    private static class AssetDefinition {
        private final String assetName;
        private final String assetPath;
        private final Map<String, String> tags;

        AssetDefinition(String texturePath, Map<String, String> tags) {
            this.assetPath = texturePath;
            this.tags = tags;
            // Extract filename from path
            this.assetName = texturePath.substring(texturePath.lastIndexOf('/') + 1);
        }
    }
    /**
     * Load config file
     * */
    private void parseConfig(String configPath) {
        try (InputStream is = AssetPicker.class.getResourceAsStream(configPath)) {
            if (is == null) {
                throw new RuntimeException("Cannot find texture config file: " + configPath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                String currentTexturePath = null;
                Map<String, String> currentTags = new HashMap<>();

                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty() && !line.startsWith(" ") && !line.startsWith("\t")) {
                        // New texture definition starts
                        if (currentTexturePath != null) {
                            assets.add(new AssetDefinition(currentTexturePath, new HashMap<>(currentTags)));
                            currentTags.clear();
                        }
                        currentTexturePath = line.trim();
                    } else if (currentTexturePath != null && !line.trim().isEmpty()) {
                        // Tags for the current texture
                        String[] pairs = line.trim().split(",");
                        for (String pair : pairs) {
                            String[] keyValue = pair.trim().split("=");
                            if (keyValue.length == 2) {
                                currentTags.put(keyValue[0].trim(), keyValue[1].trim());
                            }
                        }
                    }
                }
                // Add the last one
                if (currentTexturePath != null) {
                    assets.add(new AssetDefinition(currentTexturePath, currentTags));
                }
            }
        } catch (Exception e) {
            UrbanEye3dPlugin.debugMsg("Error reading texture config file: " + configPath);
        }
    }

    /**
     * Finds a suitable texture name based on the object's tags.
     * For now, it returns a random tree texture.
     *
     * @param objectTags Tags of the OSM object.
     * @return A texture name, or null if no suitable texture is found.
     */
    public String findBestMatch(Map<String, String> objectTags) {
        if (assets.isEmpty()) {
            throw new RuntimeException("Texture definitions are not loaded");
        }

        AssetDefinition bestMatch = null;
        int maxScore = 0;

        for (var def : assets) {
            int currentScore = 0;
            for (Map.Entry<String, String> tagEntry : def.tags.entrySet()) {
                var k = tagEntry.getKey();
                var v = tagEntry.getValue();
                if (v.equals("*")){
                    //wildcard: key=*. However, the key still should be present.
                    if( objectTags.containsKey(k)) {
                        currentScore++;
                    }
                }else if (v.equals(objectTags.get(k))) {
                    currentScore+=2;
                }
            }

            if (currentScore > maxScore) {
                maxScore = currentScore;
                bestMatch = def;
            }
        }

        return bestMatch != null ? bestMatch.assetName : null;
    }

    public String findPathByName(String name) {
        for (AssetDefinition def : assets) {
            if (def.assetName.equals(name)) {
                // Assuming a base path for all textures defined in the config
                return "/textures/" + def.assetPath;
            }
        }
        return null;
    }

    /** Just return all the key=value pairs. Useful for TagInfo report */
    public Set<Map.Entry<String, String>> getAllTags() {
        return assets.stream()
                .flatMap(def -> def.tags.entrySet().stream())
                .collect(Collectors.toSet());
    }

    public List<String> getAllNames(){
        var names = new ArrayList<String>();
        for (var asset:assets){
            names.add(asset.assetName);
        }
        return names;
    }
}
