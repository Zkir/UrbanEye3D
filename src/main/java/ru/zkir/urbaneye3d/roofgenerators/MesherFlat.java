package ru.zkir.urbaneye3d.roofgenerators;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUtessellator;
import com.jogamp.opengl.glu.GLUtessellatorCallbackAdapter;
import ru.zkir.urbaneye3d.RenderableBuildingElement;
import ru.zkir.urbaneye3d.UrbanEye3dPlugin;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;

import java.util.ArrayList;
import java.util.List;

public class MesherFlat extends RoofGenerator{

    // Tessellator callback inner class for RoofGeometryGenerator
    private static class TessellatorCallback extends GLUtessellatorCallbackAdapter {
        private final List<Point3D> vertices;
        private final List<int[]> faces;
        private int currentPrimitiveType; // To store the type from beginData
        private final RenderableBuildingElement building; // Reference to the building

        public TessellatorCallback(List<Point3D> vertices, List<int[]> faces, RenderableBuildingElement building) {
            this.vertices = vertices;
            this.faces = faces;
            this.building = building;
        }

        @Override
        public void begin(int type) {
            // This is the old begin, not beginData. We should use beginData.
            // This method is not used when beginData is overridden.
        }

        @Override
        public void end() {
            // This is the old end, not endData. Not used when endData is overridden.
        }

        @Override
        public void combine(double[] coords, Object[] data, float[] weight, Object[] outData) {
            Point3D newVertex = new Point3D(coords[0], coords[1], coords[2]);
            vertices.add(newVertex);
            outData[0] = vertices.size() - 1; // Return the index of the new vertex
        }

        @Override
        public void error(int errnum) {
            UrbanEye3dPlugin.debugMsg("Tessellation Error (" + errnum + "): " + new GLU().gluErrorString(errnum) +
                               " on building at " + building.origin.toString());
        }

        private List<Integer> currentContourVertices = new ArrayList<>();

        @Override
        public void beginData(int type, Object polygonData) {
            currentPrimitiveType = type; // Store the primitive type
            currentContourVertices.clear();
        }

        @Override
        public void vertexData(Object vertexData, Object polygonData) {
            // vertexData is now the index of the vertex
            currentContourVertices.add((Integer) vertexData);
        }

        @Override
        public void endData(Object polygonData) {
            // Now use currentPrimitiveType to correctly interpret the vertices
            if (currentPrimitiveType == GL2.GL_TRIANGLES) {
                for (int i = 0; i < currentContourVertices.size(); i += 3) {
                    faces.add(new int[]{currentContourVertices.get(i), currentContourVertices.get(i + 1), currentContourVertices.get(i + 2)});
                }
            } else if (currentPrimitiveType == GL2.GL_TRIANGLE_FAN) {
                // For triangle fan, the first vertex is common to all triangles
                int v0 = currentContourVertices.get(0);
                for (int i = 1; i < currentContourVertices.size() - 1; i++) {
                    faces.add(new int[]{v0, currentContourVertices.get(i), currentContourVertices.get(i + 1)});
                }
            } else if (currentPrimitiveType == GL2.GL_TRIANGLE_STRIP) {
                // For triangle strip, each new vertex forms a new triangle with the previous two
                for (int i = 0; i < currentContourVertices.size() - 2; i++) {
                    if (i % 2 == 0) { // Even index, normal winding
                        faces.add(new int[]{currentContourVertices.get(i), currentContourVertices.get(i + 1), currentContourVertices.get(i + 2)});
                    } else { // Odd index, reverse winding for correct normal
                        faces.add(new int[]{currentContourVertices.get(i + 1), currentContourVertices.get(i), currentContourVertices.get(i + 2)});
                    }
                }
            }
            // Other types like GL_QUADS, GL_QUAD_STRIP are less common from tessellator output,
            // but if they appear, they would need similar handling.
        }
    }

