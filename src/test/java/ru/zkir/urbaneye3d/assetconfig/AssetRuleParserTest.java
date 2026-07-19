package ru.zkir.urbaneye3d.assetconfig;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

public class AssetRuleParserTest {

    @Test
    public void testParseSimpleRule() {
        String config = "node|l0-2[natural=tree] { model: \"models/tree.obj\"; }";
        AssetRuleParser parser = new AssetRuleParser();
        List<AssetRule> rules = parser.parseString(config);
        
        assertEquals(1, rules.size());
        AssetRule rule = rules.get(0);
        assertEquals(AssetRule.TargetType.NODE, rule.targetType);
        assertEquals(0, rule.lodRange.minLod);
        assertEquals(2, rule.lodRange.maxLod);
        assertEquals("tree", rule.selector.getTags().get("natural"));
        assertNull(rule.properties.get("generator"));
        assertEquals("models/tree.obj", rule.properties.get("model"));
        assertEquals(10, rule.selector.getSpecificityScore());
    }

    @Test
    public void testParseComplexTags() {
        String config = "way|l1-[natural=tree][species=\"Betula pendula\"] { billboard: \"trees/birch.png\"; }";
        AssetRuleParser parser = new AssetRuleParser();
        List<AssetRule> rules = parser.parseString(config);
        
        assertEquals(1, rules.size());
        AssetRule rule = rules.get(0);
        assertEquals(AssetRule.TargetType.WAY, rule.targetType);
        assertEquals(1, rule.lodRange.minLod);
        assertEquals(Integer.MAX_VALUE, rule.lodRange.maxLod);
        assertEquals("tree", rule.selector.getTags().get("natural"));
        assertEquals("Betula pendula", rule.selector.getTags().get("species"));
        assertNull(rule.properties.get("generator"));
        assertEquals("trees/birch.png", rule.properties.get("billboard"));
        assertEquals(520, rule.selector.getSpecificityScore());
    }

    @Test
    public void testParseCommentsAndMultipleRules() {
        String config = "/* Some comment */\n" +
                        "node[amenity=bench] { model: \"models/bench.obj\"; rotatable: true; }\n" +
                        "// Another comment\n" +
                        "area[advertising=column] { procedure: \"ad_column\"; }";
        AssetRuleParser parser = new AssetRuleParser();
        List<AssetRule> rules = parser.parseString(config);
        
        assertEquals(2, rules.size());
        
        AssetRule rule1 = rules.get(0);
        assertEquals(AssetRule.TargetType.NODE, rule1.targetType);
        assertEquals(0, rule1.lodRange.minLod);
        assertEquals(Integer.MAX_VALUE, rule1.lodRange.maxLod);
        assertEquals("bench", rule1.selector.getTags().get("amenity"));
        assertEquals("true", rule1.properties.get("rotatable"));
        
        AssetRule rule2 = rules.get(1);
        assertEquals(AssetRule.TargetType.AREA, rule2.targetType);
        assertEquals("column", rule2.selector.getTags().get("advertising"));
        assertEquals("ad_column", rule2.properties.get("procedure"));
    }
}
