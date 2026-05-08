package ru.zkir.urbaneye3d.utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import ru.zkir.urbaneye3d.UrbanEye3dPlugin;

public class TreeSpeciesDatabase {
    private static TreeSpeciesDatabase instance;
    private final Map<String, SpeciesInfo> speciesMap = new HashMap<>();
    private final Map<String, SpeciesInfo> genusMap = new HashMap<>();

    private static class SpeciesInfo {
        String leafCycle;
        String leafType;

        SpeciesInfo(String leafCycle, String leafType) {
            this.leafCycle = leafCycle;
            this.leafType = leafType;
        }
    }

    private TreeSpeciesDatabase() {
        loadData("/data/tree_species.csv");
    }

    public static synchronized TreeSpeciesDatabase getInstance() {
        if (instance == null) {
            instance = new TreeSpeciesDatabase();
        }
        return instance;
    }

    private void loadData(String resourcePath) {
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
                        String leafCycle = parts[3].trim();
                        String leafType = parts[4].trim();

                        SpeciesInfo info = new SpeciesInfo(leafCycle, leafType);
                        if (!species.isEmpty()) {
                            speciesMap.put(species.toLowerCase(), info);
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
            UrbanEye3dPlugin.debugMsg("Error loading tree species database: " + e.getMessage());
        }
    }

    /**
     * Enriches the provided tags with leaf_type and leaf_cycle if they are missing
     * but can be inferred from species or genus tags.
     */
    public void enrichTags(Map<String, String> tags) {
        if (tags.containsKey("leaf_type") && tags.containsKey("leaf_cycle")) {
            return;
        }

        SpeciesInfo info = null;
        
        // 1. Try species
        String species = tags.get("species");
        if (species != null) {
            info = speciesMap.get(species.toLowerCase());
        }

        // 2. Try genus
        if (info == null) {
            String genus = tags.get("genus");
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
    }
}
