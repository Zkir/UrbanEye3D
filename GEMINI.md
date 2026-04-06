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


* [BUG] Fix a funny bug with `man_mane=bridge`: a linear waterway is painted above area bridge! 
    * Is it fixable at all? Lines are drawn over polygons!	


### Feature candidates

1. **Increase resolution for GroundTile/MapCSS style**.
    * Some kind of smart scaling is required, for the nearest tiles only, because it will create huge performance impact otherwise.
	
2. **Support forests**
    * Since we have trees now, it would be nice to render them on `natural=wood` and `landuse=forest`
	* We already have a plan for it: [NATURAL-WOOD.md](docs/dev/NATURAL-WOOD.md)
	* Could be tricky, because proper implementation require subtraction of roads.
	

3. **Support windows/facades**
    * Buildings with windows are nice.  This feature is present in osm2world, so we also want it. 
	* There is a tag in osm for windows: [window=*](https://wiki.openstreetmap.org/wiki/Key:window).
    * We want to implement "facade" feature similar to X-plane one. https://developer.x-plane.com/article/facade-creation
	* We already have some sample facades: https://github.com/Zkir/VFR_LANDMARKS_3D_RU/blob/master/Facades


4. **Support chimney/frustum**
    * F4 displays chimneys (`man_made=chimney`), we currently do not. To make chimneys look realistic, we need to support 'shape=frustum', like we already support 'shape=hyperboloid'. probably explicit shape=prism should be supported too.


### Ideas for the Further Development

See: [IDEAS.md](docs/dev/IDEAS.md)


## Recent Accomplishments

*   **JOSM-Style Internationalization (i18n):** Implemented a complete, JOSM-style i18n mechanism. This involved wrapping all user-facing UI strings, creating translation template (`.pot`) and language-specific (`.po`) files, and configuring the Maven build to compile them into the binary `.lang` files required by JOSM at runtime. A unit test was also added to ensure the translation files are generated correctly.

*   **MapCSS Style Documentation:** Created a comprehensive `README.md` for the `src/main/resources/mapcss-styles/` directory. This document explains the design philosophy, file structure, and key technical considerations for the project's MapCSS stylesheets, including the non-obvious way JOSM handles image paths for plugins.

### Earlier
See [Devblog](DEVBLOG.md)


## Architectural Notes

*   **Core Principle:** All meshes for all roof shapes must be generated as **watertight** bodies with correct **outward-facing normals**. This is enforced by the `assertWatertight` and `assertNormalsAndConsistency` checks in the `RoofGeometryGeneratorTest`.
*   **Normal Vector Validation:** Validating that normals face "outward" is complex for non-convex shapes (like buildings with courtyards or complex roofs like an onion dome). A naive check against the geometric center of the mesh will fail. The robust approach is a two-step process:
    1.  **Consistency Check:** Ensure the entire mesh has a consistent winding order. This can be verified by checking that for every edge, the two adjacent faces traverse the edge in opposite directions.
    2.  **Orientation Anchor:** After consistency is confirmed, check the absolute orientation of a single "anchor" face. A bottom face is ideal, as its normal should always point downwards (negative Z). If the anchor is correct and the mesh is consistent, the entire model is correctly oriented.
*   **Mesh Manipulation:** The `Mesh.java` class contains key helper methods for creating complex geometry:
    *   `Mesh.extractFaces(sourceMesh, faces)`: A static utility method to create a new, clean `Mesh` containing only a specific subset of faces from a source mesh. It handles the re-indexing of vertices, ensuring the new mesh is self-contained.
    *   `mesh.extrude(depth)`: An instance method that creates a solid body from a 2D mesh shell (like a roof). It generates side walls and a bottom by extruding the boundary edges downwards. Correct vertex winding order is crucial for this operation to produce a watertight mesh.
*   **Plugin Entry Point:** `UrbanEye3dPlugin.java` is the main entry point, responsible for initializing the 3D dialog window (`DialogWindow3D.java`).
*   **3D Scene Management:** `DialogWindow3D.java` creates and manages the `Renderer3D` canvas and handles user input for navigation (orbit, pan, zoom).
*   **Rendering:** `Renderer3D.java` is the core of the visualization. It uses JOGL (OpenGL for Java) to render the `Scene`. It manages the camera, lighting, and the main rendering loop. It also handles the switch between solid and wireframe modes.
*   **Scene Composition:** The `Scene.java` class holds the collection of `RenderableBuildingElement` objects that need to be drawn. It's responsible for rebuilding the scene when OSM data changes.
*   **Building Representation:** `RenderableBuildingElement.java` is a data class that holds all the necessary information to render a single building or building part, including its footprint, height, colors, and roof shape.
*   **Roof Generation:** The `roofgenerators` package contains the logic for creating the 3D geometry for different roof shapes.
    *   `RoofShapes.java`: An enum that maps OSM `roof:shape` tags to specific `RoofGenerator` implementations.
    *   `RoofGenerator.java`: An interface for all roof generation classes.
    *   `Mesher... .java`: Concrete implementations for each roof shape (e.g., `MesherHipped`, `MesherGabled`, `MesherSkillion`). Each is responsible for generating a `Mesh` object.
*   **Geometry:** The `utils` package contains helper classes for geometry and color.
    *   `Mesh.java`: A data structure to hold the vertices, faces, and normals of a 3D object.
    *   `Point2D.java`, `Point3D.java`: Basic 2D and 3D point representations.

## Code Structure

```
src
├── main
│   └── java
│       └── ru
│           └── zkir
│               └── urbaneye3d
│                   ├── UrbanEye3dPlugin.java    // Main plugin class, entry point
│                   ├── DialogWindow3D.java      // The dockable 3D window
│                   ├── Renderer3D.java          // OpenGL rendering logic
│                   ├── Scene.java               // Manages the objects to be rendered
│                   ├── RenderableBuildingElement.java // Data for a single building
│                   ├── UrbanEye3dPreferences.java // Manages user preferences
│                   ├── Materials.java           // Defines building material properties
│                   │
│                   ├── josmactions              // UI Actions (menu items, keyboard shortcuts)
│                   │   ├── OpenF4MapAction.java
│                   │   ├── ResetCameraAction.java
│                   │   ├── ToggleFakeAOAction.java
│                   │   └── ToggleWireframeAction.java
│                   │
│                   ├── roofgenerators           // Logic for creating roof geometries
│                   │   ├── RoofShapes.java      // Enum mapping roof tags to generators
│                   │   ├── RoofGenerator.java   // Interface for all roof generators
│                   │   ├── MesherFlat.java      // Generator for flat roofs
│                   │   ├── ...                  // Other roof generator implementations (Hipped, Gabled, etc.)
│                   │   └── linearprofile        // Sub-package for roofs with linear profiles (Gambrel, Saltbox)
│                   │       ├── LinearProfiles.java
│                   │       └── ...
│                   │
│                   ├── validator                // JOSM validation tests
│                   │   ├── SpatialConsistencyChecks.java
│                   │   └── TagChecks.java
│                   │
│                   └── utils                    // Helper classes
│                       ├── Contour.java         // Data structure and utils for building 2D outline.
│                       ├── Mesh.java            // 3D mesh data structure
│                       ├── Point2D.java         // 2D point
│                       ├── Point3D.java         // 3D point
│                       ├── ColorUtils.java      // Color manipulation helpers
│                       └── ObjExporter.java     // Exports mesh to .obj file for debugging
│
└── test
    └── java
        └── ru
            └── zkir
                └── urbaneye3d
                    ├── RoofGeneratorTopologyTest.java     // Tests mesh topology (e.g., watertightness)
                    ├── RoofGeneratorGoldenMasterTest.java // Golden master tests for roof shapes
                    ├── SceneTest.java                     // Tests for scene creation logic
                    ├── ValidatorTest.java                 // Tests for validator logic
                    └── utils
                        └── PolygonSelfIntersection.java   // Tests for polygon helper functions
```




## Unit Testing

A unit testing suite has been set up using JUnit 5. To run the tests, execute `mvn test` from the project root.

The primary test class is `RoofGeometryGeneratorTest.java`, which focuses on validating the 3D geometry produced for different roof shapes.




## Learnings

*   **JOSM I18n Build Process:** JOSM's internationalization system relies on a specific, non-trivial build process. It uses `xgettext` to extract strings into a `.pot` template, which developers use to create language-specific `.po` files. A crucial, JOSM-specific Perl script (`i18n.pl`) then compiles these text-based `.po` files into binary `.lang` files, which are the only format the JOSM runtime can load. This entire toolchain must be replicated in the plugin's build system.
*   **Maven for JOSM I18n:** While the official JOSM build uses Ant, this process can be successfully replicated in Maven by using the `exec-maven-plugin` for fine-grained control over the required command-line tools. The process involves three distinct executions:
    1.  A PowerShell/shell command to generate a list of source files.
    2.  An `xgettext` command to extract the strings.
    3.  A `perl` command to run the `i18n.pl` script for compilation.
*   **Build Environment Pitfalls (PowerShell & `mvn clean`):**
    *   **Encoding & BOM:** When using PowerShell to generate file lists for external tools like `xgettext`, it is critical to create files without a Byte Order Mark (BOM). Standard redirection (`>`) and `Out-File -Encoding utf8` both produce files with a BOM, which can cause parsing errors. The correct method is to use `[System.IO.File]::WriteAllLines()`, which creates a clean, BOM-less file.
    *   **Maven `clean` Lifecycle:** Any build step that writes to the `target` directory must be made idempotent and not assume the directory exists. The `mvn clean` command deletes the entire directory, so any subsequent command that needs to write a file into `target` will fail with a `DirectoryNotFoundException` unless it first checks for and creates the directory if it's missing.

*   **Documenting Framework Quirks:** It is vital to document non-obvious framework behaviors to ensure long-term maintainability. For instance, the way JOSM resolves image paths for plugins is counter-intuitive: it uses paths relative to the main `resources/images/` directory, not relative to the MapCSS file that references them. Capturing such details in developer-facing documentation (like a README) is crucial to prevent future confusion and streamline the development process for anyone working on the styles.

*   **Maven Resource Paths:** It's crucial to be mindful of how resource paths are resolved. The config file was correctly loaded using a path relative to the resources root (`/textures/textures.cfg`), not the full file system path.
*   **JOSM Plugin Lifecycle and `mapFrameInitialized`:**
    * `UrbanEye3dPlugin` is the entry point. It initializes `DialogWindow3D`, which is a `ToggleDialog`. JOSM automatically handles the creation of the menu item and the visibility of the dialog.
    *   The `mapFrameInitialized(oldFrame, newFrame)` method is a dual-purpose callback for both setup (`newFrame != null`) and teardown (`oldFrame != null`). It can be called multiple times during a JOSM session, especially when all data layers are removed and a new `MapFrame` is created.
    *   Failure to properly manage object lifecycles within this method can lead to "zombie" objects. Specifically, if a `ToggleDialog` is created but not explicitly destroyed when the `oldFrame` is discarded, it remains in memory and continues to receive global events. This causes bugs that are extremely difficult to diagnose, such as event handlers firing multiple times or variables appearing to "reset" magically (which is actually the state of a different "ghost" instance).
    *   The correct pattern is to call `destroy()` on any existing dialog instance when `oldFrame` is not null. This ensures all global listeners are unregistered.
    *   Furthermore, `destroy()` methods should be made idempotent (safe to be called more than once), for example by using a boolean flag. This provides a robust defense against unexpected, repeated lifecycle events from the framework, preventing `IllegalArgumentException`s when trying to remove an already-removed listener.
*   **Event Handling:** The plugin listens for changes in the OSM data (`DataSetListener`) and map view (`MapView.addZoomChangeListener`) to trigger scene updates and redraws.
    *   The `dataChanged` event on a `DataSet` is a "rollup" event. When a large number of primitives are added or modified within a `beginUpdate()`/`endUpdate()` block (as happens when loading a file or merging an API download), JOSM fires a single, generic `dataChanged` event instead of many specific ones (e.g., `primitivesAdded`). This makes it a reliable and efficient trigger for actions that should run once after a significant data load.
*   **OpenGL with JOGL:** The rendering is done in `Renderer3D` using the JOGL library, which provides Java bindings for OpenGL. The rendering pipeline is currently a fixed-function pipeline (`glBegin`/`glEnd`), with plans to move to a modern shader-based pipeline for features like SSAO.
*   **Imagery Ground Plane Rendering:**
    *   Implementing an off-screen renderer for JOSM imagery is complex due to the tight coupling of the `MapView` with its state (`MapViewState`) and the rendering pipeline.
    *   A `VirtualMapView` subclass was necessary to "trick" the imagery layer into rendering at a desired fixed meter-per-side dimension (e.g., 1000m) at a high resolution (e.g., 2048x2048px).
    *   Reflection is required to swap the `TileCoordinateConverter` in `AbstractTileSourceLayer` to ensure the imagery is drawn according to the `VirtualMapView`'s scale and bounds.
    *   **Crucial Insight: JOSM Scale and Mercator Projection:** The JOSM renderer operates in **EastNorth projection units per pixel**, not real-world meters per pixel. In the Mercator projection, the conversion factor between real meters and EastNorth units is `cos(latitude)`. Therefore, to accurately render imagery at a specific meter-per-pixel scale, it is essential to convert this to the equivalent EastNorth units per pixel (`scale_EN = scale_meters / cos(latitude)`). Failing to do so results in significant scale mismatches.
    *   **Dynamic Texture Resolution:** JOSM imagery layers are fundamentally tile-based and quantized into discrete zoom levels. To ensure the ground plane texture renders sharply and at the correct scale without distortion, the `TEXTURE_SIZE_PIXELS` must be dynamically adjusted. This involves:
        1.  Determining an `idealScale` (e.g., `PLANE_SIZE_METERS / 2048`).
        2.  Finding the nearest `snappedScale` (EastNorth units/pixel) that JOSM would use for a discrete zoom level, using `NativeScaleLayer.ScaleList.getSnapScale()`.
        3.  Calculating the `effectiveTextureSizePixels` based on `PLANE_SIZE_METERS / (snappedScale * cos(latitude))`.
        4.  Providing this `effectiveTextureSizePixels` and `snappedScale` to the `VirtualMapView` for consistent rendering.
*   **Immediate Mode Rendering:** The current rendering approach sends drawing commands to the GPU for each frame directly. While simple, it's less efficient than using Vertex Buffer Objects (VBOs), which would store geometry on the GPU.
*   **Roof Geometry Generation:** The `roofgenerators` package showcases a factory pattern. The `RoofShapes` enum acts as a factory, providing the correct `Mesher` instance for a given `roof:shape` tag. This makes it easy to add new roof shapes without changing the core rendering logic.
*   **Watertight Meshes:** A critical requirement for all generated geometry is that it must be "watertight" (i.e., have no holes). This is crucial for correct rendering and for future features like SSAO or Boolean operations. The `RoofGeometryGeneratorTest` includes checks to enforce this.
*   **TDD for Geometry:** The `RoofGeometryGeneratorTest` is a good example of Test-Driven Development. By creating a test for a new roof shape first, the implementation can be guided by the test results, ensuring correctness from the start.
*   **Coordinate Systems:** There are several coodinate systems (or, more precisely, projections) in JOSM:  geographical Latitude/Longitude (`LatLon` class) from OSM data, and projected East/North (`EastNorth` class). However, the plugin uses only geographical LatLon coordinates and itself performs projection to the 3D Cartesian coordinates used for rendering. Coordinate conversion is done in `Contour.getLocalCoords()` method. EastNorth coordinates should be never used by the plugin, because they are distorted by projection and incomparable with height values.
*   **JOSM Validation Framework:**
    *   Custom validators can be created by extending `org.openstreetmap.josm.data.validation.Test`.
    *   Tests are registered in the plugin's constructor using `OsmValidator.addTest()`.
    *   The `startTest()` method is suitable for global checks that need to be performed on the entire dataset, while `visit()` methods are better for checks on individual primitives.
    *   To provide an automatic fix for a validation error, one can override the `isFixable(TestError)` and `fixError(TestError)` methods within the validation `Test` class. This provides a cleaner approach than using the `.addFix()` method on the `TestError` builder.
*   **JOSM UI:**
    *   A settings button can be added to a `ToggleDialog` by passing the preference class to its constructor.
    *   A custom help topic can be set by overriding the `helpTopic()` method.
    *   New keyboard shortcuts can be added by creating a class that extends `JosmAction` and instantiating it in the plugin's UI initialization.
*   **Test Data Factory Pattern:** When adding tests for the `{switch}` placeholder, an initial attempt to call the `ImageryInfo` constructor directly with `null` arguments resulted in a compilation failure. The issue was resolved by following the existing project pattern of using the `ImageryProvider` enum. This experience reinforces the importance of using established factory patterns for creating complex test data. It leads to cleaner, more maintainable tests and avoids brittle, direct constructor calls. Adhering to a project's established conventions is key to preventing errors and ensuring code quality.
*   **Managing Asynchronous Operations and Race Conditions (Imagery Loading):** When dealing with UI updates that trigger asynchronous background tasks (like tile downloads) and where the UI state can change rapidly (e.g., during map panning), race conditions are prevalent.
    *   **Multiple Queues:** It's crucial to identify all affected asynchronous queues. In this case, `TileCache` (for small TMS tiles) and `GroundTile` (for stitching/painting large textures from smaller tiles) each maintained their own `ExecutorService` and `pendingRequests` maps. Effective cancellation requires addressing all relevant queues.
    *   **"Aggressive" Cancellation for UI Responsiveness:** For highly dynamic UI elements like map imagery during panning, a "sledgehammer" approach of cancelling all pending requests for a given type of update when a new update is triggered is often simpler and more effective than attempting granular, context-aware cancellation. This ensures that only requests relevant to the *current* view are active, preventing queues from exploding and improving perceived responsiveness. This method relies on the fact that older requests quickly become obsolete.
    *   **Granular Cancellation for Resource Management:** While "aggressive" cancellation is good for UI flow, specific resource management (e.g., preventing a single, large texture generation from completing for an evicted tile) benefits from targeted, granular cancellation. The decision of *when* to cancel (on deactivation vs. on destruction) significantly impacts efficiency.

