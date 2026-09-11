# Asset Configuration (MapCSS) for UrbanEye3D

This document describes the [`assets.mapcss`](../src/main/resources/assets.mapcss) configuration file used by UrbanEye3D plugin. While it leverages **MapCSS formalism** (a styling language from OpenStreetMap), it is adapted for a different purpose: **placing 3D objects** based on OSM tags during rendering.

## Overview

- **Formalism**: MapCSS (via JOSM's built-in `MapCSSStyleSource`)
- **Purpose**: Select OSM nodes/ways and define how their 3D assets are rendered
- **Execution**: Rules are compiled once, then matched against primitives at render time via [`AssetConfigLoader`](../src/main/java/ru/zkir/urbaneye3d/assetconfig/AssetConfigLoader.java).


## How It Works

Rules follow standard MapCSS cascade syntax:

```mapcss
node[selector][more-selector] {
    property: value;
}
```

Multiple rules are combined using comma-separated selectors. Matching proceeds top-to-bottom with the **last match wins**. The [`AssetConfig`](../src/main/java/ru/zkir/urbaneye3d/assetconfig/AssetConfig.java) extracts applied properties for each primitive.

Three rendering modes (priority from highest to lowest):

1. `procedure` – invokes a procedural generator using tagged options
2. `model` – loads an existing `.obj` model, optionally colored from `colour` or `material` tags
3. `billboard` – creates a quad texture placeholder with configurable aspect ratio

## Properties Reference

The following properties are supported by UrbanEye3D's custom MapCSS parser:

| Property            | Values                        | Purpose                                                                                                         |
|---------------------|-------------------------------|-----------------------------------------------------------------------------------------------------------------|
| `display`           | `false`                       | Hides the object from rendering (used for underground/indoor items).                                            |
| `procedure`         | Procedural generator ID       | Invokes a procedural generation function (e.g., `"ad_column"`) that creates geometry on-the-fly based on tags.  |
| `model`             | Path to `.obj` file           | Loads a pre-constructed 3D model from the specified path (e.g., `/models/bench.obj`).                           |
| `billboard`         | Path to texture image (`.png`)| Creates a flat billboard quad rendered with this 2D texture. Typically used for trees and bushes.               |
| `height`            | Numeric string / omitted      | For billboards: default height; useed for proper scaling.                                                       |
| `width`             | Numeric string / omitted      | For billboards: default width; used for proper scaling billboard geometry to match the texture's natural width. |
| `rotatable`         | `true` / omitted              | When `true`, allows the object's orientation to be controlled by an explicit OSM `direction` tag. Note: unfortunately, direction tag is NOT defined for many object types in OSM-wiki.     |
| `snap_to_roads`     | `true` / omitted              | When `true`, the object tries to align its heading with nearby roads.                                           |
| `orientation`       | `align_with_parent`/ omitted  | Instructs the object to compute orientation relative to a parent way (fence, wall, road, power line, etc.). For barriers/gates it aligns perpendicular to roads/parents. |
| `scalable`          | `true` / omitted              | When enabled, the model is scaled so that its rendered height matches the `height` tag.                         |
| `colorable`         | `true` / omitted              | If enabled, the object reads OSM's `colour` or `material` tags and paints its first material group accordingly. The `Materials` enum ([`Materials.fromString`](../src/main/java/ru/zkir/urbaneye3d/Materials.java)) drives defaults. |

