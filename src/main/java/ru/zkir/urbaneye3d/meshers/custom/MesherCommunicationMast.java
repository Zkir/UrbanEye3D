package ru.zkir.urbaneye3d.meshers.custom;

import org.openstreetmap.josm.data.osm.OsmPrimitive;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.MeshOperations;

import java.awt.Color;
import java.util.SplittableRandom;

import static java.lang.Math.ceil;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;
import static ru.zkir.urbaneye3d.UrbanEye3dPlugin.DEFAULT_WIND_GENERATOR_HEIGHT;
import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getColorByColourAndMaterial;
import static ru.zkir.urbaneye3d.utils.OsmDataWasher.getTagD;

public class MesherCommunicationMast {
    final static double SECTION_HEIGHT = 8;
    final static double ANTENNA_HEIGHT = 4.75;
    final static double DIAMETER0      = 0.25;
    final static double DIAMETER_STEP  = 0.07;

    /** What we are going to do:
      *  we will create telescopic mast and will put pre-made antenna on the top
      *  as well as some additional equipment to the stem.
     */
    public static Mesh generate(OsmPrimitive primitive, SplittableRandom random) {

        Double height;
        double min_height = getTagD("min_height", primitive, 0.0);
        height = getTagD("height", primitive, DEFAULT_WIND_GENERATOR_HEIGHT + min_height) - min_height;
        long numberOfSections = 0;
        double sectionHeight =  0 ;

        //process color and material
        Color main_colour = getColorByColourAndMaterial(primitive, "#F0F0F0");

        var ctx = new MeshOperations();
        ctx.loadModel("/models/mast_antenna.obj");
        if (height > ANTENNA_HEIGHT) {
            // if height is greater than antenna, we will move antenna up and create several tube sections
            ctx.translate(0, 0, height - ANTENNA_HEIGHT);
            numberOfSections = (long) ceil((height - ANTENNA_HEIGHT) / SECTION_HEIGHT);
            sectionHeight = (height - ANTENNA_HEIGHT) / numberOfSections;
        }else{
            //if the given height is smaller than antenna, we can scale it a bit down.
            double as = height / ANTENNA_HEIGHT;
            ctx.scale(1, 1, as);
        }

        ctx.setMaterial(0, main_colour);
        ctx.setMaterial(1, main_colour);
        ctx.addMaterial(main_colour);
        //ctx.addMaterial(main_colour);

        for(int i=0;i<numberOfSections;i++){
            double diameter = DIAMETER0 + DIAMETER_STEP * (numberOfSections-i-1);
            ctx.createCylinder(12);
            ctx.scale(diameter,diameter,sectionHeight);
            ctx.translate(0,0, i*sectionHeight);
            if (i==0){
                //box with equipment on the lowest section
                ctx.createCube();
                ctx.translate((diameter+1)/2,0, min(sectionHeight-1, 4.0));
            }
            if ((i==numberOfSections-1) && (i!=0)){
                //Some kind of decorative element in the middle of the topmost section
                ctx.createCube();
                ctx.scale(diameter*1.25,diameter*1.25, diameter*1.5);
                ctx.translate(0,0,(i + 0.5)*sectionHeight );
            }
        }

        return ctx.getMesh();
    }
}
