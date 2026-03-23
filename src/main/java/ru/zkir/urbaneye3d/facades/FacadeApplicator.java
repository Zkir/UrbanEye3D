package ru.zkir.urbaneye3d.facades;

import ru.zkir.urbaneye3d.UrbanEye3dPlugin;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

public class FacadeApplicator {

    // Output fields
    private final BufferedImage appliedTexture;

    public FacadeApplicator(Mesh originalMesh, FacadeDefinition facadeDef, BufferedImage baseAtlas) {
        BufferedImage facadeSourceImage = facadeDef.texture;

        // Create a writable copy of the base atlas
        BufferedImage finalAtlas = new BufferedImage(baseAtlas.getWidth(), baseAtlas.getHeight(), baseAtlas.getType());
        Graphics2D g = finalAtlas.createGraphics();
        g.drawImage(baseAtlas, 0, 0, null);

        List<Integer> wallFaceIndices = originalMesh.getWallFaceIndices();
        FacadeDefinition.LODDefinition lod = facadeDef.lods.get(0); // Assuming one LOD

        for (int faceIndex : wallFaceIndices) {
            int[] vertIndices = originalMesh.faces.get(faceIndex);
            if (vertIndices.length < 4) continue; // We assume walls are quads for now

            // --- ROBUSTLY Calculate real-world dimensions ---
            List<Point3D> faceVerts = new ArrayList<>();
            for (int vertIndex : vertIndices) {
                faceVerts.add(originalMesh.verts.get(vertIndex));
            }

            // Sort vertices by Z to find the bottom edge
            faceVerts.sort((v1, v2) -> Double.compare(v1.z, v2.z));
            Point3D low1 = faceVerts.get(0);
            Point3D low2 = faceVerts.get(1);
            Point3D high1 = faceVerts.get(2);
            Point3D high2 = faceVerts.get(3);

            double wallWidth = low1.distance(low2);
            double wallHeight = (high1.z + high2.z) / 2.0 - (low1.z + low2.z) / 2.0;
            if (wallHeight < 0.01) { // Fallback for perfectly horizontal faces if they sneak in
                 wallHeight = low1.distance(high1);
            }

            // Find the appropriate WallDefinition
            FacadeDefinition.WallDefinition selectedWallDef = null;
            for (FacadeDefinition.WallDefinition wallDef : lod.walls) {
                if (wallWidth >= wallDef.minWidth && wallWidth <= wallDef.maxWidth) {
                    selectedWallDef = wallDef;
                    break;
                }
            }

            if (selectedWallDef != null) {
                final FacadeDefinition.WallDefinition finalSelectedWallDef = selectedWallDef;

                // --- 1. HORIZONTAL TILING SEQUENCE & STRETCH FACTOR ---
                List<FacadeDefinition.SliceDefinition> horizSequence = getSliceSequenceH(finalSelectedWallDef, wallWidth);
                double sequenceWidth = horizSequence.stream().mapToDouble(s -> (s.end - s.start)).sum();

                // --- 2. VERTICAL TILING SEQUENCE & STRETCH FACTOR ---
                List<FacadeDefinition.SliceDefinition> vertSequence = getSliceSequenceV(finalSelectedWallDef, wallHeight);
                double sequenceHeight = vertSequence.stream().mapToDouble(s -> (s.end - s.start)).sum();

                // --- 3. DRAWING ---
                double minU = 1.0, minV = 1.0, maxU = 0.0, maxV = 0.0;
                for (int uvIndex : originalMesh.faceUVs.get(faceIndex)) {
                    Point2D uv = originalMesh.uvs.get(uvIndex);
                    minU = Math.min(minU, uv.x); minV = Math.min(minV, uv.y);
                    maxU = Math.max(maxU, uv.x); maxV = Math.max(maxV, uv.y);
                }
                int destBoxX = (int) Math.round(minU * finalAtlas.getWidth());
                int destBoxY = (int) Math.round((1.0 - maxV) * finalAtlas.getHeight());
                int destBoxW = (int) Math.round((maxU - minU) * finalAtlas.getWidth()) + 1;
                int destBoxH = (int) Math.round((maxV - minV) * finalAtlas.getHeight()) + 1;

                int currentDestY = destBoxY;
                for (FacadeDefinition.SliceDefinition vertSlice : vertSequence) {
                    int sliceDestHeightPx = (int) Math.round(((vertSlice.end - vertSlice.start) / sequenceHeight) * destBoxH);

                    int currentDestX = destBoxX;
                    for (FacadeDefinition.SliceDefinition horizSlice : horizSequence) {
                        int sliceDestWidthPx = (int) Math.round(((horizSlice.end - horizSlice.start)/ sequenceWidth) * destBoxW);

                        int sx1 = (int) Math.round(horizSlice.start * facadeSourceImage.getWidth());
                        int sx2 = (int) Math.round(horizSlice.end * facadeSourceImage.getWidth());
                        int sy1 = (int) Math.round((1.0 - vertSlice.end) * facadeSourceImage.getHeight());
                        int sy2 = (int) Math.round((1.0 - vertSlice.start) * facadeSourceImage.getHeight());

                        int dx1 = currentDestX;
                        int dy1 = currentDestY;
                        int dx2 = dx1 + sliceDestWidthPx;
                        int dy2 = dy1 + sliceDestHeightPx;

                        if (dx1 < dx2 && dy1 < dy2) {
                            g.drawImage(facadeSourceImage, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);
                        }
                        currentDestX += sliceDestWidthPx;
                    }
                    currentDestY += sliceDestHeightPx;
                }
            } else {
                 System.out.println("Processing Wall Face #" + faceIndex + 
                                   ": width=" + String.format("%.2f", wallWidth) + "m, height=" + String.format("%.2f", wallHeight) + "m. No suitable WallDef found.");
            }
        }

        g.dispose();
        this.appliedTexture = finalAtlas; // Return the (currently unmodified) atlas
    }

