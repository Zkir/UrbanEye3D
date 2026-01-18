package ru.zkir.urbaneye3d.roofgenerators.linearprofile;

import ru.zkir.urbaneye3d.utils.Point2D;

import java.util.Arrays;
import java.util.List;

// Профили крыш
public enum LinearProfiles {
    GABLED(new Point2D[]{
            new Point2D(0.0, 0.0),
            new Point2D(0.5, 1.0),
            new Point2D(1.0, 0.0)
    },
            10, 0.5),

    ROUND(new Point2D[]{
            new Point2D(0.0, 0.0),
            new Point2D(0.01, 0.195),
            new Point2D(0.038, 0.383),
            new Point2D(0.084, 0.556),
            new Point2D(0.146, 0.707),
            new Point2D(0.222, 0.831),
            new Point2D(0.309, 0.924),
            new Point2D(0.402, 0.981),
            new Point2D(0.5, 1.0),
            new Point2D(0.598, 0.981),
            new Point2D(0.691, 0.924),
            new Point2D(0.778, 0.831),
            new Point2D(0.854, 0.707),
            new Point2D(0.916, 0.556),
            new Point2D(0.962, 0.383),
            new Point2D(0.99, 0.195),
            new Point2D(1.0, 0.0)
    },
            1000, 0.1),

    GAMBREL(new Point2D[]{
            new Point2D(0.0000, 0.0000),
            new Point2D(0.2500, 0.7500),
            new Point2D(0.5000, 1.0000),
            new Point2D(0.7500, 0.7500),
            new Point2D(1.0000, 0.0000)
    }, 100, 0.5),

    SALTBOX(new Point2D[]{// or is it a "double_saltbox"? See https://wiki.openstreetmap.org/wiki/OSM-4D/Roof_table#Subtype_3
            new Point2D(0.0000, 0.0000),
            new Point2D(0.3333, 1.0000),
            new Point2D(0.6666, 1.0000),
            new Point2D(1.0000, 0.0000)
    },
            100, 0.5);

    final Point2D[] profile;
    final int numSamples;
    final double angleToHeight;

    // constructor
    LinearProfiles(Point2D[] points, int numSamples, double angleToHeight) {
        this.profile = points;
        this.numSamples = numSamples;
        this.angleToHeight = angleToHeight;
    }

    public List<Point2D> getProfile() {//get profile as list.
        return Arrays.asList(this.profile);
    }
}
