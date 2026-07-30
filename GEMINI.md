# Urban Eye 3D – JOSM 3D Viewer Plugin

## Operation instructions
*   **JAVA version:** use JAVA 11
*   **Definition of Done:** A task is considered DONE only when:
    * `mvn package` completes successfully without any errors.
    * Successful execution of manual test confirmed by the human.
    * Unit test is created or at least proposed.
    * GEMINI.md file is updated, including (but not limiting to) the following sections: *Recent Accomplishments*, *Architecture and Key Concepts*, and if necessary, *Next Steps*.  *Recent Accomplishments* should incude date, and be focused on value for end-user/product, but without marketing bullshit, not on technical details. 
    * [features.md](docs/features.md) is reviewed and updated if necessary.
*   **Do not suggest git commits**. Git commits in this project are allowed for protein-based developers only.
*   **JOSM source code** can be found in d:\UrbanEye3D\ext_sources\josm_source
*   Use UrbanEye3dPlugin.debugMsg() for debug messages instead of System.out.println().
*   The `GroundPlaneTest` autotest is not stable and should be run several times in case of failure. 

## Goals

* Create a JOSM plugin that displays loaded buildings (including `building:part=*`) and other objects in a separate 3D window, making creation and editing of 3d building in OSM easier.
* Make it possible to generate more realistic 3D buildings based on OSM data, including windows, cornices, doors, entrances and  building passages.

## Next Steps

### Musts for the Next Release 

* Currently, none?
    
#### JOSM patches to monitor

* [MUST BE FIXED] https://josm.openstreetmap.de/ticket/24699 
    
### Nice to have in the Next Release     

* None currently   

### Feature candidates

