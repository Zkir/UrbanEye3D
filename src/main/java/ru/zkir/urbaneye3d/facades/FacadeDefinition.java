package ru.zkir.urbaneye3d.facades;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.jcs3.access.exception.InvalidArgumentException;
import ru.zkir.urbaneye3d.utils.Point2D;

/**
 * Data structure to hold the parsed information from an X-Plane .fac file.
 */
public class FacadeDefinition {
    public String textureName;
    public boolean isRing;
    public boolean isTwoSided;
    public List<LODDefinition> lods = new ArrayList<>();
    public BufferedImage texture;

    public FacadeDefinition() {
        // Default constructor
    }

    public static class LODDefinition {
        public double minDistance;
        public double maxDistance;
        public List<WallDefinition> walls = new ArrayList<>();
        public RoofDefinition roof; // X-Plane facades can have only one roof def per LOD

        public LODDefinition() {
            // Default constructor
        }
    }

    public static class WallDefinition {
        public double minWidth;
        public double maxWidth;
        public double scaleX;
        public double scaleY;
        public double roofSlope;
        public List<SliceDefinition> horizontalSlices = new ArrayList<>(); // LEFT, CENTER, RIGHT
        public List<SliceDefinition> verticalSlices = new ArrayList<>();   // BOTTOM, MIDDLE, TOP

        public WallDefinition() {
            // Default constructor
        }
    }

    public static class SliceDefinition {
        public final String type; // e.g., "LEFT", "CENTER", "BOTTOM", "MIDDLE"
        public final double start; // S or T coordinate
        public final double end;   // S or T coordinate
        public final int idx;

        public SliceDefinition(String type, double start, double end, int idx) {
            if (end<=start) {
                throw new InvalidArgumentException("End should be greater than start, but got ["+start + "," + end + "]");
            }
            this.type = type;
            this.start = start;
            this.end = end;
            this.idx = idx;
        }

        @Override
        public String toString() {
            return type.substring(0, 1) + idx;
        }
    }

    public static class RoofDefinition {
        public List<Point2D> stCoords = new ArrayList<>(); // Can be 1 or 4 points

        public RoofDefinition() {
            // Default constructor
        }
    }
}
