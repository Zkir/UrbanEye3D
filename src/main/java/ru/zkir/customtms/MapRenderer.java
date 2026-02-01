package ru.zkir.customtms;

import org.openstreetmap.josm.data.imagery.ImageryInfo;

import java.awt.*;
import static java.lang.Math.*;
import java.awt.image.BufferedImage;

import org.openstreetmap.josm.data.Bounds;
import ru.zkir.urbaneye3d.UrbanEye3dPlugin;

/**
 *  This is a custom TMS renderer, developed specifically for the UrbanEye3D plugin, because JOSM class hierarchy
 *  related to <b>satellite imagery</b> is to difficult to understand and fix. <br />
 *  It is much more simple though, because:
 *  <ul>
 *      <li> It supports TMS layers only, because it is not feasible to support all the variety of JOSM Layer types.</li>
 *      <li> There is only one active layer, which can be rendered at a time </li>
 *  </ul>
 *  It should be eventually replaced with JOSM built-in layer functionality.
 */
public class MapRenderer {

    public static class ImageTooBigException extends  RuntimeException {
        public ImageTooBigException(String s) {
            super (s);
        }
    }
    /** Maximum allowed tiles per single image rendering. This is limit to protect performance*/
    private static final int MAX_TILES_PER_IMAGE = 256;
    private final TileCache tileCache = TileCache.getInstance();
    private static final int TILE_SIZE = 256;//TODO: move to ImageryInfo
    private ImageryInfo currentImagery; //no default, should be set explicitly and validated.
    private boolean isImageryValid=false;

    /**
     *  Renders a satellite layer to a new image.
     *  Note, that since both <b>geographical bounds</b> and <b>image dimensions</b> are specified,
     *  tiles can be scaled! It is needed for 3D rendering, where ground plane tiles can have arbitrary sizes
     */
    public BufferedImage renderMap(int zoom, Bounds bounds, int outputWidth, int outputHeight) throws ImageTooBigException {
        // 0. First of all let's calculate what real dimensions our image should have for the given bounds and zoom
        long worldSizePx = (int) (TILE_SIZE * pow(2, zoom)); // since web mercator is square, this size applies for both width and height
        int outputWidthExpected =  (int) ceil((worldSizePx *  (bounds.getMaxLon() - bounds.getMinLon())/360));
        int outputHeightExpected = (int) ceil(worldSizePx *
                                  abs(log(tan(PI/4 + toRadians(bounds.getMaxLat())/2)) -
                                      log(tan(PI/4 + toRadians(bounds.getMinLat())/2))) /
                                  (2 * PI));


        // 1. Calculate tile range
        TileBounds tileBounds = calculateTileBounds(zoom, bounds);
        int totalTiles = tileBounds.getTileNumber();
        if (totalTiles > MAX_TILES_PER_IMAGE) {
            throw new ImageTooBigException("Bounds are too big for the given zoom level. Calculated image size is " + outputWidthExpected + "x" + outputHeightExpected +
                                             ". " + totalTiles + " tiles are requested, but only " + MAX_TILES_PER_IMAGE + " are allowed per single image");
        }
        
        // 2. Create output image
        BufferedImage result = new BufferedImage(outputWidthExpected, outputHeightExpected, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = result.createGraphics();
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.fillRect(0, 0, outputWidthExpected, outputHeightExpected);


        // 3. Draw all visible tiles
        Point position = calculateTilePosition(zoom, tileBounds.minX, tileBounds.minY, bounds, outputWidthExpected, outputHeightExpected);
        for (int x = tileBounds.minX; x <= tileBounds.maxX; x++) {
            for (int y = tileBounds.minY; y <= tileBounds.maxY; y++) {
                int tilePosX = position.x + TILE_SIZE * (x - tileBounds.minX);
                int tilePosY = position.y + TILE_SIZE * (y - tileBounds.minY);
                if  (this.isImageryValid) {
                    BufferedImage tile = tileCache.getTile(currentImagery, zoom, x, y);
                    if (tile != null) {
                        g2d.drawImage(tile, tilePosX, tilePosY, TILE_SIZE, TILE_SIZE, null);
                    } else {
                        drawPlaceholder(g2d, zoom, x, y, tilePosX, tilePosY);
                    }
                }else{
                    drawInvalid(g2d, zoom, x, y, tilePosX, tilePosY);
                }
            }
        }
        g2d.dispose();

        if (outputWidth == outputWidthExpected && outputHeight == outputHeightExpected) {
            return result;
        } else{
            BufferedImage result1 = new BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_ARGB);
            g2d = result1.createGraphics();
            g2d.drawImage(result, 0, 0, outputWidth, outputHeight, null);
            g2d.dispose();
            return result1;
        }
    }

