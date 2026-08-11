package ru.zkir.urbaneye3d;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.gui.mappaint.mapcss.MapCSSStyleSource;
import org.openstreetmap.josm.gui.mappaint.mapcss.parsergen.MapCSSParser;
import org.openstreetmap.josm.gui.mappaint.mapcss.parsergen.ParseException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class AssetMapCSSValidationTest {

    @BeforeAll
    public static void setup() {
        MapCSSTest.initialize();
    }

    @Test
    public void testAssetsMapCssValidity() throws IOException {
        String content;
        try (InputStream is = getClass().getResourceAsStream("/assets.mapcss")) {
            if (is == null) {
                fail("assets.mapcss not found in classpath");
                return;
            }
            content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        // 1. Validate syntax
        try (Reader reader = new StringReader(content)) {
            MapCSSStyleSource styleSource = new MapCSSStyleSource(content);
            MapCSSParser parser = new MapCSSParser(reader, MapCSSParser.LexicalState.DEFAULT);
            parser.sheet(styleSource);
        } catch (ParseException e) {
            fail("assets.mapcss syntax validation failed: " + e.getMessage(), e);
        }

        // 2. Validate that all referenced resources exist
        List<String> resourcePaths = new ArrayList<>();

        // Find paths in billboard: "..."; model: "...";
        Pattern pathPattern = Pattern.compile("(?:billboard|model)\\s*:\\s*['\"]([^'\"]*?)['\"]");
        Matcher pathMatcher = pathPattern.matcher(content);
        while (pathMatcher.find()) {
            resourcePaths.add(pathMatcher.group(1));
        }

        Path resourcesRoot = Paths.get("src/main/resources");
        int missingResources = 0;
        for (String resourcePath : resourcePaths) {
            // Paths in assets.mapcss are expected to be absolute classpath paths like /textures/... or /models/...
            // For file system check, we strip the leading slash.
            String relativePath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
            Path fullPath = resourcesRoot.resolve(relativePath);
            
            if (!Files.exists(fullPath)) {
                System.out.println("Resource not found: '" + resourcePath + "' (checked " + fullPath.toAbsolutePath() + ")");
                missingResources++;
            }
        }
        assertEquals(0, missingResources, missingResources + " referenced assets are missing from src/main/resources");
    }
}
