package ru.zkir.urbaneye3d.utils;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import ru.zkir.urbaneye3d.UrbanEye3dPlugin;

import java.awt.image.BufferedImage;
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

        BufferedImage img = rasterizeSvg(key);
        if (img == null) {
            return null;
        }
        Texture tex = AWTTextureIO.newTexture(gl.getGLProfile(), img, false);
        tex.setTexParameteri(gl, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
        tex.setTexParameteri(gl, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
        tex.setTexParameteri(gl, GL2.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP_TO_EDGE);
        tex.setTexParameteri(gl, GL2.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP_TO_EDGE);

        return tex;
    }

    private BufferedImage rasterizeSvg(String countryCode) {
        String resourcePath = "/textures/flags/" + countryCode + ".svg";
        URL svgUrl = FlagTextureGenerator.class.getResource(resourcePath);
        if (svgUrl == null) {
            throw new RuntimeException("Unable to load flag texture: " + resourcePath);
        }
        // Read the SVG to learn its viewBox so we can render it at the right
        // aspect ratio. Forcing a fixed WIDTH/HEIGHT would letterbox the flag
        // and create transparent bands on the model.
        float[] dims = readSvgDimensions(svgUrl);
        int imgWidth, imgHeight;
        if (dims == null) {
            // Fallback: just use a square if we can't read the SVG header.
            imgWidth = imgHeight = FLAG_SIZE;
        } else {
            float aspect = dims[0] / dims[1];
            if (aspect >= 1.0f) {
                imgWidth = FLAG_SIZE;
                imgHeight = Math.max(1, Math.round(FLAG_SIZE / aspect));
            } else {
                imgHeight = FLAG_SIZE;
                imgWidth = Math.max(1, Math.round(FLAG_SIZE * aspect));
            }
        }

        ByteArrayOutputStreamImageTranscoder transcoder = new ByteArrayOutputStreamImageTranscoder();
        transcoder.addTranscodingHint(ImageTranscoder.KEY_WIDTH, (float) imgWidth);
        transcoder.addTranscodingHint(ImageTranscoder.KEY_HEIGHT, (float) imgHeight);
        try (InputStream is = svgUrl.openStream()) {
            TranscoderInput input = new TranscoderInput(is);
            transcoder.transcode(input, null);
        } catch (IOException | TranscoderException e) {
            UrbanEye3dPlugin.debugMsg("Failed to rasterise flag " + countryCode + ": " + e.getMessage());
            return null;
        }
        BufferedImage img = transcoder.getImage();
        if (img == null) {
            UrbanEye3dPlugin.debugMsg("Failed to rasterise flag " + countryCode  );
            return null;
        }
        return img;
    }

    public boolean checkCountryCode(String countryCode) {
        String resourcePath = "/textures/flags/" + countryCode + ".svg";
        URL svgUrl = FlagTextureGenerator.class.getResource(resourcePath);
        if (svgUrl == null) {
            return false;
        }
        return true;
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


    /**
     * Parse the SVG header to extract its intrinsic dimensions.
     * Returns {width, height} in user units, or null if it cannot be determined.
     * Prefers the explicit width/height attributes; falls back to viewBox.
     */
    private float[] readSvgDimensions(URL svgUrl) {
        try (InputStream is = svgUrl.openStream()) {
            String parser = XMLResourceDescriptor.getXMLParserClassName();
            SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
            Document doc = factory.createDocument(svgUrl.toString(), is);
            Element root = doc.getDocumentElement();
            float w = parseLength(root.getAttribute("width"));
            float h = parseLength(root.getAttribute("height"));
            if (w > 0 && h > 0) {
                return new float[]{w, h};
            }
            String vb = root.getAttribute("viewBox");
            if (!vb.isEmpty()) {
                String[] parts = vb.trim().split("[\\s,]+");
                if (parts.length == 4) {
                    float vbw = Float.parseFloat(parts[2]);
                    float vbh = Float.parseFloat(parts[3]);
                    if (vbw > 0 && vbh > 0) {
                        return new float[]{vbw, vbh};
                    }
                }
            }
        } catch (Exception e) {
            // ignore, fall through to null
        }
        return null;
    }

    /**
     * Parse an SVG length like "1000", "1000px", "12.5cm". Returns -1 if not parseable.
     */
    private static float parseLength(String s) {
        if (s == null || s.isEmpty()) return -1;
        int i = 0;
        while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.' || s.charAt(i) == '-')) {
            i++;
        }
        if (i == 0) return -1;
        try {
            return Float.parseFloat(s.substring(0, i));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
