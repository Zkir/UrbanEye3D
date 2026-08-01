package ru.zkir.urbaneye3d.mapcssproxy;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.Point2D;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import static java.lang.Math.cos;
import static java.lang.Math.toRadians;

/**
 * There are some objects we would like to draw directly on the ground, but it is not possible via MapCSS
 */
public class GroundDecorations {

    /**
     * Render ground decorations which MapCss is not capable of, like sport pitch markings
     *
     * @param image       The BufferedImage representing the ground texture tile.
     * @param dataSet     The OSM dataset containing map objects.
     * @param tileBounds  The geographic boundaries of the current tile.
     * @param mapcssScale Meters per pixel
     */
    public static void drawGroundDecorations(BufferedImage image, DataSet dataSet, Bounds tileBounds, double scale) {

        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        try {
            for (OsmPrimitive primitive : dataSet.allPrimitives()) {
                if (!(primitive instanceof Way) && !(primitive instanceof Relation)) {
                    continue;
                }

                if (!(primitive.hasTag("leisure", "pitch") &&
                        (primitive.hasTag("sport", "soccer") || primitive.hasTag("sport", "tennis") || primitive.hasTag("sport", "volleyball") || primitive.hasTag("sport", "badminton") || primitive.hasTag("sport", "futsal")))) {
                    continue;
                }

                // Skip objects that are completely outside the tile
                if (primitive.getBBox() != null && !tileBounds.intersects(primitive.getBBox())) {
                    continue;
                }

                if (primitive.hasTag("leisure", "pitch") && primitive.hasTag("sport", "soccer")) {
                    drawSoccerMarkings(g2d, image.getWidth(), image.getHeight(), primitive, tileBounds, scale);
                    continue; // one marking is enough
                }

                if (primitive.hasTag("leisure", "pitch") && primitive.hasTag("sport", "tennis")) {
                    drawTennisMarkings(g2d, image.getWidth(), image.getHeight(), primitive, tileBounds, scale);
                    continue; // one marking is enough
                }

                if (primitive.hasTag("leisure", "pitch") && primitive.hasTag("sport", "volleyball")) {
                    drawVolleyballMarkings(g2d, image.getWidth(), image.getHeight(), primitive, tileBounds, scale);
                    continue; // one marking is enough
                }

                if (primitive.hasTag("leisure", "pitch") && primitive.hasTag("sport", "badminton")) {
                    drawBadmintonMarkings(g2d, image.getWidth(), image.getHeight(), primitive, tileBounds, scale);
                    continue; // one marking is enough
                }

                if (primitive.hasTag("leisure", "pitch") && primitive.hasTag("sport", "futsal")) {
                    drawFutsalMarkings(g2d, image.getWidth(), image.getHeight(), primitive, tileBounds, scale);
                    continue; // one marking is enough
                }
            }
        } finally {
            g2d.dispose();
        }

    }

