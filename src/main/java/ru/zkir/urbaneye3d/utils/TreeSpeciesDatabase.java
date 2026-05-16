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
        loadData("/data/tree_species.csv");
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
            UrbanEye3dPlugin.debugMsg("Error loading tree species database: " + e.getMessage());
        }
    }

    public static String normalizeSpecies(String name) {
        if (name == null || name.isEmpty()) return "";

        // Lowercase and trim
        String n = name.trim().toLowerCase();

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
     * Enriches the provided tags with leaf_type and leaf_cycle if they are missing
     * but can be inferred from species or genus tags.
     */
    public void enrichTags(Map<String, String> tags) {
        String species = tags.get("species");
        String normalizedSpecies = null;
        if (species != null) {
            normalizedSpecies = normalizeSpecies(species);
            // Replace the original species tag with properly formatted binomial name
            tags.put("species", formatSpecies(species));
        }

        if (tags.containsKey("leaf_type") && tags.containsKey("leaf_cycle")) {
            return;
        }

        SpeciesInfo info = null;
        
        // 1. Try species
        if (normalizedSpecies != null) {
            info = speciesMap.get(normalizedSpecies);
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
