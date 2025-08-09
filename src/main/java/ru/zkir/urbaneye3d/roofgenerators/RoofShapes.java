package ru.zkir.urbaneye3d.roofgenerators;

public enum RoofShapes {
    //supported roof shapes
    FLAT("flat", new MesherFlat()),
    PYRAMIDAL("pyramidal", new MesherConicProfile("pyramidal")),
    DOME("dome", new MesherConicProfile("dome")),
    HALF_DOME("half-dome", new MesherConicProfile("half-dome")),
    ONION("onion", new MesherConicProfile("onion")),
    SKILLION("skillion", new MesherSkillion()),
    GABLED("gabled", new MesherGabled()),
    HIPPED("hipped", new MesherHipped()),
    MANSARD("mansard", new MesherMansard()),
    ROUND("round", new MesherLinearProfile(MesherLinearProfile.LinerProfiles.ROUND )),
    GAMBREL("gambrel", new MesherLinearProfile(MesherLinearProfile.LinerProfiles.GAMBREL)),
    SALTBOX("saltbox", new MesherLinearProfile(MesherLinearProfile.LinerProfiles.SALTBOX)),
    HALF_HIPPED("half-hipped", new MesherHalfHipped()),
    CROSS_GABLED("cross_gabled", new MesherCrossGabled());

    /* roof shapes yet to be supported.
    CROSS_GABLED("cross_gabled"),
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

    // Дополнительный метод для конвертации из строки
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
