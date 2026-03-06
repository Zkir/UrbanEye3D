package ru.zkir.urbaneye3d;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureIO;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TextureManager {
    private static TextureManager instance;
    private final Map<String, Texture> textureCache = new ConcurrentHashMap<>();
    private final List<TextureDefinition> textureDefinitions = new ArrayList<>();

    /**
     * Inner class to hold information about a single tree texture definition.
     */
    private static class TextureDefinition {
        private final String textureName;
        private final String texturePath;
        private final Map<String, String> tags;

        TextureDefinition(String texturePath, Map<String, String> tags) {
            this.texturePath = texturePath;
            this.tags = tags;
            // Extract filename from path
            this.textureName = texturePath.substring(texturePath.lastIndexOf('/') + 1);
        }
    }


    private TextureManager() {
        parseConfig("/textures/textures.cfg");
    }

    private void parseConfig(String configPath) {
        try (InputStream is = TextureManager.class.getResourceAsStream(configPath)) {
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
                            textureDefinitions.add(new TextureDefinition(currentTexturePath, new HashMap<>(currentTags)));
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
                    textureDefinitions.add(new TextureDefinition(currentTexturePath, currentTags));
                }
            }
        } catch (Exception e) {
            UrbanEye3dPlugin.debugMsg("Error reading texture config file: " + configPath);
            e.printStackTrace();
        }
    }


    public static synchronized TextureManager getInstance() {
        if (instance == null) {
            instance = new TextureManager();
        }
        return instance;
    }

    /**
     * Finds a suitable texture name based on the object's tags.
     * For now, it returns a random tree texture.
     *
     * @param objectTags Tags of the OSM object.
     * @return A texture name, or null if no suitable texture is found.
     */
    public String findTextureName(Map<String, String> objectTags) {
        if (textureDefinitions.isEmpty()) {
            throw new RuntimeException("Texture definitions are not loaded");
        }

        TextureDefinition bestMatch = null;
        int maxScore = 0;

        for (TextureDefinition def : textureDefinitions) {
            int currentScore = 0;
            for (Map.Entry<String, String> tagEntry : def.tags.entrySet()) {
                if (tagEntry.getValue().equals(objectTags.get(tagEntry.getKey()))) {
                    currentScore++;
                }
            }

            if (currentScore > maxScore) {
                maxScore = currentScore;
                bestMatch = def;
            }
        }

        return bestMatch != null ? bestMatch.textureName : null;
    }


    /**
     * Gets a texture by name. If the texture is already loaded, returns it from the cache.
     * Otherwise, loads it from the resources, caches it, and returns it.
     *
     * @param gl   The GL2 context.
     * @param name The symbolic name of the texture (e.g., "tree_000.png").
     * @return The Texture object, or null if loading fails.
     */
    public Texture get(GL2 gl, String name) {
        if (textureCache.containsKey(name)) {
            return textureCache.get(name);
        }

        String path = findPathByName(name);
        if (path == null) {
            UrbanEye3dPlugin.debugMsg("Texture path not found for name: " + name);
            return null;
        }

        try (InputStream stream = TextureManager.class.getResourceAsStream(path)) {
            if (stream == null) {
                UrbanEye3dPlugin.debugMsg("Texture resource not found at path: " + path);
                return null;
            }
            Texture texture = TextureIO.newTexture(stream, true, TextureIO.PNG);
            textureCache.put(name, texture);
            return texture;
        } catch (Exception e) {
            UrbanEye3dPlugin.debugMsg("Error loading texture: " + name + " from " + path);
            e.printStackTrace();
            return null;
        }
    }

    private String findPathByName(String name) {
        for (TextureDefinition def : textureDefinitions) {
            if (def.textureName.equals(name)) {
                // Assuming a base path for all textures defined in the config
                return "/textures/" + def.texturePath;
            }
        }
        return null;
    }


    /**
     * Disposes of a single specified texture.
     *
     * @param gl   The GL2 context.
     * @param name The name of the texture to dispose.
     */
    public void dispose(GL2 gl, String name) {
        if (textureCache.containsKey(name)) {
            textureCache.get(name).destroy(gl);
            textureCache.remove(name);
        }
    }

    /**
     * Disposes of all textures managed by this manager.
     *
     * @param gl The GL2 context.
     */
    public void disposeAll(GL2 gl) {
        for (Texture texture : textureCache.values()) {
            texture.destroy(gl);
        }
        textureCache.clear();
    }
}
