package ru.zkir.urbaneye3d.meshers.custom;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import ru.zkir.urbaneye3d.BuildingRecipe;
import ru.zkir.urbaneye3d.Materials;
import ru.zkir.urbaneye3d.RenderableElement;
import ru.zkir.urbaneye3d.utils.Contour;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;

import java.awt.Color;
import java.util.ArrayList;
import java.util.SplittableRandom;

import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_CHIMNEY_HEIGHT;
import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getTagD;
import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getTagStr;

public class MesherChimney {
    public static Mesh generate(OsmPrimitive primitive, SplittableRandom random) {

        if (primitive instanceof Way || primitive instanceof Relation) {
            return null;
        }

        double diameter = getTagD("diameter", primitive, 2); //Unit for diameter is handled inside getTag()
        double min_height = getTagD("min_height", primitive, 0.0);
        double height = getTagD("height", primitive, DEFAULT_CHIMNEY_HEIGHT + min_height) - min_height;

        var material = Materials.fromString(getTagStr("material", primitive, ""));
        var default_colour = ""; // Unified default (BuildingRecipe will handle it)
        if (material!=null){
            default_colour=material.getColor();
        }
        var colour = getTagStr("colour", primitive, default_colour);

        // Support for shape and tapering rates, same as polygon chimneys
        String buildingShape = getTagStr("shape", primitive, "frustum");
        Double topRate = getTagD("hyperboloid:top_rate", primitive.getInterestingTags(), null);
        Double middleRate = getTagD("hyperboloid:middle_rate", primitive.getInterestingTags(), null);

        int segments = 12;
        ArrayList<Point2D> circle = new ArrayList<Point2D>();

        for (int i = 0; i < segments; i++) {
            double angle = (2 * Math.PI / segments) * i;
            double x = diameter / 2 * Math.cos(angle);
            double y = diameter / 2 * Math.sin(angle);
            circle.add(new Point2D(x, y));
        }

        Contour contour = new Contour(circle, "XY");
        contour.removeRedundantNodes();

        BuildingRecipe buildingRecipe = new BuildingRecipe(primitive.getPrimitiveId(), contour, height, 0, 0.1, colour, colour, buildingShape, "", "", null, false, topRate, middleRate);
        Mesh mesh = RenderableElement.composeMesh(buildingRecipe);

        return mesh;
    }

    public static Mesh createSmokeMesh(double height, SplittableRandom random) {
        // Add smoke particles
        Mesh smokeMesh = new Mesh();
        smokeMesh.textureName = "/textures/smoke.png";
        smokeMesh.shaderName = "smoke";

        int numParticles = 15;
        for (int i = 0; i < numParticles; i++) {
            // Add jitter to phase to break periodic spawning
            double phase = ((double) i + random.nextDouble()) / numParticles;
            double size = 3.0 + random.nextDouble() * 4.0; // Larger smoke clouds

            // We use UV coordinates for the particle quad
            int uv00 = smokeMesh.addUV(0, 0);
            int uv10 = smokeMesh.addUV(1, 0);
            int uv11 = smokeMesh.addUV(1, 1);
            int uv01 = smokeMesh.addUV(0, 1);
            int[] texIndices = {uv00, uv10, uv11, uv01};

            // We use glColor to pass extra data: R=phase, G=size, B=random_offset
            Color particleData = new Color((float)phase, (float)Math.min(1.0, size/10.0), (float)random.nextDouble());
            int matIdx = smokeMesh.materials.size();
            smokeMesh.materials.add(particleData);

            double hs = size / 2.0;
            int v0 = smokeMesh.addVertex(new Point3D(-hs, -hs, height));
            int v1 = smokeMesh.addVertex(new Point3D( hs, -hs, height));
            int v2 = smokeMesh.addVertex(new Point3D( hs,  hs, height));
            int v3 = smokeMesh.addVertex(new Point3D(-hs,  hs, height));

            smokeMesh.addFace(new int[]{v0, v1, v2, v3}, matIdx);
            smokeMesh.faceUVs.set(smokeMesh.faceUVs.size()-1, texIndices);
        }
        return smokeMesh;
    }
}
