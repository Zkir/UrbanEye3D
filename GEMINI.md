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

* JOSM parameters (e.g. draw oneway arrows and feature labels) override MapCSS styles in 3D window.  Probably josm patch should be created.
* Make ground tiles to pan more nicely (less flashing, maybe cut by visible area).		
* Make UI more responsive, because it seems that full redrawal of 2d layer after primitive EDIT impacts performance badly.
* [**50%** -- workaround found] Fix the **InterruptedException crash**. Exception occures in the josm mapcss engine when a worker thread is terminated. 

       (2026-02-14 03:27:35.601 SEVERE: Exception raised in EDT: java.lang.InterruptedException
        at org.openstreetmap.josm.gui.util.GuiHelper.runInEDTAndWait(GuiHelper.java:228)
        at org.openstreetmap.josm.gui.NavigatableComponent.fireZoomChanged(NavigatableComponent.java:152))
		
    * Termination of a worker process is quite a normal thing, e.g. when the camera is moved and the ground tile is no longer needed. However, josm *prints* (sic!) exception, even without raising it forward. The issue seems to be rather cosmetic (no real harm except dirty log). 
	* Workaround found: do not terminate a process, if it is already running, just cancell task if it have not yet started. This workaround negatevly affects performance.  It is still not clear how a proper fix in josm could look like. NavigatableComponent has STATIC global listeners.  
	* Does this workaround affects satellite layers???
* **[85%]** "realistic" 2d style for roads -- with darkgray asphalt colour and lanes, instead of red-green importance colouring.
    * Now all refereced png images are present in jar resources. Maybe some could be excluded as non-3D?
    * Fix a funny bug with `man_mane=bridge`: a linear waterway is painted above area bridge! -- is it fixable at all? lines are drawn over polygons!	
	
#### Patches to monitor