    /*
     * Draws professional soccer pitch markings on the generated ground texture.
     * This method identifies soccer pitches, finds the largest inscribed rectangle,
     * and renders FIFA-standard markings (penalty areas, circles, arcs) centered within it.
     */
    private static void drawSoccerMarkings(Graphics2D g2d, int imgWidth, int imgHeight, OsmPrimitive primitive, Bounds tileBounds, double scale) {
        final double PADDINGS = 1.5;
        LatLon tileCenter = tileBounds.getCenter();

        // Factor to convert real meters to pixels (since 1 EN unit = 1 pixel in our MapCSS render)
        double mToPixFactor = 1.0 / scale / cos(toRadians(tileCenter.lat()));

        Contour contour = new Contour(primitive);
        if (contour.outerRings.isEmpty()) {
            return;
        }

        // Convert contour to local coordinates (meters relative to tileCenter)
        contour.toLocalCoords(tileCenter);
        contour.removeRedundantNodes();

        Point2D u = findUVForInscribedRectangle(contour); // axis system, probably rotated
        if (u == null) {
            return;
        }

        Rectangle2D.Double rect = contour.findLargestInscribedRectangle(u.x, u.y);

        if (rect == null) {
            return;
        }

        // Center in projected (rotated) coordinates in meters
        double centerPX = rect.x + rect.width / 2.0;
        double centerPY = rect.y + rect.height / 2.0;

        // Back to local XY in meters (relative to tileCenter)
        double nx = -u.y;
        double ny = u.x;
        double localCenterX = centerPX * u.x + centerPY * nx;
        double localCenterY = centerPX * u.y + centerPY * ny;

        AffineTransform oldTransform = g2d.getTransform();

        // 1. Move to tile center (in pixels)
        g2d.translate(imgWidth / 2.0, imgHeight / 2.0);

        // 2. Move to object center (localCenterX/Y are in meters, Y is inverted in pixels)
        g2d.translate(localCenterX * mToPixFactor, -localCenterY * mToPixFactor);

        // 3. Rotate along the pitch axis
        g2d.rotate(-Math.atan2(u.y, u.x));

        // 4. Scale so all subsequent rendering is done in METERS
        g2d.scale(mToPixFactor, mToPixFactor);

        double pitchLenM = rect.width;
        double pitchWidthM = rect.height;

        // Paint FIFA markings with 2m offset from edges
        double drawLenM = Math.max(0, pitchLenM - PADDINGS*2);
        double drawWidthM = Math.max(0, pitchWidthM - PADDINGS*2);

        // Proportional scaling for small pitches
        // Base thresholds: 90m length, 45m width
        double pitchScale = 1.0;
        if (drawLenM < 90.0) {
            pitchScale = Math.min(pitchScale, drawLenM / 90.0);
        }
        if (drawWidthM < 45.0) {
            pitchScale = Math.min(pitchScale, drawWidthM / 45.0);
        }


        g2d.setColor(new Color(255, 255, 255, 220));
        g2d.setStroke(new BasicStroke(0.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));

        double halfL = drawLenM / 2.0;
        double halfW = drawWidthM / 2.0;

        // Main boundaries and center line
        g2d.draw(new Rectangle2D.Double(-halfL, -halfW, drawLenM, drawWidthM));
        g2d.draw(new Line2D.Double(0, -halfW, 0, halfW));

        // Center mark and circle
        double R_center = 9.15 * pitchScale;
        g2d.fill(new Ellipse2D.Double(-0.2, -0.2, 0.4, 0.4));
        if (R_center > 0.5) {
            g2d.draw(new Ellipse2D.Double(-R_center, -R_center, 2 * R_center, 2 * R_center));
        }

        for (int side : new int[]{-1, 1}) {
            double goalLineX = side * halfL;
            double dir = -side;

            // Penalty area (standard: 16.5m deep, 40.32m wide)
            double pWidth = 40.32 * pitchScale;
            double pDepth = 16.5 * pitchScale;

            g2d.draw(new Rectangle2D.Double(
                    side > 0 ? halfL - pDepth : -halfL, -pWidth / 2.0, pDepth, pWidth));

            // Goal area (standard: 5.5m deep, 18.32m wide)
            double gWidth = 18.32 * pitchScale;
            double gDepth = 5.5 * pitchScale;
            g2d.draw(new Rectangle2D.Double(
                    side > 0 ? halfL - gDepth : -halfL, -gWidth / 2.0, gDepth, gWidth));

            // Penalty spot (standard: 11m from goal line)
            double spotX = goalLineX + dir * 11.0 * pitchScale;
            if (Math.abs(spotX) < halfL) {
                g2d.fill(new Ellipse2D.Double(spotX - 0.2, -0.2, 0.4, 0.4));

                // Penalty arc (standard: 9.15m radius from spot)
                double arcR = 9.15 * pitchScale;
                double distToLine = pDepth - (11.0 * pitchScale);
                if (distToLine < arcR && arcR > 0.5) {
                    double angle = Math.toDegrees(Math.acos(distToLine / arcR));
                    double start = -angle;
                    if (side > 0) {
                        start = 180 - angle;
                    }

                    g2d.draw(new Arc2D.Double(spotX - arcR, -arcR, 2 * arcR, 2 * arcR,
                            start, 2 * angle, Arc2D.OPEN));
                }
            }
        }

        // Corner arcs (1m radius, also scaled)
        double R_corner = 1.0 * pitchScale;
        if (R_corner > 0.1) {
            g2d.draw(new Arc2D.Double(-halfL - R_corner, -halfW - R_corner, 2 * R_corner, 2 * R_corner, 270, 90, Arc2D.OPEN));
            g2d.draw(new Arc2D.Double(halfL - R_corner, -halfW - R_corner, 2 * R_corner, 2 * R_corner, 180, 90, Arc2D.OPEN));
            g2d.draw(new Arc2D.Double(halfL - R_corner, halfW - R_corner, 2 * R_corner, 2 * R_corner, 90, 90, Arc2D.OPEN));
            g2d.draw(new Arc2D.Double(-halfL - R_corner, halfW - R_corner, 2 * R_corner, 2 * R_corner, 0, 90, Arc2D.OPEN));
        }

        g2d.setTransform(oldTransform);
    }

