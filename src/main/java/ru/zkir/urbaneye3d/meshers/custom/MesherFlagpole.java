package ru.zkir.urbaneye3d.meshers.custom;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import ru.zkir.urbaneye3d.utils.ColorUtils;
import ru.zkir.urbaneye3d.utils.FlagsDatabase;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point3D;

import java.awt.Color;
import java.util.Map;
import java.util.SplittableRandom;

import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getColorByColourAndMaterial;
import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getFirstValue;
import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getTagD;
import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getTagStr;

public class MesherFlagpole {
    public static Mesh generate(OsmPrimitive primitive, SplittableRandom random) {
        final int poleSegments = 8;
        final int flagSegments = 8;
        final double DEFAULT_FLAGPOLE_HEIGHT = 10.0;

        if (primitive.isDeleted()) return null;

        String shape = getTagStr("shape", primitive, "prism");
        double min_height = getTagD("min_height", primitive, 0);
        double height = getTagD("height", primitive, DEFAULT_FLAGPOLE_HEIGHT+min_height)-min_height;


        boolean advertising = primitive.hasTag("flag:type", "advertising");

        double flagHeight = Math.pow(height, 0.5) / 1.9;
        double flagWidth = flagHeight * 1.5;


        double estimatedPolyRadius = Math.pow(height, 0.5) / 63.0;
        double polyRadius = getTagD("diameter", primitive, estimatedPolyRadius*2*1000)/2/1000; //NOTE: default unit for diameter tag is MILLIMETER!
        polyRadius = Math.max(polyRadius, 0.02); // pole should not be too narrow, even if units have messed up, e.g. diameter=1

        double top_rate = shape.equals("frustum") || shape.equals("hyperboloid") ? 0.5 : 1;

        double finialRadius = polyRadius * 1.5 * top_rate;
        double finialHeight = finialRadius * 2;

        //TODO: implement several flags on the same pole
        //  For now just the first one.
        String flagColorStr = getFirstValue(getTagStr("flag:colour", primitive, ""));
        String flagQID =  getFirstValue(getTagStr("flag:wikidata", primitive, ""));

        // If explicit colour or flag:wikidata value is missing, try data-driven inference
        var flagDatabase =  FlagsDatabase.getInstance();
        if (flagQID.isBlank()) {
            flagQID = flagDatabase.getInferredQID(primitive);
        }

        if (flagColorStr.isBlank()) {
            flagColorStr = flagDatabase.getInferredColor(primitive);
        }
        // If still null, use the default
        if (flagColorStr.isBlank()) {
            flagColorStr = "#FFFFFF";
        }

        Color mastColor = getColorByColourAndMaterial(primitive, "#C0C0C0");
        Color flagColor = ColorUtils.parseColor(flagColorStr);
        Color finialColor = ColorUtils.parseColor("#FFD700"); // Gold

        Mesh mesh = new Mesh();
        // Materials: 0: mast, 1: finial, 2: flag
        mesh.materials.add(mastColor);
        mesh.materials.add(finialColor);
        mesh.materials.add(flagColor);

        // If the OSM feature has a country tag, use a rasterised flag texture
        // for the front/back faces of the flag. The texture is generated lazily.
        boolean texturedFlag = false;

        if (flagDatabase.checkQID(flagQID)) {
            mesh.textureName = flagDatabase.getFlagTextureName(flagQID);
            texturedFlag = true;
        }

        //hack. Most advertising flags use vertical format.
        if (advertising && ! texturedFlag) {
            flagWidth  = flagWidth * 0.4;
            flagHeight = height * 0.4;
        }

        // 1. Mast
        int[] bottomIndices = new int[poleSegments];
        int[] topIndices = new int[poleSegments];
        for (int i = 0; i < poleSegments; i++) {
            double angle = (2 * Math.PI / poleSegments) * i;
            double x = polyRadius * Math.cos(angle);
            double y = polyRadius * Math.sin(angle);
            bottomIndices[i] = mesh.addVertex(new Point3D(x, y, 0));
            topIndices[i] = mesh.addVertex(new Point3D(top_rate*x, top_rate*y, height-finialHeight));
        }
        // Walls of the mast
        for (int i = 0; i < poleSegments; i++) {
            int next = (i + 1) % poleSegments;
            mesh.addFace(new int[]{bottomIndices[i], bottomIndices[next], topIndices[next], topIndices[i]}, 0);
        }
        // Top cap of the mast (under the finial)
        int[] topCap = new int[poleSegments];
        for (int i = 0; i < poleSegments; i++) {
            topCap[i] = topIndices[i];
        };
        mesh.addFace(topCap, 0);

        //Bottom cap of the mast (invisible, on the ground)
        int[] bottomCap = new int[poleSegments];
        for (int i = 0; i < poleSegments; i++) {
            bottomCap[i] = bottomIndices[poleSegments - 1 - i];
        };
        mesh.addFace(bottomCap, 0);

        // 2. Finial (A small diamond/octahedron at the top)
        Point3D pTop = new Point3D(0, 0, height );
        Point3D pBottom = new Point3D(0, 0, height - finialHeight);
        int vTop = mesh.addVertex(pTop);
        int vBottom = mesh.addVertex(pBottom);
        int[] midRing = new int[4];
        midRing[0] = mesh.addVertex(new Point3D(finialRadius, 0, pBottom.z + finialHeight / 2.0));
        midRing[1] = mesh.addVertex(new Point3D(0, finialRadius, pBottom.z + finialHeight / 2.0));
        midRing[2] = mesh.addVertex(new Point3D(-finialRadius, 0, pBottom.z + finialHeight / 2.0));
        midRing[3] = mesh.addVertex(new Point3D(0, -finialRadius, pBottom.z + finialHeight / 2.0));

        for (int i = 0; i < 4; i++) {
            int next = (i + 1) % 4;
            mesh.addFace(new int[]{vTop, midRing[i], midRing[next]}, 1);
            mesh.addFace(new int[]{vBottom, midRing[next], midRing[i]}, 1);
        }

        // 3. Flag (Waving strip with thickness)
        double windAngle = 90 * Math.PI / 180.0; // Global wind direction (same for all flags)
        double phaseOffset =  10 * random.nextDouble() ;//Math.abs(primitive.getUniqueId()) % 100) / 10.0; // Random phase start for variety
        double cosW = Math.cos(windAngle);
        double sinW = Math.sin(windAngle);

        double flagTopZ = height - finialHeight - 0.15; // slightly below finial
        double flagBottomZ = flagTopZ - flagHeight;
        double thickness = 0.01; // 1cm thick

        int[] topFront = new int[flagSegments + 1];
        int[] bottomFront = new int[flagSegments + 1];
        int[] topBack = new int[flagSegments + 1];
        int[] bottomBack = new int[flagSegments + 1];

        for (int i = 0; i <= flagSegments; i++) {
            double progress = (double) i / flagSegments;
            double x = progress * flagWidth;
            // The wave amplitude must be zero at the attachment point (x=0)
            double damping = progress;
            double waveH = damping * 0.1 * flagWidth * Math.sin(phaseOffset + progress * 1.5 * Math.PI); // Horizontal wave
            double waveV = damping * 0.04 * flagHeight * Math.sin(phaseOffset * 1.3 + progress * 2.0 * Math.PI); // Vertical wave
            double drop = x * Math.tan(Math.toRadians(20.0)); // 20-degree downward tilt

            // Surface normal to the flag (perpendicular to wind)
            double nx = -sinW;
            double ny = cosW;

            // Front vertices
            double fx = x * cosW - waveH * sinW + nx * (thickness / 2.0);
            double fy = x * sinW + waveH * cosW + ny * (thickness / 2.0);
            topFront[i] = mesh.addVertex(new Point3D(fx, fy, flagTopZ + waveV - drop));
            bottomFront[i] = mesh.addVertex(new Point3D(fx, fy, flagBottomZ + waveV - drop));

            // Back vertices
            double bx = x * cosW - waveH * sinW - nx * (thickness / 2.0);
            double by = x * sinW + waveH * cosW - ny * (thickness / 2.0);
            topBack[i] = mesh.addVertex(new Point3D(bx, by, flagTopZ + waveV - drop));
            bottomBack[i] = mesh.addVertex(new Point3D(bx, by, flagBottomZ + waveV - drop));
        }

        // Faces for the flag (watertight mesh)
        for (int i = 0; i < flagSegments; i++) {
            double u0 = (double) i / flagSegments;
            double u1 = (double) (i + 1) / flagSegments;

            if (texturedFlag) {
                // Front face with UVs
                int uvTL = mesh.addUV(u0, 1.0);
                int uvTR = mesh.addUV(u1, 1.0);
                int uvBR = mesh.addUV(u1, 0.0);
                int uvBL = mesh.addUV(u0, 0.0);
                mesh.addFace(new int[]{topFront[i], topFront[i + 1], bottomFront[i + 1], bottomFront[i]},
                        new int[]{uvTL, uvTR, uvBR, uvBL});
                // Back face with mirrored UVs (so the flag image isn't backward)
                int uvTLb = mesh.addUV(u0, 1.0);
                int uvBLb = mesh.addUV(u0, 0.0);
                int uvBRb = mesh.addUV(u1, 0.0);
                int uvTRb = mesh.addUV(u1, 1.0);
                mesh.addFace(new int[]{topBack[i], bottomBack[i], bottomBack[i + 1], topBack[i + 1]},
                        new int[]{uvTLb, uvBLb, uvBRb, uvTRb});
            } else {
                // Front face
                mesh.addFace(new int[]{topFront[i], topFront[i + 1], bottomFront[i + 1], bottomFront[i]}, 2);
                // Back face
                mesh.addFace(new int[]{topBack[i], bottomBack[i], bottomBack[i + 1], topBack[i + 1]}, 2);
            }
            // Top edge
            mesh.addFace(new int[]{topFront[i], topBack[i], topBack[i + 1], topFront[i + 1]}, 2);
            // Bottom edge
            mesh.addFace(new int[]{bottomFront[i], bottomFront[i + 1], bottomBack[i + 1], bottomBack[i]}, 2);
        }
        // Left edge (near mast)
        mesh.addFace(new int[]{topFront[0], bottomFront[0], bottomBack[0], topBack[0]}, 2);
        // Right edge (far end)
        mesh.addFace(new int[]{topFront[flagSegments], topBack[flagSegments], bottomBack[flagSegments], bottomFront[flagSegments]}, 2);

        return mesh;
    }
}
