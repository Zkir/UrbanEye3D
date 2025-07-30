package ru.zkir.urbaneye3d.roofgenerators;

import ru.zkir.urbaneye3d.RenderableBuildingElement;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;

import java.util.ArrayList;
import java.util.List;

public class MesherCrossGabled extends RoofGenerator{
    @Override
    public Mesh generate(RenderableBuildingElement building) {


        List<Point2D> basePoints = building.getContour();
        double height = building.height;
        double minHeight = building.minHeight;
        double wallHeight = building.wallHeight;

        if (basePoints.size() != 4) {
            // Fallback to flat roof for non-quadrilaterals
            return null;
        }
        Mesh mesh = new Mesh();

        List<Point3D> verts = new ArrayList<>();
        int n = basePoints.size();

        int a_idx = 0;
        int b_idx = 1;
        int c_idx = 2;
        int d_idx = 3;

        // --- Create Vertices ---
        // 1. Base vertices (at the bottom of the walls)
        int baseIdx = 0;
        for (Point2D p : basePoints) {
            verts.add(new Point3D(p.x, p.y, minHeight));
        }

        // 2. Wall top vertices (at the height of the eaves)
        int wallIdx;
        if (wallHeight > minHeight) {
            wallIdx = verts.size();
            for (Point2D p : basePoints) {
                verts.add(new Point3D(p.x, p.y, wallHeight));
            }
        } else {
            wallIdx = baseIdx; // Reuse base vertices if no walls
        }

        // 3. Roof ridge vertices. for cross gables there are two ridges and 4 vertices for them
        int rab_idx=verts.size();
        Point3D rab = verts.get(wallIdx+a_idx).add(verts.get(wallIdx+b_idx)).div(2.0);
        rab.z=height;
        verts.add(rab);

        int rbc_idx=verts.size();
        Point3D rbc = verts.get(wallIdx+b_idx).add(verts.get(wallIdx+c_idx)).div(2.0);
        rbc.z=height;
        verts.add(rbc);

        int rcd_idx=verts.size();
        Point3D rcd = verts.get(wallIdx+c_idx).add(verts.get(wallIdx+d_idx)).div(2.0);
        rcd.z=height;
        verts.add(rcd);

        int rda_idx=verts.size();
        Point3D rda = verts.get(wallIdx+d_idx).add(verts.get(wallIdx+a_idx)).div(2.0);
        rda.z=height;
        verts.add(rda);

        //O - rooftop, where ridges cross.
        int o_idx=verts.size();
        Point3D o = rab.add(rcd).div(2.0);
        o.z = height;
        verts.add(o);

        mesh.verts = verts;

        // --- Create Faces ---

        // Create Walls only if they have height
        if (wallHeight > minHeight) {
            // in case of cross_gabled roof all the walls are pentagon
            mesh.wallFaces.add(new int[]{baseIdx + a_idx, baseIdx + b_idx, wallIdx + b_idx, rab_idx, wallIdx + a_idx});
            mesh.wallFaces.add(new int[]{baseIdx + b_idx, baseIdx + c_idx, wallIdx + c_idx, rbc_idx, wallIdx + b_idx});
            mesh.wallFaces.add(new int[]{baseIdx + c_idx, baseIdx + d_idx, wallIdx + d_idx, rcd_idx, wallIdx + c_idx});
            mesh.wallFaces.add(new int[]{baseIdx + d_idx, baseIdx + a_idx, wallIdx + a_idx, rda_idx, wallIdx + d_idx});

        } else {
            // If there are no walls, the gables are triangles, added to the wall faces
            mesh.wallFaces.add(new int[]{ wallIdx + b_idx, rab_idx, wallIdx + a_idx});
            mesh.wallFaces.add(new int[]{ wallIdx + c_idx, rbc_idx, wallIdx + b_idx});
            mesh.wallFaces.add(new int[]{ wallIdx + d_idx, rcd_idx, wallIdx + c_idx});
            mesh.wallFaces.add(new int[]{ wallIdx + a_idx, rda_idx, wallIdx + d_idx});

        }

        // Create Roof Planes (8 triangles)
        mesh.roofFaces.add(new int[]{wallIdx + a_idx, o_idx, rda_idx });
        mesh.roofFaces.add(new int[]{wallIdx + a_idx, rab_idx, o_idx });

        mesh.roofFaces.add(new int[]{wallIdx + b_idx, o_idx, rab_idx });
        mesh.roofFaces.add(new int[]{wallIdx + b_idx, rbc_idx, o_idx });

        mesh.roofFaces.add(new int[]{wallIdx + c_idx, o_idx, rbc_idx });
        mesh.roofFaces.add(new int[]{wallIdx + c_idx, rcd_idx, o_idx });

        mesh.roofFaces.add(new int[]{wallIdx + d_idx, o_idx, rcd_idx });
        mesh.roofFaces.add(new int[]{wallIdx + d_idx, rda_idx, o_idx });

        // Create bottom face
        int[] bottomFace = new int[n];
        for (int i = 0; i < n; i++) {
            bottomFace[i] = baseIdx + n - 1 - i; // Reverse order for correct normal
        }
        mesh.bottomFaces.add(bottomFace);

        return mesh;
    }
}
