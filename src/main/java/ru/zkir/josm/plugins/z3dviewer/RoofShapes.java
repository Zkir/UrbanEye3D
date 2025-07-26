package ru.zkir.josm.plugins.z3dviewer;

public enum RoofShapes {
    //supported roof shapes
    FLAT("flat"),
    PYRAMIDAL("pyramidal"),
    DOME("dome"),
    HALF_DOME("half-dome"),
    ONION("onion"),
    SKILLION("skillion"),
    GABLED("gabled"),
    HIPPED("hipped");

    /* roof shapes yet to be supported.


    ZAKOMAR("zakomar"),
    CROSS_GABLED("cross_gabled"),
    ROUND("round"),
    HALF_HIPPED("half-hipped"),
    GAMBREL("gambrel"),
    SALTBOX("saltbox"),
    MANSARD("mansard");

     */

    private final String displayName;

    RoofShapes(String displayName) {
        this.displayName = displayName;
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
}
