package ru.zkir.josm.plugins.z3dviewer;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.glu.GLU;
import org.openstreetmap.josm.data.osm.Node;

import java.awt.Color;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;

import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Renderer3D extends GLJPanel implements GLEventListener {
    private final List<Z3dViewerDialog.Building> buildings;
    private final GLU glu = new GLU();

    private double camX_angle = 30.0;
    private double camY_angle = -45.0;
    private double cam_dist = 500.0;

    private Point lastMousePoint;

    private static class Point3D {
        double x, y, z;
        Point3D(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    }

    private static class DrawableBuilding {
        Z3dViewerDialog.Building building;
        double distanceToCamera;

        DrawableBuilding(Z3dViewerDialog.Building building, double distance) {
            this.building = building;
            this.distanceToCamera = distance;
        }
    }

    public Renderer3D(List<Z3dViewerDialog.Building> buildings) {
        this.buildings = buildings;
        this.addGLEventListener(this);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastMousePoint = e.getPoint();
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (lastMousePoint != null) {
                    int dx = e.getX() - lastMousePoint.x;
                    int dy = e.getY() - lastMousePoint.y;

                    camY_angle -= dx * 0.5;
                    camX_angle += dy * 0.5;

                    camX_angle = Math.max(-89.0, Math.min(89.0, camX_angle));

                    lastMousePoint = e.getPoint();
                    repaint();
                }
            }
        });

        addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                cam_dist += e.getWheelRotation() * 50;
                cam_dist = Math.max(50.0, cam_dist); // Prevent zooming too close
                repaint();
            }
        });
    }

    @Override
    public void init(GLAutoDrawable glAutoDrawable) {
        GL2 gl = glAutoDrawable.getGL().getGL2();
        gl.glClearColor(1.0f, 1.0f, 1.0f, 1.0f); // White background
        gl.glEnable(GL2.GL_DEPTH_TEST);
        //gl.glEnable(GL2.GL_CULL_FACE);
        //gl.glCullFace(GL2.GL_BACK);
    }

    @Override
    public void dispose(GLAutoDrawable glAutoDrawable) {}

    @Override
    public void display(GLAutoDrawable glAutoDrawable) {
        GL2 gl = glAutoDrawable.getGL().getGL2();
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
        gl.glLoadIdentity();

        MapView mapView = MainApplication.getMap().mapView;
        if (mapView == null || buildings == null || buildings.isEmpty()) {
            return;
        }

        // --- Camera Setup ---
        double camX_rad = Math.toRadians(camX_angle);
        double camY_rad = Math.toRadians(camY_angle);

        double eyeX = cam_dist * Math.cos(camX_rad) * Math.sin(camY_rad);
        double eyeY = cam_dist * Math.sin(camX_rad);
        double eyeZ = cam_dist * Math.cos(camX_rad) * Math.cos(camY_rad);

        glu.gluLookAt(eyeX, eyeY, eyeZ, 0, 0, 0, 0, 1, 0);

        // --- Prepare buildings for rendering ---
        Point center = mapView.getPoint(mapView.getCenter());
        List<DrawableBuilding> drawableBuildings = new ArrayList<>();
        for (Z3dViewerDialog.Building building : buildings) {
            if (building.way.getNodesCount() < 2) continue;
            
            double centerX = 0, centerZ = 0;
            for (Node node : building.way.getNodes()) {
                Point p = mapView.getPoint(node.getCoor());
                centerX += p.x - center.x;
                centerZ += p.y - center.y;
            }
            centerX /= building.way.getNodesCount();
            centerZ /= building.way.getNodesCount();
            
            double dist = Math.sqrt(Math.pow(centerX - eyeX, 2) + Math.pow(centerZ - eyeZ, 2));
            drawableBuildings.add(new DrawableBuilding(building, dist));
        }

        drawableBuildings.sort(Comparator.comparingDouble(b -> -b.distanceToCamera));

        // --- Render buildings ---
        for (DrawableBuilding drawableBuilding : drawableBuildings) {
            Way way = drawableBuilding.building.way;
            double height = drawableBuilding.building.height;
            double minHeight = drawableBuilding.building.minHeight;

            List<Point3D> basePoints = new ArrayList<>();
            for (Node node : way.getNodes()) {
                Point p = mapView.getPoint(node.getCoor());
                basePoints.add(new Point3D(p.x - center.x, minHeight, p.y - center.y));
            }

            // Draw walls
            gl.glBegin(GL2.GL_QUAD_STRIP);
            Color wallColor = ColorUtils.parseColor(drawableBuilding.building.color);
            if (wallColor == null) {
                wallColor = new Color(204, 204, 204); // Default light gray (0.8f)
            }
            Color darkerWallColor = wallColor.darker();
            for (int i = 0; i <= basePoints.size(); i++) {
                Point3D p = basePoints.get(i % basePoints.size());
                gl.glColor3f(wallColor.getRed() / 255.0f, wallColor.getGreen() / 255.0f, wallColor.getBlue() / 255.0f);
                gl.glVertex3d(p.x, height, p.z);
                gl.glColor3f(darkerWallColor.getRed() / 255.0f, darkerWallColor.getGreen() / 255.0f, darkerWallColor.getBlue() / 255.0f);
                gl.glVertex3d(p.x, minHeight, p.z);
            }
            gl.glEnd();

            // Draw roof
            gl.glBegin(GL2.GL_POLYGON);
            setColor(gl, drawableBuilding.building.roofColor, 0.9f, 0.9f, 0.9f);
            for (Point3D p : basePoints) {
                gl.glVertex3d(p.x, height, p.z);
            }
            gl.glEnd();
        }
        gl.glFlush();
    }

    private void setColor(GL2 gl, String colorStr, float defaultR, float defaultG, float defaultB) {
        Color color = ColorUtils.parseColor(colorStr);
        if (color != null) {
            gl.glColor3f(color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f);
        } else {
            gl.glColor3f(defaultR, defaultG, defaultB);
        }
    }

    @Override
    public void reshape(GLAutoDrawable glAutoDrawable, int x, int y, int width, int height) {
        GL2 gl = glAutoDrawable.getGL().getGL2();
        if (height <= 0) height = 1;
        float aspect = (float) width / (float) height;
        gl.glViewport(0, 0, width, height);
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();
        glu.gluPerspective(45.0, aspect, 1.0, 10000.0);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glLoadIdentity();
    }
}
