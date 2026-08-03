package ru.zkir.urbaneye3d.roofgenerators;

import ru.zkir.urbaneye3d.BuildingRecipe;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;

import java.util.List;

public class MesherFrustum extends RoofGenerator {

    @Override
    public Mesh generate(BuildingRecipe building) {
        List<Point2D> basePoints = building.getContour();
        double height = building.height;
        double minHeight = building.minHeight;

        double topRate = building.hyperboloidTopRate;
        // topRate for frustum is just a simple scaling factor at the top.
        // It's generalized from hyperboloidTopRate.
        if (topRate <= 0) topRate = 0.5;

        Mesh mesh = new Mesh(building.bottomColor, building.color, building.roofColor);
        Point3D centroid = calculateCentroid(basePoints);

        int numPointsPerLayer = basePoints.size();
        double totalHeight = height - minHeight;

        // Bottom layer (at minHeight, scale = 1.0)
        for (Point2D p : basePoints) {
            mesh.verts.add(new Point3D(p.x, p.y, minHeight));
        }

        // Top layer (at height, scale = topRate)
        for (Point2D p : basePoints) {
            double scaledX = centroid.x + (p.x - centroid.x) * topRate;
            double scaledY = centroid.y + (p.y - centroid.y) * topRate;
            mesh.verts.add(new Point3D(scaledX, scaledY, height));
        }

        // Create wall faces
        for (int j = 0; j < numPointsPerLayer; j++) {
            int p1 = j;
            int p2 = (j + 1) % numPointsPerLayer;
            int p3 = numPointsPerLayer + (j + 1) % numPointsPerLayer;
            int p4 = numPointsPerLayer + j;
            mesh.addWallFace(new int[]{p1, p2, p3, p4});
        }

        // Create bottom face
        int[] bottomFace = new int[numPointsPerLayer];
        for (int i = 0; i < numPointsPerLayer; i++) {
            bottomFace[i] = numPointsPerLayer - 1 - i; // Reverse order for correct normal
        }
        mesh.addBottomFace(bottomFace);

        // Create top face (roof)
        int[] topFace = new int[numPointsPerLayer];
        for (int i = 0; i < numPointsPerLayer; i++) {
            topFace[i] = numPointsPerLayer + i;
        }
        mesh.addRoofFace(topFace);

        return mesh;
    }
}