    /*
     * Draws professional tennis court markings on the generated ground texture.
     */
    private static void drawTennisMarkings(Graphics2D g2d, int imgWidth, int imgHeight, OsmPrimitive primitive, Bounds tileBounds, double scale) {
        final double PADDINGS=0.75;
        LatLon tileCenter = tileBounds.getCenter();
        double mToPixFactor = 1.0 / scale / cos(toRadians(tileCenter.lat()));

        Contour contour = new Contour(primitive);
        if (contour.outerRings.isEmpty()) {
            return;
        }

        contour.toLocalCoords(tileCenter);
        contour.removeRedundantNodes();

        Point2D u = findUVForInscribedRectangle(contour);
        if (u == null) {
            return;
        }
        Rectangle2D.Double rect = contour.findLargestInscribedRectangle(u.x, u.y);

        if (rect == null) {
            return;
        }

        double centerPX = rect.x + rect.width / 2.0;
        double centerPY = rect.y + rect.height / 2.0;

        double nx = -u.y;
        double ny = u.x;
        double localCenterX = centerPX * u.x + centerPY * nx;
        double localCenterY = centerPX * u.y + centerPY * ny;

        AffineTransform oldTransform = g2d.getTransform();

        g2d.translate(imgWidth / 2.0, imgHeight / 2.0);
        g2d.translate(localCenterX * mToPixFactor, -localCenterY * mToPixFactor);
        g2d.rotate(-Math.atan2(u.y, u.x));
        g2d.scale(mToPixFactor, mToPixFactor);

        double pitchLenM = rect.width;
        double pitchWidthM = rect.height;

        // offset from edges
        double drawLenM = Math.max(0, pitchLenM - PADDINGS*2);
        double drawWidthM = Math.max(0, pitchWidthM - PADDINGS*2);

        // Proportional scaling for small courts (standard: 23.77m x 10.97m)
        double pitchScale = 1.0;
        if (drawLenM < 23.77) {
            pitchScale = Math.min(pitchScale, drawLenM / 23.77);
        }
        if (drawWidthM < 10.97) {
            pitchScale = Math.min(pitchScale, drawWidthM / 10.97);
        }

        g2d.setColor(new Color(255, 255, 255, 220));
        g2d.setStroke(new BasicStroke(0.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));

        double L = 23.77 * pitchScale;
        double W = 10.97 * pitchScale;
        double singleW = 8.23 * pitchScale;
        double serviceLineDist = 6.40 * pitchScale;

        double halfL = L / 2.0;
        double halfW = W / 2.0;
        double halfSingleW = singleW / 2.0;

        // Overall perimeter (Doubles sidelines + Baselines)
        g2d.draw(new Rectangle2D.Double(-halfL, -halfW, L, W));

        // Singles sidelines
        g2d.draw(new Line2D.Double(-halfL, -halfSingleW, halfL, -halfSingleW));
        g2d.draw(new Line2D.Double(-halfL, halfSingleW, halfL, halfSingleW));

        // Net line (center)
        g2d.draw(new Line2D.Double(0, -halfW, 0, halfW));

        // Service lines
        g2d.draw(new Line2D.Double(-serviceLineDist, -halfSingleW, -serviceLineDist, halfSingleW));
        g2d.draw(new Line2D.Double(serviceLineDist, -halfSingleW, serviceLineDist, halfSingleW));

        // Central service line
        g2d.draw(new Line2D.Double(-serviceLineDist, 0, serviceLineDist, 0));

        // Center marks on baselines
        double markLen = 0.2 * pitchScale;
        g2d.draw(new Line2D.Double(-halfL, 0, -halfL + markLen, 0));
        g2d.draw(new Line2D.Double(halfL, 0, halfL - markLen, 0));

        g2d.setTransform(oldTransform);
    }

