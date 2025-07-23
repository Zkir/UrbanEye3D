package ru.zkir.josm.plugins.z3dviewer;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.glu.GLU;
import org.openstreetmap.josm.gui.MainApplication;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Renderer3D extends GLJPanel implements GLEventListener {
    private final List<RenderableBuildingElement> buildings;
    private final GLU glu = new GLU();

    private double camX_angle = 35; //this is rather Z-angle (in vertical plane)
    private double camY_angle = -90; // x and y mixed, but it is not a problem yet.
    private double cam_dist = 500.0;

    private Point lastMousePoint;

    public Renderer3D(List<RenderableBuildingElement> buildings) {
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

        if ( buildings == null || buildings.isEmpty()) {
            return;
        }

        // --- Camera Setup (Z-up) ---
        double camX_rad = Math.toRadians(camX_angle);
        double camY_rad = Math.toRadians(camY_angle);

        double eyeX = cam_dist * Math.cos(camX_rad) * Math.cos(camY_rad);
        double eyeY = cam_dist * Math.cos(camX_rad) * Math.sin(camY_rad);
        double eyeZ = cam_dist * Math.sin(camX_rad);

        glu.gluLookAt(eyeX, eyeY, eyeZ, 0, 0, 0, 0, 0, 1);

        // --- Render buildings ---
        for (RenderableBuildingElement building : buildings) {
            double height = building.height;
            double minHeight = building.minHeight;
            List<RenderableBuildingElement.Point3D> basePoints = building.getContour();

            // Draw walls
            gl.glBegin(GL2.GL_QUAD_STRIP);
            Color wallColor = building.color;
            Color darkerWallColor = wallColor.darker();
            for (int i = 0; i <= basePoints.size(); i++) {
                RenderableBuildingElement.Point3D p = basePoints.get(i % basePoints.size());
                gl.glColor3f(wallColor.getRed() / 255.0f, wallColor.getGreen() / 255.0f, wallColor.getBlue() / 255.0f);
                gl.glVertex3d(p.x, p.y, height); // Use p.y, and height for z
                gl.glColor3f(darkerWallColor.getRed() / 255.0f, darkerWallColor.getGreen() / 255.0f, darkerWallColor.getBlue() / 255.0f);
                gl.glVertex3d(p.x, p.y, minHeight); // Use p.y, and p.z (minHeight) for z
            }
            gl.glEnd();

            // Draw roof
            gl.glBegin(GL2.GL_POLYGON);// TODO: GL_POLYGON draws CONVEX polygon, which is not always the case!!!
            gl.glColor3f(building.roofColor.getRed() / 255.0f, building.roofColor.getGreen() / 255.0f, building.roofColor.getBlue() / 255.0f);
            for (RenderableBuildingElement.Point3D p : basePoints) {
                gl.glVertex3d(p.x, p.y, height); // Use p.y, and height for z
            }
            gl.glEnd();
        }
        gl.glFlush();
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
