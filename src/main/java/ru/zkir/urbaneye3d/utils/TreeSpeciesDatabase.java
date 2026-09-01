package ru.zkir.urbaneye3d.utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.openstreetmap.josm.data.coor.LatLon;
import ru.zkir.urbaneye3d.UrbanEye3dPlugin;

import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_TREE_HEIGHT;
import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getTagD;

public class TreeSpeciesDatabase {
    private static TreeSpeciesDatabase instance;
    private final Map<String, SpeciesInfo> speciesMap = new HashMap<>();
    private final Map<String, SpeciesInfo> genusMap = new HashMap<>();
    private final Map<String, Map<String, Double>> spatialStats = new HashMap<>();
    private static final double GRID_SIZE = 5.0;

    public static class SpeciesInfo {
        public final String leafCycle;
        public final String leafType;
        public final String wikidata;
        public final String genus;

        SpeciesInfo(String leafCycle, String leafType, String wikidata, String genus) {
            this.leafCycle = leafCycle;
            this.leafType = leafType;
            this.wikidata = wikidata;
            this.genus = genus;
        }
    }

    private TreeSpeciesDatabase() {
        loadSpeciesData("/data/tree_species.csv");
        loadSpatialData("/data/spatial_stats_5x5.json");
        UrbanEye3dPlugin.debugMsg("loaded tree data ... OK");
    }

    public static synchronized TreeSpeciesDatabase getInstance() {
        if (instance == null) {
            instance = new TreeSpeciesDatabase();
        }
        return instance;
    }

    public Map<String, SpeciesInfo> getSpeciesMap() {
        return java.util.Collections.unmodifiableMap(speciesMap);
    }

    public Map<String, SpeciesInfo> getGenusMap() {
        return java.util.Collections.unmodifiableMap(genusMap);
    }

    /**
     * Loads defaults for leaf_type and leaf_cycle for known species
     */
    private void loadSpeciesData(String resourcePath) {
        try (InputStream is = TreeSpeciesDatabase.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                UrbanEye3dPlugin.debugMsg("Tree species database not found: " + resourcePath);
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line = reader.readLine(); // Skip header
                while ((line = reader.readLine()) != null) {
                    // Using a simple CSV split. Since we don't expect commas in names, it's fine.
                    String[] parts = line.split(",", -1);
                    if (parts.length >= 5) {
                        String species = parts[0].trim();
                        String genus = parts[1].trim();
                        String wikidata = parts[2].trim();
                        String leafCycle = parts[3].trim();
                        String leafType = parts[4].trim();

                        SpeciesInfo info = new SpeciesInfo(leafCycle, leafType, wikidata, genus);
                        if (!species.isEmpty()) {
                            speciesMap.put(normalizeSpecies(species), info);
                        }
                        // For genus, we populate it if it has a leafType. 
                        // If multiple species have the same genus, the last one wins, 
                        // which is usually fine for leaf_type/leaf_cycle.
                        if (!genus.isEmpty() && !leafType.isEmpty()) {
                            genusMap.put(genus.toLowerCase(), info);
                        }
                    }
                }
                UrbanEye3dPlugin.debugMsg("Loaded " + speciesMap.size() + " tree species from database.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            UrbanEye3dPlugin.debugMsg("Error loading tree species database: " + e.getMessage());
        }
    }

    /**
     * Loads defaults for leaf_type for each 5x5 degree square
     */
    private void loadSpatialData(String resourcePath) {
        try (InputStream is = TreeSpeciesDatabase.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                UrbanEye3dPlugin.debugMsg("Spatial stats database not found: " + resourcePath);
                return;
            }
            try (JsonReader reader = Json.createReader(is)) {
                JsonObject root = reader.readObject();
                for (String key : root.keySet()) {
                    JsonObject cell = root.getJsonObject(key);
                    if (cell.containsKey("leaf_type_prob")) {
                        JsonObject probs = cell.getJsonObject("leaf_type_prob");
                        Map<String, Double> cellProbs = new HashMap<>();
                        for (String type : probs.keySet()) {
                            cellProbs.put(type, probs.getJsonNumber(type).doubleValue());
                        }
                        spatialStats.put(key, cellProbs);
                    }
                }
                UrbanEye3dPlugin.debugMsg("Loaded spatial stats for " + spatialStats.size() + " grid cells.");
            }
        } catch (Throwable t) {
            t.printStackTrace();
            UrbanEye3dPlugin.debugMsg("Error loading spatial stats database: " + t.getMessage());
        }
    }

    public static String normalizeSpecies(String name) {
        if (name == null || name.isEmpty()) return "";

        String trimmed = name.trim();
        // Check for cultivar format: Genus 'Cultivar'
        if (trimmed.matches("^[A-Z][a-z]+\\s+'[A-Z].*'$")) {
            String genus = trimmed.split(" ")[0].toLowerCase();
            return genus + " sp.";
        }

        // Lowercase and trim
        String n = trimmed.toLowerCase();

        // Normalize hybrid symbol: 'x' -> '×' (only if separate)
        n = n.replaceAll("(^|\\s)x(\\s|$)", "$1×$2");

        // Ensure spaces around '×'
        n = n.replaceAll("\\s*×\\s*", " × ");

        // Clean multiple spaces
        n = n.replaceAll("\\s+", " ").trim();

        String[] parts = n.split(" ");
        if (parts.length < 2) return n;

        // Find × position in the first few tokens
        int xPos = -1;
        for (int i = 0; i < Math.min(parts.length, 3); i++) {
            if (parts[i].equals("×")) {
                xPos = i;
                break;
            }
        }

        if (xPos == 0) { // × Genus species
            return parts.length >= 3 ? String.join(" ", parts[0], parts[1], parts[2]) : n;
        } else if (xPos == 1) { // Genus × species
            return parts.length >= 3 ? String.join(" ", parts[0], parts[1], parts[2]) : n;
        } else if (xPos == 2) { // Genus species1 × species2
            return parts.length >= 4 ? String.join(" ", parts[0], parts[1], parts[2], parts[3]) : n;
        } else {
            // Default binomial: Genus species
            return String.join(" ", parts[0], parts[1]);
        }
    }

