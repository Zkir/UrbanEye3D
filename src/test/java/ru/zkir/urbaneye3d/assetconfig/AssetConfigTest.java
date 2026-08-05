package ru.zkir.urbaneye3d.assetconfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.gui.mappaint.mapcss.MapCSSStyleSource;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.data.preferences.JosmBaseDirectories;
import org.openstreetmap.josm.data.preferences.JosmUrls;
import org.openstreetmap.josm.spi.preferences.MemoryPreferences;
import static org.junit.jupiter.api.Assertions.*;

public class AssetConfigTest {

    @BeforeAll
    public static void setUp() {
        Config.setPreferencesInstance(new MemoryPreferences());
        Config.setBaseDirectoriesProvider(JosmBaseDirectories.getInstance());
        Config.setUrlsProvider(JosmUrls.getInstance());
    }

    @Test
    public void testFindBestMatchWithCascading() {
        String configText = 
            "node[natural=tree] { billboard: \"textures/tree.png\"; height: 10; }\n" +
            "node[natural=tree][leaf_type=broadleaved] { billboard: \"textures/broadleaved_tree.png\"; }\n" +
            "node[natural=tree][species=\"Betula pendula\"] { billboard: \"textures/birch.png\"; }\n";
            
        MapCSSStyleSource source = new MapCSSStyleSource(configText);
        source.loadStyleSource(false);
        AssetConfig config = new AssetConfig(source);
        
        Node basicTree = new Node();
        basicTree.put("natural", "tree");
        
        Node broadTree = new Node();
        broadTree.put("natural", "tree");
        broadTree.put("leaf_type", "broadleaved");
        
        Node birchTree = new Node();
        birchTree.put("natural", "tree");
        birchTree.put("leaf_type", "broadleaved");
        birchTree.put("species", "Betula pendula");
        
        // Test basic tree
        AssetRule match1 = config.findBestMatch(basicTree);
        assertNotNull(match1);
        assertEquals("textures/tree.png", match1.properties.get("billboard"));
        assertEquals("10.0", match1.properties.get("height"));
        
        // Test broadleaved tree (should override billboard, but INHERIT height)
        AssetRule match2 = config.findBestMatch(broadTree);
        assertNotNull(match2);
        assertEquals("textures/broadleaved_tree.png", match2.properties.get("billboard"));
        assertEquals("10.0", match2.properties.get("height")); // Inherited via cascade
        
        // Test birch tree (should override billboard, inherit height)
        AssetRule match3 = config.findBestMatch(birchTree);
        assertNotNull(match3);
        assertEquals("textures/birch.png", match3.properties.get("billboard"));
        assertEquals("10.0", match3.properties.get("height")); // Inherited via cascade
    }

    @Test
    public void testNonStandardProperties() {
        String configText = "node[amenity=bench] { model: \"bench.obj\"; orientation: align_with_parent; my_custom_prop: \"ignored\"; }";
        MapCSSStyleSource source = new MapCSSStyleSource(configText);
        source.loadStyleSource(false);
        AssetConfig config = new AssetConfig(source);

        Node bench = new Node();
        bench.put("amenity", "bench");

        AssetRule match = config.findBestMatch(bench);
        assertNotNull(match);
        assertEquals("bench.obj", match.properties.get("model"));
        assertEquals("align_with_parent", match.properties.get("orientation"));
        // 'my_custom_prop' is not in our extracted list in AssetConfig.java, so it should be absent in AssetRule.properties
        assertNull(match.properties.get("my_custom_prop"));
    }
}
