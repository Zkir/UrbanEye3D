package ru.zkir.urbaneye3d.roofgenerators;

import ru.zkir.urbaneye3d.BuildingRecipe;
import ru.zkir.urbaneye3d.RenderableElement;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;

import java.util.List;

import static java.lang.Math.pow;

public class MesherHyperboloid extends RoofGenerator {

    @Override
    public Mesh generate(BuildingRecipe building) {
        List<Point2D> basePoints = building.getContour();
        double height = building.height;
        double minHeight = building.minHeight;
        double wallHeight = building.wallHeight;

        double topRate = building.hyperboloidTopRate;
        double middleRate = building.hyperboloidMiddleRate;

        // Ensure rates are positive and reasonable
        if (topRate <= 0) topRate = 1.0;
        if (middleRate <= 0) middleRate = 1.0;


        Mesh mesh = new Mesh(building.bottomColor, building.color, building.roofColor);

        Point3D centroid = calculateCentroid(basePoints);

        int numSegments = 16; // Number of vertical segments
        int numPointsPerLayer = basePoints.size(); // Number of points in the contour

        double totalHeight = height - minHeight;

        // Calculate scaling factors for top and middle relative to base (at minHeight)
        double scaledTopRate = topRate; // Top contour scales by topRate
        double scaledMiddleRate = middleRate; // Middle contour scales by middleRate

        // Generate intermediate layers
        for (int i = 0; i <= numSegments; i++) {
            double currentZ = minHeight + (double) i / numSegments * totalHeight;
            double relativeZ = (currentZ - minHeight) / totalHeight; // 0.0 at bottom, 1.0 at top

            // Hyperboloid scaling function
            double currentScale = calculateHyperboloidScale(relativeZ, scaledTopRate, scaledMiddleRate);

            for (Point2D p : basePoints) {
                // Scale around centroid
                double scaledX = centroid.x + (p.x - centroid.x) * currentScale;
                double scaledY = centroid.y + (p.y - centroid.y) * currentScale;
                mesh.verts.add(new Point3D(scaledX, scaledY, currentZ));
            }
        }

        // Create faces
        for (int i = 0; i < numSegments; i++) {
            int currentLayerStartIdx = i * numPointsPerLayer;
            int nextLayerStartIdx = (i + 1) * numPointsPerLayer;

            for (int j = 0; j < numPointsPerLayer; j++) {
                int p1 = currentLayerStartIdx + j;
                int p2 = currentLayerStartIdx + (j + 1) % numPointsPerLayer;
                int p3 = nextLayerStartIdx + (j + 1) % numPointsPerLayer;
                int p4 = nextLayerStartIdx + j;
                mesh.addWallFace(new int[]{p1, p2, p3, p4});
            }
        }

        // Create bottom face
        int[] bottomFace = new int[numPointsPerLayer];
        for (int i = 0; i < numPointsPerLayer; i++) {
            bottomFace[i] = numPointsPerLayer - 1 - i; // Reverse order for correct normal
        }
        mesh.addBottomFace(bottomFace);

        // Create top face (roof)
        int[] topFace = new int[numPointsPerLayer];
        int lastLayerStartIdx = numSegments * numPointsPerLayer;
        for (int i = 0; i < numPointsPerLayer; i++) {
            topFace[i] = lastLayerStartIdx + i;
        }
        mesh.addRoofFace(topFace);

        return mesh;
    }

    /**
     * Calculates the scaling factor for a hyperboloid shape at a given relative height.

     * @param relativeZ The relative height (0.0 at minHeight, 1.0 at height).
     * @param topScale The desired scale at the top (relativeZ = 1.0).
     * @param waistScale The desired scale at the "waist" (relativeZ = somewhere in the middle).
     * @return The scaling factor for the current height.
     */
    private double calculateHyperboloidScale(double relativeZ, double topScale, double waistScale) {
        double k1 = waistScale;
        double k2 = topScale;

        if (k1>k2){
            k1=k2; //k1 (waist) is the narrowest, it cannot be greater than top (k2)!!
        }
        if (k1>1){
            k1=1; //also it cannot be wider than bottom, which we took for 1.
        }

        double z0 = -pow(pow(1/k1, 2) - 1, 0.5);
        double z1 = pow(pow(k2/k1, 2) - 1, 0.5);

        // 0 --> z0, 1--> z1
        double zx = z0+relativeZ*(z1-z0);
        double r = pow(pow(zx, 2) + 1, 0.5);

        //UrbanEye3dPlugin.debugMsg(" zprime=" + relativeZ + " z="+zx + " r=" +r );
        if (k2>1){
            return r * k1 / k2;
        }else {
            return r * k1;
        }
    }
}
