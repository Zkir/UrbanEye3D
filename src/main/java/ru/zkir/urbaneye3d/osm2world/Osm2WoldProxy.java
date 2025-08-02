package ru.zkir.urbaneye3d.osm2world;

import com.drew.lang.annotations.NotNull;
import org.osm2world.O2WConverter;
import org.osm2world.map_data.creation.MapDataBuilder;
import org.osm2world.map_data.data.MapData;
import org.osm2world.map_data.data.MapNode;
import org.osm2world.map_data.data.TagSet;
import org.osm2world.math.shapes.TriangleXYZ;
import org.osm2world.scene.Scene;
import org.osm2world.scene.mesh.Mesh;
import org.osm2world.scene.mesh.TriangleGeometry;
import ru.zkir.urbaneye3d.RenderableBuildingElement;
import ru.zkir.urbaneye3d.roofgenerators.RoofShapes;
import ru.zkir.urbaneye3d.utils.ObjExporter;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;


import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Osm2WoldProxy {
    public static ru.zkir.urbaneye3d.utils.Mesh composeMesh(RenderableBuildingElement element) {

        var o2w = new O2WConverter();
        var builder = new MapDataBuilder();

        final boolean DEBUG=false;

        if(DEBUG){
            // this is sample provided by Osm2world author
            var tags = TagSet.of(
                    "building", "yes",
                    "building:levels", "5",
                    "roof:levels", "2",
                    "roof:shape", "flat"
            );

            // coordinates are in meters
            var wayNodes = List.of(
                    builder.createNode(0.0, 0.0),
                    builder.createNode(15.0, 0.0),
                    builder.createNode(15.0, 10.0),
                    builder.createNode(0.0, 10.0)
            );

            builder.createWayArea(wayNodes, tags);
        }else{
            var tags = TagSet.of(element.tags);

            if (!element.hasComplexContour()){
                List<MapNode> wayNodes = new ArrayList<>();
                //we can treat is as simple polygon (closed way)
                var contour=element.getContour();
                for (Point2D point: contour){
                    wayNodes.add (builder.createNode(point.x, point.y));
                }
                builder.createWayArea(wayNodes, tags);

            }else{
                //relation multipolygon
                List<MapNode> outerRing = new ArrayList<>();
                List<List<MapNode>> innerRings =new ArrayList<>();

                var oring = element.getContourOuterRings().get(0);
                for (Point2D point: oring){
                    outerRing.add (builder.createNode(point.x, point.y));
                }

                for (var iring: element.getContourInnerRings()){
                    var innerRing = new ArrayList<MapNode>();
                    for (Point2D point: iring){
                        innerRing.add (builder.createNode(point.x, point.y));
                    }
                    innerRings.add(innerRing);
                }
                //TODO: report BUG in the osm2world API. Buildings with several outer rings are possible.
                builder.createMultipolygonArea(outerRing, innerRings, tags);

            }
        }

        Scene scene = null;
        try {
            scene = o2w.convert(builder.build(), null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ru.zkir.urbaneye3d.utils.Mesh ueMesh = new ru.zkir.urbaneye3d.utils.Mesh();

        int M=0;
        int i=0;
        for (Mesh mesh : scene.getMeshes()) {
            TriangleGeometry tg = mesh.geometry.asTriangles();

            for (TriangleXYZ t : tg.triangles) {
                ueMesh.verts.add(new Point3D (t.v1.x, t.v1.z, t.v1.y));
                ueMesh.verts.add(new Point3D (t.v2.x, t.v2.z, t.v2.y));
                ueMesh.verts.add(new Point3D (t.v3.x, t.v3.z, t.v3.y));
                ueMesh.roofFaces.add(new int[]{i, i+1, i+2});
                i+=3;
            }
            M += tg.triangles.size();

        }
        /*
        try {
            ObjExporter.saveMeshToObj(ueMesh, "d:/test.obj");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
         */

        return ueMesh;
    }
}
