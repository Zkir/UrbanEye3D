package ru.zkir.josm.plugins.z3dviewer;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUtessellator;
import com.jogamp.opengl.glu.GLUtessellatorCallbackAdapter;
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

                    camX_angle = Math.max(-0.0, Math.min(89.0, camX_angle));

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

            // Draw roof using tessellation for non-convex polygons
            drawPolygon(gl, basePoints, height, building.roofColor);

            // Draw floor
            if (building.minHeight > 0) {
                drawPolygon(gl, basePoints, building.minHeight, building.color.darker());
            }
        }
        gl.glFlush();
    }

    private void drawPolygon(GL2 gl, List<RenderableBuildingElement.Point3D> points, double z, Color color) {
        GLUtessellator tess = glu.gluNewTess();
        TessellatorCallback callback = new TessellatorCallback(gl, glu);

        glu.gluTessCallback(tess, GLU.GLU_TESS_VERTEX, callback);
        glu.gluTessCallback(tess, GLU.GLU_TESS_BEGIN, callback);
        glu.gluTessCallback(tess, GLU.GLU_TESS_END, callback);
        glu.gluTessCallback(tess, GLU.GLU_TESS_ERROR, callback);
        glu.gluTessCallback(tess, GLU.GLU_TESS_COMBINE, callback);

        gl.glColor3f(color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f);

        // Set winding rule to correctly handle complex polygons
        glu.gluTessProperty(tess, GLU.GLU_TESS_WINDING_RULE, GLU.GLU_TESS_WINDING_ODD);

        glu.gluTessBeginPolygon(tess, null);
        glu.gluTessBeginContour(tess);

        for (RenderableBuildingElement.Point3D p : points) {
            double[] vertex = {p.x, p.y, z};
            glu.gluTessVertex(tess, vertex, 0, vertex);
        }

        glu.gluTessEndContour(tess);
        glu.gluTessEndPolygon(tess);
        glu.gluDeleteTess(tess);
    }

    // Tessellator callback inner class
    private static class TessellatorCallback extends GLUtessellatorCallbackAdapter {
        private final GL2 gl;
        private final GLU glu;

        public TessellatorCallback(GL2 gl, GLU glu) {
            this.gl = gl;
            this.glu = glu;
        }

        @Override
        public void begin(int type) {
            gl.glBegin(type);
        }

        @Override
        public void end() {
            gl.glEnd();
        }

        @Override
        public void vertex(Object vertexData) {
            if (vertexData instanceof double[]) {
                gl.glVertex3dv((double[]) vertexData, 0);
            }
        }

        @Override
        public void combine(double[] coords, Object[] data, float[] weight, Object[] outData) {
            double[] vertex = new double[3];
            vertex[0] = coords[0];
            vertex[1] = coords[1];
            vertex[2] = coords[2];
            outData[0] = vertex;
        }

        @Override
        public void error(int errnum) {
            System.err.println("Tessellation Error (" + errnum + "): " + glu.gluErrorString(errnum));
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
