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
import java.util.Comparator;
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
        if ("steps".equals(building.buildingPart)) {
            return generateSteps(building);
        }

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

                boolean isInnerContour = (c > 0);

                if (p1_base != p1_roof && p2_base != p2_roof) {
                    if (isInnerContour) {
                        mesh.wallFaces.add(new int[]{p2_base, p1_base, p1_roof, p2_roof});
                    } else {
                        mesh.wallFaces.add(new int[]{p1_base, p2_base, p2_roof, p1_roof});
                    }
                } else if (p1_base == p1_roof) {
                    if (isInnerContour) {
                        mesh.wallFaces.add(new int[]{p2_roof, p2_base, p1_base});
                    } else {
                        mesh.wallFaces.add(new int[]{p2_base, p2_roof, p1_base});
                    }
                } else { // p2_base == p2_roof
                    if (isInnerContour) {
                        mesh.wallFaces.add(new int[]{p2_base, p1_base, p1_roof});
                    } else {
                        mesh.wallFaces.add(new int[]{p1_base, p2_base, p1_roof});
                    }
                }
            }
        }

        if (contours.size() == 1) {
            List<Point2D> outerContour = contours.get(0);
            int n = outerContour.size();

            int[] roofFace = new int[n];
            List<Integer> roofTopIdxs = contourRoofTopVertexIndices.get(0);
            for (int i = 0; i < n; i++) {
                roofFace[i] = roofTopIdxs.get(i);
            }
            mesh.roofFaces.add(roofFace);

            int[] bottomFace = new int[n];
            int baseStartIdx = contourBaseVertexStartIndices.get(0);
            for (int i = 0; i < n; i++) {
                bottomFace[i] = baseStartIdx + (n - 1 - i);
            }
            mesh.bottomFaces.add(bottomFace);
        } else {
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

    private Mesh generateSteps(RenderableBuildingElement building) {
        if (building.hasComplexContour()) {
            UrbanEye3dPlugin.debugMsg("Steps generation for complex contours is not yet supported. Building: " + building.primitiveId);
            return null;
        }
        List<Point2D> contour = building.getContour();
        if (contour.isEmpty()) return new Mesh();

        double minHeight = building.minHeight;
        double roofHeight = building.roofHeight;
        double wallHeight = building.height - roofHeight;
        double roofDirection = building.roofDirection;

        Mesh mesh = new Mesh();
        List<Point3D> verts = mesh.verts;

        Point2D slopeVector = calculateSlopeVector(contour, roofDirection);

        double maxProj = -Double.MAX_VALUE, minProj = Double.MAX_VALUE;
        for (Point2D p : contour) {
            double proj = p.x * slopeVector.x + p.y * slopeVector.y;
            maxProj = Math.max(maxProj, proj);
            minProj = Math.min(minProj, proj);
        }

        final double STEP_HEIGHT = 0.25;
        int numSteps = (int) Math.max(1, Math.floor(roofHeight / STEP_HEIGHT));
        double actualStepHeight = roofHeight / numSteps;
        double projDiff = maxProj - minProj;
        double stepDepth = (projDiff > 1e-9) ? projDiff / numSteps : 0;

        // Add base vertices and bottom face
        int baseStartIndex = verts.size();
        contour.forEach(p -> verts.add(new Point3D(p.x, p.y, minHeight)));
        int[] bottomFace = new int[contour.size()];
        for (int i = 0; i < contour.size(); i++) bottomFace[i] = baseStartIndex + (contour.size() - 1 - i);
        mesh.bottomFaces.add(bottomFace);

        // --- Generate Steps (Risers and Treads) --- 
        List<Point3D> frontIntersectionPoints = getIntersectionPoints(contour, slopeVector, minProj);
        if (frontIntersectionPoints.size() < 2) return mesh; // Cannot form steps

        // Initial vertices at the bottom of the first riser
        int prev_v1_idx = verts.size();
        verts.add(new Point3D(frontIntersectionPoints.get(0).x, frontIntersectionPoints.get(0).y, wallHeight));
        int prev_v2_idx = verts.size();
        verts.add(new Point3D(frontIntersectionPoints.get(1).x, frontIntersectionPoints.get(1).y, wallHeight));

        // Store side vertices to build walls later
        List<Integer> side1_indices = new ArrayList<>();
        List<Integer> side2_indices = new ArrayList<>();
        side1_indices.add(prev_v1_idx);
        side2_indices.add(prev_v2_idx);

        for (int s = 0; s < numSteps; s++) {
            double z_top = wallHeight + (s + 1) * actualStepHeight;
            double proj_back = minProj + (s + 1) * stepDepth;
            List<Point3D> backIntersectionPoints = getIntersectionPoints(contour, slopeVector, proj_back);
            if (backIntersectionPoints.size() < 2) continue;

            // Create vertices for the top of the riser
            int riser_v1_top_idx = verts.size();
            verts.add(new Point3D(verts.get(prev_v1_idx).x, verts.get(prev_v1_idx).y, z_top));
            int riser_v2_top_idx = verts.size();
            verts.add(new Point3D(verts.get(prev_v2_idx).x, verts.get(prev_v2_idx).y, z_top));

            // Create vertices for the back of the tread
            int tread_v1_back_idx = verts.size();
            verts.add(new Point3D(backIntersectionPoints.get(0).x, backIntersectionPoints.get(0).y, z_top));
            int tread_v2_back_idx = verts.size();
            verts.add(new Point3D(backIntersectionPoints.get(1).x, backIntersectionPoints.get(1).y, z_top));

            // Add faces (winding is reversed to point normals outwards)
            mesh.roofFaces.add(new int[]{riser_v1_top_idx, riser_v2_top_idx, prev_v2_idx, prev_v1_idx}); // Riser
            mesh.roofFaces.add(new int[]{tread_v1_back_idx, tread_v2_back_idx, riser_v2_top_idx, riser_v1_top_idx}); // Tread

            // Add to side wall profiles
            side1_indices.add(riser_v1_top_idx);
            side1_indices.add(tread_v1_back_idx);
            side2_indices.add(riser_v2_top_idx);
            side2_indices.add(tread_v2_back_idx);

            // Update previous vertices for next step
            prev_v1_idx = tread_v1_back_idx;
            prev_v2_idx = tread_v2_back_idx;
        }

        // --- Wall Generation (for rectangular base) ---
        int front_v1_base_idx = baseStartIndex + 0;
        int front_v2_base_idx = baseStartIndex + 1;
        int back_v1_base_idx = baseStartIndex + 3;
        int back_v2_base_idx = baseStartIndex + 2;

        // Front Wall
        mesh.wallFaces.add(new int[]{front_v1_base_idx, front_v2_base_idx, side2_indices.get(side2_indices.size() - 1), side1_indices.get(side1_indices.size() - 1)});
        // Back Wall
        mesh.wallFaces.add(new int[]{back_v2_base_idx, back_v1_base_idx, side1_indices.get(0), side2_indices.get(0)});

        // Side Wall 1
        int[] wall1_face = new int[2 + side1_indices.size()];
        wall1_face[0] = back_v1_base_idx;
        wall1_face[1] = front_v1_base_idx;
        for (int i = 0; i < side1_indices.size(); i++) {
            wall1_face[2 + i] = side1_indices.get(side1_indices.size() - 1 - i);
        }
        mesh.wallFaces.add(wall1_face);

        // Side Wall 2 (reversed winding)
        int[] wall2_face = new int[2 + side2_indices.size()];
        wall2_face[wall2_face.length - 1] = back_v2_base_idx;
        wall2_face[wall2_face.length - 2] = front_v2_base_idx;
        for (int i = 0; i < side2_indices.size(); i++) {
            wall2_face[i] = side2_indices.get(i);
        }
        mesh.wallFaces.add(wall2_face);

        return mesh;
    }

    private Point2D calculateSlopeVector(List<Point2D> contour, double roofDirection) {
        Point2D slopeVector;
        if (!Double.isNaN(roofDirection)) {
            double angleRad = Math.toRadians(roofDirection);
            slopeVector = new Point2D(-Math.sin(angleRad), -Math.cos(angleRad));
        } else {
            int[] longestEdgeIndices = findLongestEdge(contour);
            Point2D p1 = contour.get(longestEdgeIndices[0]);
            Point2D p2 = contour.get(longestEdgeIndices[1]);
            slopeVector = new Point2D(-(p2.y - p1.y), p2.x - p1.x);
        }
        slopeVector.normalize();
        return slopeVector;
    }

    private int findClosestVertexIndex(List<Point3D> vertices, Point3D target, int start, int count) {
        int bestIdx = -1;
        double minDst = Double.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            double dst = vertices.get(start + i).distance(target);
            if (dst < minDst) {
                minDst = dst;
                bestIdx = start + i;
            }
        }
        return bestIdx;
    }

    private List<Point3D> getIntersectionPoints(List<Point2D> contour, Point2D slopeVector, double proj) {
        List<Point3D> intersections = new ArrayList<>();
        Point2D normal = new Point2D(-slopeVector.y, slopeVector.x);

        for (int i = 0; i < contour.size(); i++) {
            Point2D p1 = contour.get(i);
            Point2D p2 = contour.get((i + 1) % contour.size());

            double a1 = p2.y - p1.y, b1 = p1.x - p2.x, c1 = a1 * p1.x + b1 * p1.y;
            double a2 = slopeVector.x, b2 = slopeVector.y, c2 = proj;
            double det = a1 * b2 - a2 * b1;

            if (Math.abs(det) > 1e-9) {
                double x = (b2 * c1 - b1 * c2) / det;
                double y = (a1 * c2 - a2 * c1) / det;
                if (Math.min(p1.x, p2.x) <= x && x <= Math.max(p1.x, p2.x) &&
                    Math.min(p1.y, p2.y) <= y && y <= Math.max(p1.y, p2.y)) {
                    intersections.add(new Point3D(x, y, 0)); // Z is set later
                }
            }
        }
        intersections.sort(Comparator.comparingDouble(p -> p.x * normal.x + p.y * normal.y));
        return intersections;
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
