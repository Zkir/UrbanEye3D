package ru.zkir.urbaneye3d;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureIO;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TextureManager {
    private static TextureManager instance;
    private final Map<String, Texture> textureCache = new ConcurrentHashMap<>();
    private final AssetPicker assetPicker = new AssetPicker("/textures/textures.cfg");

    public static synchronized TextureManager getInstance() {
        if (instance == null) {
            instance = new TextureManager();
        }
        return instance;
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

        String path = assetPicker.findPathByName(name);
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

    /**
     * Gets a set of all unique key-value tags used across all texture definitions.
     * @return A set of map entries representing all unique tags.
     */
    public Set<Map.Entry<String, String>> getAllTags() {
        return assetPicker.getAllTags();
    }

    public String findTextureName(Map<String, String> tags) {
        return assetPicker.findBestMatch(tags);
    }
}
