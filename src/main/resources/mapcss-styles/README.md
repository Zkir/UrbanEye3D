# UrbanEye3D MapCSS Styles

This directory contains the MapCSS style files used by the UrbanEye3D plugin to render the 2D ground map texture.

## What is MapCSS?

MapCSS is a CSS-like language used by JOSM to style map data. It allows for defining how OpenStreetMap ways, nodes, and relations are rendered in the map view, including their color, width, icons, and textures.

You can learn more about the specifics of MapCSS as implemented in JOSM on the [JOSM Wiki](https://josm.openstreetmap.de/wiki/Help/Styles/MapCSSImplementation).

### Testing and Development

A major advantage of MapCSS is that you can develop and test styles without recompiling the plugin. To test changes:

1.  Open JOSM's Preferences.
2.  Go to the "Map Paint Styles" tab.
3.  Add your local `.mapcss` file to the list of active styles.
4.  JOSM will hot-reload the style, allowing you to see your changes immediately.

## Style Philosophy and Structure

The visual style for the UrbanEye3D ground plane is designed with a 3D context in mind. It is intentionally different from standard 2D map styles like OSM-Carto. The primary goal is to create a more realistic and less cluttered base map that complements the 3D buildings.

The style is split into two main files for modularity:

*   `urbaneye2d.general.mapcss`: Handles all non-road features (land use, water, barriers, etc.).
*   `urbaneye2d.roads.mapcss`: Specifically handles the rendering of the road network.

### `urbaneye2d.general.mapcss`: The General Style

This style is loosely based on the standard OSM-Carto style but is adapted for a 3D viewer and for JOSM's MapCSS engine (OSM-Carto itself uses CartoCSS).

The key principles are:

*   **Realism over Readability:** Colors are chosen to be more natural and less symbolic, even if it means sacrificing the high contrast typically found in 2D cartography.
*   **No 2D Clutter:** Features that are nonsensical on a 3D map are removed. This includes:
    *   **Text labels.**
    *   **2D symbolic icons** (e.g., pictograms for trees) that would look strange when the camera is rotated. Simple area textures (like for sand or scrub) are retained.
*   **Meaningful `landuse`:** Abstract `landuse` areas like `landuse=residential` or `landuse=industrial` are not rendered, as they do not represent a distinct physical ground type. However, tags like `landuse=forest` are kept, as they are a synonym for `natural=wood` and represent a physical feature.

### `urbaneye2d.roads.mapcss`: The Roads Style

Rendering roads with variable widths based on the `lanes=*` tag is a notoriously difficult problem in MapCSS. Existing public styles that attempt this often produce poor visual results with artifacts at intersections. While brilliant proprietary solutions like [OSMPIE](https://osmpie.org/#what-is-osmpie) exist, their code is not available for us to use.

Therefore, we have adopted a simpler, more robust approach:

*   **Fixed Width:** All roads are rendered with a fixed, standard two-lane width.
*   **Multi-layer Rendering:** The style uses multiple stacked layers to create a realistic effect with an asphalt fill, white edge lines, and appropriate centerlines (dashed for one-way, double-solid for two-way).

## Contributions

While this style is a work in progress, any future improvements and contributions should align with the principles outlined above: prioritizing realism, reducing 2D clutter, and maintaining a robust and clean visual representation suitable for a 3D environment.

## A Note on Image Paths

Due to how JOSM's internal rendering system works for plugins, image paths referenced in MapCSS files (e.g., `fill-image: "mapcss-styles/symbols/quarry2.png";`) are **not** relative to the MapCSS file itself. 

Instead, they are resolved relative to the plugin's main resource root's `images` folder (i.e., `src/main/resources/images/`). This means a path like `"mapcss-styles/symbols/quarry2.png"` will actually resolve to `src/main/resources/images/mapcss-styles/symbols/quarry2.png`. This is a crucial detail to remember when adding or modifying image references in the stylesheets.