    @Override
    public Mesh generate(RenderableBuildingElement building) {
        List<List<Point2D>> contours = new ArrayList<>();
        contours.addAll(building.getContourOuterRings());
        contours.addAll(building.getContourInnerRings());

        double height = building.height;
        double minHeight = building.minHeight;
        double wallHeight = building.height - building.roofHeight;

        Mesh mesh = new Mesh();
        List<Point3D> verts = mesh.verts;

        List<Integer> contourBaseVertexStartIndices = new ArrayList<>(); // Start index of minHeight vertices for each contour
        List<Integer> contourWallTopVertexStartIndices = new ArrayList<>(); // Start index of wallHeight vertices for each contour
        List<Integer> contourRoofTopVertexStartIndices = new ArrayList<>(); // Start index of height vertices for each contour


        // Add vertices for all contours at minHeight, wallHeight, and height, avoiding duplicates
        for (List<Point2D> contour : contours) {
            contourBaseVertexStartIndices.add(verts.size());
            for (Point2D p : contour) {
                verts.add(new Point3D(p.x, p.y, minHeight));
            }

            if (wallHeight > minHeight) {
                contourWallTopVertexStartIndices.add(verts.size());
                for (Point2D p : contour) {
                    verts.add(new Point3D(p.x, p.y, wallHeight));
                }
            } else {
                // wallHeight == minHeight, so wall-top vertices are the same as base vertices
                contourWallTopVertexStartIndices.add(contourBaseVertexStartIndices.get(contourBaseVertexStartIndices.size() - 1));
            }

            if (height > wallHeight) {
                contourRoofTopVertexStartIndices.add(verts.size());
                for (Point2D p : contour) {
                    verts.add(new Point3D(p.x, p.y, height));
                }
            } else {
                // height == wallHeight, so roof-top vertices are the same as wall-top vertices
                contourRoofTopVertexStartIndices.add(contourWallTopVertexStartIndices.get(contourWallTopVertexStartIndices.size() - 1));
            }
        }

        // Create main wall faces (between minHeight and wallHeight)
        if (wallHeight > minHeight) {
            for (int c = 0; c < contours.size(); c++) {
                List<Point2D> contour = contours.get(c);
                int n = contour.size();
                int baseStartIdx = contourBaseVertexStartIndices.get(c);
                int wallTopStartIdx = contourWallTopVertexStartIndices.get(c);

                for (int i = 0; i < n; i++) {
                    int next = (i + 1) % n;
                    mesh.wallFaces.add(new int[]{
                            baseStartIdx + i,
                            baseStartIdx + next,
                            wallTopStartIdx + next,
                            wallTopStartIdx + i
                    });
                }
            }
        }

        // Create roof fascia faces (between wallHeight and height)
        if (height > wallHeight) {
            for (int c = 0; c < contours.size(); c++) {
                List<Point2D> contour = contours.get(c);
                int n = contour.size();
                int wallTopStartIdx = contourWallTopVertexStartIndices.get(c);
                int roofTopStartIdx = contourRoofTopVertexStartIndices.get(c);

                for (int i = 0; i < n; i++) {
                    int next = (i + 1) % n;
                    mesh.roofFaces.add(new int[]{ // Add to roofFaces for roof color
                            wallTopStartIdx + i,
                            wallTopStartIdx + next,
                            roofTopStartIdx + next,
                            roofTopStartIdx + i
                    });
                }
            }
        }

        List<Point2D> outerContour = contours.get(0);
        if (contours.size() == 1) {
            // Simple case: one outer contour, no inner contours.
            // Create roof face as a single polygon
            int n = outerContour.size();
            int[] roofFace = new int[n];
            int roofTopStartIdx = contourRoofTopVertexStartIndices.get(0);
            for (int i = 0; i < n; i++) {
                roofFace[i] = roofTopStartIdx + i;
            }
            mesh.roofFaces.add(roofFace);

            // Create bottom face as a single polygon (with reversed winding)
            int[] bottomFace = new int[n];
            int baseStartIdx = contourBaseVertexStartIndices.get(0);
            for (int i = 0; i < n; i++) {
                bottomFace[i] = baseStartIdx + (n - 1 - i);
            }
            mesh.bottomFaces.add(bottomFace);
        } else {
            // Complex case: multiple contours (holes). Use tessellation.
            GLU glu = new GLU();
            GLUtessellator tess = glu.gluNewTess();
            TessellatorCallback roofCallback = new TessellatorCallback(verts, mesh.roofFaces, building);

            glu.gluTessCallback(tess, GLU.GLU_TESS_VERTEX_DATA, roofCallback);
            glu.gluTessCallback(tess, GLU.GLU_TESS_BEGIN_DATA, roofCallback);
            glu.gluTessCallback(tess, GLU.GLU_TESS_END_DATA, roofCallback);
            glu.gluTessCallback(tess, GLU.GLU_TESS_ERROR, roofCallback);
            glu.gluTessCallback(tess, GLU.GLU_TESS_COMBINE_DATA, roofCallback);

            glu.gluTessProperty(tess, GLU.GLU_TESS_WINDING_RULE, GLU.GLU_TESS_WINDING_ODD);

            glu.gluTessBeginPolygon(tess, null);

            // Outer contour for roof (using vertices at 'height')
            glu.gluTessBeginContour(tess);
            for (int i = 0; i < outerContour.size(); i++) {
                int vertexIndex = contourRoofTopVertexStartIndices.get(0) + i;
                Point3D vertex = verts.get(vertexIndex);
                double[] coords = {vertex.x, vertex.y, vertex.z};
                glu.gluTessVertex(tess, coords, 0, vertexIndex);
            }
            glu.gluTessEndContour(tess);

            // Inner contours (holes) for roof (using vertices at 'height')
            for (int c = 1; c < contours.size(); c++) {
                List<Point2D> innerContour = contours.get(c);
                glu.gluTessBeginContour(tess);
                for (int i = 0; i < innerContour.size(); i++) {
                    int vertexIndex = contourRoofTopVertexStartIndices.get(c) + i;
                    Point3D vertex = verts.get(vertexIndex);
                    double[] coords = {vertex.x, vertex.y, vertex.z};
                    glu.gluTessVertex(tess, coords, 0, vertexIndex);
                }
                glu.gluTessEndContour(tess);
            }

            glu.gluTessEndPolygon(tess);
            glu.gluDeleteTess(tess);

            // Create bottom face using tessellation (using vertices at 'minHeight')
            GLUtessellator tessBottom = glu.gluNewTess();
            TessellatorCallback bottomCallback = new TessellatorCallback(verts, mesh.bottomFaces, building);

            glu.gluTessCallback(tessBottom, GLU.GLU_TESS_VERTEX_DATA, bottomCallback);
            glu.gluTessCallback(tessBottom, GLU.GLU_TESS_BEGIN_DATA, bottomCallback);
            glu.gluTessCallback(tessBottom, GLU.GLU_TESS_END_DATA, bottomCallback);
            glu.gluTessCallback(tessBottom, GLU.GLU_TESS_ERROR, bottomCallback);
            glu.gluTessCallback(tessBottom, GLU.GLU_TESS_COMBINE_DATA, bottomCallback);

            glu.gluTessProperty(tessBottom, GLU.GLU_TESS_WINDING_RULE, GLU.GLU_TESS_WINDING_ODD);

            glu.gluTessBeginPolygon(tessBottom, null);

            // Outer contour for bottom (using vertices at 'minHeight')
            glu.gluTessBeginContour(tessBottom);
            for (int i = outerContour.size() - 1; i >= 0; i--) {
                int vertexIndex = contourBaseVertexStartIndices.get(0) + i;
                Point3D vertex = verts.get(vertexIndex);
                double[] coords = {vertex.x, vertex.y, vertex.z};
                glu.gluTessVertex(tessBottom, coords, 0, vertexIndex);
            }
            glu.gluTessEndContour(tessBottom);

            // Inner contours (holes) for bottom (using vertices at 'minHeight')
            for (int c = 1; c < contours.size(); c++) {
                List<Point2D> innerContour = contours.get(c);
                glu.gluTessBeginContour(tessBottom);
                for (int i = innerContour.size() - 1; i >= 0; i--) {
                    int vertexIndex = contourBaseVertexStartIndices.get(c) + i;
                    Point3D vertex = verts.get(vertexIndex);
                    double[] coords = {vertex.x, vertex.y, vertex.z};
                    glu.gluTessVertex(tessBottom, coords, 0, vertexIndex);
                }
                glu.gluTessEndContour(tessBottom);
            }

            glu.gluTessEndPolygon(tessBottom);
            glu.gluDeleteTess(tessBottom);
        }

        return mesh;
    }
}
