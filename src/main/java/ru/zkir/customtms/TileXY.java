package ru.zkir.customtms;

/**
 * Represents a tile XY index (not meters)
 */
public class TileXY {
    public final long x;
    public final long y;

    public TileXY(long x, long y) {
        this.x = x;
        this.y = y;
    }
}
