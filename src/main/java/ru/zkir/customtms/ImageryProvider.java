package ru.zkir.customtms;

import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.ImageryInfo.ImageryType;

/**
 * An enum representing the available imagery providers for tests.
 */
public enum ImageryProvider {

    OSM_CARTO("osm-carto", "OpenStreetMap Carto", "https://tile.openstreetmap.org/{zoom}/{x}/{y}.png", ImageryType.TMS),
    ESRI_WORLD_IMAGERY("esri-world", "Esri World Imagery", "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{zoom}/{y}/{x}", ImageryType.TMS),
    BING("bing", "Bing aerial imagery", "https://bing.com/maps/", ImageryType.BING),
    GOOGLE_SATELLITE("google-satellite", "Google Satellite", "https://mt1.google.com/vt/lyrs=s&x={x}&y={y}&z={zoom}", ImageryType.TMS),
    SWITCH_VALID("switch-valid", "Imagery with valid switch", "https://{switch:a,b,c}.example.com/map/{zoom}/{x}/{y}", ImageryType.TMS),
    INVALID_PLACEHOLDERS("invalid-placeholders", "URL with invalid placeholders", "https://tile.openstreetmap.org/{z}/{a}/{b}.png", ImageryType.TMS), //for stability tests
    STUB_NO_HTTP("fake-layer", "Stub with fake tiles for autotest", "https://tile.bogus.org/{zoom}/{x}/{y}.png", ImageryType.TMS); //for DDOS attacks!


    private final ImageryInfo imageryInfo;

    ImageryProvider(String id, String name, String url, ImageryType type) {
        this.imageryInfo = new ImageryInfo(name, url);
        this.imageryInfo.setId(id);
        this.imageryInfo.setImageryType(type);
    }

    public ImageryInfo getImageryInfo() {
        return imageryInfo;
    }

    @Override
    public String toString() {
        return this.imageryInfo.getName();
    }
}
