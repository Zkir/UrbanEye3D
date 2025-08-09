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

public class MesherSkillion extends RoofGenerator {

    private GLU glu = new GLU();

    private static class TessellatorCallback extends GLUtessellatorCallbackAdapter {
        private final List<Point3D> vertices;
        private final List<int[]> faces;
        private int currentPrimitiveType;
        private final RenderableBuildingElement building;
        private List<Integer> currentContourVertices = new ArrayList<>();

        public TessellatorCallback(List<Point3D> vertices, List<int[]> faces, RenderableBuildingElement building) {
            this.vertices = vertices;
            this.faces = faces;
            this.building = building;
        }

        @Override
        public void beginData(int type, Object polygonData) {
            currentPrimitiveType = type;
            currentContourVertices.clear();
        }

        @Override
        public void vertexData(Object vertexData, Object polygonData) {
            currentContourVertices.add((Integer) vertexData);
        }

        @Override
        public void endData(Object polygonData) {
            if (currentPrimitiveType == GL2.GL_TRIANGLES) {
                for (int i = 0; i < currentContourVertices.size(); i += 3) {
                    faces.add(new int[]{currentContourVertices.get(i), currentContourVertices.get(i + 1), currentContourVertices.get(i + 2)});
                }
            } else if (currentPrimitiveType == GL2.GL_TRIANGLE_FAN) {
                int v0 = currentContourVertices.get(0);
                for (int i = 1; i < currentContourVertices.size() - 1; i++) {
                    faces.add(new int[]{v0, currentContourVertices.get(i), currentContourVertices.get(i + 1)});
                }
            } else if (currentPrimitiveType == GL2.GL_TRIANGLE_STRIP) {
                for (int i = 0; i < currentContourVertices.size() - 2; i++) {
                    if (i % 2 == 0) {
                        faces.add(new int[]{currentContourVertices.get(i), currentContourVertices.get(i + 1), currentContourVertices.get(i + 2)});
                    } else {
                        faces.add(new int[]{currentContourVertices.get(i + 1), currentContourVertices.get(i), currentContourVertices.get(i + 2)});
                    }
                }
            }
        }

        @Override
        public void combine(double[] coords, Object[] data, float[] weight, Object[] outData) {
            Point3D newVertex = new Point3D(coords[0], coords[1], coords[2]);
            vertices.add(newVertex);
            outData[0] = vertices.size() - 1;
        }

        @Override
        public void error(int errnum) {
            UrbanEye3dPlugin.debugMsg("Tessellation Error (" + errnum + "): " + new GLU().gluErrorString(errnum) +
                               " on building at " + building.origin.toString());
        }
    }

