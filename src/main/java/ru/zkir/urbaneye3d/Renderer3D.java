package ru.zkir.urbaneye3d;

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
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.data.coor.LatLon;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point3D;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.List;

public class Renderer3D extends GLJPanel implements GLEventListener {
    private final List<RenderableBuildingElement> buildings;
    private final GLU glu = new GLU();
    public boolean isWireframeMode;

    private double camX_angle = 35; //this is rather Z-angle (in vertical plane)
    private double camY_angle = -90; // x and y mixed, but it is not a problem yet.
    private double cam_dist = 500.0;

    private Point lastMousePoint;

    // Sun direction (normalized)
    private final Point3D SUN_DIRECTION = new Point3D(0.5, 0.5, 1.0).normalize();


    public Renderer3D( Scene scene) {
        this.buildings = scene.renderableElements;
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
    public void toggleWireframeMode() {
        isWireframeMode = !isWireframeMode;
        Config.getPref().putBoolean("urbaneye3d.wireframe.enabled", isWireframeMode);
    }


    @Override
    public void display(GLAutoDrawable glAutoDrawable) {
        GL2 gl = glAutoDrawable.getGL().getGL2();

        isWireframeMode = Config.getPref().getBoolean("urbaneye3d.wireframe.enabled", false);

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
            gl.glPushMatrix();
            LatLon mapCenter = MainApplication.getMap().mapView.getProjection().eastNorth2latlon(MainApplication.getMap().mapView.getCenter());
            double dx = building.origin.lon() - mapCenter.lon();
            double dy = building.origin.lat() - mapCenter.lat();
            double transX = dx * Math.cos(Math.toRadians(mapCenter.lat())) * 111320.0;
            double transY = dy * 111320.0;
            gl.glTranslated(transX, transY, 0);

            Mesh buildingMesh = building.getMesh();

            if (buildingMesh != null ){
                //in normal circumstances we should be able to compose mesh for building.
                //so we can render mesh directly.

                // Draw wall faces
                for (int[] face : buildingMesh.wallFaces) {
                    drawPolygon(gl, building, face, building.color);
                }

                // Draw roof faces
                for (int[] face : buildingMesh.roofFaces) {
                    drawPolygon(gl, building, face, building.roofColor);
                }
                // Draw bottom faces
                for (int[] face : buildingMesh.bottomFaces) {
                    drawPolygon(gl, building, face, building.bottomColor );
                }
            }

            gl.glPopMatrix();
        }
        gl.glFlush();
    }


