package ru.zkir.urbaneye3d.assetconfig;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;

import java.util.List;

public class AssetConfig {
    private final List<AssetRule> rules;

    public AssetConfig(List<AssetRule> rules) {
        this.rules = rules;
    }

    public AssetRule findBestMatch(OsmPrimitive primitive, int currentLod) {
        AssetRule bestRule = null;
        int bestScore = -1;

        for (AssetRule rule : rules) {
            if (!matchesTargetType(rule.targetType, primitive)) {
                continue;
            }

            if (!rule.lodRange.matches(currentLod)) {
                continue;
            }

            if (!rule.selector.matches(primitive)) {
                continue;
            }

            int score = rule.selector.getSpecificityScore();
            // In case of a tie, the later rule could override, but let's keep it simple for now (first wins or last wins).
            // Let's do >= to allow later rules to override.
            if (score >= bestScore) {
                bestScore = score;
                bestRule = rule;
            }
        }

        return bestRule;
    }

    private boolean matchesTargetType(AssetRule.TargetType targetType, OsmPrimitive primitive) {
        if (targetType == AssetRule.TargetType.ALL) {
            return true;
        }
        if (targetType == AssetRule.TargetType.NODE && primitive instanceof Node) {
            return true;
        }
        
        boolean isArea = primitive instanceof Relation || (primitive instanceof Way && ((Way)primitive).isClosed());
        
        if (targetType == AssetRule.TargetType.AREA && isArea) {
            return true;
        }
        
        if (targetType == AssetRule.TargetType.WAY && primitive instanceof Way) {
            return true;
        }
        
        return false;
    }
}
