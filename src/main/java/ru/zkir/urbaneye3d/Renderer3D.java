package ru.zkir.urbaneye3d;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUtessellator;
import com.jogamp.opengl.glu.GLUtessellatorCallbackAdapter;
import org.openstreetmap.josm.data.Bounds;
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

import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.debugMsg;

public class Renderer3D extends GLJPanel implements GLEventListener {
    private final Scene scene;
    private final GLU glu = new GLU();
    private final double CUTOFF_DISTANCE=5000.0;
    public boolean isWireframeMode;
    public boolean isFakeAOEnabled;

    private final double DEFAULT_CAM_VERT_ANGLE = 35;
    private final double DEFAULT_CAM_HOR_ANGLE = -90;

    private double camX_angle = DEFAULT_CAM_VERT_ANGLE; //this is rather Z-angle (in vertical plane)
    private double camY_angle = DEFAULT_CAM_HOR_ANGLE; // x and y mixed, but it is not a problem yet.
    private double cam_dist = 500.0;

    private Point lastMousePoint;

    private boolean npotSupport = true;

    public double getCamX_angle() {
        return camX_angle;
    }

    public double getCamY_angle() {
        return camY_angle;
    }

    public double getCam_dist() {
        return cam_dist;
    }

    // Sun direction (normalized)
    private final Point3D SUN_DIRECTION = new Point3D(0.5, 0.5, 1.0).normalize();


    public Renderer3D( Scene scene) {
        this.scene = scene;
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
                cam_dist += e.getWheelRotation() * cam_dist/10;
                cam_dist = Math.max(25.0, cam_dist); // Prevent zooming too close
                cam_dist = Math.min(CUTOFF_DISTANCE*0.9, cam_dist); // Prevent zooming too far (limited by cut-off distance).
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
        CheckOpenGL(gl);
    }

    public void CheckOpenGL(GL2 gl) {

        String glVersion = gl.glGetString(GL.GL_VERSION);
        String glVendor = gl.glGetString(GL.GL_VENDOR);
        String glRenderer = gl.glGetString(GL.GL_RENDERER);
        String extensions = gl.glGetString(GL.GL_EXTENSIONS);

        // Check NPOT support
        // For desktop OpenGL (usually versions 2.1 and above )
        if (extensions.contains("GL_ARB_texture_non_power_of_two")) {
            npotSupport = true;
        }
        // For OpenGL ES 2.0 (mobile or embedded systems)
        else if (extensions.contains("GL_OES_texture_npot")) {
            npotSupport = true;
        }

        // Check max texture size.
        int[] maxSize = new int[1];
        gl.glGetIntegerv(GL.GL_MAX_TEXTURE_SIZE, maxSize, 0);
        int intMaxTextureSize = maxSize[0];
        boolean hiResTextures = intMaxTextureSize >= 4096;

        if (!npotSupport || !hiResTextures){
            debugMsg("There are problems with OpenGL drivers: ");
            if (!npotSupport) {
                debugMsg("NPOT textures are not supported"); //non_power_of_two
            }
            if (!hiResTextures) {
                debugMsg("Hires textures are not supported"); //non_power_of_two
            }

            debugMsg("OpenGL version: " + glVersion);
            debugMsg("Hardware manufacturer " + glVendor);
            debugMsg("Video card: " + glRenderer);
            debugMsg("Available extensions: " + extensions);
        }
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

    public void toggleFakeAO() {
        isFakeAOEnabled = !isFakeAOEnabled;
        Config.getPref().putBoolean("urbaneye3d.fakeao.enabled", isFakeAOEnabled);
    }

    public void resetCameraToNorth(){
        camX_angle = DEFAULT_CAM_VERT_ANGLE;
        camY_angle = DEFAULT_CAM_HOR_ANGLE;
        repaint();
    }
    public static LatLon getCameraPosition() {
        return MainApplication.getMap().mapView.getRealBounds().getCenter();
    }

    @Override
    public void display(GLAutoDrawable glAutoDrawable) {
        GL2 gl = glAutoDrawable.getGL().getGL2();

        isWireframeMode = Config.getPref().getBoolean("urbaneye3d.wireframe.enabled", false);
        isFakeAOEnabled = Config.getPref().getBoolean("urbaneye3d.fakeao.enabled", true);

        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
        gl.glLoadIdentity();

        // --- Camera Setup (Z-up) ---
        double camX_rad = Math.toRadians(camX_angle);
        double camY_rad = Math.toRadians(camY_angle);

        double eyeX = cam_dist * Math.cos(camX_rad) * Math.cos(camY_rad);
        double eyeY = cam_dist * Math.cos(camX_rad) * Math.sin(camY_rad);
        double eyeZ = cam_dist * Math.sin(camX_rad);

        glu.gluLookAt(eyeX, eyeY, eyeZ, 0, 0, 0, 0, 0, 1);

        // --- Render Ground Plane Tiles ---
        LatLon mapCenter = getCameraPosition();
        for (GroundTile tile : scene.getGroundPlane().getActiveTiles()) {
            gl.glPushMatrix();
            LatLon tileCenter = tile.bounds.getCenter();
            double dx = tileCenter.lon() - mapCenter.lon();
            double dy = tileCenter.lat() - mapCenter.lat();
            double transX = dx * Math.cos(Math.toRadians(mapCenter.lat())) * 111320.0;
            double transY = dy * 111320.0;
            gl.glTranslated(transX, transY, 0);
            tile.render(gl);
            gl.glPopMatrix();
        }

        if ( scene.renderableElements == null || scene.renderableElements.isEmpty()) {
            gl.glFlush();
            return;
        }

        //this may seem to be a circular definition, but what we actually want
        // is to render buildings in the same area as ground tiles.
        Bounds visibleArea = this.scene.getVisibleArea();

        // --- Render buildings ---
        for (RenderableBuildingElement building : scene.renderableElements) {
            if (!visibleArea.contains(building.origin)){
                continue;
            }
            gl.glPushMatrix();
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
        final float AO_STRENGTH;
        if (isFakeAOEnabled) {
            AO_STRENGTH=0.3f;
            //Fake ambient occlusion is applied to each part individually.
            //This way parts become more visible and interestingly looking.
            //however, tiger stripes effect occurs, if parts are lying on each other
        }else {
            AO_STRENGTH=0.0f;
        }

        float aoFactor = 1.0f;
        if (totalHeight > 0.1) { // Avoid division by zero
            aoFactor = (1-AO_STRENGTH) + AO_STRENGTH * (float)(vertexHeight / totalHeight);
        }

        if (aoFactor<0) {
            aoFactor=0;
            //TODO: this can happen when building height/min_height is not calculated properly. solution: use actual values from mesh.
        }

        if (aoFactor>1) {
            aoFactor=1;
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
        glu.gluPerspective(45.0, aspect, 10.0, CUTOFF_DISTANCE);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glLoadIdentity();
    }

    public boolean isNpotSupported() {
        return npotSupport;
    }
}