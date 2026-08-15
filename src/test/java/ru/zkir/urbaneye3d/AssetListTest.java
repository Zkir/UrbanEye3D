package ru.zkir.urbaneye3d;

import org.junit.jupiter.api.Test;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.ObjImporter;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class AssetListTest {

    private static class AssetInfo {
        final String source;
        final String attribution;
        final String license;

        AssetInfo(String source, String attribution, String license) {
            this.source = source;
            this.attribution = attribution;
            this.license = license;
        }
    }

    private static final Map<String, AssetInfo> MASTER_ASSET_LIST = new HashMap<>();
    static {
        MASTER_ASSET_LIST.put("/models/colored_cube.obj", new AssetInfo("UrbanEye3D own work", "Zkir/Gemini", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/bench.obj",        new AssetInfo("UrbanEye3D own work", "Zkir", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/bench_002.obj",        new AssetInfo("UrbanEye3D own work", "Zkir", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/basket.obj",       new AssetInfo("https://github.com/tordanik/OSM2World", "OSM2World", "MIT license"));
        MASTER_ASSET_LIST.put("/models/street_lamp.obj",  new AssetInfo("UrbanEye3D own work", "Zkir", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/street_lamp_bent.obj",  new AssetInfo("UrbanEye3D own work", "Zkir/Gemini", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/bus_stop_001.obj", new AssetInfo("UrbanEye3D own work", "Zkir/Gemini", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/bus_stop_002.obj", new AssetInfo("UrbanEye3D own work", "Zkir/Gemini", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/bus_stop_sign.obj", new AssetInfo("UrbanEye3D own work", "Zkir/Gemini", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/guidepost.obj",    new AssetInfo("UrbanEye3D own work", "Zkir/Gemini", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/info_board.obj",    new AssetInfo("UrbanEye3D own work", "Zkir/Gemini", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/info_post.obj",     new AssetInfo("UrbanEye3D own work", "Zkir/Gemini", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/fire_hydrant.obj", new AssetInfo("UrbanEye3D own work", "Zkir/Gemini", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/water_column.obj", new AssetInfo("UrbanEye3D own work", "Zkir/Gemini", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/recycling_container.obj", new AssetInfo("UrbanEye3D own work", "Zkir/Gemini", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/power_tower.obj", new AssetInfo("UrbanEye3D own work", "Zkir", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/power_pole.obj", new AssetInfo("UrbanEye3D own work", "Zkir", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/power_tower_billboard.obj", new AssetInfo("UrbanEye3D own work", "Zkir", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/power_tower_billboard.png", new AssetInfo("UrbanEye3D own work", "Zkir", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/barrier_block.obj", new AssetInfo("UrbanEye3D own work", "Zkir/Gemini", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/barrier_bollard.obj", new AssetInfo("UrbanEye3D own work", "Zkir/Gemini", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/barrier_gate.obj", new AssetInfo("UrbanEye3D own work", "Zkir/Gemini", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/barrier_lift_gate.obj", new AssetInfo("UrbanEye3D own work", "Zkir/Gemini", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/bicycle_parking.obj", new AssetInfo("UrbanEye3D own work", "Zkir/Gemini", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/picnic_table.obj", new AssetInfo("UrbanEye3D own work", "Zkir/Gemini", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/memorial_obelisk.obj", new AssetInfo("UrbanEye3D own work", "Zkir/Gemini", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/memorial_bust.obj", new AssetInfo("UrbanEye3D own work", "Zkir/Gemini", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/memorial_stone.obj", new AssetInfo("UrbanEye3D own work", "Zkir/Gemini", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/memorial_stele.obj", new AssetInfo("UrbanEye3D own work", "Zkir/Gemini", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/memorial_stele_star.obj", new AssetInfo("UrbanEye3D own work", "Zkir/Gemini", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/sculpture_abstract_spiral.obj", new AssetInfo("UrbanEye3D own work", "Zkir/Gemini", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/memorial_stolperstein.obj", new AssetInfo("UrbanEye3D own work", "Zkir/Gemini", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/railway_buffer_stop.obj", new AssetInfo("UrbanEye3D own work", "Zkir/Gemini", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/traffic_bump.obj",       new AssetInfo("UrbanEye3D own work", "Zkir", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/traffic_hump.obj",       new AssetInfo("UrbanEye3D own work", "Zkir", "CC0 1.0"));
        MASTER_ASSET_LIST.put("/models/water_well.obj",         new AssetInfo("UrbanEye3D own work", "Zkir", "CC0 1.0"));

        // Textures
        MASTER_ASSET_LIST.put("/textures/trees/tree_000.png",
                new AssetInfo("https://github.com/tordanik/OSM2World-default-style", "OSM2World-default-style", "CC0 1.0"));

        MASTER_ASSET_LIST.put("/textures/trees/tree_001.png",
                new AssetInfo("https://github.com/tordanik/OSM2World-default-style", "OSM2World-default-style", "CC0 1.0"));

        MASTER_ASSET_LIST.put("/textures/trees/tree_002.png",
                new AssetInfo("https://www.magnific.com/free-psd/majestic-palm-tree-isolated-transparent-background_408655328.htm", "Designed by Magnific", "Magnific Free"));

        MASTER_ASSET_LIST.put("/textures/bushes/rose_bush.png",
                new AssetInfo("https://pngtree.com/freepng/blooming-red-rose-bush_19859191.html?sol=downref&id=bef", "Pngtree", "Pngtree Free"));
    }
    private static final Map<String, String> LICENSE_URLS = new HashMap<>();
    static {
        LICENSE_URLS.put("CC0 1.0", "https://creativecommons.org/publicdomain/zero/1.0/");
        LICENSE_URLS.put("Magnific Free", "https://www.magnific.com/ai/docs/licenses-attribution");
        LICENSE_URLS.put("CC-BY-4.0", "https://creativecommons.org/licenses/by/4.0/");
        LICENSE_URLS.put("MIT license", "https://opensource.org/license/mit");
        LICENSE_URLS.put("Pngtree Free", "");



    }


    @Test
    void verifyAssetsAndGenerateDocumentation() throws IOException {
        Path resourcesRoot = Paths.get("src/main/resources");

        // 1. Find all assets on the filesystem
        Set<String> foundAssets;
        try (Stream<Path> stream = Files.walk(resourcesRoot)) {
            foundAssets = stream
                    .filter(Files::isRegularFile)
                    .map(resourcesRoot::relativize)
                    .map(Path::toString)
                    .map(p -> "/" + p.replace('\\', '/')) // Convert to resource path format
                    .filter(p -> p.endsWith(".obj") || p.endsWith(".png"))
                    .filter(p -> p.startsWith("/models/") || p.startsWith("/textures/"))
                    .collect(Collectors.toSet());
        }

        // 2. Perform the two-way comparison
        Set<String> masterListKeys = MASTER_ASSET_LIST.keySet();

        Set<String> unlistedAssets = foundAssets.stream()
                .filter(asset -> !masterListKeys.contains(asset))
                .collect(Collectors.toSet());

        Set<String> missingAssets = masterListKeys.stream()
                .filter(asset -> !foundAssets.contains(asset))
                .collect(Collectors.toSet());

        StringBuilder errorMessageBuilder = new StringBuilder();
        if (!unlistedAssets.isEmpty()) {
            errorMessageBuilder.append("The following assets were found on disk but are not in the MASTER_ASSET_LIST in AssetListTest.java:\n");
            errorMessageBuilder.append(String.join("\n", unlistedAssets));
            errorMessageBuilder.append("\n\n");
        }
        if (!missingAssets.isEmpty()) {
            errorMessageBuilder.append("The following assets are in MASTER_ASSET_LIST but were not found on disk:\n");
            errorMessageBuilder.append(String.join("\n", missingAssets));
        }

        String errorMessage = errorMessageBuilder.toString();
        if (!errorMessage.isEmpty()) {
            assertEquals(0, unlistedAssets.size() + missingAssets.size(), errorMessage);
        }

        // 3. Perform sanity checks on each asset
        Map<String, String> assetDetails = new HashMap<>();
        ObjImporter objImporter = new ObjImporter();

        for (String path : masterListKeys) {
            if (path.endsWith(".obj")) {
                Mesh mesh = assertDoesNotThrow(() -> objImporter.loadModel(path), "Failed to load OBJ model: " + path);
                assertNotNull(mesh, "Loaded mesh is null for: " + path);
                assetDetails.put(path, mesh.faces.size() + "&nbsp;faces");
            } else if (path.endsWith(".png")) {
                try (InputStream is = getClass().getResourceAsStream(path)) {
                    assertNotNull(is, "Could not find PNG resource: " + path);
                    BufferedImage image = assertDoesNotThrow(() -> ImageIO.read(is), "Failed to read PNG image: " + path);
                    assertNotNull(image, "Decoded image is null for: " + path);
                    assetDetails.put(path, image.getWidth() + "x" + image.getHeight() + "&nbsp;px");
                }
            }
        }


        // 4. If checks pass, generate the ASSET-LIST.md file
        StringBuilder markdownBuilder = new StringBuilder();
        markdownBuilder.append("# Project Asset List\n\n");
        markdownBuilder.append("This file provides an inventory of all 3D models and textures used in the project. It is auto-generated by `AssetListTest.java`.\n\n");
        markdownBuilder.append("| Resource Path | Details | Attribution/Source | License |\n");
        markdownBuilder.append("|---|---|---|---|\n");

        masterListKeys.stream()
                .sorted()
                .forEach(path -> {
                    AssetInfo info = MASTER_ASSET_LIST.get(path);
                    String details = assetDetails.getOrDefault(path, "N/A");

                    String attributionSource;
                    String source = info.source.isEmpty() ? "TODO" : info.source;
                    String attribution = info.attribution.isEmpty() ? "TODO" : info.attribution;

                    if (source.startsWith("http://") || source.startsWith("https://")) {
                        attributionSource = String.format("[%s](%s)", attribution, source);
                    } else {
                        if (attribution.equals("TODO") && source.equals("TODO")) {
                            attributionSource = "TODO";
                        } else if (attribution.equals("TODO")) {
                            attributionSource = source;
                        } else if (source.equals("TODO")) {
                            attributionSource = attribution;
                        } else {
                            attributionSource = attribution + ", " + source;
                        }
                    }
                    String license_str;
                    if (info.license.isEmpty()) {
                        license_str =  "TODO";
                    }else {
                        String license_url = LICENSE_URLS.get(info.license);
                        if (license_url==null){
                            throw new RuntimeException("Unknow license " + info.license);
                        }
                        license_str = "[" + info.license + "](" + license_url + ")";
                    }

                    markdownBuilder.append(String.format("| `%s` | %s | %s | %s |\n",
                            path,
                            details,
                            attributionSource,
                            license_str
                    ));
                });

        Files.write(Paths.get("docs/ASSET-LIST.md"), markdownBuilder.toString().getBytes(StandardCharsets.UTF_8));
    }
}
