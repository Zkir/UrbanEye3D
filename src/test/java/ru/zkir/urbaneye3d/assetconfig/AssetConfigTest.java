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
            "node|l0-2[natural=tree] { generator: model; }\n" +
            "node|l0-2[natural=tree][leaf_type=broadleaved] { generator: broadleaved_model; }\n" +
            "node|l0-2[natural=tree][species=\"Betula pendula\"] { generator: birch_model; }\n" +
            "node|l3-[natural=tree] { generator: billboard; }\n";
            
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
        
        // Test basic tree at LOD 0
        AssetRule match1 = config.findBestMatch(basicTree, 0);
        assertNotNull(match1);
        assertEquals("model", match1.properties.get("generator"));
        
        // Test broadleaved tree at LOD 0 (should override basic tree)
        AssetRule match2 = config.findBestMatch(broadTree, 0);
        assertNotNull(match2);
        assertEquals("broadleaved_model", match2.properties.get("generator"));
        
        // Test birch tree at LOD 0 (should override broadleaved)
        AssetRule match3 = config.findBestMatch(birchTree, 0);
        assertNotNull(match3);
        assertEquals("birch_model", match3.properties.get("generator"));
        
        // Test birch tree at LOD 5 (should fall back to billboard, as no specific LOD 3 rule exists)
        // Wait, the rule is node|l3-[natural=tree], which is score 10.
        // There are no specific birch rules for l3-. So it will match the basic l3- rule.
        AssetRule match4 = config.findBestMatch(birchTree, 5);
        assertNotNull(match4);
        assertEquals("billboard", match4.properties.get("generator"));
    }
}