    public void shutdown() {
        tileCache.shutdown();
    }


    private void drawPlaceholder(Graphics2D g, int zoom, int x, int y, int tilePosX, int tilePosY) {
        g.setColor(Color.GRAY);
        g.drawRect(tilePosX, tilePosY, TILE_SIZE, TILE_SIZE);
        g.setColor(Color.WHITE);
        String text = zoom + "/" + x + "/" + y;
        FontMetrics fm = g.getFontMetrics();
        int stringWidth = fm.stringWidth(text);
        g.drawString(text, tilePosX + (TILE_SIZE - stringWidth) / 2, tilePosY + TILE_SIZE / 2);
    }

    private void drawInvalid(Graphics2D g, int zoom, int x, int y, int tilePosX, int tilePosY) {
        g.setColor(Color.RED);
        g.fillRect(tilePosX, tilePosY, TILE_SIZE, TILE_SIZE);
        g.setColor(Color.WHITE);
        g.drawRect(tilePosX, tilePosY, TILE_SIZE, TILE_SIZE);
        String text = "LAYER NOT SUPPORTED";
        FontMetrics fm = g.getFontMetrics();
        int stringWidth = fm.stringWidth(text);
        g.setColor(Color.BLACK);
        g.drawString(text, tilePosX + (TILE_SIZE - stringWidth) / 2, tilePosY + TILE_SIZE / 2);
        g.setColor(Color.WHITE);
        g.drawString(text, tilePosX + (TILE_SIZE - stringWidth) / 2 + 2, tilePosY + TILE_SIZE / 2 + 2);
    }

    public TileBounds calculateTileBounds(int zoom, Bounds bbox) {
        TileXY minTile = latLonToTile(bbox.getMaxLat(), bbox.getMinLon(), zoom);
        TileXY maxTile = latLonToTile(bbox.getMinLat(), bbox.getMaxLon(), zoom);
        return new TileBounds((int) minTile.x, (int) maxTile.x, (int) minTile.y, (int) maxTile.y);
    }
    
    public static TileXY latLonToTile(double lat, double lon, int zoom) {
        double x = (lon + 180) / 360 * pow(2, zoom);
        double y = (1 - log(tan(toRadians(lat)) + 1 / cos(toRadians(lat))) / PI) / 2 * pow(2, zoom);
        return new TileXY((long) floor(x), (long) floor(y));
    }

    private Point calculateTilePosition(int zoom, int x, int y, Bounds bbox, int outputWidth, int outputHeight) {
        double tileLon = tile2lon(x, zoom);
        double tileLat = tile2lat(y, zoom);

        // Simple linear scaling. For more accuracy, projection math should be used for each pixel.
        // This is a good approximation for small areas.
        double px = (tileLon - bbox.getMinLon()) / (bbox.getMaxLon() - bbox.getMinLon()) * outputWidth;
        double py = (tileLat - bbox.getMaxLat()) / (bbox.getMinLat() - bbox.getMaxLat()) * outputHeight;

        return new Point((int)round(px), (int)round(py));
    }

    private static double tile2lon(int x, int zoom) {
        return x / pow(2.0, zoom) * 360.0 - 180;
    }

    private static double tile2lat(int y, int zoom) {
        double n = PI - (2.0 * PI * y) / pow(2.0, zoom);
        return toDegrees(atan(sinh(n)));
    }

    /** Calculates geographic boundaries of the given tile.*/
    public Bounds getTileBounds(int zoom, int x, int y){
        return new Bounds(tile2lat(y+1, zoom), tile2lon(x, zoom), tile2lat(y, zoom), tile2lon(x+1, zoom));
    }

    /** Sets and validates imagery for rending, based on JOSM {@link  ImageryInfo} data structure. */
    public void setCurrentImagery(ImageryInfo imageryInfo) {
        this.isImageryValid = this.tileCache.validateImageryInfo(imageryInfo);
        this.currentImagery = imageryInfo;
    }

    public void clearCache() {
        this.tileCache.clearCache();
    }

    public int getPendingRequestsCount() {
        return this.tileCache.getPendingRequestsCount();
    }

    public void addCacheUpdateListener(TileCache.CacheUpdateListener l) {
        this.tileCache.addCacheUpdateListener(l);
    }

    public void removeCacheUpdateListener(TileCache.CacheUpdateListener l) {
        this.tileCache.removeCacheUpdateListener(l);
    }
}
