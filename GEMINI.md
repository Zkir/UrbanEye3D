# Urban Eye 3D – JOSM 3D Viewer Plugin

## Operation instructions
*   **JAVA version:** use JAVA 11
*   **Definition of Done:** A task is considered DONE only when:
    * `mvn package` completes successfully without any errors.
    * Successful execution of manual test confirmed by the human.
    * Unit test is created or at least proposed.
    * GEMINI.md file is updated, including (but not limiting to) the following sections: *Recent Accomplishments*, *Learnings*, and if necessary, *Next Steps*.  
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

* None currently?
    
#### JOSM patches to monitor

* None currently   
    
### Nice to have in the Next Release     

* None currently   

### Feature candidates

1. **Support windows/facades**
    * Buildings with windows are nice.  This feature is present in osm2world, so we also want it. 
    * There is a tag in osm for windows: [window=*](https://wiki.openstreetmap.org/wiki/Key:window).
    * We want to implement "facade" feature similar to X-plane one. https://developer.x-plane.com/article/facade-creation
    * We already have some sample facades: https://github.com/Zkir/VFR_LANDMARKS_3D_RU/blob/master/Facades

2. **Support objects from pre-made meshes**
    * `highway=street_light`    
    * `amenity=bench` 

3. **Increase resolution for GroundTile/MapCSS style**.
    * Some kind of smart scaling is required, for the nearest tiles only, because it will create huge performance impact otherwise.

4. **Support chimney/frustum**
    * F4 displays chimneys (`man_made=chimney`), we currently do not. To make chimneys look realistic, we need to support 'shape=frustum', like we already support 'shape=hyperboloid'. probably explicit shape=prism should be supported too.
    
6. **Validate overlapping 3D walls**
    * Coplanar walls cause flickering and look bad. 
    * Validator check should be added to find such walls.   
    * This check could be based on finding walls which share common ways/nodes   

5. **Improve Forest Support**
    * We have now support for `natural=wood` and `landuse=forest`, but it can be improved.
    * What can be done: subtraction of road corridors to prevent trees from growing on highways, and considering manual trees for density calculations.
	* We already have a plan for it: [NATURAL-WOOD.md](docs/dev/NATURAL-WOOD.md)

6.  **Display MapCSS-based ground-plane regardless of selected satellite layers**    
    * Currently, 2D Ground Plane only shows up if satellite imagery is disabled. It's not realy convinient ad counter-intuitive. One may want to see sattelite in 2D window and MapCss based layer in 3D windows. 
	* Solution can be to introduce a new switch in plugin Preferences and a keyboard shortcut to quickly turn satellite imagery on and of, if selected.

### Ideas for the Further Development

See: [IDEAS.md](docs/dev/IDEAS.md)


## Recent Accomplishments

### April 14, 2026
*   **Scene Statistics Overlay:**
    *   Implemented a statistics overlay in the top-left corner of the 3D scene, displaying the number of objects, total face count, and average frame rendering time (moving average over the last 60 frames).
    *   Added a new preference setting "Show scene statistics" in the plugin settings to toggle this display.
    *   Ensured full internationalization (i18n) support for the new strings, including updated Russian translations.
    *   Calculated object and face counts efficiently during scene updates.

### April 17, 2026

*   **Graphics Pipeline Optimization - Frustum Culling:**
    *   Implemented **Frustum Culling** in `Renderer3D` to improve rendering performance.
    *   Added bounding box computation to the `Mesh` class to enable efficient culling checks.
    *   The renderer now extracts 6 frustum planes from the ModelView-Projection matrix and skips objects entirely outside the view, while still respecting the distance-based `visibleArea` culling.
    *   This reduces the number of draw calls and vertex data transfers for complex scenes.

### April 13, 2026

*   **Improved Forest Generation (Poisson Disk Sampling):**
    *   Replaced the uniform random tree placement with **Poisson Disk Sampling** using Robert Bridson's O(N) algorithm.
    *   This ensures a more natural and organic distribution of trees, preventing unnatural clusters and overlapping trunks.
    *   Implemented the new `PoissonDiskSampler` utility class.
    *   Configured the minimum distance between trees to be $R = \text{DEFAULT\_TREE\_HEIGHT} / 3.0$ at maximum density, with dynamic scaling based on the user's forest density setting.
    *   Integrated JTS `PreparedGeometry` for highly efficient point-in-polygon filtering during generation.

### April 11, 2026

*   **Automatic Forest Generation:**
    *   Implemented automatic population of `natural=wood` and `landuse=forest` polygons with 3D tree models.
    *   Integrated JTS (Java Topology Suite) for robust polygon triangulation, handling complex forest footprints with holes.
    *   Added a "Forest density" slider to the plugin preferences, allowing users to control the tree density in real-time.
    *   Updated `FlatEarth` utility with inverse coordinate conversion and `toJTSPolygon` helper in `Contour`.
    *   Enhanced `SceneTest` with comprehensive unit tests for forest generation.
    *   Updated `TagInfoGeneratorTest` and `textures.cfg` to support and document the new forest tags.

### Earlier
See [Devblog](DEVBLOG.md)


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
*    There is an autotest that enforces that all po files are converted to lang files and print [report of translation completeness](docs/dev/translation-status.md). 
*    There is still `I18n.bat`, which include calls to traditional josm toolchain. It should not be used in normal process, only in case of bugs in `ru.zkir.easytext` java solution. Note that you are on your own regarding  the installation of gettext and JOSM I18n. 


### 3D Geometry Generation
*   **Core Principle: Watertight Meshes:** All 3D models, especially roofs, must be generated as **watertight** (fully enclosed) meshes with consistent, **outward-facing normals**. This is fundamental for correct rendering and future features like ambient occlusion. This is enforced by unit tests (`RoofGeneratorTopologyTest`). Those autotests have helped greatly during development of  geometry generation code.
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
| I18nStatusTest.java |  Test for checking internationalization status. Reads `.po` and `.pot` files, calculates translation coverage, and verifies the existence of compiled `.lang` files. Generates [translation-status](docs/dev/translation-status.md) report.|
| MapCSSTest.java |  Verifies the syntax of project MapCSS files, the existence of referenced resources (e.g., images), and the rendering of OSM data using MapCSS. |
| RoofGeneratorGoldenMasterTest.java| Compares the output of 3D geometry generators (in OBJ format)against a verified "golden" result to ensure regression stability.  Tests various roof shapes on different bases. |
| RoofGeneratorTopologyTest.java | Tests the topology of generated 3D roof models. Verifies watertightness, correct normals, absence of zero-length edges, self-intersections, and duplicate vertices. Includes tests  for all roof shapes and special cases (with holes, different orientations). |
| SceneTest.java| Integration tests for the Scene component. Verifies the correct construction of the 3D scene from various OSM data (buildings with parts, multipolygons, barriers, trees).  Analyzes how Scene interprets data and forms RenderableElement objects. |
| TagInfoGeneratorTest.java | Does not really test anything, but collects used tags from the source code and produces `taginfo.json`, so we can [take a look at used tags](https://taginfo.openstreetmap.org/projects/urbaneye3d#tags).  |
| ValidatorTest.java |  Tests for custom JOSM validators (SpatialConsistencyChecks, TagChecks). Verifies that validators correctly identify expected errors and do not produce false positives on valid data. |