    public static String formatSpecies(String name) {
        String normalized = normalizeSpecies(name);
        if (normalized.isEmpty()) return "";
        
        String[] parts = normalized.split(" ");
        if (parts[0].equals("×") && parts.length > 1) {
            // × Genus species -> × Genus species
            return "× " + parts[1].substring(0, 1).toUpperCase() + parts[1].substring(1) + 
                   (parts.length > 2 ? " " + parts[2] : "");
        }
        
        // Capitalize first letter of Genus
        StringBuilder sb = new StringBuilder();
        sb.append(parts[0].substring(0, 1).toUpperCase()).append(parts[0].substring(1));
        
        // Append remaining parts
        for (int i = 1; i < parts.length; i++) {
            sb.append(" ").append(parts[i]);
        }
        return sb.toString();
    }

    /**
     * Generates geographical cell index from the given latitude and logitude
     * @param lat latitude
     * @param lon longitude
     * @param gridSize expected 5
     * @return cell index in the form +30+050.
     */
    public static String getGridIndex(double lat, double lon, double gridSize) {
        int latBin = (int) (Math.floor(lat / gridSize) * gridSize);
        int lonBin = (int) (Math.floor(lon / gridSize) * gridSize);
        return String.format("%+03d%+04d", latBin, lonBin);
    }

    /**
     * Generates leaf_type value from the given probabilities
     * @param probs  values with their probabilities
     * @param random Random object, needed to generate each time the same values for the same object
     * @return selected leaf_type value
     */
    private String pickLeafTypeFromProbs(Map<String, Double> probs, Random random) {
        if (probs == null || probs.isEmpty()) return null;
        double r = random.nextDouble();
        double cumulative = 0.0;
        for (Map.Entry<String, Double> entry : probs.entrySet()) {
            cumulative += entry.getValue();
            if (r <= cumulative) {
                return entry.getKey();
            }
        }
        // Fallback to the last one if rounding issues occur
        return probs.keySet().iterator().next();
    }

    /**
     * Enriches the provided tags with leaf_type and leaf_cycle if they are missing
     * but can be inferred from species or genus tags, or geographic location.
     */
    public Map<String, String> enrichTags(Map<String, String> originalTags, LatLon location, Random random) {
        Map<String, String> tags = new HashMap<>(originalTags);
        String species = tags.get("species");
        String normalizedSpecies = null;
        if (species != null) {
            normalizedSpecies = normalizeSpecies(species);
            // Replace the original species tag with properly formatted binomial name
            tags.put("species", formatSpecies(species));
        }

        SpeciesInfo info = null;
        
        // 1. Try species lookup
        if (normalizedSpecies != null) {
            info = speciesMap.get(normalizedSpecies);
        }

        // 2. Try genus lookup
        if (info == null) {
            String genus = tags.get("genus");
            if (genus == null && normalizedSpecies != null) {
                // If genus tag is missing, but we have species, try to extract genus from species
                String[] parts = normalizedSpecies.split(" ");
                if (parts.length > 0) {
                    genus = parts[0];
                    if (genus.equals("×") && parts.length > 1) {
                        genus = parts[1];
                    }
                }
            }
            if (genus != null) {
                info = genusMap.get(genus.toLowerCase());
            }
        }

        if (info != null) {
            if (!tags.containsKey("leaf_type") && !info.leafType.isEmpty()) {
                tags.put("leaf_type", info.leafType);
            }
            if (!tags.containsKey("leaf_cycle") && !info.leafCycle.isEmpty()) {
                tags.put("leaf_cycle", info.leafCycle);
            }
        }

        // 3. Spatial fallback for leaf_type
        if (!tags.containsKey("leaf_type") && location != null && random != null) {
            String gridIdx = getGridIndex(location.lat(), location.lon(), GRID_SIZE);
            Map<String, Double> probs = spatialStats.get(gridIdx);
            String spatialType = pickLeafTypeFromProbs(probs, random);
            if (spatialType != null) {
                tags.put("leaf_type", spatialType);
            }
        }

        // 4. Default height for a tree. Probably it could be species dependent.
        if (!tags.containsKey("height")){
            double height;
            if (tags.containsKey("circumference")){
                double treeCircumference = getTagD("circumference", tags, 1);
                height = Math.pow((Math.log(treeCircumference)/Math.log(2) * 0.33 + 3), 2);
            } else {
                height = DEFAULT_TREE_HEIGHT;
            }
            double min_height = getTagD("min_height", tags, 0);
            height += min_height;
            tags.put("height", String.valueOf(height));
        }
        return tags;
    }
}
