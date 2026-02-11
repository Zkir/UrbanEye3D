package ru.zkir.customtms;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openstreetmap.josm.data.Version;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import ru.zkir.urbaneye3d.UrbanEye3dPlugin;

/** Cache to store TMS tiles and fetch them via http. Relies on  {@link ImageryInfo} as layer properties
 * There can be only once instance of this class */
public class TileCache {

    private final static BufferedImage TILE_NOT_FOUND_DEFAULT_IMAGE = getTileNotFoundDefaultImage();
    private static final String TILE_CACHE_FOLDER = ".UrbanEye3D";

    @FunctionalInterface
    public interface CacheUpdateListener {
        void tileCacheUpdated(TileCacheUpdateEvent event);
    }

    public static class TileCacheUpdateEvent{
        private final int zoom;
        private final int x;
        private final int y;
        private final int pendingTiles;

        public TileCacheUpdateEvent(int zoom, int x, int y, int pendingTiles) {
            this.zoom = zoom;
            this.x = x;
            this.y = y;
            this.pendingTiles = pendingTiles;
        }
        /** Returns number of tiles with pending request to download*/
        public int getPendingTiles() {
            return this.pendingTiles;
        }

        public int getZoom() {
            return zoom;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        @Override
       public String toString(){
            return "[" + zoom + ", " + x + ", "  + y + " (" + pendingTiles + ")]";
        }

    }

    /** there can be only one instance of the tile cache, otherwise they will duplicate tiles in memory and fight for tiles on disk. */
    private static final TileCache INSTANCE = new TileCache();

    private static final int MEMORY_CACHE_SIZE = 200;
    private static final int THREAD_POOL_SIZE = 4;
    private static final long CACHE_EXPIRATION_MS = 24 * 60 * 60 * 1000; // 24 hours
    private final String diskCachePath;
    private final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    private final Map<String, Future<BufferedImage>> pendingRequests = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private final CopyOnWriteArrayList<CacheUpdateListener> cacheUpdateListeners = new CopyOnWriteArrayList<>();
    private static boolean created=false;
    final String bingUrlTemplate = "http://ecn.{switch:t0,t1,t2,t3}.tiles.virtualearth.net/tiles/a{quadkey}.jpeg?g=12345";

