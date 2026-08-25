package ru.zkir.urbaneye3d.utils;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;
import ru.zkir.urbaneye3d.UrbanEye3dPlugin;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lazy SVG → GL Texture generator for country flags.
 * Caches generated textures in memory by country code.
 *
 * SVG files are loaded from /textures/flags/&lt;country&gt;.svg
 * and rasterised at FLAG_SIZE pixels on first request.
 */
public class FlagTextureGenerator {

    private static final int FLAG_SIZE = 128;

    private static FlagTextureGenerator instance;
    private final Map<String, Texture> textureCache = new ConcurrentHashMap<>();

    private FlagTextureGenerator() {
    }

    public static synchronized FlagTextureGenerator getInstance() {
        if (instance == null) {
            instance = new FlagTextureGenerator();
        }
        return instance;
    }

    /**
     * Returns a Texture for the flag of the given country code.
     * If the texture is not yet in the cache, it is generated on first call.
     * Returns null if the SVG resource cannot be found or parsed.
     *
     * @param gl          GL2 context (must be current).
     * @param countryCode two-letter ISO 3166-1 alpha-2 country code (lowercase).
     */
    public Texture getFlagTexture(GL2 gl, String countryCode) {
        if (gl == null || countryCode == null || countryCode.length() != 2) {
            return null;
        }
        final String key = countryCode.toLowerCase();
        Texture cached = textureCache.get(key);
        if (cached != null) {
            return cached;
        }
        synchronized (textureCache) {
            cached = textureCache.get(key);
            if (cached != null) {
                return cached;
            }
            BufferedImage img = rasterizeSvg(key);
            if (img == null) {
                return null;
            }
            Texture tex = AWTTextureIO.newTexture(gl.getGLProfile(), img, false);
            tex.setTexParameteri(gl, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
            tex.setTexParameteri(gl, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
            tex.setTexParameteri(gl, GL2.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP_TO_EDGE);
            tex.setTexParameteri(gl, GL2.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP_TO_EDGE);
            textureCache.put(key, tex);
            return tex;
        }
    }

    private BufferedImage rasterizeSvg(String countryCode) {
        String resourcePath = "/textures/flags/" + countryCode + ".svg";
        URL svgUrl = FlagTextureGenerator.class.getResource(resourcePath);
        if (svgUrl == null) {
            return null;
        }
        ByteArrayOutputStreamImageTranscoder transcoder = new ByteArrayOutputStreamImageTranscoder();
        transcoder.addTranscodingHint(ImageTranscoder.KEY_WIDTH, (float) FLAG_SIZE);
        transcoder.addTranscodingHint(ImageTranscoder.KEY_HEIGHT, (float) FLAG_SIZE);
        try (InputStream is = svgUrl.openStream()) {
            TranscoderInput input = new TranscoderInput(is);
            transcoder.transcode(input, null);
        } catch (IOException | TranscoderException e) {
            UrbanEye3dPlugin.debugMsg("Failed to rasterise flag " + countryCode + ": " + e.getMessage());
            return null;
        }
        BufferedImage img = transcoder.getImage();
        if (img == null) {
            return null;
        }
        return img;
    }

    /**
     * Batik ImageTranscoder that captures the rendered image into a BufferedImage.
     */
    private static class ByteArrayOutputStreamImageTranscoder extends ImageTranscoder {
        private BufferedImage image;

        @Override
        public BufferedImage createImage(int width, int height) {
            // ARGB preserves full colour information; renderer uses GL_ALPHA_TEST for transparency.
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            return image;
        }

        @Override
        public void writeImage(BufferedImage img, TranscoderOutput out) {
            this.image = img;
        }

        public BufferedImage getImage() {
            return image;
        }
    }

    public void disposeAll(GL2 gl) {
        for (Texture tex : textureCache.values()) {
            tex.destroy(gl);
        }
        textureCache.clear();
    }
}
