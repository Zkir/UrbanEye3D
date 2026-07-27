package ru.zkir.urbaneye3d.assetconfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.data.preferences.JosmBaseDirectories;
import org.openstreetmap.josm.data.preferences.JosmUrls;
import org.openstreetmap.josm.spi.preferences.MemoryPreferences;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

public class AssetConfigTest {

    @BeforeAll
    public static void setUp() {
        Config.setPreferencesInstance(new MemoryPreferences());
        Config.setBaseDirectoriesProvider(JosmBaseDirectories.getInstance());
        Config.setUrlsProvider(JosmUrls.getInstance());
    }

    @Test
    public void testFindBestMatch() {
        String configText = 
            "node[natural=tree] { model: \"models/tree.obj\"; }\n" +
            "node[natural=tree][leaf_type=broadleaved] { model: \"models/broadleaved_tree.obj\"; }\n" +
            "node[natural=tree][species=\"Betula pendula\"] { model: \"models/birch.obj\"; }\n";
            
        AssetRuleParser parser = new AssetRuleParser();
        List<AssetRule> rules = parser.parseString(configText);
        AssetConfig config = new AssetConfig(rules);
        
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
        assertEquals("models/tree.obj", match1.properties.get("model"));
        
        // Test broadleaved tree (should override basic tree)
        AssetRule match2 = config.findBestMatch(broadTree);
        assertNotNull(match2);
        assertEquals("models/broadleaved_tree.obj", match2.properties.get("model"));
        
        // Test birch tree (should override broadleaved)
        AssetRule match3 = config.findBestMatch(birchTree);
        assertNotNull(match3);
        assertEquals("models/birch.obj", match3.properties.get("model"));
    }
}
