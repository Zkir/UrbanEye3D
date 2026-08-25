package ru.zkir.urbaneye3d;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureIO;

import java.io.InputStream;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.HashSet;

public class TextureManager {
    private static TextureManager instance;
    private final Map<String, Texture> textureCache = new ConcurrentHashMap<>();

    private TextureManager() {
    }

    public static synchronized TextureManager getInstance() {
        if (instance == null) {
            instance = new TextureManager();
        }
        return instance;
    }

    /**
     * Used by tests to get all tags. Now returns an empty list since the config is migrated.
     */
    public Set<Map.Entry<String, String>> getAllTags() {
        return new HashSet<>();
    }

    /**
     * Gets a texture by resource path. If the texture is already loaded, returns it from the cache.
     * Otherwise, loads it from the resources, caches it, and returns it.
     *
     * @param gl   The GL2 context.
     * @param path The full resource path of the texture (e.g., "/textures/trees/tree_000.png" or "trees/tree_000.png").
     * @return The Texture object, or null if loading fails.
     */
    public Texture get(GL2 gl, String path) {
        if (textureCache.containsKey(path)) {
            return textureCache.get(path);
        }

        // Delegate country-flag textures (e.g. "flag:ru") to the lazy SVG generator.
        if (path.startsWith("flag:")) {
            String cc = path.substring("flag:".length());
            Texture tex = ru.zkir.urbaneye3d.utils.FlagTextureGenerator.getInstance()
                    .getFlagTexture(gl, cc);
            if (tex != null) {
                textureCache.put(path, tex);
            }
            return tex;
        }

        String fullPath = path.startsWith("/") ? path : "/textures/" + path;

        try (InputStream stream = TextureManager.class.getResourceAsStream(fullPath)) {
            if (stream == null) {
                UrbanEye3dPlugin.debugMsg("Could not find texture: " + fullPath);
                return null;
            }
            // Determine suffix (e.g., ".png")
            String suffix = fullPath.substring(fullPath.lastIndexOf('.'));
            Texture texture = TextureIO.newTexture(stream, true, suffix);

            texture.setTexParameteri(gl, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
            texture.setTexParameteri(gl, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
            texture.setTexParameteri(gl, GL2.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP_TO_EDGE);
            texture.setTexParameteri(gl, GL2.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP_TO_EDGE);

            textureCache.put(path, texture);
            return texture;
        } catch (Exception e) {
            UrbanEye3dPlugin.debugMsg("Error loading texture: " + fullPath);
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
}

