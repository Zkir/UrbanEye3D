package ru.zkir.urbaneye3d;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureIO;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.HashSet;

/**
 * The single owner of OpenGL textures in the plugin.
 * <p>
 * It serves two kinds of textures:
 * <ul>
 *   <li>static textures of {@link RenderableElement}s, loaded from plugin resources by path
 *       (see {@link #get(GL2, String)});</li>
 *   <li>dynamic textures of ground plane tiles, created from the {@link BufferedImage} painted
 *       by a {@link GroundTile} (see {@link #getGroundTileTexture(GL2, GroundTile)}).</li>
 * </ul>
 * Both kinds live in one cache and are disposed here only, so neither {@link GroundTile} nor
 * {@link GroundPlane} has to know anything about OpenGL.
 * <p>
 * All methods must be called from the GL thread.
 */
public class TextureManager {
    private static TextureManager instance;
    private final Map<String, Texture> textureCache = new ConcurrentHashMap<>();

    /**
     * Image version of the ground tile a cached GL texture was created from.
     * When a tile paints a new image, its version changes and the texture is recreated.
     */
    private final Map<String, Integer> groundTextureVersion = new ConcurrentHashMap<>();

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



        Texture texture;

        String fullPath = path.startsWith("/") ? path : "/textures/" + path;
        try (InputStream stream = TextureManager.class.getResourceAsStream(fullPath)) {
            if (stream == null) {
                UrbanEye3dPlugin.debugMsg("Could not find texture: " + fullPath);
                return null;
            }
            // Determine suffix (e.g., ".png")
            String suffix = fullPath.substring(fullPath.lastIndexOf('.'));
            texture = TextureIO.newTexture(stream, true, suffix);
        } catch (Exception e) {
            UrbanEye3dPlugin.debugMsg("Error loading texture: " + fullPath + " " + e.getMessage());
            return null;
        }


        if (texture == null) {
            UrbanEye3dPlugin.debugMsg("Error loading texture: " + path);
            return null;
        }

        applyTextureParameters(gl, texture);
        textureCache.put(path, texture);
        return texture;
    }

    /**
     * Returns the GL texture of a ground plane tile, creating or recreating it when the tile
     * has painted a new image. Ground tiles are owned by this manager: once an image is uploaded
     * to video memory, the tile is asked to free the copy that lives in system memory.
     *
     * @param gl   The GL2 context.
     * @param tile The ground tile to draw.
     * @return The Texture object, or null if there is nothing to draw yet.
     */
    public Texture getGroundTileTexture(GL2 gl, GroundTile tile) {
        if (tile == null) {
            return null;
        }
        String key = tile.getTextureKey();
        int version = tile.getImageVersion();

        Texture cached = textureCache.get(key);
        Integer uploadedVersion = groundTextureVersion.get(key);
        if (cached != null && uploadedVersion != null && uploadedVersion.intValue() == version) {
            // The tile image is either already in video memory, or was never changed since upload.
            return cached;
        }

        BufferedImage image = tile.getImage();
        if (image == null) {
            // Nothing to upload: the texture we may have found is stale, keep drawing it
            // until a new image arrives (this is what the old code did as well).
            return cached;
        }

        Texture texture = createGroundTexture(gl, image);
        if (texture == null) {
            UrbanEye3dPlugin.debugMsg("Could not create ground texture for " + key);
            return cached;
        }

        if (cached != null) {
            cached.destroy(gl);
        }
        applyTextureParameters(gl, texture);
        textureCache.put(key, texture);
        groundTextureVersion.put(key, version);
        if (!tile.isPaintedByMapCss()) {
            // System memory is no longer needed for this image, video memory holds it now.
            // Repainting a TMS tile is cheap (its imagery tiles are cached), so we free the raster.
            // A MapCSS tile is rendered from OSM data, which is expensive, so its raster is kept:
            // this preserves the behaviour of the ground plane before this refactoring.
            tile.clearImageData();
        }
        return texture;
    }

    /**
     * Whether a ground tile already has an up-to-date GL texture.
     * GroundPlane and Renderer3D can ask this without touching OpenGL.
     */
    public boolean hasUpToDateGroundTileTexture(GroundTile tile) {
        if (tile == null) {
            return false;
        }
        String key = tile.getTextureKey();
        Integer uploadedVersion = groundTextureVersion.get(key);
        return uploadedVersion != null && uploadedVersion.intValue() == tile.getImageVersion() && textureCache.containsKey(key);
    }

    /**
     * Destroys the GL texture belonging to a ground tile that is being thrown away.
     * Safe to call for a tile that never had a texture.
     */
    public void disposeGroundTileTexture(GL2 gl, GroundTile tile) {
        if (tile == null) {
            return;
        }
        dispose(gl, tile.getTextureKey());
        groundTextureVersion.remove(tile.getTextureKey());
    }

    /**
     * Creates a GL texture from the painted image, resizing it first if the GPU cannot
     * handle non-power-of-two textures.
     */
    private Texture createGroundTexture(GL2 gl, BufferedImage image) {
        BufferedImage toUpload = image;
        if (!Renderer3D.isNpotSupported()) {
            int potWidth = Integer.highestOneBit(image.getWidth());
            int potHeight = Integer.highestOneBit(image.getHeight());
            if (potWidth != image.getWidth() || potHeight != image.getHeight()) {
                toUpload = resize(image, potWidth, potHeight);
            }
        }
        try {
            return AWTTextureIO.newTexture(gl.getGLProfile(), toUpload, false);
        } catch (Exception e) {
            UrbanEye3dPlugin.debugMsg("Error creating ground texture: " + e.getMessage());
            return null;
        }
    }

    private static BufferedImage resize(BufferedImage image, int width, int height) {
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }

    private static void applyTextureParameters(GL2 gl, Texture texture) {
        texture.setTexParameteri(gl, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
        texture.setTexParameteri(gl, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
        texture.setTexParameteri(gl, GL2.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP_TO_EDGE);
        texture.setTexParameteri(gl, GL2.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP_TO_EDGE);
    }

    /**
     * Disposes of a single specified texture.
     *
     * @param gl   The GL2 context.
     * @param name The name of the texture to dispose.
     */
    public void dispose(GL2 gl, String name) {
        groundTextureVersion.remove(name);
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
        groundTextureVersion.clear();
    }
}
