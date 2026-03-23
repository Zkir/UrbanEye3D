package ru.zkir.urbaneye3d.facades;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import ru.zkir.urbaneye3d.utils.Point2D;

import javax.imageio.ImageIO;

public class FacadeParser {

    private static final String FACADE_ROOT_DIR = "/facades/";

    /**
     *  Method to load facade definition, including facade texture.
     *  Note that it loads file from JAR resources, in FACADE_ROOT_DIR folder
     */
    public static FacadeDefinition parse(String facadeFileName) throws IOException {
        InputStream facadeStream = FacadeDefinition.class.getResourceAsStream(FACADE_ROOT_DIR + facadeFileName);
        if (facadeStream==null){
            throw new IOException("Facade definition not found at resource path: " + FACADE_ROOT_DIR + facadeFileName);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(facadeStream, StandardCharsets.UTF_8))) {
            List<String> lines = reader.lines().collect(Collectors.toList());
            return parse(lines);
        }
    }

    private static FacadeDefinition parse(List<String> lines) throws IOException {
        FacadeDefinition definition = new FacadeDefinition();

        FacadeDefinition.LODDefinition currentLOD = null;
        FacadeDefinition.WallDefinition currentWall = null;

        int idx_t = 0;
        int idx_m = 0;
        int idx_b = 0;
        int idx_l = 0;
        int idx_c = 0;
        int idx_r = 0;

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                continue;
            }

            String[] parts = trimmedLine.split("\\s+", 2);
            String command = parts[0].toUpperCase();
            String args = parts.length > 1 ? parts[1] : "";
            String[] argParts = args.split("\\s+");

            switch (command) {
                case "TEXTURE":
                    definition.textureName = args;
                    break;
                case "RING":
                    definition.isRing = Integer.parseInt(args) == 1;
                    break;
                case "TWO_SIDED":
                    definition.isTwoSided = Integer.parseInt(args) == 1;
                    break;
                case "LOD":
                    if (argParts.length >= 2) {
                        currentLOD = new FacadeDefinition.LODDefinition();
                        currentLOD.minDistance = Double.parseDouble(argParts[0]);
                        currentLOD.maxDistance = Double.parseDouble(argParts[1]);
                        definition.lods.add(currentLOD);
                    }
                    break;
                case "WALL":
                    if (currentLOD != null && argParts.length >= 2) {
                        currentWall = new FacadeDefinition.WallDefinition();
                        currentWall.minWidth = Double.parseDouble(argParts[0]);
                        currentWall.maxWidth = Double.parseDouble(argParts[1]);
                        currentLOD.walls.add(currentWall);
                        idx_t = 0;
                        idx_m = 0;
                        idx_b = 0;
                        idx_l = 0;
                        idx_c = 0;
                        idx_r = 0;
                    }
                    break;
                case "ROOF":
                     if (currentLOD != null) {
                        if (currentLOD.roof == null) {
                            currentLOD.roof = new FacadeDefinition.RoofDefinition();
                        }
                        if (argParts.length >= 2) {
                            currentLOD.roof.stCoords.add(new Point2D(Double.parseDouble(argParts[0]), Double.parseDouble(argParts[1])));
                        }
                    }
                    break;
                case "SCALE":
                    if (currentWall != null && argParts.length >= 2) {
                        currentWall.scaleX = Double.parseDouble(argParts[0]);
                        currentWall.scaleY = Double.parseDouble(argParts[1]);
                    }
                    break;
                case "ROOF_SLOPE":
                    if (currentWall != null) {
                        currentWall.roofSlope = Double.parseDouble(args);
                    }
                    break;
                case "LEFT":
                case "CENTER":
                case "RIGHT":
                    if (currentWall != null && argParts.length >= 2) {
                        int idx=0;
                        switch (command) {
                            case "LEFT":
                                idx_l+=1; idx=idx_l;
                                break;
                            case "CENTER":
                                idx_c+=1; idx=idx_c;
                                break;
                            case "RIGHT":
                                idx_r+=1; idx=idx_r;
                                break;
                        }
                        var slice = new FacadeDefinition.SliceDefinition(command, Double.parseDouble(argParts[0]), Double.parseDouble(argParts[1]), idx);
                        currentWall.horizontalSlices.add(slice);
                    }
                    break;
                case "BOTTOM":
                case "MIDDLE":
                case "TOP":
                    if (currentWall != null && argParts.length >= 2) {
                        int idx=0;
                        switch (command) {
                            case "BOTTOM":
                                idx_b+=1; idx=idx_b;
                                break;
                            case "MIDDLE":
                                idx_m+=1; idx=idx_m;
                                break;
                            case "TOP":
                                idx_t+=1; idx=idx_t;
                                break;
                        }
                        var slice = new FacadeDefinition.SliceDefinition(command, Double.parseDouble(argParts[0]), Double.parseDouble(argParts[1]), idx);
                        currentWall.verticalSlices.add(slice);
                    }
                    break;
                // Ignoring A, 800, FACADE, etc.
                default:
                    break;
            }
        }
        //Load texture
        if (definition.textureName==null){
            throw new RuntimeException("Texture name is not specified");
        }
        definition.texture = loadFacadeSourceImage(definition.textureName) ;

        return definition;
    }

    /**
     * Helper to load facade source image from resources.
     */
    private static BufferedImage loadFacadeSourceImage(String imageFileName) throws IOException {
        String resourcePath = FACADE_ROOT_DIR + imageFileName;
        InputStream stream = FacadeApplicator.class.getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new IOException("Facade source image not found at resource path: " + resourcePath);
        }
        return ImageIO.read(stream);
    }
}
