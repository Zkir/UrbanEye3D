package ru.zkir.urbaneye3d.assetconfig;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

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
            AssetRuleParser parser = new AssetRuleParser();
            List<AssetRule> rules = parser.parse(is);
            config = new AssetConfig(rules);
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
