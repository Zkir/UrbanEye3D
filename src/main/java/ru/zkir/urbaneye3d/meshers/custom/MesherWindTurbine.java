package ru.zkir.urbaneye3d.meshers.custom;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.MeshOperations;

import java.awt.Color;
import java.util.SplittableRandom;

import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_WIND_GENERATOR_HEIGHT;
import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getColorByColourAndMaterial;
import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getTagD;

public class MesherWindTurbine {
    public static Mesh generate(OsmPrimitive primitive, SplittableRandom random) {

        Double height;
        Double hub_height;
        Double rotor_diameter;

        double min_height = getTagD("min_height", primitive, 0.0);

        //here we have three parameters, but only two of them are independent. They can appear in different combinations.
        //  Defaults could be tricky.
        //  hub height is more important, because it is used for scaling
        //  also note that the tag for hub height is `height:hub`, instead of more logical `hub:height`
        //TODO: support both `height:hub` and `hub:height`
        if (primitive.hasTag("height")) {
            height = getTagD("height", primitive, DEFAULT_WIND_GENERATOR_HEIGHT + min_height) - min_height;
            rotor_diameter = getTagD("rotor:diameter", primitive, 0.66 * height);
            hub_height = getTagD("height:hub", primitive, height - rotor_diameter/2.0);
        }else if (primitive.hasTag("height:hub")){
            hub_height = getTagD("height:hub", primitive, DEFAULT_WIND_GENERATOR_HEIGHT*0.66);
            rotor_diameter = getTagD("rotor:diameter", primitive, hub_height);
        }else {
            //we have only rotor diameter
            rotor_diameter = getTagD("rotor:diameter", primitive, DEFAULT_WIND_GENERATOR_HEIGHT*0.66);
            hub_height = getTagD("height:hub", primitive, rotor_diameter);
        }

        // The rotor radius cannot exceed the height of the support; otherwise, the rotor will strike the ground.
        rotor_diameter = Math.min( rotor_diameter, hub_height*2*0.9);

        //process colour and material
        Color main_colour = getColorByColourAndMaterial(primitive, "#F0F0F0");

        var ctx = new MeshOperations();

        /*
         * Create rotor and nacelle
         *   We will just load this part from OBJ and scale
         */

        ctx.loadModel("/models/wind_turbine_rotor.obj");
        ctx.setMaterial(0, main_colour);
        ctx.addMaterial(main_colour);
        ctx.addMaterial(main_colour);

        ctx.rotateY(random.nextDouble()*120); // some random phase of rotor rotation
        //ctx.rotate(wind_angle!) // should be wind oriented, like flags!
        ctx.scale(rotor_diameter);
        ctx.translate(0,0, hub_height);

        /*
         * Create hub (column)
         *   It could be cylinder, frustum or even hyperboloid,
         *   we will just create frustum
         */

        //TODO: here we need to create *sub-mesh* for hub (column)
        // We will create vertices and faced directly for now.

        ctx.createCylinder(12);
        double sxy = rotor_diameter/40; //it seems that top diameter of the column is related to rotor diameter.
        ctx.scale(sxy,sxy, hub_height);

        //it also seems  that the base of the column is wider than the top, and the extent of this difference is somehow related to the height.
        ctx.selectVerticesByZ(0);
        ctx.scale(2.0);

        return ctx.getMesh();

    }
}