    /**
     * Creates slice sequence based on the given length.
     *
     */
    public static List<FacadeDefinition.SliceDefinition> getSliceSequence(
            List<FacadeDefinition.SliceDefinition> allSlices,  double scale,  double requiredWidth,
            String startType, String middleType, String endType) {

        List<FacadeDefinition.SliceDefinition> leftSlices = allSlices.stream().filter(s -> startType.equals(s.type)).collect(Collectors.toList());
        List<FacadeDefinition.SliceDefinition> centerSlices = allSlices.stream().filter(s -> middleType.equals(s.type)).collect(Collectors.toList());
        List<FacadeDefinition.SliceDefinition> rightSlices = allSlices.stream().filter(s -> endType.equals(s.type)).collect(Collectors.toList());

        // Queues for Left and Right slices (consumable)
        Deque<FacadeDefinition.SliceDefinition> leftRemaining = new LinkedList<>(leftSlices);
        Deque<FacadeDefinition.SliceDefinition> rightRemaining = new LinkedList<>(rightSlices);
        // Queue for center slices (also consumable, but restorable).
        Deque<FacadeDefinition.SliceDefinition> centerRemaining = new LinkedList<>(centerSlices);

        // Data structure for the slice sequence we are creating: we need to have both left and right half separately.
        Deque<FacadeDefinition.SliceDefinition> leftQueue = new LinkedList<>();   // Slices to the left from "center"
        Deque<FacadeDefinition.SliceDefinition> rightQueue = new LinkedList<>();  // Slices to the right of "center"
        boolean add_to_left;
        int center_to_right_count=0;
        double sequenceWidth = 0;

        while (sequenceWidth<requiredWidth) {
            // Is current number of slices odd or even?
            boolean even = ((leftQueue.size() + rightQueue.size()) % 2 == 0);

            FacadeDefinition.SliceDefinition chosen = null;

            if (even) {
                // Чётная длина: приоритет левый → правый → центральный
                if (!leftRemaining.isEmpty()) {
                    chosen = leftRemaining.pollFirst();
                    add_to_left = true;
                } else if (!rightRemaining.isEmpty()) {
                    chosen = rightRemaining.pollFirst();
                    add_to_left = false;
                } else {
                    // Take element from "center" list
                    chosen = centerRemaining.pollFirst();
                    add_to_left = true;
                }
            } else {
                // Нечётная длина: приоритет правый → левый → центральный
                if (!rightRemaining.isEmpty()) {
                    chosen = rightRemaining.pollLast(); //take from the right side
                    add_to_left = false;
                } else if (!leftRemaining.isEmpty()) {
                    chosen = leftRemaining.pollFirst();
                    add_to_left = true;
                } else {
                    chosen = centerRemaining.pollLast(); //take from the right side
                    add_to_left = false;
                    center_to_right_count++;
                }
            }
            if (chosen == null) {
                break; // no more elements!
            }

            sequenceWidth += (chosen.end - chosen.start) * scale;

            if(add_to_left){
                leftQueue.addLast(chosen);
            }else{
                rightQueue.addFirst(chosen);
            }

            //restoration of the center list and some magic!
            if (centerRemaining.isEmpty() && !centerSlices.isEmpty()) {
                centerRemaining = new LinkedList<>(centerSlices);
                // once full set of middle slices is completed, we need to group them,
                // and start collecting them anew.
                for(int i=0; i<center_to_right_count; i++){
                    leftQueue.addLast(rightQueue.pollFirst());
                }
                center_to_right_count=0;
            }
        }

        // Create resulting list.
        List<FacadeDefinition.SliceDefinition> result = new ArrayList<>();
        result.addAll(leftQueue);
        result.addAll(rightQueue);
        return result;
    }

    public static List<FacadeDefinition.SliceDefinition> getSliceSequenceV(FacadeDefinition.WallDefinition wallDef, double wallHeight) {
        var sequence = getSliceSequence(wallDef.verticalSlices, wallDef.scaleY, wallHeight, "BOTTOM", "MIDDLE", "TOP");
        Collections.reverse(sequence);
        return sequence;
    }

    public static List<FacadeDefinition.SliceDefinition> getSliceSequenceH(FacadeDefinition.WallDefinition wallDef, double wallWidth) {
        return getSliceSequence(wallDef.horizontalSlices, wallDef.scaleX, wallWidth, "LEFT", "CENTER", "RIGHT");
    }


    public BufferedImage getAppliedTexture() {
        return appliedTexture;
    }

}
