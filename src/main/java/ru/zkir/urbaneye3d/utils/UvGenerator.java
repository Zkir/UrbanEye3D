package ru.zkir.urbaneye3d.utils;

import com.jogamp.opengl.util.packrect.Rect;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UvGenerator {

    private static final int ATLAS_TEXTURE_SIZE = 512;
    private final Mesh sourceMesh;

    // Cached results after initialization
    private List<FaceLayoutInfo> faceLayouts;
    private UvPacker packer;
    private int optimalAtlasSize;
    private Mesh generatedMesh;
    private BufferedImage generatedAtlas;

    /**
     * Stores the result of unwrapping a 3D face to a 2D plane.
     * All coordinates are in meters.
     */
    private static class FaceUnwrapResult {
        final List<Point2D> unwrappedVertices;
        final double minX, minY, width, height;

        FaceUnwrapResult(List<Point2D> unwrappedVertices, double minX, double minY, double width, double height) {
            this.unwrappedVertices = unwrappedVertices;
            this.minX = minX;
            this.minY = minY;
            this.width = width;
            this.height = height;
        }
    }

    /**
     * Helper inner class to store layout information for each face.
     */
    private static class FaceLayoutInfo {
        final int faceIndex;
        final FaceUnwrapResult unwrapResult;

        FaceLayoutInfo(int faceIndex, FaceUnwrapResult unwrapResult) {
            this.faceIndex = faceIndex;
            this.unwrapResult = unwrapResult;
        }
    }

    public UvGenerator(Mesh sourceMesh) {
        this.sourceMesh = sourceMesh;
        init();
    }

    private void init() {
        if (this.packer != null) return; // Already initialized

        faceLayouts = new ArrayList<>();
        List<Rect> rectsToPack = new ArrayList<>();

        for (int i = 0; i < sourceMesh.faces.size(); i++) {
            FaceUnwrapResult unwrapResult = unwrapFace(i);
            if (unwrapResult == null) continue;

            FaceLayoutInfo layoutInfo = new FaceLayoutInfo(i, unwrapResult);
            faceLayouts.add(layoutInfo);

            Rect rect = new Rect(0, 0, (int) Math.ceil(unwrapResult.width), (int) Math.ceil(unwrapResult.height), i);
            rectsToPack.add(rect);
        }

        this.packer = new UvPacker(rectsToPack);
        this.optimalAtlasSize = packer.getOptimalSize();
    }

    /**
     * Unwraps a 3D face into a 2D polygon and calculates its bounding box.
     */
    private FaceUnwrapResult unwrapFace(int faceIndex) {
        int[] vertIndices = sourceMesh.faces.get(faceIndex);
        List<Point3D> faceVerts3D = new ArrayList<>();
        for (int vertIndex : vertIndices) {
            faceVerts3D.add(sourceMesh.verts.get(vertIndex));
        }

        if (faceVerts3D.size() < 3) return null;

        // 1. Calculate face normal
        Point3D v0 = faceVerts3D.get(0), v1 = faceVerts3D.get(1), v2 = faceVerts3D.get(2);
        Point3D normal = (v1.subtract(v0)).cross(v2.subtract(v0)).normalize();

        Point3D p1, uAxis, vAxis;
        final Point3D worldUp = new Point3D(0, 0, 1);
        final double cos5deg = Math.cos(Math.toRadians(5));
        double dotProduct = normal.dot(worldUp);

        // 2. Differentiate between horizontal and vertical/inclined faces
        if (Math.abs(dotProduct) < cos5deg) {
            // Vertical/Inclined face
            vAxis = worldUp.subtract(normal.mult(normal.dot(worldUp))).normalize();
            uAxis = vAxis.cross(normal);
            p1 = faceVerts3D.get(0); // Use the first vertex as the origin
        } else {
            // Horizontal face
            // Find the longest ADJACENT edge to serve as the U-axis
            p1 = faceVerts3D.get(0); // Initialize with first edge
            Point3D p2 = faceVerts3D.get(1);
            double maxEdgeLengthSq = p1.distance(p2);
            maxEdgeLengthSq *= maxEdgeLengthSq;

            for (int i = 0; i < faceVerts3D.size(); i++) {
                Point3D currentP1 = faceVerts3D.get(i);
                Point3D currentP2 = faceVerts3D.get((i + 1) % faceVerts3D.size()); // Get next adjacent vertex

                double currentEdgeLength = currentP1.distance(currentP2);
                double currentEdgeLengthSq = currentEdgeLength * currentEdgeLength;

                if (currentEdgeLengthSq > maxEdgeLengthSq) {
                    maxEdgeLengthSq = currentEdgeLengthSq;
                    p1 = currentP1;
                    p2 = currentP2;
                }
            }
            uAxis = p2.subtract(p1).normalize();
            vAxis = normal.cross(uAxis);
        }

        List<Point2D> unwrapped = new ArrayList<>();
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;

        for (Point3D p3d : faceVerts3D) {
            Point3D relP = p3d.subtract(p1);
            Point2D p2d = new Point2D(relP.dot(uAxis), relP.dot(vAxis));
            unwrapped.add(p2d);
            minX = Math.min(minX, p2d.x);
            minY = Math.min(minY, p2d.y);
            maxX = Math.max(maxX, p2d.x);
            maxY = Math.max(maxY, p2d.y);
        }

        // Rotate the unwrapped polygon 180 degrees within its bounding box
        double width = maxX - minX;
        double height = maxY - minY;
        List<Point2D> rotatedUnwrapped = new ArrayList<>();
        for (Point2D p : unwrapped) {
            double rotatedX = minX + width - (p.x - minX);
            double rotatedY = minY + height - (p.y - minY);
            rotatedUnwrapped.add(new Point2D(rotatedX, rotatedY));
        }

        return new FaceUnwrapResult(rotatedUnwrapped, minX, minY, width, height);
    }


    public Mesh getMeshWithUvs() {
        if (generatedMesh != null) return generatedMesh;

        Mesh newMesh = new Mesh(Color.GRAY, Color.GRAY, Color.GRAY);
        newMesh.materials.clear();
        newMesh.materials.addAll(sourceMesh.materials);
        newMesh.verts.addAll(sourceMesh.verts);

        Map<Object, Rect> packedRects = packer.getPackedRects();
        double atlasSize = this.optimalAtlasSize;

        for (FaceLayoutInfo layout : faceLayouts) {
            int[] originalVertIndices = sourceMesh.faces.get(layout.faceIndex);
            int[] newUvIndices = new int[originalVertIndices.length];

            Rect packedRect = packedRects.get(layout.faceIndex);

            for (int i = 0; i < layout.unwrapResult.unwrappedVertices.size(); i++) {
                Point2D localUv = layout.unwrapResult.unwrappedVertices.get(i);

                double relativeX = localUv.x - layout.unwrapResult.minX;
                double relativeY = localUv.y - layout.unwrapResult.minY;

                double u = (packedRect.x() + relativeX) / atlasSize;
                double v = 1.0 - ((packedRect.y() + relativeY) / atlasSize);

                int uvIndex = newMesh.addUV(u, v);
                newUvIndices[i] = uvIndex;
            }

            newMesh.faces.add(originalVertIndices);
            newMesh.faceUVs.add(newUvIndices);
            newMesh.faceMaterials.add(sourceMesh.faceMaterials.get(layout.faceIndex));
        }

        this.generatedMesh = newMesh;
        return newMesh;
    }

    public BufferedImage getTextureAtlas() {
        if (generatedAtlas != null) return generatedAtlas;
        if (optimalAtlasSize == 0) return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

        BufferedImage atlas = new BufferedImage(ATLAS_TEXTURE_SIZE, ATLAS_TEXTURE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, ATLAS_TEXTURE_SIZE, ATLAS_TEXTURE_SIZE);

        Map<Object, Rect> packedRects = packer.getPackedRects();
        double scale = (double) ATLAS_TEXTURE_SIZE / optimalAtlasSize;

        for (FaceLayoutInfo layout : faceLayouts) {
            Rect rect = packedRects.get(layout.faceIndex);
            Color faceColor = sourceMesh.materials.get(sourceMesh.faceMaterials.get(layout.faceIndex));

            int drawX = (int) (rect.x() * scale);
            int drawY = (int) (rect.y() * scale);
            int drawW = (int) (rect.w() * scale);
            int drawH = (int) (rect.h() * scale);
            
            g.setColor(faceColor);
            g.fillRect(drawX, drawY, drawW, drawH);

            g.setColor(Color.BLACK);
            g.drawRect(drawX, drawY, drawW - 1, drawH - 1);

            g.setColor(new Color(0, 0, 0, 128));
            for (int i = 0; i < layout.unwrapResult.unwrappedVertices.size(); i++) {
                Point2D p1 = layout.unwrapResult.unwrappedVertices.get(i);
                Point2D p2 = layout.unwrapResult.unwrappedVertices.get((i + 1) % layout.unwrapResult.unwrappedVertices.size());
                
                double p1x = (p1.x - layout.unwrapResult.minX) * scale;
                double p1y = (p1.y - layout.unwrapResult.minY) * scale;
                double p2x = (p2.x - layout.unwrapResult.minX) * scale;
                double p2y = (p2.y - layout.unwrapResult.minY) * scale;

                g.drawLine(drawX + (int)p1x, drawY + (int)p1y, drawX + (int)p2x, drawY + (int)p2y);
            }

            String text = String.valueOf(layout.faceIndex);
            g.setFont(new Font("Arial", Font.BOLD, 14));
            int textWidth = g.getFontMetrics().stringWidth(text);
            g.drawString(text, drawX + drawW / 2 - textWidth / 2, drawY + drawH / 2 + 5);
        }

        g.dispose();
        this.generatedAtlas = atlas;
        return atlas;
    }
}