    private void drawPolygon(GL2 gl, RenderableBuildingElement building, int[] faceIndices, Color color) {
        if (faceIndices.length < 3) return;
        List<Point3D> vertices = building.getMesh().verts;
        // Calculate face normal for lighting
        Point3D p1 = vertices.get(faceIndices[0]);
        Point3D p2 = vertices.get(faceIndices[1]);
        Point3D p3 = vertices.get(faceIndices[2]);

        Point3D v1 = new Point3D(p2.x - p1.x, p2.y - p1.y, p2.z - p1.z);
        Point3D v2 = new Point3D(p3.x - p1.x, p3.y - p1.y, p3.z - p1.z);
        Point3D normal = new Point3D(
                v1.y * v2.z - v1.z * v2.y,
                v1.z * v2.x - v1.x * v2.z,
                v1.x * v2.y - v1.y * v2.x
        ).normalize();

        double dotProduct = normal.dot(SUN_DIRECTION);
        Color litColor = applyLighting(color, dotProduct);

        if (isWireframeMode) {
            gl.glBegin(GL2.GL_LINE_LOOP);
            gl.glColor3f(litColor.getRed() / 255.0f, litColor.getGreen() / 255.0f, litColor.getBlue() / 255.0f);
            for (int index : faceIndices) {
                Point3D p = vertices.get(index);
                gl.glVertex3d(p.x, p.y, p.z);
            }
            gl.glEnd();
        } else if (faceIndices.length == 4) {
            gl.glBegin(GL2.GL_QUADS);
            Point3D p4 = vertices.get(faceIndices[3]);

            drawVertexWithFakeAO(gl, p1, litColor, building);
            drawVertexWithFakeAO(gl, p2, litColor, building);
            drawVertexWithFakeAO(gl, p3, litColor, building);
            drawVertexWithFakeAO(gl, p4, litColor, building);

            gl.glEnd();

        } else {
            // Use tessellator for all polygons to handle non-convex cases correctly.
            GLUtessellator tess = glu.gluNewTess();
            TessellatorCallback callback = new TessellatorCallback(gl, glu, litColor, building);

            glu.gluTessCallback(tess, GLU.GLU_TESS_VERTEX_DATA, callback);
            glu.gluTessCallback(tess, GLU.GLU_TESS_BEGIN, callback);
            glu.gluTessCallback(tess, GLU.GLU_TESS_END, callback);
            glu.gluTessCallback(tess, GLU.GLU_TESS_ERROR, callback);
            glu.gluTessCallback(tess, GLU.GLU_TESS_COMBINE_DATA, callback);

            glu.gluTessProperty(tess, GLU.GLU_TESS_WINDING_RULE, GLU.GLU_TESS_WINDING_ODD);

            glu.gluTessBeginPolygon(tess, null);
            glu.gluTessBeginContour(tess);

            for (int index : faceIndices) {
                Point3D p = vertices.get(index);
                double[] vertexData = {p.x, p.y, p.z};
                glu.gluTessVertex(tess, vertexData, 0, p);
            }

            glu.gluTessEndContour(tess);
            glu.gluTessEndPolygon(tess);
            glu.gluDeleteTess(tess);
        }
    }

    private void drawVertexWithFakeAO(GL2 gl, Point3D vertex, Color baseColor, RenderableBuildingElement building) {
        double totalHeight = building.height - building.minHeight;
        double vertexHeight = vertex.z - building.minHeight;
        float aoFactor = 1.0f;
        if (totalHeight > 0.1) { // Avoid division by zero
            aoFactor = 0.6f + 0.4f * (float)(vertexHeight / totalHeight);
        }

        Color finalColor = new Color(
                (int)(baseColor.getRed() * aoFactor),
                (int)(baseColor.getGreen() * aoFactor),
                (int)(baseColor.getBlue() * aoFactor)
        );

        gl.glColor3f(finalColor.getRed() / 255.0f, finalColor.getGreen() / 255.0f, finalColor.getBlue() / 255.0f);
        gl.glVertex3d(vertex.x, vertex.y, vertex.z);
    }

    // Tessellator callback inner class
    private class TessellatorCallback extends GLUtessellatorCallbackAdapter {
        private final GL2 gl;
        private final GLU glu;
        private final Color baseColor;
        private final RenderableBuildingElement building;

        public TessellatorCallback(GL2 gl, GLU glu, Color baseColor, RenderableBuildingElement building) {
            this.gl = gl;
            this.glu = glu;
            this.baseColor = baseColor;
            this.building = building;
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
            if (vertexData instanceof Point3D) {
                drawVertexWithFakeAO(gl, (Point3D) vertexData, baseColor, building);
            }
        }

        @Override
        public void combine(double[] coords, Object[] data, float[] weight, Object[] outData) {
            Point3D newVertex = new Point3D(coords[0], coords[1], coords[2]);
            outData[0] = newVertex;
        }

        @Override
        public void vertexData(Object vertexData, Object polygonData) {
            if (vertexData instanceof Point3D) {
                drawVertexWithFakeAO(gl, (Point3D) vertexData, baseColor, building);
            }
        }

        @Override
        public void error(int errnum) {
            //TODO: uncomment when some way to output more specific error, including object id is found.
            //UrbanEye3dPlugin.debugMsg("Tessellation Error (" + errnum + "): " + glu.gluErrorString(errnum));
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
        glu.gluPerspective(45.0, aspect, 10.0, 5000.0);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glLoadIdentity();
    }
}