*  [[PATCH] MapCSS style cache should be dependent on ElemStyles instance](https://josm.openstreetmap.de/ticket/24637). -- **DONE.**

### Features needed to catch up with f4map
* man_made=chimney 

### Ideas for the Further Development

See also: [IDEAS.md](docs/dev/IDEAS.md)

In order [voted](https://community.openstreetmap.org/t/urban-eye-3d-josm-3d-viewer-plugin/133674/240) by the community.

1. Add more objects, e.g. street lights, benches, statues, forests(!)

2. Support the [base:shape proposal](https://community.openstreetmap.org/t/rfc-feature-proposal-3d-tagging-for-building-base-shapes) (#35)
	
3. Improve performance/responsiveness of editing in large scenes.
    * Implement **partial scene update**. If a primitive is changed, geometry of only related objects should be updated, not of the whole scene, as now. 
        * Performance is not a big issue right now, but it may become important if more complex geometry (e.g. polygonal windows) is generated.
        * The tricky part is to determine what objects are related. 
			* First of all we need to process OSM primitive hierarchy: if a node is moved, then a parent way is affected. if the way is affected, parent relation is also affected.
			* Secondly, objects may be only spatially related. if a building part is moved outside of it's parent building, the latter may become visible.
			* are any other cases? It would be very embarrassing to miss something here!
			
    *  **Update 3D view in a separate thread**, thus not affecting editing experience. Proper update queue is required. 
	
4. Improve rendering, implement **real ambient occlusion** and/or **support materials** (e.g. metal and glass).	
	* **Real Ambient Occlusion.** 
		* Current rendering engine is good enough for the editing plugin. 
		* See [Plan for Screen-Space Ambient Occlusion (SSAO) Implementation](docs/dev/IDEAS.md#plan-for-screen-space-ambient-occlusion-ssao-implementation) section below
	* **Support of materials** (tags building:material  and roof:material). 
		* Note: material does not affect color, it affects procedural texture and metalness. 
		* Some more advanced shading is obviously required. 
		
5. Support `roof:shape=saltbox` as well as `roof:shape=double_saltbox`, `roof:shape=quadruple_saltbox` (#28)
	* There is no consistent opinion about what this shape is.		
	
6. Implement rendering of building passages (`tunnel=building_passage`).  #6
    * Definitely, this requires support of boolean operations with meshes: "difference". for this purpose we have JCSG library (https://github.com/Zkir/JCSG)
and even example of this library usage: JCSG_test (no repository for it yet). How to preserve face colors while using it is  still the big unknown. 
    Screenshots in JCSG readme.md suggest that it should be possible.
	

Some other wishes:

* Check whether `roof:shape` should be inherited from building. Both F4 and osm2world inherits both roof:shape and roof:height from parent building. Should we also do that? 
* Explore further the integration with **Osm2World**. 
    * What is a best way to use it? Can it be an alternative rendering engine in the plugin (considering it's limitations)?
      Can it be a separate (manually updatable) window? 
	  
* Support [windows](https://wiki.openstreetmap.org/wiki/Key:window).
    * Since this feature is present in osm2world, we also want that.
	
* Implement other roof shapes:
	* Implement `zakomar` roof somehow. 
		* It was implemented in Blosm, but that implementation is not suitable for us (not watertight). Probably boolean operation should be tried.
	* Implement  `sawtooth`, `gabled_row` roofs. They say that F4 Map supports them.  
	* Implement `butterfly`  roof. Note that the first attempt to implement it has failed. Just a new profile is not enough. Some significant changes are required in MesherLinerProfile to support such 'inverse' geometry.


To be prioritized via MoSCoW method.

#### Must 
#### Should
#### Could 

	
#### Would [Not]
	
* Support of `roof:ridge=yes` as described in [ProposedRoofLines](https://wiki.openstreetmap.org/wiki/ProposedRoofLines)	
	* seems it is not really feasible with existing mesher structure.

## Recent Accomplishments
### March 7, 2026
*   `TagInfoGeneratorTest` has been refactored to collect tags from the new source: /textures/textures.cfg


### March 6, 2026
*   **Refactored Tree Texture Handling to be Data-Driven.**
    *   `TextureManager` no longer uses hardcoded paths. It now parses a configuration file (`/textures/textures.cfg`) to load a list of available texture definitions, each with its own set of tags.
    *   Implemented a new method `findTextureName()` which selects the best texture for an object by scoring how well the object's OSM tags match the tags of each texture definition. This creates a flexible, data-driven system.

### March 4, 2026
*   **Successfully implemented `natural=tree` rendering.**
    *   Refactored `RenderableBuildingElement` into a universal `RenderableElement` class to handle various object types.
    *   Created `TextureManager` for centralized loading and caching of textures.
    *   `Renderer3D` now processes both colored (buildings) and textured (trees) objects from a single list.
    *   Fixed transparency issues for tree billboards by enabling `GL_ALPHA_TEST`.
    *   A series of build errors were fixed along the way, including a private constructor access, test failures due to new objects, undocumented tags, and an `IndexOutOfBoundsException` in the rendering loop.

### March 3, 2026
*   **Completed a major refactoring** of the core geometry and rendering pipeline.
    *   The `Mesh` class now supports a universal data-driven structure with separate material (color) and texture coordinate (UV) attributes for each face.
    *   The `Renderer3D` has been updated with a universal `drawMesh()` method that can render both colored (buildings) and textured objects.
    *   `GroundTile` rendering has been successfully migrated to this new universal mechanism, removing special-case code from the renderer.
    *   This work lays a complete foundation for adding new textured objects. The immediate next step is to implement `natural=tree`.

### February 23, 2026
* Creation of `taginfo.json` has been improved. It turned out that there is .hasTag(key, value) method in JOSM, so we can find exact tags (key=value) in the source code.

### February 22, 2026
* 2D style `urbaneye2d.general.mapcss` has been reviewed, "non-3d" icons have been excluded.

### February 21, 2026
*   **Implemented a MapCSS validation autotest.** This test ensures:
    * All image resources (e.g., `.png` files referenced within the MapCSS files) exist in the project's resources.
	* It does NOT really check syntactical correctness of MapCSS files -- JOSM's internal MapCSS parser eats the exceptions!

### February 20, 2026
* Added support for `surface=unpaved` (and some other unpaved roads). They are rendered in gray in our 2d style.

### February 17, 2026
* Drawing area for buildings has been restricted. Buildings are only rendered within the visible ground plane.
* Re-implemented highlighting of selected OSM primitives in the 3D view using a red, thick wireframe.

### February 16, 2026
* **Fixed a major lifecycle bug** that caused multiple "ghost" instances of the 3D dialog to be created. This resolves long-standing issues with event handlers firing multiple times and improves overall stability.
    * Implemented the canonical JOSM pattern for managing dialogs by correctly using `mapFrameInitialized` to destroy old dialog instances before creating new ones.
    * As a major benefit, this fix significantly reduces redundant calls to expensive operations like geometry recalculation (`updateData`), improving plugin responsiveness.
* The **automatic download of incomplete multipolygons** (and building relation members) has been implemented. Without this feature, the rendered 3D map can appear as if a natural disaster has struck, with buildings destroyed and water overflowing, due to missing geometric components.  The feature is controllable via a new "Automatically download incomplete multipolygons" checkbox in the plugin settings panel.
* Required JOSM version uplifted to 19528 (expected release March 26)

### February 15, 2026

* Workaround has been found for crash: change future.cancel(true) --> future.cancel(false); // Prevents task from starting, but doesn't interrupt running tasks. Obviously it requires a proper fix in JOSM

        (2026-02-14 03:27:35.601 SEVERE: Exception raised in EDT: java.lang.InterruptedException
        at org.openstreetmap.josm.gui.util.GuiHelper.runInEDTAndWait(GuiHelper.java:228)
        at org.openstreetmap.josm.gui.NavigatableComponent.fireZoomChanged(NavigatableComponent.java:152))

* Initial implementation for "realistic" 2d road styles: [urbaneye2d.roads.mapcss](src/main/resources/mapcss-styles/urbaneye2d.roads.mapcss) has been created. Roads are rendered in gray asphalt colour. Lanes and width tags are ignored for now. Non-ugly implementation for lanes maybe impossible with current MapCSS capabilities.

### February 14, 2026

* Patch for JOSM API (which fixes the problem with singleton MapCSS style cache) has been created and accepted by the upstream. 
Mapillary and MapRoulette plugins have been also fixed. See [Josm ticket #24637](https://josm.openstreetmap.de/ticket/24637)

### February 13, 2026

* Underground barriers (layer<0 or location=underground) are excluded from 2D rendering.

### February 8, 2026
* Own, self-rendered MapCSS-based 2D layer is displayed by default, if no satellite imagery is selected

### February 21, 2026

* Creation of `taginfo.json` is updated. It is now a part of the Maven build process and ensures tags from both Java source code and [features.md](docs/features.md) are synchronized and documented. Changes:
    * Code rewritten from Python to Java. 
    * The file is updated only if there are some changes in tags, not just file creation date. 


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