1. **Support windows/facades**
    * Buildings with windows are nice.  This feature is present in osm2world, so we also want it. 
    * There is a tag in osm for windows: [window=*](https://wiki.openstreetmap.org/wiki/Key:window).
    * We want to implement "facade" feature similar to X-plane one. https://developer.x-plane.com/article/facade-creation
    * We already have some sample facades: https://github.com/Zkir/VFR_LANDMARKS_3D_RU/blob/master/Facades

3. **Increase resolution for GroundTile/MapCSS style**.
    * Some kind of smart scaling is required, for the nearest tiles only, because it will create huge performance impact otherwise.

4. **Support chimney/frustum**
    * F4 displays chimneys (`man_made=chimney`), we currently do not. To make chimneys look realistic, we need to support 'shape=frustum', like we already support 'shape=hyperboloid'. probably explicit shape=prism should be supported too.


5. **Improve Forest Support**
    * We have now support for `natural=wood` and `landuse=forest`, but it can be improved.
    * What can be done: subtraction of road corridors to prevent trees from growing on highways, and considering manual trees for density calculations.
	* We already have a plan for it: [NATURAL-WOOD.md](docs/dev/NATURAL-WOOD.md)

### Ideas for the Further Development

See: [IDEAS.md](docs/dev/IDEAS.md)

## Recent Accomplishments

### Jul 30, 2026

*   **Integrated JOSM MapCSS Engine for Assets (Интеграция движка MapCSS из JOSM):**
    *   **Native Parser Adoption:** Replaced the custom regex-based `AssetRuleParser` and `Selector` implementations with JOSM's native `MapCSSStyleSource` and `Cascade` engine. This significantly reduces custom code and ensures 100% compatibility with standard MapCSS features (expressions, complex selectors, operators).
    *   **Support for CSS Cascading:** The asset configuration now supports standard CSS cascading and property inheritance. Common properties (like `rotatable` or `snap_to_roads`) can be defined in base rules and automatically inherited by more specific rules, reducing duplication in `assets.mapcss`.
    *   **Refactored Asset Configuration:** Simplified the `AssetRule` data model and updated `AssetConfigLoader` to utilize the JOSM style loading pipeline.
    *   **Updated Tooling:** Adjusted `TagInfoGeneratorTest` to maintain tag extraction capabilities using the new configuration structure.

### Jul 26, 2026

* Support for street furniture via pre-made models:
    * **Recycling Container:** Added support for `amenity=recycling` using a new 3D model with a green body and grey lid.
    * Bus stop, both with shelter and just a post with sign.
 The plugin  distinguishes between sheltered stops (`shelter=yes`) and standard sign-on-a-pole stops.
	    *  Some support for transparency for glass panels. Enhanced the rendering pipeline and `ObjImporter` to support semi-transparent materials. The system now correctly parses the `d` (dissolve) parameter from `.mtl` files and applies alpha blending in OpenGL. This allows for realistic rendering of glass surfaces, such as those in the new 3D bus stop model.
    * Set of models for `tourism=information`.
        *   `information=board`: Large information stands with a sturdy wooden-post design.
        *   `information=post`: Smaller pillar with information plate.
        *   `information=guidepost`: Signposts with arrow-shaped indicators pointing in multiple directions.
    *   **Fire Hydrant Model:** Low-poly 3D model for `emergency=fire_hydrant`.
	
### Jul 25, 2026	
* Support for `natural=shrub`. 
    * Rose bush texture	added
	* Refactoring for more general billboards done

### Jul 24, 2026
*   **Implemented Automatic Pixel-Based Culling:** Removed manual distance thresholds (`maxVisibleDistance`) in favor of a professional engine-like approach. The renderer now automatically calculates the projected screen area of each object in pixels based on its 3D bounding box and camera distance. Objects smaller than N pixels are automatically culled, significantly improving performance in dense scenes without any manual configuration.

### Jul 19, 2026
*   **Universal Asset Configuration:** Replaced hardcoded object mappings and `textures.cfg` with a new, extensible `assets.mapcss` format. The new system cleanly separates configuration from code, uses MapCSS-like specificity rules (e.g., handling OSM taxonomy like `species` > `leaf_type`), and routes assets to their respective procedural, model, or billboard generators dynamically.

### Jul 18, 2026
*   **Added support for `direction` tag for benches:** Models for `amenity=bench` can now be correctly rotated by reading the `direction` tag.

### Jun 29, 2026
*   **Added support for `amenity=bench`:**  `amenity=bench` is rendered using pre-generated 3D model.
*   **Implemented OBJ Material (.mtl) support:** the `ObjImporter` was significantly refactored to parse `.mtl` files and apply material *colors* to faces based on `usemtl` commands.
*   **Developed the Asset Sanity Test (`AssetSanityTest.java`)** to ensure the integrity and documentation of all project assets.
    *   The test inventories all 3D models (`.obj`) and textures (`.png`) and verifies them against a master list defined in the test file.
    *   It performs a "sanity check" by loading each asset to ensure it is not corrupt, and it extracts details like face count for models and dimensions for textures.
    *   On a successful run, it automatically generates an `ASSETS-LIST.md` file, serving as a detailed manifest with metadata, licensing information, and asset details.

### Earlier
See [Devblog](DEVBLOG.md)
*   See [Devblog](DEVBLOG.md) for the full development history and recent changes.


## Architecture and Key Concepts

This section combines high-level architectural overview with key lessons learned during development.

### Code Structure

```
src
├── main
│   └── java
│       └── ru
│           └── zkir
│               ├── customtms              // Module for working with satellite imagery (TMS).
│               │   └── ...                // Contains the implementation for tile loading and caching,
│               │                          // as well as the definition of imagery providers. 
│               │                           
│               ├── easytext               // Internationalization (i18n) module, implemented purely in Java.
│               │   └── ...                // Replaces external utilities for parsing PO/POT files
│               │                          // and compiling binary LANG files for JOSM.
│               │
│               └── urbaneye3d                   // Main module for the "UrbanEye3D" JOSM plugin.
│                   ├── UrbanEye3dPlugin.java    // Main plugin class, entry point.
│                   ├── DialogWindow3D.java      // Dockable window for displaying the 3D scene.
│                   └── ...                      // All other files :) 
│
└── test
    └── java
        └── ru
            └── zkir
                ├── customtms              // Tests for the TMS engine. 
                │   └── ...                //More or less independent from main plugin funtionality 
                │
                └── urbaneye3d
                    ├── RoofGeneratorTopologyTest.java     // Tests the topology of generated 3D roof models.
                    └── ...                                // Other important tests :)  
           
```

### JOSM Framework Integration
*   **Entry Point & UI:** The plugin is initiated by `UrbanEye3dPlugin.java`, which launches the main dockable window, `DialogWindow3D.java`. This dialog manages the `Renderer3D` canvas, which handles all OpenGL rendering.
*   **Text Rendering in OpenGL:** To display 2D text over a 3D scene in JOGL, `com.jogamp.opengl.util.awt.TextRenderer` is a powerful tool. It requires initialization with a Font and follows a lifecycle: `beginRendering(width, height)`, followed by `draw(text, x, y)` calls, and finally `endRendering()`.
*   **Internationalization with Placeholders:** For localizing strings that contain dynamic data (like counts or times), the JOSM `tr()` function supports positional placeholders (e.g., `{0}`, `{1}`). This is much more robust than manual string concatenation, as it allows translators to reorder the data as needed for their language's grammar.
*   **Extensibility:** The plugin extends the JOSM environment in several ways:
    *   **Validation:** Custom tests are added to the JOSM validator by extending `org.openstreetmap.josm.data.validation.Test`.
    *   **Actions & Shortcuts:** New keyboard shortcuts are created by extending `JosmAction`.
    *   **Preferences:** A settings panel is added to the `ToggleDialog` by providing a preference class to its constructor.
*   **Documenting Quirks:** Experience has shown it is vital to document non-obvious framework behaviors. For example, JOSM's MapCSS engine resolves image paths relative to the global `resources/images/` directory, not the CSS file's location. Documenting these discoveries saves significant time for future development.

### Internationalization
*    JOSM uses a non-trivial internationalization (i18n) system that compiles text-based `.po` files into binary `.lang` files using a specific Perl script. `.po` files are created via xgettext utility, which is a living classics of the industry, but is still an external dependency.  
*    We have rewritten everything into pure Java (see `ru.zkir.easytext` package), both collecting string for pot creation and compling `po` intoto `lang`. Both functions are integrated into Maven build (pom.xml) using the `exec-maven-plugin`.
*    There is an autotest that enforces that all po files are converted to lang files and print [report of translation completeness](docs/translation-status.md). 
*    There is still `I18n.bat`, which include calls to traditional josm toolchain. It should not be used in normal process, only in case of bugs in `ru.zkir.easytext` java solution. Note that you are on your own regarding  the installation of gettext and JOSM I18n. 


### Botanical Engine
*   **Species Normalization:** The `TreeSpeciesDatabase` class handles the normalization of botanical names, including hybrid symbols (standardizing to '×') and cultivar formatting.
*   **Cultivar Handling:** Names in the `Genus 'Cultivar Name'` format are recognized and automatically mapped to the parent genus for attribute inference (leaf type, leaf cycle) while preserving the specific name in validation.
*   **Tag Enrichment:** The plugin automatically enriches OSM objects with `leaf_type` and `leaf_cycle` tags derived from their `species` or `genus` tags using an internal database.
*   **Biological Priority:** Botanical family information (sourced via POWO) is used as the primary authority for `leaf_type`. Species in the `Arecaceae` family are always typed as `palm`, while conifer families (Pinaceae, etc.) are always `needleleaved`, overriding potentially incorrect OSM tags or wiki data.
*   **Geographic Defaults:** When both the `species` and `leaf_type` tags are missing, the engine uses a built-in spatial database (`spatial_stats_5x5.json`) to determine the most likely `leaf_type` based on geographic coordinates. This weighted probability approach ensures realistic local vegetation (e.g., preventing palms in northern latitudes).

### 3D Geometry Generation
*   **Core Principle: Watertight Meshes:** All 3D models, especially roofs, must be generated as **watertight** (fully enclosed) meshes with consistent, **outward-facing normals**. This is fundamental for correct rendering and future features like ambient occlusion. This is enforced by unit tests (`RoofGeneratorTopologyTest`). Those autotests have helped greatly during development of  geometry generation code.
*   **Non-triangulated Meshes:** The plugin deliberately maintains meshes in their original, non-triangulated form (using polygons and quads where possible). This is essential for the **wireframe mode**, as triangulated meshes would appear cluttered and confusing with unnecessary diagonal lines. Keeping the original polygons also makes it immediately obvious what kind of geometry is being generated by the plugin's algorithms.
*   **Coordinate System:** The plugin deliberately avoids using JOSM's projected `EastNorth` coordinates. Instead, it uses geographic `LatLon` coordinates and performs its own projection to a local 3D Cartesian system. This is crucial because `EastNorth` coordinates are distorted by map projection and are not directly comparable to height values, which would lead to malformed 3D shapes.
*   **Roof Generation Factory:** The `roofgenerators` package uses a factory pattern. The `RoofShapes` enum maps OSM `roof:shape` tags to specific `Mesher` implementations (e.g., `MesherHipped`), making the system easily extensible for new roof types.


### Rendering Pipeline
*   **Technology:** The scene is rendered using JOGL (OpenGL for Java). The current implementation uses an immediate-mode-style pipeline, with plans to modernize it with shaders.
*   **Ground Plane Imagery:** 
    *  We have two modes for ground plane: **satellite imagery** (loaded from TMS) and **live data** based imagery (rendered from the loaded osm data on-the-fly using MapCSS). Both modes faced significant challenges.
    *  Josm has a lot of different satellite layers, but it tightly coupled with the main map window. I failed to reuse existing josm code in the plugin and had to create own(!) simple TMS rendering library, [ru.zkir.customtms](src/main/java/ru/zkir/customtms). It works fine, but probably should be eventually replaced with 'standard' josm calls, because some layers, e.g. MapBox, cannot be used without API key.
    *  JOSM MapCSS engine is also quite strange. I've managed to decouple it from JOSM main window, so we have now [own MapCSS styles](src/main/resources/mapcss-styles) for 3D window. However, JOSM MapCSS engine has some single-threaded bottlenechs and some bugs (rendering cannot be properly interrupted). So it's a big area for [performance] improvement. 


### Testing Strategy / Test driven development
*   A unit testing suite has been set up using JUnit 5. To run the tests, execute `mvn test` from the project root.
*   A Test-Driven Development (TDD) approach proved highly effective in this project. Since it's a JOSM plugin, you cannot debug it directly. However, autotests can be run and debugged separately, without JOSM. So it make sence to develop some feature test it in isolation.
*   Automated checks for mesh validity do not let AI/LLM produce crap and report success.
*   There are several autotests for different things, both "functional" (to test functionality) and "pseudo tests" to collect statistics. 
   

|Test name | Details | 
|---|---|
| AssetListTest.java   |  Test for verifying and documenting project assets (textures, models). Scans the src/main/resources directory, compares found assets with a master list, and generates ASSET-LIST.md. |
| GroundPlaneTest.java | Verifies the correct creation, loading, and rendering of Ground Plane tiles for satellite imagery or MapCSS data. Includes tests for cache clearing and behavior during rapid panning. |
| I18nStatusTest.java |  Test for checking internationalization status. Reads `.po` and `.pot` files, calculates translation coverage, and verifies the existence of compiled `.lang` files. Generates [translation-status](docs/translation-status.md) report.|
| MapCSSTest.java |  Verifies the syntax of project MapCSS files, the existence of referenced resources (e.g., images), and the rendering of OSM data using MapCSS. |
| RoofGeneratorGoldenMasterTest.java| Compares the output of 3D geometry generators (in OBJ format)against a verified "golden" result to ensure regression stability.  Tests various roof shapes on different bases. |
| RoofGeneratorTopologyTest.java | Tests the topology of generated 3D roof models. Verifies watertightness, correct normals, absence of zero-length edges, self-intersections, and duplicate vertices. Includes tests  for all roof shapes and special cases (with holes, different orientations). |
| SceneTest.java| Integration tests for the Scene component. Verifies the correct construction of the 3D scene from various OSM data (buildings with parts, multipolygons, barriers, trees).  Analyzes how Scene interprets data and forms RenderableElement objects. |
| TagInfoGeneratorTest.java | Does not really test anything, but collects used tags from the source code and produces `taginfo.json`, so we can [take a look at used tags](https://taginfo.openstreetmap.org/projects/urbaneye3d#tags).  |
| TreeSpeciesReportTest.java | Generates a Markdown report (`docs/tree_species.md`) listing all supported tree species from the internal database. |
| ValidatorTest.java |  Tests for custom JOSM validators (SpatialConsistencyChecks, TagChecks). Verifies that validators correctly identify expected errors and do not produce false positives on valid data. |


### OSM Data Processing Pipeline (misc subproject)
The `misc` folder contains a specialized subproject for large-scale processing of OpenStreetMap data. This pipeline starts with the global `planet-latest.osm.pbf` file and serves to extract, analyze, and enrich botanical and building data for the main plugin.
*   There are the following main goals for this pipeline:
    * Create the tree species list (`src/main/resources/data/tree_species.csv`) which is used to infer `leaf_type` tag, in case it is missing, in order to select most appropriate tree texture/model.
    * Generate geographic tree statistics (`src/main/resources/data/spatial_stats_5x5.json`) based on OSM data to provide realistic rendering defaults when explicit tags are missing.
	* Correct typo errors in `species` tag in the OSM database itself, creating osm-files with changes, so that they can be uploaded via JOSM.
	* Create smart defaults for various building attributes, depending on building type (`building=*` value). This is not yet used currently by the plugin.
*   See [misc/GEMINI.md](misc/GEMINI.md) for details.

---
The Urban Eye is watching you!