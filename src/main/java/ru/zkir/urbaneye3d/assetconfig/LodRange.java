package ru.zkir.urbaneye3d.assetconfig;

public class LodRange {
    public final int minLod;
    public final int maxLod;

    public LodRange(int minLod, int maxLod) {
        this.minLod = minLod;
        this.maxLod = maxLod;
    }

    public boolean matches(int lod) {
        return lod >= minLod && lod <= maxLod;
    }
}
