package ru.zkir.josm.plugins.z3dviewer;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class Renderer3D extends JPanel {
    private final List<Z3dViewerDialog.Building> buildings;
    private static final double SCALE = 2.0; // Let's add a scale factor

    public Renderer3D(List<Z3dViewerDialog.Building> buildings) {
        this.buildings = buildings;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        MapView mapView = MainApplication.getMap().mapView;
        if (mapView == null) {
            g.drawString("No active map view.", 10, 20);
            return;
        }

        if (buildings == null || buildings.isEmpty()) {
            g.drawString("No buildings with height found to display.", 10, 20);
            return;
        }

        Point center = mapView.getPoint(mapView.getCenter());
        int panelCenterX = getWidth() / 2;
        int panelCenterY = getHeight() / 2;

        for (Z3dViewerDialog.Building building : buildings) {
            Way way = building.way;
            double height = building.height * SCALE;

            Polygon basePolygon = new Polygon();
            for (Node node : way.getNodes()) {
                Point p = mapView.getPoint(node.getCoor());
                int x = (int) ((p.x - center.x) * SCALE) + panelCenterX;
                int y = (int) ((p.y - center.y) * SCALE) + panelCenterY;
                basePolygon.addPoint(x, y);
            }

            // Simple isometric-like projection
            int z_offset_x = (int) (height * 0.5);
            int z_offset_y = (int) (height * -0.25);

            Polygon topPolygon = new Polygon();
            for (int i = 0; i < basePolygon.npoints; i++) {
                topPolygon.addPoint(basePolygon.xpoints[i] + z_offset_x, basePolygon.ypoints[i] + z_offset_y);
            }

            // Draw walls
            g2d.setColor(new Color(210, 210, 210));
            for (int i = 0; i < basePolygon.npoints; i++) {
                int next = (i + 1) % basePolygon.npoints;
                Polygon wall = new Polygon();
                wall.addPoint(basePolygon.xpoints[i], basePolygon.ypoints[i]);
                wall.addPoint(basePolygon.xpoints[next], basePolygon.ypoints[next]);
                wall.addPoint(topPolygon.xpoints[next], topPolygon.ypoints[next]);
                wall.addPoint(topPolygon.xpoints[i], topPolygon.ypoints[i]);
                g2d.fill(wall);
            }
            
            // Draw roof
            g2d.setColor(new Color(230, 230, 230));
            g2d.fill(topPolygon);

            // Draw outlines
            g2d.setColor(Color.DARK_GRAY);
            g2d.draw(basePolygon);
            g2d.draw(topPolygon);
            for (int i = 0; i < basePolygon.npoints; i++) {
                 g2d.drawLine(basePolygon.xpoints[i], basePolygon.ypoints[i], topPolygon.xpoints[i], topPolygon.ypoints[i]);
            }
        }
    }
}