    /*
     * Draws professional volleyball court markings on the generated ground texture.
     */
    private static void drawVolleyballMarkings(Graphics2D g2d, int imgWidth, int imgHeight, OsmPrimitive primitive, Bounds tileBounds, double scale) {
        final double PADDINGS=0.75;
        LatLon tileCenter = tileBounds.getCenter();
        double mToPixFactor = 1.0 / scale / cos(toRadians(tileCenter.lat()));

        Contour contour = new Contour(primitive);
        if (contour.outerRings.isEmpty()) {
            return;
        }

        contour.toLocalCoords(tileCenter);
        contour.removeRedundantNodes();

        Point2D u = findUVForInscribedRectangle(contour);
        if (u == null) {
            return;
        }
        Rectangle2D.Double rect = contour.findLargestInscribedRectangle(u.x, u.y);

        if (rect == null) {
            return;
        }

        double centerPX = rect.x + rect.width / 2.0;
        double centerPY = rect.y + rect.height / 2.0;

        double nx = -u.y;
        double ny = u.x;
        double localCenterX = centerPX * u.x + centerPY * nx;
        double localCenterY = centerPX * u.y + centerPY * ny;

        AffineTransform oldTransform = g2d.getTransform();

        g2d.translate(imgWidth / 2.0, imgHeight / 2.0);
        g2d.translate(localCenterX * mToPixFactor, -localCenterY * mToPixFactor);
        g2d.rotate(-Math.atan2(u.y, u.x));
        g2d.scale(mToPixFactor, mToPixFactor);

        double pitchLenM = rect.width;
        double pitchWidthM = rect.height;

        // 2m offset from edges
        double drawLenM = Math.max(0, pitchLenM - PADDINGS*2);
        double drawWidthM = Math.max(0, pitchWidthM - PADDINGS*2);

        // Proportional scaling for small courts (standard: 18m x 9m)
        double pitchScale = 1.0;
        if (drawLenM < 18.0) {
            pitchScale = Math.min(pitchScale, drawLenM / 18.0);
        }
        if (drawWidthM < 9.0) {
            pitchScale = Math.min(pitchScale, drawWidthM / 9.0);
        }

        g2d.setColor(new Color(255, 255, 255, 220));
        g2d.setStroke(new BasicStroke(0.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));

        double L = 18.0 * pitchScale;
        double W = 9.0 * pitchScale;
        double attackLineDist = 3.0 * pitchScale;

        double halfL = L / 2.0;
        double halfW = W / 2.0;

        // Perimeter
        g2d.draw(new Rectangle2D.Double(-halfL, -halfW, L, W));

        // Center line
        g2d.draw(new Line2D.Double(0, -halfW, 0, halfW));

        // Attack lines
        g2d.draw(new Line2D.Double(-attackLineDist, -halfW, -attackLineDist, halfW));
        g2d.draw(new Line2D.Double(attackLineDist, -halfW, attackLineDist, halfW));

        // Dashed extensions of attack lines (FIVB standard)
        // Simplified for rendering: dashed lines to the end of the free zone (2m)
        float[] dashPattern = {0.15f * (float)pitchScale, 0.20f * (float)pitchScale};
        g2d.setStroke(new BasicStroke(0.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1.0f, dashPattern, 0.0f));

        for (int side : new int[]{-1, 1}) {
            double x = side * attackLineDist;
            // Draw extensions up and down from the court boundaries to the 2m offset
            g2d.draw(new Line2D.Double(x, -halfW, x, -halfW - 2.0));
            g2d.draw(new Line2D.Double(x, halfW, x, halfW + 2.0));
        }

        g2d.setTransform(oldTransform);
    }

