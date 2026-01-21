package ru.zkir.customtms;

/**
 * Represents the boundary of tiles to be rendered.
 * x and x are tile numbers, not meters.
 */
public class TileBounds {
    public final int minX;
    public final int maxX;
    public final int minY;
    public final int maxY;

    public TileBounds(int minX, int maxX, int minY, int maxY) {
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
    }

    /**  Check whether tile (x,y) belongs to this tileset */
    public boolean contains(int x, int y) {
         return !(x < this.minX || x > this.maxX ||  y < this.minY || y> this.maxY);
    }

    /** Returns the number of tiles, required for this tile set*/
    public int getTileNumber() {
        return (this.maxX - this.minX + 1) * (this.maxY - this.minY + 1);
    }
}