    @Override
    public Mesh generate(RenderableBuildingElement building) {
        List<List<Point2D>> contours = new ArrayList<>();
        contours.addAll(building.getContourOuterRings());
        if (contours.isEmpty()) {
            return new Mesh();
        }
        contours.addAll(building.getContourInnerRings());

        double minHeight = building.minHeight;
        double height = building.height;
        double wallHeight = building.height - building.roofHeight;
        double roofDirection = building.roofDirection;

        Mesh mesh = new Mesh();
        List<Point3D> verts = mesh.verts;

        Point2D slopeVector;
        if (!Double.isNaN(roofDirection)) {
            double angleRad = Math.toRadians(roofDirection);
            slopeVector = new Point2D(-Math.sin(angleRad), -Math.cos(angleRad));
        } else {
            int[] longestEdgeIndices = findLongestEdge(contours.get(0));
            Point2D p1 = contours.get(0).get(longestEdgeIndices[0]);
            Point2D p2 = contours.get(0).get(longestEdgeIndices[1]);
            slopeVector = new Point2D(-(p2.y - p1.y), p2.x - p1.x);
        }
        slopeVector.normalize();

        List<List<Double>> allProjections = new ArrayList<>();
        double maxProj = -Double.MAX_VALUE;
        double minProj = Double.MAX_VALUE;

        for (List<Point2D> contour : contours) {
            List<Double> contourProjections = new ArrayList<>();
            for (Point2D p : contour) {
                double proj = p.x * slopeVector.x + p.y * slopeVector.y;
                contourProjections.add(proj);
                maxProj = Math.max(maxProj, proj);
                minProj = Math.min(minProj, proj);
            }
            allProjections.add(contourProjections);
        }

        double roofHeight = height - wallHeight;
        double projDiff = maxProj - minProj;
        double tan = (projDiff > 1e-9) ? roofHeight / projDiff : 0;

        List<Integer> contourBaseVertexStartIndices = new ArrayList<>();
        List<List<Integer>> contourRoofTopVertexIndices = new ArrayList<>();

        for (int c = 0; c < contours.size(); c++) {
            List<Point2D> contour = contours.get(c);
            List<Double> projections = allProjections.get(c);
            int baseContourStartIndex = verts.size();
            contourBaseVertexStartIndices.add(baseContourStartIndex);

            for (Point2D p : contour) {
                verts.add(new Point3D(p.x, p.y, minHeight));
            }

            List<Integer> roofTopIndices = new ArrayList<>();
            for (int i = 0; i < contour.size(); i++) {
                double z = wallHeight + (projections.get(i) - minProj) * tan;
                if (Math.abs(z - minHeight) < 1e-6) {
                    roofTopIndices.add(baseContourStartIndex + i);
                } else {
                    verts.add(new Point3D(contour.get(i).x, contour.get(i).y, z));
                    roofTopIndices.add(verts.size() - 1);
                }
            }
            contourRoofTopVertexIndices.add(roofTopIndices);
        }

        for (int c = 0; c < contours.size(); c++) {
            int n = contours.get(c).size();
            int baseStartIdx = contourBaseVertexStartIndices.get(c);
            List<Integer> roofTopIdxs = contourRoofTopVertexIndices.get(c);

            for (int i = 0; i < n; i++) {
                int next = (i + 1) % n;
                int p1_base = baseStartIdx + i;
                int p2_base = baseStartIdx + next;
                int p1_roof = roofTopIdxs.get(i);
                int p2_roof = roofTopIdxs.get(next);

                if (p1_base == p1_roof && p2_base == p2_roof) continue;

                if (p1_base != p1_roof && p2_base != p2_roof) {
                    mesh.wallFaces.add(new int[]{p1_base, p2_base, p2_roof, p1_roof});
                } else if (p1_base == p1_roof) {
                    mesh.wallFaces.add(new int[]{p2_base, p2_roof, p1_base});
                } else { // p2_base == p2_roof
                    mesh.wallFaces.add(new int[]{p1_base, p2_base, p1_roof});
                }
            }
        }

        if (contours.size() == 1) {
            // Simple case: one outer contour, no inner contours.
            List<Point2D> outerContour = contours.get(0);
            int n = outerContour.size();

            // Create roof face as a single polygon
            int[] roofFace = new int[n];
            List<Integer> roofTopIdxs = contourRoofTopVertexIndices.get(0);
            for (int i = 0; i < n; i++) {
                roofFace[i] = roofTopIdxs.get(i);
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
            GLUtessellator tess = glu.gluNewTess();
            TessellatorCallback roofCallback = new TessellatorCallback(verts, mesh.roofFaces, building);
            setupTessellator(tess, roofCallback);
            glu.gluTessBeginPolygon(tess, null);
            tessellateContours(tess, contours, contourRoofTopVertexIndices, verts, false);
            glu.gluTessEndPolygon(tess);
            glu.gluDeleteTess(tess);

            GLUtessellator tessBottom = glu.gluNewTess();
            TessellatorCallback bottomCallback = new TessellatorCallback(verts, mesh.bottomFaces, building);
            setupTessellator(tessBottom, bottomCallback);
            glu.gluTessBeginPolygon(tessBottom, null);
            tessellateContours(tessBottom, contours, contourBaseVertexStartIndices, verts, true);
            glu.gluTessEndPolygon(tessBottom);
            glu.gluDeleteTess(tessBottom);
        }

        return mesh;
    }

    private void setupTessellator(GLUtessellator tess, TessellatorCallback callback) {
        glu.gluTessCallback(tess, GLU.GLU_TESS_VERTEX_DATA, callback);
        glu.gluTessCallback(tess, GLU.GLU_TESS_BEGIN_DATA, callback);
        glu.gluTessCallback(tess, GLU.GLU_TESS_END_DATA, callback);
        glu.gluTessCallback(tess, GLU.GLU_TESS_ERROR, callback);
        glu.gluTessCallback(tess, GLU.GLU_TESS_COMBINE_DATA, callback);
        glu.gluTessProperty(tess, GLU.GLU_TESS_WINDING_RULE, GLU.GLU_TESS_WINDING_ODD);
    }

    private void tessellateContours(GLUtessellator tess, List<List<Point2D>> contours, List<?> vertexIndices, List<Point3D> verts, boolean reverse) {
        for (int c = 0; c < contours.size(); c++) {
            List<Point2D> contour = contours.get(c);
            glu.gluTessBeginContour(tess);
            int n = contour.size();
            for (int i = 0; i < n; i++) {
                int idx = reverse ? (n - 1 - i) : i;
                int vertexIndex;
                if (vertexIndices.get(c) instanceof List) {
                    vertexIndex = ((List<Integer>) vertexIndices.get(c)).get(idx);
                } else {
                    vertexIndex = (Integer) vertexIndices.get(c) + idx;
                }
                Point3D vertex = verts.get(vertexIndex);
                double[] coords = {vertex.x, vertex.y, vertex.z};
                glu.gluTessVertex(tess, coords, 0, vertexIndex);
            }
            glu.gluTessEndContour(tess);
        }
    }

    private static int[] findLongestEdge(List<Point2D> points) {
        if (points == null || points.size() < 2) return new int[]{-1, -1};
        double maxDistSq = -1;
        int[] edgeIndices = new int[2];
        for (int i = 0; i < points.size(); i++) {
            Point2D p1 = points.get(i);
            Point2D p2 = points.get((i + 1) % points.size());
            double dx = p2.x - p1.x;
            double dy = p2.y - p1.y;
            double distSq = dx * dx + dy * dy;
            if (distSq > maxDistSq) {
                maxDistSq = distSq;
                edgeIndices[0] = i;
                edgeIndices[1] = (i + 1) % points.size();
            }
        }
        return edgeIndices;
    }
}