    /*
     * Draws professional badminton court markings on the generated ground texture.
     */
    private static void drawBadmintonMarkings(Graphics2D g2d, int imgWidth, int imgHeight, OsmPrimitive primitive, Bounds tileBounds, double scale) {
        final double PADDINGS = 0.5;
        LatLon tileCenter = tileBounds.getCenter();
        double mToPixFactor = 1.0 / scale / cos(toRadians(tileCenter.lat()));

        Contour contour = new Contour(primitive);
        if (contour.outerRings.isEmpty()) {
            return;
        }

        contour.toLocalCoords(tileCenter);
        contour.removeRedundantNodes();

        Point2D u = findUVForInscribedRectangle(contour);
        if (u == null) {
            return;
        }
        Rectangle2D.Double rect = contour.findLargestInscribedRectangle(u.x, u.y);

        if (rect == null) {
            return;
        }

        double centerPX = rect.x + rect.width / 2.0;
        double centerPY = rect.y + rect.height / 2.0;

        double nx = -u.y;
        double ny = u.x;
        double localCenterX = centerPX * u.x + centerPY * nx;
        double localCenterY = centerPX * u.y + centerPY * ny;

        AffineTransform oldTransform = g2d.getTransform();

        g2d.translate(imgWidth / 2.0, imgHeight / 2.0);
        g2d.translate(localCenterX * mToPixFactor, -localCenterY * mToPixFactor);
        g2d.rotate(-Math.atan2(u.y, u.x));
        g2d.scale(mToPixFactor, mToPixFactor);

        double pitchLenM = rect.width;
        double pitchWidthM = rect.height;

        // offset from edges
        double drawLenM = Math.max(0, pitchLenM - PADDINGS*2);
        double drawWidthM = Math.max(0, pitchWidthM - PADDINGS*2);

        // Proportional scaling for small courts (standard: 13.4m x 6.1m)
        double pitchScale = 1.0;
        if (drawLenM < 13.4) {
            pitchScale = Math.min(pitchScale, drawLenM / 13.4);
        }
        if (drawWidthM < 6.1) {
            pitchScale = Math.min(pitchScale, drawWidthM / 6.1);
        }

        g2d.setColor(new Color(255, 255, 255, 220));
        g2d.setStroke(new BasicStroke(0.3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));

        double L = 13.4 * pitchScale;
        double W = 6.1 * pitchScale;
        double singleW = 5.18 * pitchScale;
        double shortServiceDist = 1.98 * pitchScale;
        double longServiceDistDoubles = 0.76 * pitchScale;

        double halfL = L / 2.0;
        double halfW = W / 2.0;
        double halfSingleW = singleW / 2.0;

        // Perimeter (Doubles sidelines + Baselines)
        g2d.draw(new Rectangle2D.Double(-halfL, -halfW, L, W));

        // Singles sidelines
        g2d.draw(new Line2D.Double(-halfL, -halfSingleW, halfL, -halfSingleW));
        g2d.draw(new Line2D.Double(-halfL, halfSingleW, halfL, halfSingleW));

        // Net line (center)
        g2d.draw(new Line2D.Double(0, -halfW, 0, halfW));

        // Short service lines
        g2d.draw(new Line2D.Double(-shortServiceDist, -halfW, -shortServiceDist, halfW));
        g2d.draw(new Line2D.Double(shortServiceDist, -halfW, shortServiceDist, halfW));

        // Long service lines for doubles
        g2d.draw(new Line2D.Double(-halfL + longServiceDistDoubles, -halfW, -halfL + longServiceDistDoubles, halfW));
        g2d.draw(new Line2D.Double(halfL - longServiceDistDoubles, -halfW, halfL - longServiceDistDoubles, halfW));

        // Central service lines
        g2d.draw(new Line2D.Double(-halfL, 0, -shortServiceDist, 0));
        g2d.draw(new Line2D.Double(shortServiceDist, 0, halfL, 0));

        g2d.setTransform(oldTransform);
    }

