package ru.zkir.urbaneye3d;

import static org.openstreetmap.josm.tools.I18n.tr;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUtessellator;
import com.jogamp.opengl.glu.GLUtessellatorCallbackAdapter;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.awt.TextRenderer;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.NavigatableComponent;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.data.coor.LatLon;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;
import ru.zkir.urbaneye3d.utils.GeometryUtils;

import java.awt.Color;
import java.awt.Font;
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
    public boolean isStatsEnabled;

    // Frame time statistics
    private final long[] frameTimes = new long[60];
    private int frameTimeIndex = 0;
    private int frameTimeCount = 0;
    private long lastFrameTime = 0;

    private TextRenderer textRenderer;

    private final double DEFAULT_CAM_VERT_ANGLE = 35;
    private final double DEFAULT_CAM_HOR_ANGLE = -90;

    private double camX_angle = DEFAULT_CAM_VERT_ANGLE; //this is rather Z-angle (in vertical plane)
    private double camY_angle = DEFAULT_CAM_HOR_ANGLE; // x and y mixed, but it is not a problem yet.
    private double cam_dist = 500.0;

    private Point lastMousePoint;

    private boolean npotSupport = true;

    private final double[] lastProjMatrix = new double[16];
    private final double[] lastMvMatrix = new double[16];
    private final int[] lastViewport = new int[4];

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

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 1) {
                   // Picking will be handled by the parent window/dialog
                }
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

        textRenderer = new TextRenderer(new Font("SansSerif", Font.BOLD, 14));
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
    public void dispose(GLAutoDrawable glAutoDrawable) {
        GL2 gl = glAutoDrawable.getGL().getGL2();
        TextureManager.getInstance().disposeAll(gl);
        textRenderer = null;
    }

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
        int objectCount=0;
        int faceCount=0;

        long startTime = System.nanoTime();
        GL2 gl = glAutoDrawable.getGL().getGL2();

        isWireframeMode = Config.getPref().getBoolean("urbaneye3d.wireframe.enabled", false);
        isFakeAOEnabled = Config.getPref().getBoolean("urbaneye3d.fakeao.enabled", true);
        isStatsEnabled = Config.getPref().getBoolean("urbaneye3d.stats.enabled", false);

        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
        gl.glLoadIdentity();

        // --- Camera Setup (Z-up) ---
        double camX_rad = Math.toRadians(camX_angle);
        double camY_rad = Math.toRadians(camY_angle);

        double eyeX = cam_dist * Math.cos(camX_rad) * Math.cos(camY_rad);
        double eyeY = cam_dist * Math.cos(camX_rad) * Math.sin(camY_rad);
        double eyeZ = cam_dist * Math.sin(camX_rad);

        glu.gluLookAt(eyeX, eyeY, eyeZ, 0, 0, 0, 0, 0, 1);

        // Store matrices and viewport for picking
        gl.glGetIntegerv(GL2.GL_VIEWPORT, lastViewport, 0);
        gl.glGetDoublev(GL2.GL_PROJECTION_MATRIX, lastProjMatrix, 0);
        gl.glGetDoublev(GL2.GL_MODELVIEW_MATRIX, lastMvMatrix, 0);

        // --- Frustum Culling Setup ---
        double[] projMatrix = new double[16];
        double[] mvMatrix = new double[16];
        double[] clipMatrix = new double[16];
        gl.glGetDoublev(GL2.GL_PROJECTION_MATRIX, projMatrix, 0);
        gl.glGetDoublev(GL2.GL_MODELVIEW_MATRIX, mvMatrix, 0);

        // clipMatrix = mvMatrix * projMatrix (Note: OpenGL uses column-major, but let's be careful with multiplication order)
        // Standard frustum extraction expects M = P * V
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                clipMatrix[j * 4 + i] = mvMatrix[j * 4 + 0] * projMatrix[0 * 4 + i] +
                                        mvMatrix[j * 4 + 1] * projMatrix[1 * 4 + i] +
                                        mvMatrix[j * 4 + 2] * projMatrix[2 * 4 + i] +
                                        mvMatrix[j * 4 + 3] * projMatrix[3 * 4 + i];
            }
        }

        double[][] frustum = new double[6][4];
        // Extraction from clipMatrix (row-major logic for the planes)
        // Right
        frustum[0][0] = clipMatrix[3] - clipMatrix[0];
        frustum[0][1] = clipMatrix[7] - clipMatrix[4];
        frustum[0][2] = clipMatrix[11] - clipMatrix[8];
        frustum[0][3] = clipMatrix[15] - clipMatrix[12];
        // Left
        frustum[1][0] = clipMatrix[3] + clipMatrix[0];
        frustum[1][1] = clipMatrix[7] + clipMatrix[4];
        frustum[1][2] = clipMatrix[11] + clipMatrix[8];
        frustum[1][3] = clipMatrix[15] + clipMatrix[12];
        // Bottom
        frustum[2][0] = clipMatrix[3] + clipMatrix[1];
        frustum[2][1] = clipMatrix[7] + clipMatrix[5];
        frustum[2][2] = clipMatrix[11] + clipMatrix[9];
        frustum[2][3] = clipMatrix[15] + clipMatrix[13];
        // Top
        frustum[3][0] = clipMatrix[3] - clipMatrix[1];
        frustum[3][1] = clipMatrix[7] - clipMatrix[5];
        frustum[3][2] = clipMatrix[11] - clipMatrix[9];
        frustum[3][3] = clipMatrix[15] - clipMatrix[13];
        // Far
        frustum[4][0] = clipMatrix[3] - clipMatrix[2];
        frustum[4][1] = clipMatrix[7] - clipMatrix[6];
        frustum[4][2] = clipMatrix[11] - clipMatrix[10];
        frustum[4][3] = clipMatrix[15] - clipMatrix[14];
        // Near
        frustum[5][0] = clipMatrix[3] + clipMatrix[2];
        frustum[5][1] = clipMatrix[7] + clipMatrix[6];
        frustum[5][2] = clipMatrix[11] + clipMatrix[10];
        frustum[5][3] = clipMatrix[15] + clipMatrix[14];

        for (int i = 0; i < 6; i++) {
            double t = Math.sqrt(frustum[i][0] * frustum[i][0] + frustum[i][1] * frustum[i][1] + frustum[i][2] * frustum[i][2]);
            frustum[i][0] /= t;
            frustum[i][1] /= t;
            frustum[i][2] /= t;
            frustum[i][3] /= t;
        }

        // --- Render Ground Plane Tiles ---
        LatLon mapCenter = getCameraPosition();
        for (GroundTile tile : scene.getGroundPlane().getActiveTiles()) {
            LatLon tileCenter = tile.bounds.getCenter();
            double dx = tileCenter.lon() - mapCenter.lon();
            double dy = tileCenter.lat() - mapCenter.lat();
            double transX = dx * Math.cos(Math.toRadians(mapCenter.lat())) * 111320.0;
            double transY = dy * 111320.0;

            Mesh tileMesh = tile.getMesh();
            Point3D minB = tileMesh.getMinBounds();
            Point3D maxB = tileMesh.getMaxBounds();

            if (!isBoxInFrustum(frustum, minB.x + transX, minB.y + transY, minB.z, maxB.x + transX, maxB.y + transY, maxB.z)) {
                continue;
            }

            gl.glPushMatrix();
            gl.glTranslated(transX, transY, 0);
            Texture texture = tile.render(gl);
            drawMesh(gl, tileMesh, false, 0, 0, texture);
            gl.glPopMatrix();
            objectCount += 1;
            faceCount += tileMesh.faces.size();
        }

        if ( scene.renderableElements != null && !scene.renderableElements.isEmpty()) {
            //this may seem to be a circular definition, but what we actually want
            // is to render buildings in the same area as ground tiles.
            Bounds visibleArea = this.scene.getVisibleArea();

            // --- Render All Elements (Buildings, Trees, etc.) ---
            for (RenderableElement element : scene.renderableElements) {
                if (visibleArea != null && !visibleArea.contains(element.origin)) {
                    continue;
                }

                double dx = element.origin.lon() - mapCenter.lon();
                double dy = element.origin.lat() - mapCenter.lat();
                double transX = dx * Math.cos(Math.toRadians(mapCenter.lat())) * 111320.0;
                double transY = dy * 111320.0;

                Mesh mesh = element.getMesh();
                if (mesh == null) continue;

                Point3D minB = mesh.getMinBounds();
                Point3D maxB = mesh.getMaxBounds();

                if (!isBoxInFrustum(frustum, minB.x + transX, minB.y + transY, minB.z, maxB.x + transX, maxB.y + transY, maxB.z)) {
                    continue;
                }

                gl.glPushMatrix();
                gl.glTranslated(transX, transY, 0);

                if (element.textureName != null) {
                    // It's a textured object (like a tree)
                    Texture texture = TextureManager.getInstance().get(gl, element.textureName);
                    if (texture != null) {
                        drawMesh(gl, mesh, element.isSelected, 0, 0, texture);
                    }
                } else {
                    // It's a colored object (like a building)
                    drawMesh(gl, mesh, element.isSelected, element.height, element.minHeight, null);
                }
                objectCount += 1;
                faceCount += mesh.faces.size();

                gl.glPopMatrix();
            }
        }
        gl.glFlush();

        long endTime = System.nanoTime();
        long duration = endTime - startTime;

        // Update statistics
        frameTimes[frameTimeIndex] = duration;
        frameTimeIndex = (frameTimeIndex + 1) % 60;
        if (frameTimeCount < 60) {
            frameTimeCount++;
        }

        if (isStatsEnabled && textRenderer != null) {
            long sum = 0;
            for (int i = 0; i < frameTimeCount; i++) {
                sum += frameTimes[i];
            }
            double avgTimeMs = Math.ceil( (double) sum / (frameTimeCount * 1_000_000.0));
            String stats = tr("Objects: {0}, Faces: {1}, Avg frame time: {2} ms",
                    objectCount, faceCount, String.format("%.0f", avgTimeMs));

            java.awt.geom.Rectangle2D bounds = textRenderer.getBounds(stats);

            // Draw semi-transparent background panel
            gl.glMatrixMode(GL2.GL_PROJECTION);
            gl.glPushMatrix();
            gl.glLoadIdentity();
            glu.gluOrtho2D(0, glAutoDrawable.getSurfaceWidth(), 0, glAutoDrawable.getSurfaceHeight());
            gl.glMatrixMode(GL2.GL_MODELVIEW);
            gl.glPushMatrix();
            gl.glLoadIdentity();

            gl.glEnable(GL2.GL_BLEND);
            gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
            gl.glColor4f(0.4f, 0.4f, 0.4f, 0.5f); // Dark gray, 60% opacity

            double paddingX = 8.0;
            double paddingY = 6.0;
            double bgWidth = bounds.getWidth() + paddingX * 2;
            double bgHeight = bounds.getHeight() + paddingY * 2;
            double bgX = 4.0;
            double bgY = glAutoDrawable.getSurfaceHeight() - bgHeight - 4.0;

            gl.glBegin(GL2.GL_QUADS);
            gl.glVertex2d(bgX, bgY);
            gl.glVertex2d(bgX + bgWidth, bgY);
            gl.glVertex2d(bgX + bgWidth, bgY + bgHeight);
            gl.glVertex2d(bgX, bgY + bgHeight);
            gl.glEnd();

            gl.glDisable(GL2.GL_BLEND);

            gl.glMatrixMode(GL2.GL_PROJECTION);
            gl.glPopMatrix();
            gl.glMatrixMode(GL2.GL_MODELVIEW);
            gl.glPopMatrix();

            textRenderer.beginRendering(glAutoDrawable.getSurfaceWidth(), glAutoDrawable.getSurfaceHeight());
            textRenderer.setColor(Color.WHITE);
            // Draw text centered within the background panel
            textRenderer.draw(stats, (int)(bgX + paddingX), (int)(bgY + paddingY+2));
            textRenderer.endRendering();
        }
    }

    private void drawMesh(GL2 gl, Mesh mesh, boolean isSelected, double maxHeightForAO, double minHeightForAO, Texture texture){
        // Draw all faces
        for(int i=0; i<mesh.faces.size(); i++){
            var face = mesh.faces.get(i);
            var faceUV = mesh.faceUVs.get(i);

            if (faceUV != null && texture != null && !isWireframeMode) {
                // It's a textured face
                drawPolygonUV(gl, face, faceUV,  mesh.verts, mesh.uvs, texture, isSelected);
                //TODO: empty ground tile should be rendered rather as wireframe.
            } else {
                // It's a colored face
                var color = mesh.materials.get(mesh.faceMaterials.get(i));
                if (color == null){
                    color = Color.decode("#f1eee8");
                }
                drawPolygon(gl, face, mesh.verts, color, isSelected, maxHeightForAO, minHeightForAO);
            }
        }
    }

    private void drawSelectedOutline(GL2 gl, int[] faceIndices, List<Point3D> vertices){
        gl.glLineWidth(3.0f); // Make the line thick
        gl.glColor3f(1.0f, 0.0f, 0.0f); // Red color for selection

        gl.glBegin(GL2.GL_LINE_LOOP);
        for (int index : faceIndices) {
            Point3D p = vertices.get(index);
            gl.glVertex3d(p.x, p.y, p.z);
        }
        gl.glEnd();
        gl.glLineWidth(1.0f); // Make the line thin again

    }

    private void drawPolygonUV(GL2 gl, int[] face, int[] faceUV, List<Point3D> verts, List<Point2D> uvs, Texture texture, boolean isSelected ) {

        if (face.length!=4){
            throw new RuntimeException("Only quads are supported currently for textured faces");
        }

        // --- Draw selection outline (red, thick wireframe) ---
        if (isSelected) {
            drawSelectedOutline(gl, face, verts);
        }

        texture.bind(gl);
        gl.glEnable(GL2.GL_TEXTURE_2D);
        gl.glColor4d(1.0, 1.0, 1.0, 1.0);

        // Enable alpha testing to handle transparency in tree textures
        gl.glEnable(GL2.GL_ALPHA_TEST);
        gl.glAlphaFunc(GL2.GL_GREATER, 0.5f); // Discard fragments with alpha <= 0.5

        gl.glBegin(GL2.GL_QUADS);
        for (int i=0; i<face.length; i++){
            Point3D vert = verts.get(face[i]);
            Point2D uv = uvs.get(faceUV[i]);
            gl.glTexCoord2d(uv.x, uv.y);
            gl.glVertex3d(vert.x, vert.y, vert.z);
        }
        gl.glEnd();
        // Disable states
        gl.glDisable(GL2.GL_ALPHA_TEST);
        gl.glDisable(GL2.GL_TEXTURE_2D);
    }



    private void drawPolygon(GL2 gl, int[] faceIndices, List<Point3D> vertices, Color color, boolean isSelected, double maxHeightForAO, double minHeightForAO) {
        if (faceIndices.length < 3) return;

        // --- Draw selection outline (red, thick wireframe) ---
        if (isSelected) {
            drawSelectedOutline(gl,faceIndices, vertices);
        }
        
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
            if (!isSelected) {
                gl.glBegin(GL2.GL_LINE_LOOP);
                gl.glColor3f(litColor.getRed() / 255.0f, litColor.getGreen() / 255.0f, litColor.getBlue() / 255.0f);
                for (int index : faceIndices) {
                    Point3D p = vertices.get(index);
                    gl.glVertex3d(p.x, p.y, p.z);
                }
                gl.glEnd();
            }
        } else if (faceIndices.length == 4) {
            gl.glBegin(GL2.GL_QUADS);
            Point3D p4 = vertices.get(faceIndices[3]);

            drawVertexWithFakeAO(gl, p1, litColor, maxHeightForAO, minHeightForAO);
            drawVertexWithFakeAO(gl, p2, litColor, maxHeightForAO, minHeightForAO);
            drawVertexWithFakeAO(gl, p3, litColor, maxHeightForAO, minHeightForAO);
            drawVertexWithFakeAO(gl, p4, litColor, maxHeightForAO, minHeightForAO);

            gl.glEnd();

        } else {
            // Use tessellator for all polygons to handle non-convex cases correctly.
            GLUtessellator tess = glu.gluNewTess();
            TessellatorCallback callback = new TessellatorCallback(gl, glu, litColor, maxHeightForAO, minHeightForAO);

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

    private void drawVertexWithFakeAO(GL2 gl, Point3D vertex, Color baseColor, double maxHeightForAO, double minHeightForAO) {
        double totalHeight = maxHeightForAO - minHeightForAO;
        double vertexHeight = vertex.z - minHeightForAO;
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
        double maxHeightForAO;
        double minHeightForAO;


        public TessellatorCallback(GL2 gl, GLU glu, Color baseColor, double maxHeightForAO, double minHeightForAO) {
            this.gl = gl;
            this.glu = glu;
            this.baseColor = baseColor;
            this.maxHeightForAO = maxHeightForAO;
            this.minHeightForAO = minHeightForAO;
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
                drawVertexWithFakeAO(gl, (Point3D) vertexData, baseColor, maxHeightForAO, minHeightForAO);
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
                drawVertexWithFakeAO(gl, (Point3D) vertexData, baseColor, maxHeightForAO, minHeightForAO);
            }
        }

        @Override
        public void error(int errnum) {
            //TODO: uncomment when some way to output more specific error, including object id is found.
            //UrbanEye3dPlugin.debugMsg("Tessellation Error (" + errnum + "): " + glu.gluErrorString(errnum));
        }
    }
    public RenderableElement getPickedElement(int x, int y) {
        if (lastViewport[2] == 0 || lastViewport[3] == 0) return null;

        // Invert Y coordinate for OpenGL
        int glY = lastViewport[3] - y;

        double[] nearPos = new double[3];
        double[] farPos = new double[3];

        // Unproject two points to define a ray in world space
        glu.gluUnProject(x, glY, 0.0, lastMvMatrix, 0, lastProjMatrix, 0, lastViewport, 0, nearPos, 0);
        glu.gluUnProject(x, glY, 1.0, lastMvMatrix, 0, lastProjMatrix, 0, lastViewport, 0, farPos, 0);

        Point3D rayOrigin = new Point3D(nearPos[0], nearPos[1], nearPos[2]);
        Point3D rayDir = new Point3D(farPos[0] - nearPos[0], farPos[1] - nearPos[1], farPos[2] - nearPos[2]);
        GeometryUtils.Ray ray = new GeometryUtils.Ray(rayOrigin, rayDir);

        RenderableElement closestElement = null;
        double minDistance = Double.POSITIVE_INFINITY;

        LatLon mapCenter = getCameraPosition();

        if (scene.renderableElements == null) return null;

        for (RenderableElement element : scene.renderableElements) {
            Mesh mesh = element.getMesh();
            if (mesh == null) continue;

            double dx = element.origin.lon() - mapCenter.lon();
            double dy = element.origin.lat() - mapCenter.lat();
            double transX = dx * Math.cos(Math.toRadians(mapCenter.lat())) * 111320.0;
            double transY = dy * 111320.0;
            Point3D translation = new Point3D(transX, transY, 0);

            // Move ray to element's local space
            GeometryUtils.Ray localRay = new GeometryUtils.Ray(ray.origin.subtract(translation), ray.direction);

            // Fast BBox check
            if (!GeometryUtils.intersectRayAABB(localRay, mesh.getMinBounds(), mesh.getMaxBounds())) {
                continue;
            }

            // Precise polygon check
            for (int[] face : mesh.faces) {
                double dist = GeometryUtils.intersectRayPolygon(localRay, mesh.verts, face);

                if (!Double.isNaN(dist) && dist < minDistance) {
                    minDistance = dist;
                    closestElement = element;
                }
            }
        }

        return closestElement;
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

    private boolean isBoxInFrustum(double[][] frustum, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        for (int i = 0; i < 6; i++) {
            if (frustum[i][0] * (frustum[i][0] > 0 ? maxX : minX) +
                frustum[i][1] * (frustum[i][1] > 0 ? maxY : minY) +
                frustum[i][2] * (frustum[i][2] > 0 ? maxZ : minZ) +
                frustum[i][3] < 0) {
                return false;
            }
        }
        return true;
    }
}