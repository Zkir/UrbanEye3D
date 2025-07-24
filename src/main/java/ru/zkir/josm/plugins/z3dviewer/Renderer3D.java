package ru.zkir.josm.plugins.z3dviewer;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUtessellator;
import com.jogamp.opengl.glu.GLUtessellatorCallbackAdapter;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.NavigatableComponent;

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

    // Sun direction (normalized)
    private final RenderableBuildingElement.Point3D SUN_DIRECTION = new RenderableBuildingElement.Point3D(0.5, 0.5, 1.0).normalize();


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
                if (lastMousePoint == null) {
                    return;
                }
                int dx = e.getX() - lastMousePoint.x;
                int dy = e.getY() - lastMousePoint.y;

                if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
                    // Pan the map, taking camera orientation into account
                    if (MainApplication.getMap() != null && MainApplication.getMap().mapView != null) {
                        NavigatableComponent nc = MainApplication.getMap().mapView;
                        EastNorth center = nc.getCenter();

                        // Panning sensitivity based on 3D camera distance.
                        double panSensitivity = cam_dist * 0.002; // Magic number, may require tuning.

                        // Rotate the pan vector to align with the map's East-North coordinates.
                        double angleRad = Math.toRadians(camY_angle + 90); // +90 degree offset for default camera view
                        double cosAngle = Math.cos(angleRad);
                        double sinAngle = Math.sin(angleRad);

                        // To make the scene follow the cursor, the map must move in the opposite direction of the drag.
                        // The screen's Y-axis is inverted relative to North, so the base vector is (-dx, dy).
                        double panEast = (-dx * cosAngle) - (dy * sinAngle);
                        double panNorth = (-dx * sinAngle) + (dy * cosAngle);

                        // Add the calculated pan vector to the current map center.
                        double newEast = center.east() + panEast * panSensitivity;
                        double newNorth = center.north() + panNorth * panSensitivity;

                        nc.zoomTo(new EastNorth(newEast, newNorth));
                    }
                } else { // Assume left button for rotation
                    camY_angle -= dx * 0.5;
                    camX_angle += dy * 0.5;
                    camX_angle = Math.max(-0.0, Math.min(89.0, camX_angle));
                }

                lastMousePoint = e.getPoint();
                repaint();
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

    private Color applyLighting(Color baseColor, double dotProduct) {
        // 70% ambient light + 30% diffuse light from the sun
        // We clamp the dot product to 0 so that faces pointing away from the light aren't darkened
        double diffuseFactor = Math.abs(dotProduct);
        float factor = (float) (0.5 + 0.5 * diffuseFactor);

        // Ensure the factor does not exceed 1.0
        factor = Math.min(1.0f, factor);

        return new Color(
                (int) (baseColor.getRed() * factor),
                (int) (baseColor.getGreen() * factor),
                (int) (baseColor.getBlue() * factor)
        );
    }


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
            double roofHeight = building.roofHeight;
            double wallHeight = height - roofHeight;
            List<RenderableBuildingElement.Point3D> basePoints = building.getContour();

            // Draw walls
            gl.glBegin(GL2.GL_QUADS);
            for (int i = 0; i < basePoints.size(); i++) {
                RenderableBuildingElement.Point3D p1 = basePoints.get(i);
                RenderableBuildingElement.Point3D p2 = basePoints.get((i + 1) % basePoints.size());

                // Calculate wall normal
                RenderableBuildingElement.Point3D wallNormal = new RenderableBuildingElement.Point3D(p2.y - p1.y, p1.x - p2.x, 0).normalize();
                double dotProduct = wallNormal.dot(SUN_DIRECTION);
                Color litWallColor = applyLighting(building.color, dotProduct);
                Color darkerLitWallColor = litWallColor.darker();

                // Top vertices get the calculated lit color
                gl.glColor3f(litWallColor.getRed() / 255.0f, litWallColor.getGreen() / 255.0f, litWallColor.getBlue() / 255.0f);
                gl.glVertex3d(p1.x, p1.y, wallHeight);
                gl.glVertex3d(p2.x, p2.y, wallHeight);

                // Bottom vertices get a darker version for the Fake AO effect
                gl.glColor3f(darkerLitWallColor.getRed() / 255.0f, darkerLitWallColor.getGreen() / 255.0f, darkerLitWallColor.getBlue() / 255.0f);
                gl.glVertex3d(p2.x, p2.y, minHeight);
                gl.glVertex3d(p1.x, p1.y, minHeight);
            }
            gl.glEnd();


            // Draw roof
            if ("pyramidal".equals(building.roofShape)) {
                // --- Pyramidal Roof ---
                // 1. Find the geometric centroid of the base polygon
                RenderableBuildingElement.Point3D center = calculateCentroid(basePoints);

                // 2. Define the apex of the pyramid at the full building height
                RenderableBuildingElement.Point3D apex = new RenderableBuildingElement.Point3D(center.x, center.y, height);

                // 3. Draw the triangular faces of the pyramid
                gl.glBegin(GL2.GL_TRIANGLES);
                for (int i = 0; i < basePoints.size(); i++) {
                    RenderableBuildingElement.Point3D p1 = basePoints.get(i);
                    RenderableBuildingElement.Point3D p2 = basePoints.get((i + 1) % basePoints.size());

                    // Base vertices of the triangle (at the top of the walls)
                    RenderableBuildingElement.Point3D baseP1 = new RenderableBuildingElement.Point3D(p1.x, p1.y, wallHeight);
                    RenderableBuildingElement.Point3D baseP2 = new RenderableBuildingElement.Point3D(p2.x, p2.y, wallHeight);

                    // Calculate normal vector for lighting
                    RenderableBuildingElement.Point3D v1 = new RenderableBuildingElement.Point3D(baseP2.x - baseP1.x, baseP2.y - baseP1.y, 0);
                    RenderableBuildingElement.Point3D v2 = new RenderableBuildingElement.Point3D(apex.x - baseP1.x, apex.y - baseP1.y, apex.z - baseP1.z);
                    RenderableBuildingElement.Point3D normal = new RenderableBuildingElement.Point3D(
                        v1.y * v2.z - v1.z * v2.y,
                        v1.z * v2.x - v1.x * v2.z,
                        v1.x * v2.y - v1.y * v2.x
                    ).normalize();

                    double dotProduct = normal.dot(SUN_DIRECTION);
                    Color litRoofColor = applyLighting(building.roofColor, dotProduct);
                    gl.glColor3f(litRoofColor.getRed() / 255.0f, litRoofColor.getGreen() / 255.0f, litRoofColor.getBlue() / 255.0f);

                    // Draw the triangle
                    gl.glVertex3d(baseP1.x, baseP1.y, baseP1.z);
                    gl.glVertex3d(baseP2.x, baseP2.y, baseP2.z);
                    gl.glVertex3d(apex.x, apex.y, apex.z);
                }
                gl.glEnd();
            } else {
                // --- Flat Roof (Default) ---
                // The top surface of the roof is at the full building height
                RenderableBuildingElement.Point3D roofNormal = new RenderableBuildingElement.Point3D(0, 0, 1);
                double roofDotProduct = roofNormal.dot(SUN_DIRECTION);
                Color litRoofColor = applyLighting(building.roofColor, roofDotProduct);
                drawPolygon(gl, basePoints, height, litRoofColor);

                // If the roof has a defined height, it's a slab. We need to draw its sides (fascia).
                if (building.roofHeight > 0) {
                    gl.glBegin(GL2.GL_QUADS);
                    for (int i = 0; i < basePoints.size(); i++) {
                        RenderableBuildingElement.Point3D p1 = basePoints.get(i);
                        RenderableBuildingElement.Point3D p2 = basePoints.get((i + 1) % basePoints.size());

                        // Calculate normal for the fascia wall
                        RenderableBuildingElement.Point3D wallNormal = new RenderableBuildingElement.Point3D(p2.y - p1.y, p1.x - p2.x, 0).normalize();
                        double wallDotProduct = wallNormal.dot(SUN_DIRECTION);
                        Color litFasciaColor = applyLighting(building.roofColor, wallDotProduct); // Use roof color for fascia

                        gl.glColor3f(litFasciaColor.getRed() / 255.0f, litFasciaColor.getGreen() / 255.0f, litFasciaColor.getBlue() / 255.0f);

                        // Top vertices of the fascia quad
                        gl.glVertex3d(p1.x, p1.y, height);
                        gl.glVertex3d(p2.x, p2.y, height);

                        // Bottom vertices of the fascia quad (top of the main walls)
                        gl.glVertex3d(p2.x, p2.y, wallHeight);
                        gl.glVertex3d(p1.x, p1.y, wallHeight);
                    }
                    gl.glEnd();
                }
            }


            // Draw floor
            if (building.minHeight > 0) {
                // Assuming floor is not lit
                drawPolygon(gl, basePoints, building.minHeight, building.color.darker());
            }
        }
        gl.glFlush();
    }

    private RenderableBuildingElement.Point3D calculateCentroid(List<RenderableBuildingElement.Point3D> points) {
        double signedArea = 0.0;
        double cx = 0.0;
        double cy = 0.0;

        for (int i = 0; i < points.size(); i++) {
            RenderableBuildingElement.Point3D p1 = points.get(i);
            RenderableBuildingElement.Point3D p2 = points.get((i + 1) % points.size());
            double crossProduct = (p1.x * p2.y) - (p2.x * p1.y);
            signedArea += crossProduct;
            cx += (p1.x + p2.x) * crossProduct;
            cy += (p1.y + p2.y) * crossProduct;
        }
        signedArea *= 0.5;

        if (Math.abs(signedArea) < 1e-9) {
            // Fallback for zero-area polygons (collinear points) to simple average
            cx = 0;
            cy = 0;
            for (RenderableBuildingElement.Point3D p : points) {
                cx += p.x;
                cy += p.y;
            }
            cx /= points.size();
            cy /= points.size();
        } else {
            cx /= (6.0 * signedArea);
            cy /= (6.0 * signedArea);
        }
        return new RenderableBuildingElement.Point3D(cx, cy, 0);
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