    /*
     * Draws professional futsal pitch markings on the generated ground texture.
     */
    private static void drawFutsalMarkings(Graphics2D g2d, int imgWidth, int imgHeight, OsmPrimitive primitive, Bounds tileBounds, double scale) {
        final double PADDINGS = 1.0;
        LatLon tileCenter = tileBounds.getCenter();
        double mToPixFactor = 1.0 / scale / cos(toRadians(tileCenter.lat()));

        Contour contour = new Contour(primitive);
        if (contour.outerRings.isEmpty()) {
            return;
        }

        contour.toLocalCoords(tileCenter);
        contour.removeRedundantNodes();

        Point2D u = findUVForInscribedRectangle(contour);
        if (u == null) {
            return;
        }
        Rectangle2D.Double rect = contour.findLargestInscribedRectangle(u.x, u.y);

        if (rect == null) {
            return;
        }

        double centerPX = rect.x + rect.width / 2.0;
        double centerPY = rect.y + rect.height / 2.0;

        double nx = -u.y;
        double ny = u.x;
        double localCenterX = centerPX * u.x + centerPY * nx;
        double localCenterY = centerPX * u.y + centerPY * ny;

        AffineTransform oldTransform = g2d.getTransform();

        g2d.translate(imgWidth / 2.0, imgHeight / 2.0);
        g2d.translate(localCenterX * mToPixFactor, -localCenterY * mToPixFactor);
        g2d.rotate(-Math.atan2(u.y, u.x));
        g2d.scale(mToPixFactor, mToPixFactor);

        double pitchLenM = rect.width;
        double pitchWidthM = rect.height;

        double drawLenM = Math.max(0, pitchLenM - PADDINGS * 2);
        double drawWidthM = Math.max(0, pitchWidthM - PADDINGS * 2);

        // Proportional scaling for small pitches (standard: 40m x 20m)
        double pitchScale = 1.0;
        if (drawLenM < 40.0) {
            pitchScale = Math.min(pitchScale, drawLenM / 40.0);
        }
        if (drawWidthM < 20.0) {
            pitchScale = Math.min(pitchScale, drawWidthM / 20.0);
        }

        g2d.setColor(new Color(255, 255, 255, 220));
        g2d.setStroke(new BasicStroke(0.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));

        double halfL = drawLenM / 2.0;
        double halfW = drawWidthM / 2.0;

        // Perimeter and center line
        g2d.draw(new Rectangle2D.Double(-halfL, -halfW, drawLenM, drawWidthM));
        g2d.draw(new Line2D.Double(0, -halfW, 0, halfW));

        // Center circle (3m radius)
        double R_center = 3.0 * pitchScale;
        g2d.fill(new Ellipse2D.Double(-0.3, -0.3, 0.6, 0.6));
        if (R_center > 0.5) {
            g2d.draw(new Ellipse2D.Double(-R_center, -R_center, 2 * R_center, 2 * R_center));
        }

        for (int side : new int[]{-1, 1}) {
            double goalLineX = side * halfL;
            double dir = -side;

            // Penalty area (6m radius arcs from goal posts 3.16m apart)
            double r = 6.0 * pitchScale;
            double halfGoalWidth = 1.58 * pitchScale;

            // Arcs from goal posts
            g2d.draw(new Arc2D.Double(goalLineX - r, -halfGoalWidth - r, 2 * r, 2 * r, (side > 0 ? 90 : 0), 90, Arc2D.OPEN));
            g2d.draw(new Arc2D.Double(goalLineX - r, halfGoalWidth - r, 2 * r, 2 * r, (side > 0 ? 180 : 270), 90, Arc2D.OPEN));

            // Connecting line (3m long)
            g2d.draw(new Line2D.Double(goalLineX + dir * r, -halfGoalWidth, goalLineX + dir * r, halfGoalWidth));

            // Penalty spot (6m)
            double spot6 = goalLineX + dir * 6.0 * pitchScale;
            g2d.fill(new Ellipse2D.Double(spot6 - 0.25, -0.25, 0.5, 0.5));

            // Second penalty spot (10m)
            double spot10 = goalLineX + dir * 10.0 * pitchScale;
            g2d.fill(new Ellipse2D.Double(spot10 - 0.25, -0.25, 0.5, 0.5));
        }

        // Substitution zones (5m wide, 5m from center line)
        double zoneStart = 5.0 * pitchScale;
        double zoneEnd = 10.0 * pitchScale;
        double markLen = 0.8 * pitchScale;
        for (int sideX : new int[]{-1, 1}) {
            for (int sideY : new int[]{-1, 1}) {
                double x1 = sideX * zoneStart;
                double x2 = sideX * zoneEnd;
                double yBase = sideY * halfW;
                g2d.draw(new Line2D.Double(x1, yBase - sideY * markLen / 2.0, x1, yBase + sideY * markLen / 2.0));
                g2d.draw(new Line2D.Double(x2, yBase - sideY * markLen / 2.0, x2, yBase + sideY * markLen / 2.0));
            }
        }

        // Corner arcs (0.25m radius)
        double R_corner = 0.25 * pitchScale;
        g2d.draw(new Arc2D.Double(-halfL - R_corner, -halfW - R_corner, 2 * R_corner, 2 * R_corner, 270, 90, Arc2D.OPEN));
        g2d.draw(new Arc2D.Double(halfL - R_corner, -halfW - R_corner, 2 * R_corner, 2 * R_corner, 180, 90, Arc2D.OPEN));
        g2d.draw(new Arc2D.Double(halfL - R_corner, halfW - R_corner, 2 * R_corner, 2 * R_corner, 90, 90, Arc2D.OPEN));
        g2d.draw(new Arc2D.Double(-halfL - R_corner, halfW - R_corner, 2 * R_corner, 2 * R_corner, 0, 90, Arc2D.OPEN));

        g2d.setTransform(oldTransform);
    }

    /**
     * Find the coordinate system for inscribed rectangle
     */
    private static Point2D findUVForInscribedRectangle(Contour contour) {
        // Use the longest edge among all outer rings for the main axis
        double maxEdgeLenSq = -1;
        Point2D axisStart = null;
        Point2D axisEnd = null;

        for (var ring : contour.outerRings) {
            for (int i = 0; i < ring.size(); i++) {
                var p1 = ring.get(i);
                var p2 = ring.get((i + 1) % ring.size());
                double dx = p2.x - p1.x;
                double dy = p2.y - p1.y;
                double lenSq = dx * dx + dy * dy;
                if (lenSq > maxEdgeLenSq) {
                    maxEdgeLenSq = lenSq;
                    axisStart = p1;
                    axisEnd = p2;
                }
            }
        }
        if (axisStart == null) {
            return null;
        }

        double lenM = Math.sqrt(maxEdgeLenSq);
        double ux = (axisEnd.x - axisStart.x) / lenM;
        double uy = (axisEnd.y - axisStart.y) / lenM;

        return new Point2D(ux, uy);
    }

}
