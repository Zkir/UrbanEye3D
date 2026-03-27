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
* [**BUG**, workaround -- **50%**] Fix the **InterruptedException crash**. Exception occures in the josm mapcss engine when a worker thread is terminated. 

       (2026-02-14 03:27:35.601 SEVERE: Exception raised in EDT: java.lang.InterruptedException
        at org.openstreetmap.josm.gui.util.GuiHelper.runInEDTAndWait(GuiHelper.java:228)
        at org.openstreetmap.josm.gui.NavigatableComponent.fireZoomChanged(NavigatableComponent.java:152))
		
    * Termination of a worker process is quite a normal thing, e.g. when the camera is moved and the ground tile is no longer needed. However, josm *prints* (sic!) exception, even without raising it forward. The issue seems to be rather cosmetic (no real harm except dirty log). 
	* Workaround found: do not terminate a process, if it is already running, just cancell task if it have not yet started. This workaround negatevly affects performance.  It is still not clear how a proper fix in josm could look like. NavigatableComponent has STATIC global listeners.  
	* Does this workaround affects satellite layers???


* [BUG] Fix a funny bug with `man_made=bridge`: a linear waterway is painted above area bridge! 
    * Is it fixable at all? Lines are drawn over polygons!	


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
	
4. **Support forests**
    * Since we have trees now, it would be nice to render them on `natural=wood` and `landuse=forest`
	* We already have a plan for it: [NATURAL-WOOD.md](docs/dev/NATURAL-WOOD.md)
	* Could be tricky, because proper implementation require subtraction of roads.

5. **Support chimney/frustum**
    * F4 displays chimneys (`man_made=chimney`), we currently do not. To make chimneys look realistic, we need to support 'shape=frustum', like we already support 'shape=hyperboloid'. probably explicit shape=prism should be supported too.
	



### Ideas for the Further Development

See: [IDEAS.md](docs/dev/IDEAS.md)


## Recent Accomplishments
### March 26, 2026
*   **Implemented Robust Polygon Tessellation for Textured Faces.**
    *   Replaced the simple `quad-only` drawing logic in `drawPolygonUV` with a robust solution that handles polygons with any number of vertices.
    *   The new implementation uses fast paths for common triangles and quads, but utilizes a `GLUtessellator` for complex polygons (5+ vertices).
    *   A custom `TexturedTessellatorCallback` class was implemented with a `combine` method to correctly interpolate UV coordinates for vertices created during tessellation, ensuring textures map correctly even on non-convex faces.
*   **Fixed Inverted UV Textures on Dynamic Atlases.**
    *   Diagnosed and fixed a bug where dynamically generated textures (like the UV debug atlas) appeared upside-down in the live plugin.
    *   The root cause was the difference in behavior between `TextureIO` (which auto-flips textures from streams) and `AWTTextureIO` (which does not flip `BufferedImage` data).
    *   Implemented a `flipImageVertically` helper method in `Renderer3D` to manually correct the atlas orientation before creating the texture, ensuring consistency with OpenGL's coordinate system.
*   **Integrated Facade Rendering into Main Pipeline (Proof of Concept).**
    *   Modified `RenderableElement.createBuildingOrPart` to apply a default facade texture to all main buildings (`building=*`), initiating the full facade generation pipeline (UV generation, texture application) for these objects.
    *   Enhanced `RenderableElement` to carry the dynamically generated `BufferedImage` atlas.
    *   Updated `Renderer3D` to handle these dynamic atlases by creating JOGL `Texture` objects on the render thread and caching them within the `RenderableElement`, thus confirming and implementing the two-stage texture creation pattern.
*   **Refactored Facade Loading Logic for Simplicity and Robustness.**
    *   Centralized all facade loading logic within `FacadeParser`, which now loads both the `.fac` definition and its corresponding texture from a common `/facades/` resource root.
    *   The `FacadeParser.parse()` method now returns a fully-hydrated `FacadeDefinition` object that includes the loaded `BufferedImage`, making it self-contained.
    *   Simplified `FacadeApplicator` by removing all I/O responsibilities, turning it into a pure data processor. This elegant design is cleaner, more robust, and correctly handles asset bundling for production builds.

### March 23, 2026
* Implemented facade generation, and autotes `FacadeGeneratorTest.java` has been created. Proper support of facades in main rendering is yet to be implemented.

### March 22, 2026
*   **Created a TDD-driven `UvGenerator` for Texture Mapping.**
    *   Developed a new `UvGenerator` utility that takes a `Mesh` and produces a valid UV-mapping and a corresponding debug texture atlas.
    *   The process was strictly guided by a new unit test, `UvGeneratorTest`, which creates a hipped-roof building and verifies the generated UV coordinates and atlas image.
    *   The implementation correctly "unwraps" each 3D face to its own 2D plane, packs the resulting shapes into a single atlas using `UvPacker`, and calculates the final UVs, handling local coordinate systems as per detailed user guidance.

*   **Developed a UV Packing Utility with TDD and Visualization.**
    *   Created `UvPacker`, a new utility to find the optimal square size for packing a series of rectangles using a binary search algorithm. This will be foundational for creating texture atlases.
	
### April 07, 2026

*   **Pure Java I18n Compiler :**
    *   Successfully replaced the Perl-based i18n compilation script (`i18n.pl`) with a 100% Java-based solution, removing external script dependencies from the build.
    *   Developed `ru.zkir.easytext.io.PoParser` to parse `.po` and `.pot` files into memory.
    *   Developed `ru.zkir.easytext.io.LangWriter` to compile the parsed data into binary `.lang` files, achieving byte-for-byte identity with the original script's output.
    *   Integrated the new tool into the Maven build lifecycle using `exec-maven-plugin` on the `generate-resources` phase.

*   **I18n Translation Status Reporting:**
    *   Created `ru.zkir.urbaneye3d.I18nStatusTest.java` (a JUnit 5 test) to:
        *   Automatically scan `.pot` and all `.po` files using `PoParser`.
        *   Calculate and report translation coverage per language into `docs/dev/translation-status.md`.
        *   Assert the existence of generated `.lang` files, ensuring build integrity.

### April 06, 2026

*   **JOSM-Style Internationalization (i18n):** Implemented a complete, JOSM-style i18n mechanism. This involved wrapping all user-facing UI strings, creating translation template (`.pot`) and language-specific (`.po`) files, and configuring the Maven build to compile them into the binary `.lang` files required by JOSM at runtime. A unit test was also added to ensure the translation files are generated correctly.

### April 05, 2026

*   **MapCSS Style Documentation:** Created a comprehensive `README.md` for the `src/main/resources/mapcss-styles/` directory. This document explains the design philosophy, file structure, and key technical considerations for the project's MapCSS stylesheets, including the non-obvious way JOSM handles image paths for plugins.


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

