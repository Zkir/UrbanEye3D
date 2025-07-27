package ru.zkir.josm.plugins.z3dviewer.roofgenerators;

import ru.zkir.josm.plugins.z3dviewer.RenderableBuildingElement;
import ru.zkir.josm.plugins.z3dviewer.utils.Mesh;
import ru.zkir.josm.plugins.z3dviewer.utils.Point2D;
import ru.zkir.josm.plugins.z3dviewer.utils.Point3D;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.*;
import static java.lang.Math.PI;

public class MesherConicProfile extends RoofGenerator{

    // Generatrix for a dome
    private static List<Point2D> domeProfile(int rows) {
        List<Point2D> profile = new ArrayList<>();
        for (int j = 0; j <= rows; j++) {
            double x = cos((double) j / rows * PI / 2);
            double z = sin((double) j / rows * PI / 2);
            profile.add(new Point2D(x, z));
        }
        return profile;
    }

    // Generatrix for an onion roof
    private static List<Point2D> onionProfile(){
        List<Point2D> profile = new ArrayList<>();

        profile.add(new Point2D(1.0000, 0.0000));
        profile.add(new Point2D(1.2971, 0.0999));
        profile.add(new Point2D(1.2971, 0.2462));
        profile.add(new Point2D(1.1273, 0.3608));
        profile.add(new Point2D(0.6219, 0.4785));
        profile.add(new Point2D(0.2131, 0.5984));
        profile.add(new Point2D(0.1003, 0.7243));
        profile.add(new Point2D(0.0000, 1.0000));
        return profile;
    }

    // Generatrix for a pyramidal roof
    // Just 2 points indeed.
    private static List<Point2D> pyramidalProfile(){
        List<Point2D> profile = new ArrayList<>();
        profile.add(new Point2D(1.0000, 0.0000));
        profile.add(new Point2D(0.0000, 1.0000));
        return profile;
    }


    String profile;
    public MesherConicProfile(String profile) {
        super();
        this.profile=profile;
    }

    @Override
    public Mesh generate(RenderableBuildingElement building) {

        List<Point2D> basePoints = building.getContour() ;
        double height= building.height;
        double minHeight = building.minHeight;
        double wallHeight = building.height - building.roofHeight;
        RoofShapes roofShape = building.roofShape;

        Mesh mesh = new Mesh();
        List<Point3D> verts = new ArrayList<>();
        for (Point2D p:basePoints){
            verts.add(new Point3D(p.x, p.y, minHeight));
        }

        List<Point2D> profile;
        if ((roofShape==RoofShapes.DOME) || (roofShape == RoofShapes.HALF_DOME)) {
            profile = domeProfile(7);
        } else if (roofShape == RoofShapes.PYRAMIDAL){
            profile = pyramidalProfile();
        } else if (roofShape == RoofShapes.ONION) {
            profile = onionProfile();
        } else {
            throw new IllegalArgumentException("Unknown roof type: " + roofShape);
        }

        int n = basePoints.size();
        double z1 = wallHeight;
        double z2 = height;

        Point3D center;
        if (roofShape== RoofShapes.HALF_DOME) {
            center = calculateMidpointOfLongestEdge(basePoints);
        } else {
            center = calculateCentroid(basePoints);
        }

        // Create walls
        if (minHeight < wallHeight) { // Only create walls if there's a height difference
            for (int i = 0; i < n; i++) {
                verts.add(new Point3D(basePoints.get(i).x, basePoints.get(i).y, z1));
            }
            int indexOffset = n;
            for (int i = 0; i < n - 1; i++) {
                mesh.wallFaces.add(new int[]{i, i + 1, i + n + 1, i + n});
            }
            mesh.wallFaces.add(new int[]{n - 1, 0, n, 2 * n - 1});
        }

        // Create roof mesh vertices
        int rows = profile.size() - 1;
        for (int j = 1; j < rows; j++) {
            for (int i = 0; i < n; i++) {
                double xi = basePoints.get(i).x + (1 - profile.get(j).x) * (center.x - basePoints.get(i).x);
                double yi = basePoints.get(i).y + (1 - profile.get(j).x) * (center.y - basePoints.get(i).y);
                double zi = wallHeight + (height - wallHeight) * profile.get(j).y;
                verts.add(new Point3D(xi, yi, zi));
            }
        }

        // Add the top vertex (apex)
        verts.add(new Point3D(center.x, center.y, z2));
        int centreIdx = verts.size() - 1;

        // Create roof faces
        int indexOffset = (minHeight < wallHeight) ? n : 0; // Adjust offset if walls were created

        for (int j = 0; j < rows - 1; j++) {
            for (int i = 0; i < n - 1; i++) {
                mesh.roofFaces.add(new int[]{
                        indexOffset + j * n + i,
                        indexOffset + j * n + i + 1,
                        indexOffset + j * n + i + 1 + n,
                        indexOffset + j * n + i + n
                });
            }
            mesh.roofFaces.add(new int[]{
                    indexOffset + j * n + n - 1,
                    indexOffset + j * n + 0,
                    indexOffset + j * n + n,
                    indexOffset + j * n + 2 * n - 1
            });
        }

        // Faces in the last loop are triangles (connecting to the apex)
        for (int i = 0; i < n - 1; i++) {
            mesh.roofFaces.add(new int[]{
                    indexOffset + (rows - 1) * n + i,
                    indexOffset + (rows - 1) * n + i + 1,
                    centreIdx
            });
        }
        mesh.roofFaces.add(new int[]{
                indexOffset + (rows - 1) * n + n - 1,
                indexOffset + (rows - 1) * n + 0,
                centreIdx
        });

        // Create bottom face
        int[] bottomFace = new int[n];
        for (int i = 0; i < n; i++) {
            bottomFace[i] = n - 1 - i; // Reverse order for correct normal
        }
        mesh.bottomFaces.add(bottomFace);

        mesh.verts = verts;
        return mesh;
    }



}
