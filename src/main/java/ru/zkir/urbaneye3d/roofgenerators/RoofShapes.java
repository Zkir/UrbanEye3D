package ru.zkir.urbaneye3d.roofgenerators;

import ru.zkir.urbaneye3d.roofgenerators.linearprofile.LinearProfiles;

public enum RoofShapes {
    //supported roof shapes
    FLAT("flat", new MesherFlat()),
    PYRAMIDAL("pyramidal", new MesherConicProfile("pyramidal")),
    DOME("dome", new MesherConicProfile("dome")),
    HALF_DOME("half-dome", new MesherConicProfile("half-dome")),
    APSE_GABLED("apse_gabled", new MesherConicProfile("pyramidal")),
    ONION("onion", new MesherConicProfile("onion")),
    SKILLION("skillion", new MesherSkillion()),
    HIPPED("hipped", new MesherHipped()),
    SIDE_HIPPED("side_hipped", new MesherSideHipped()) ,
    MANSARD("mansard", new MesherMansard()),
    GABLED("gabled", new MesherLinearProfile(LinearProfiles.GABLED)),
    ROUND("round", new MesherLinearProfile(LinearProfiles.ROUND)),
    GAMBREL("gambrel", new MesherLinearProfile(LinearProfiles.GAMBREL)),
    SALTBOX("saltbox", new MesherLinearProfile(LinearProfiles.SALTBOX)),
    HALF_HIPPED("half-hipped", new MesherHalfHipped()),
    CROSS_GABLED("cross_gabled", new MesherCrossGabled()),
    STEPS("steps", new MesherSteps());
    /* roof shapes yet to be supported.
    ZAKOMAR("zakomar"),
    */

    private final String displayName;
    private final RoofGenerator mesher;

    RoofShapes(String displayName, RoofGenerator mesher) {
        this.displayName = displayName;
        this.mesher = mesher;
    }

    @Override
    public String toString() {
        return displayName;
    }

    /*
    * converts string, especially osm-tag, to roofShape object.
    * Default value is flat.
    */
    public static RoofShapes fromString(String text) {
        for (RoofShapes type : RoofShapes.values()) {
            if (type.displayName.equalsIgnoreCase(text)) {
                return type;
            }
        }
        return FLAT;
    }

    public RoofGenerator getMesher(){
        return this.mesher;
    }
}