    private final Map<String, BufferedImage> memoryCache = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > MEMORY_CACHE_SIZE;
        }
    };

    private TileCache() {
        if (created) {
            throw new RuntimeException("Only one instance of TileCash is allowed");
        }
        this.diskCachePath = System.getProperty("user.home") + File.separator + TILE_CACHE_FOLDER  + File.separator + "tile_cache";
        new File(diskCachePath).mkdirs();
        created = true;
    }
    public static TileCache getInstance() {
        return INSTANCE;
    }

    /** TileCache knows how to validate {@link ImageryInfo}, because this class uses it */
    public boolean validateImageryInfo(ImageryInfo imageryInfo) {
        // layer Id is necessary, because it is used as a key.
        String layerID = imageryInfo.getId();
        if (layerID==null || layerID.isEmpty()){
            UrbanEye3dPlugin.debugMsg("Satellite layer without id is not supported");
            return false;
        }

        // For Bing, the URL is not a template in the same way, so we skip placeholder validation.
        // The type check in downloadTile is what matters.
        if (imageryInfo.getImageryType() == ImageryInfo.ImageryType.BING) {
            return true;
        }

        String urlTemplate = imageryInfo.getUrl();
        // Still need to check for mandatory placeholders for non-Bing TMS.
        if (!(urlTemplate.contains("{zoom}")  && urlTemplate.contains("{x}")  && urlTemplate.contains("{y}")) ){
           return false;
        }

        // Now, let's "dry run" the resolver to validate all placeholders.
        try {
            // Using dummy values to check the template's structure.
            resolveTileUrl(urlTemplate, 0, 0, 0);
        } catch (IllegalArgumentException e) {
            // If resolveTileUrl throws an error, the template is invalid.
            return false;
        }
        return true;
    }

    /** Cache is cleared and the ongoing downloads are canceled.
     * This ensures that cache remains clear */
    public void clearCache() {
        // Cancel and clear pending requests
        cancelAllPendingRequests();

        // Clear memory cache
        synchronized (memoryCache) {
            memoryCache.clear();
        }

        // Clear disk cache
        Path rootPath = Paths.get(diskCachePath);
        if (Files.exists(rootPath)) {
            try {
                Files.walk(rootPath)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (IOException e) {
                System.err.println("Error clearing disk cache: " + e.getMessage());
                e.printStackTrace();
            }
        }
        // Re-create the root directory
        new File(diskCachePath).mkdirs();
    }

    public void cancelAllPendingRequests() {
        for (Future<BufferedImage> future : pendingRequests.values()) {
            future.cancel(true); // Interrupt if running
        }
        pendingRequests.clear();
    }

    private String getTileKey(String layerId, int zoom, int x, int y) {
        return layerId + "/" + zoom + "/" + x + "/" + y; // Add layer ID to key for uniqueness
    }

    /**
     * Fetches the requested tile from the cache or puts it into the download queue.
     * In case of cache miss, the tile can be later obtained via {@link TileCacheUpdateEvent }
     * @param layer requested satellite layer. Must be TMS
     * @param zoom zoom level
     * @param x x coordinate of the requested tile
     * @param y y coordinate of the requested tile
     * @return tile as {link BufferedImage} or null if tile is absent in the cache
     */
    public BufferedImage getTile(ImageryInfo layer, int zoom, int x, int y) {
        String key = getTileKey(layer.getId(), zoom, x, y);
        // 1. Check memory cache
        synchronized (memoryCache) {
            if (memoryCache.containsKey(key)) {
                return memoryCache.get(key);
            }
        }

        // 2. Check disk cache
        BufferedImage tile = loadFromDisk(layer.getId(), zoom, x, y);
        if (tile != null) {
            synchronized (memoryCache) {
                memoryCache.put(key, tile);
            }
            return tile;
        }

        // 3. Asynchronously load from network
        submitLoadRequest(layer, zoom, x, y);
        return null; // Return null to indicate the tile is loading
    }

    private void submitLoadRequest(ImageryInfo layer, int zoom, int x, int y) {
        String key = getTileKey(layer.getId(), zoom, x, y);
        pendingRequests.computeIfAbsent(key, k -> executorService.submit(() -> {
            try {
                BufferedImage tile;
                if (!layer.getId().equals("fake-layer")) {
                    tile = downloadTile(layer, zoom, x, y);
                }else{
                    tile = fakeTile(layer, zoom, x, y);
                }
                if (tile != null) {
                    synchronized (memoryCache) {
                        memoryCache.put(key, tile);
                    }
                    saveToDisk(tile, layer.getId(), zoom, x, y);
                    raiseCacheUpdatedEvent(zoom, x, y);
                }
                return tile;
            } finally {
                pendingRequests.remove(key);
            }
        }));
    }

    private BufferedImage loadFromDisk(String layerId, int zoom, int x, int y) {
        Path tilePath = getDiskCachePath(layerId, zoom, x, y);
        if (Files.exists(tilePath)) {
            try {
                FileTime lastModified = Files.getLastModifiedTime(tilePath);
                long age = System.currentTimeMillis() - lastModified.toMillis();
                if (age > CACHE_EXPIRATION_MS) {
                    //System.out.println("Tile expired: " + tilePath);
                    return null; // Tile is too old
                }
                return ImageIO.read(tilePath.toFile());
            } catch (IOException e) {
                System.err.println("Error loading tile from disk: " + tilePath);
                e.printStackTrace();
            }
        }
        return null;
    }

    private void saveToDisk(BufferedImage tile, String layerId, int zoom, int x, int y) {
        Path tilePath = getDiskCachePath(layerId, zoom, x, y);
        try {
            Files.createDirectories(tilePath.getParent());
            ImageIO.write(tile, "png", tilePath.toFile());
        } catch (IOException e) {
            System.err.println("Error saving tile to disk: " + tilePath);
            e.printStackTrace();
        }
    }

    /**
     * Converts tile coordinates (x, y) and zoom level to a QuadKey string.
     * @param tileX The X coordinate of the tile.
     * @param tileY The Y coordinate of the tile.
     * @param levelOfDetail The zoom level.
     * @return The QuadKey string.
     */
    public static String tileXYToQuadKey(int tileX, int tileY, int levelOfDetail) {
        StringBuilder quadKey = new StringBuilder();
        for (int i = levelOfDetail; i > 0; i--) {
            char digit = '0';
            int mask = 1 << (i - 1);
            if ((tileX & mask) != 0) {
                digit++; // Add 1 for X
            }
            if ((tileY & mask) != 0) {
                digit += 2; // Add 2 for Y
            }
            quadKey.append(digit);
        }
        return quadKey.toString();
    }


    String resolveTileUrl(String urlTemplate, int zoom, int x, int y) {
        // Regex to find content within {}
        Pattern pattern = Pattern.compile("\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(urlTemplate);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement;

            if ("zoom".equals(placeholder)) {
                replacement = String.valueOf(zoom);
            } else if ("x".equals(placeholder)) {
                replacement = String.valueOf(x);
            } else if ("y".equals(placeholder)) {
                replacement = String.valueOf(y);
            } else if ("quadkey".equals(placeholder)) {
                replacement = tileXYToQuadKey(x, y, zoom);
            }
            else if (placeholder.startsWith("switch:")) {
                String[] options = placeholder.substring("switch:".length()).split(",");
                if (options.length > 0) {
                    replacement = options[random.nextInt(options.length)].trim();
                } else {
                    replacement = ""; // Or throw an error for empty switch
                }
            } else {
                throw new IllegalArgumentException("Unsupported placeholder: " + placeholder);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
    
    private BufferedImage downloadTile(ImageryInfo layer, int zoom, int x, int y) {
        String urlString;
        if (layer.getImageryType() == ImageryInfo.ImageryType.BING) {
            urlString = resolveTileUrl(bingUrlTemplate, zoom, x, y);
        } else {
            urlString = resolveTileUrl(layer.getUrl(), zoom, x, y);
        }

        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", Version.getInstance().getFullAgentString());
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                return ImageIO.read(conn.getInputStream());
            } else if (conn.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND){
                //TODO: maybe we should set a shorter expiry period
                return TILE_NOT_FOUND_DEFAULT_IMAGE;
            } else {
                UrbanEye3dPlugin.debugMsg("Unable to get tile "+urlString+". error code: " + conn.getResponseCode());
            }
        } catch (IOException e) {
            UrbanEye3dPlugin.debugMsg("Failed to download tile: " + urlString);
            UrbanEye3dPlugin.debugMsg(e.getMessage());
        }
        return null;
    }
    private BufferedImage fakeTile(ImageryInfo layer, int zoom, int x, int y) throws InterruptedException {
        int t=10 +(int) (Math.random() * 50);
        TimeUnit.MILLISECONDS.sleep(t);
        final int TILE_SIZE = 256;
        BufferedImage result = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);

        Random rand = new Random();


        for (int j = 0; j < TILE_SIZE; j++) {
            for (int i = 0; i < TILE_SIZE; i++) {
                int r = rand.nextInt(256);
                int g = rand.nextInt(256);
                int b = rand.nextInt(256);
                Color color = new Color(r, g, b);
                result.setRGB(i, j, color.getRGB());
            }
        }

        var g2d = result.createGraphics();

        g2d.setColor(Color.WHITE);
        g2d.drawRect(0, 0, TILE_SIZE, TILE_SIZE);
        g2d.setColor(Color.GRAY);
        g2d.drawRect(1, 1, TILE_SIZE-1, TILE_SIZE-1);


        String text =  "Just a fake tile"; //zoom + "/" + x + "/" + y;
        g2d.setFont(new Font("Arial", Font.BOLD, 24));

        FontMetrics fm = g2d.getFontMetrics();
        int stringWidth = fm.stringWidth(text);
        int stringHeight = (int) Math.floor(fm.getHeight()*1.5);
        g2d.setColor(Color.BLACK);
        g2d.fillRect((TILE_SIZE - stringWidth) / 2 - stringHeight,  TILE_SIZE/2-stringHeight/2, stringWidth+2*stringHeight, stringHeight);
        g2d.setColor(Color.WHITE);
        g2d.drawString(text, (TILE_SIZE - stringWidth) / 2,  TILE_SIZE / 2);


        text =  zoom + "/" + x + "/" + y;
        stringWidth = fm.stringWidth(text);
        //g2d.setColor(Color.PINK);
        //g2d.fillRect((TILE_SIZE - stringWidth) / 2 - stringHeight,  TILE_SIZE/2 + 100 , stringWidth+2*stringHeight, stringHeight);
        g2d.setColor(Color.BLACK);
        g2d.drawString(text, (TILE_SIZE - stringWidth) / 2,  TILE_SIZE / 2+ 75) ;


        g2d.dispose();


        return result;
    }


    private static BufferedImage getTileNotFoundDefaultImage(){
        //TODO: move this method from TileCache somewhere

        final int TILE_SIZE = 256;
        BufferedImage result = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        var g2d = result.createGraphics();
        g2d.setColor(Color.PINK);
        g2d.fillRect(0, 0, TILE_SIZE, TILE_SIZE);
        g2d.setColor(Color.WHITE);
        String text =  "404: Tile not available"; //zoom + "/" + x + "/" + y;
        FontMetrics fm = g2d.getFontMetrics();
        int stringWidth = fm.stringWidth(text);
        g2d.drawString(text, (TILE_SIZE - stringWidth) / 2,  TILE_SIZE / 2);
        g2d.dispose();
        return result;
    };

    private Path getDiskCachePath(String layerId, int zoom, int x, int y) {
        return Paths.get(diskCachePath, layerId, String.valueOf(zoom), String.valueOf(x), y + ".png");
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public int getPendingRequestsCount(){
        return this.pendingRequests.size();
    }

    public void addCacheUpdateListener(CacheUpdateListener l) {
        cacheUpdateListeners.add(l);
    }

    public void removeCacheUpdateListener(CacheUpdateListener l) {
        cacheUpdateListeners.remove(l);
    }

    public void raiseCacheUpdatedEvent(int zoom, int x, int y) {
        var event = new TileCacheUpdateEvent(zoom, x, y, this.getPendingRequestsCount());
        for (var l : cacheUpdateListeners) {
            l.tileCacheUpdated(event);
        }
    }
}
