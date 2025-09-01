package ru.zkir.urbaneye3d.roofgenerators;

import ru.zkir.urbaneye3d.RenderableBuildingElement;
import ru.zkir.urbaneye3d.UrbanEye3dPlugin;
import ru.zkir.urbaneye3d.roofgenerators.linearprofile.LinearProfiles;
import ru.zkir.urbaneye3d.roofgenerators.linearprofile.MesherLinearProfileQuasiRectangular;
import ru.zkir.urbaneye3d.roofgenerators.linearprofile.MesherLinearProfileRectangular;
import ru.zkir.urbaneye3d.utils.*;

import java.util.*;

public class MesherLinearProfile extends RoofGenerator {
    private final LinearProfiles profile_data;

    //this is a "thin mesher". It just dispatches between two meshes
    //for to cases: rectangular and quasi-rectangular

    public MesherLinearProfile(LinearProfiles profile_data) {
        super();
        this.profile_data = profile_data;
    }

    /**
    * main method to be called
    */
    public Mesh generate(RenderableBuildingElement building){
        if (building.getContour().size() == 4) {
            return generateR(building);
        }else{
            return generateQR(building);
        }
    }

    /**
    * Generates roof via "simple" mesher for rectangular roof
    */
    public Mesh generateR(RenderableBuildingElement building){
        var simpleMesher = new MesherLinearProfileRectangular(profile_data);
        return simpleMesher.generate(building);
    }

    /**
    * Generates roof via "complex" mesher for quasi-rectangular roof
    */
    public Mesh generateQR(RenderableBuildingElement building)
    {
        var mesherLinearProfileQR = new MesherLinearProfileQuasiRectangular(profile_data);
        try {
            mesherLinearProfileQR.init(building);
            mesherLinearProfileQR.make();
        }
        catch (Exception e){
            UrbanEye3dPlugin.debugMsg("LinearProfile2 unable to create mesh, with message: " + e.getMessage() );
            // fallback to flat mesher.
            return null;
        }

        Mesh mesh= new Mesh();

        mesh.verts = mesherLinearProfileQR.verts;

        mesh.roofFaces = copyFaces(mesherLinearProfileQR.roofIndices);
        mesh.wallFaces = copyFaces(mesherLinearProfileQR.wallIndices);
        mesh.bottomFaces = copyFaces(mesherLinearProfileQR.bottomFaces);

        /* uncomment if debugging of the mesh is needed !! do not remove !!
        try {
            ObjExporter.saveMeshToObj(mesh, "d:/UrbanEye3D/tests/test.obj" );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
         */

        return mesh;
    }

    List<int[]> copyFaces( List<List<Integer>>  source ){
        List<int[]> result = new ArrayList<>();
        for (List<Integer> innerList : source) {
            int[] arr = new int[innerList.size()];
            for (int i = 0; i < innerList.size(); i++) {
                arr[i] = innerList.get(i); // Автораспаковка Integer -> int
            }
            result.add(arr);
        }
        return result;
    }

}