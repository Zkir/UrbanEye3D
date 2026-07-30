package ru.zkir.urbaneye3d.assetconfig;

import org.openstreetmap.josm.gui.mappaint.mapcss.MapCSSStyleSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class AssetConfigLoader {
    private static final AssetConfigLoader instance = new AssetConfigLoader();
    private AssetConfig config;

    private AssetConfigLoader() {
        // Load default config
        try {
            InputStream is = getClass().getResourceAsStream("/assets.mapcss");
            if (is == null) {
                throw new IllegalStateException("Critical resource /assets.mapcss not found.");
            }
            byte[] bytes = is.readAllBytes();
            String cssString = new String(bytes, StandardCharsets.UTF_8);
            
            MapCSSStyleSource source = new MapCSSStyleSource(cssString);
            // Compile the rules
            source.loadStyleSource(false);
            
            config = new AssetConfig(source);
        } catch (Exception e) {
            throw new RuntimeException("Error loading asset configuration", e);
        }
    }

    public static AssetConfigLoader getInstance() {
        return instance;
    }

    public AssetConfig getConfig() {
        return config;
    }
}
