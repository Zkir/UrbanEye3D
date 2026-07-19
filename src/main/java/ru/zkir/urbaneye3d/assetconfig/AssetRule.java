package ru.zkir.urbaneye3d.assetconfig;

import java.util.Map;

public class AssetRule {
    public enum TargetType { NODE, WAY, AREA, ALL }

    public final TargetType targetType;
    public final LodRange lodRange;
    public final Selector selector;
    public final Map<String, String> properties;

    public AssetRule(TargetType targetType, LodRange lodRange, Selector selector, Map<String, String> properties) {
        this.targetType = targetType;
        this.lodRange = lodRange;
        this.selector = selector;
        this.properties = properties;
    }
}
