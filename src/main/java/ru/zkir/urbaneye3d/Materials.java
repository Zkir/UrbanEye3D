package ru.zkir.urbaneye3d;

/**
 *  Enum, representing roof and facade materials.
 *  we do not use materials for rendering (yet), just take a default color
 */
public enum Materials {

   BRICK        ("brick",        "#b86848"),
   CONCRETE     ("concrete",     "#a0a0a0"),
   COPPER       ("copper",       "#b0e0c0"),
   GLASS        ("glass",        "#c8d8d8"),
   SLATE        ("slate",        "#686868"),
   STONE        ("stone",        "#d0c8c0"),
   WOOD         ("wood",         "#c09870"),
   ROOF_TILES   ("roof_tiles",   "#d88868"),
   CEMENT_BLOCK ("cement_block", "#a0a0a0"),
   PLASTER      ("plaster",      "#a8a8a8"),
   METAL        ("metal",        "#b0b0b0"),
   THATCH       ("thatch",       "#a88070"),
   SANDSTONE    ("sandstone",    "#a89078"),
   TAR_PAPER    ("tar_paper",    "#3e2a1f");

    final String displayName;
    final String defaultColour;

    Materials(String displayName, String defaultColour) {
        this.displayName = displayName;
        this.defaultColour = defaultColour;
    }

    /*
     * converts string, especially osm-tag, to Material object.
     * Default value is null.
     */
    public static Materials fromString(String text) {
        for (Materials type : Materials.values()) {
            if (type.displayName.equalsIgnoreCase(text)) {
                return type;
            }
        }
        return null;
    }

}
