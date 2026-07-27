# Development History

# Version 2.4.2 (Jul 22, 2026)
* Fixed bug with gabled roof direction: opposite directions are considered equal.

# Version 2.4.1 (Jul 17, 2026)

*   **Fixed a crash in the JOSM validator:** The `OverlappingWallsCheck` validator no longer incorrectly processes primitives tagged with `building=no` or `building:part=no`. This prevents an invalid input `RuntimeException` when `RenderableElement.createBuildingOrPartRecipe` is strictly called to create a building recipe.

# Version 2.4.0 (Jul 15, 2026)

*   **Enhanced Botanical Support & Spatial Statistics:**
    *   Implemented full support for `leaf_type=palm`, including new textures and botanical database enrichment.
    *   Developed a spatial analysis engine (`spatial_tree_stats.py`) to calculate tree type probabilities per 5x5 degree grid cell.
    *   Integrated spatial statistics into the plugin, preventing unrealistic tree rendering (like palms in northern climates) by using geographic-based defaults.
    *   Enforced botanical family-based validation: species belonging to `Arecaceae` are automatically identified as palms, and conifers are correctly typed as `needleleaved`.
    *   Updated the data pipeline to automatically regenerate and sync spatial statistics with plugin resources.

*   **Object Selection in 3D View:**
    *   Implemented the ability to select OSM objects by clicking on them in the 3D window.
    *   Added `GeometryUtils` for precise ray-triangle and ray-AABB intersection calculations.
    *   Updated `Renderer3D` to perform "picking" by unprojecting mouse coordinates into a world-space ray.
    *   Enhanced `DialogWindow3D` to sync the selection with the main JOSM window.
    *   Verified the implementation with new unit tests and a full build.
	
# Version 2.3.3 (Jun 28, 2026)

* Validator check for building parts covering building outline has been improved.
    * Home-made code replaced with Java Topology Suite (JTS) calls.
	* False positive errors fixed [#64](https://github.com/Zkir/UrbanEye3D/issues/64)
	* Support for building outlines with mutiple outer rings added
	
* Forest multipolygons with several outer rings supported

# Version 2.3.2 (Jun 18, 2026)

*  More proper defaults for the `leaf_type` tag, based on taxonomic family. Species belonging to the families 'Araucariaceae', 'Cephalotaxaceae', 'Cupressaceae', 'Pinaceae', 'Podocarpaceae', 'Sciadopityaceae', 'Taxaceae' are considered needleleaved by default. 

# Version 2.3.1 (Jun 10, 2026)

* Support for `lane_markings=no` in MapCSS

# Version 2.3.0 (May 29, 2026)

* Species database: If `leaf_type` tag is missing but `species` or `genus` tags are present, `leaf_type` is now inferred, and appropriate tree texture is selected.

* Pipeline for collecting and analyzing tree statistics (misc subproject) is improved Significantly.

* Validation check for `species` and `genus` tags are added, so you may be sure that the entered values are correct.

* Added support for advertising columns (Morris columns). Nodes tagged with `advertising=column` will now be rendered as 3D street furniture.



## Version 2.2.2 (May 23, 2026)
* Error reporting improved a bit

## Version 2.2.1 (May 16, 2026)
* Cornish translation updated
* Support for `roof:material=grass`

## Version 2.2.0 (May 08, 2026)

*   **Finalized and cleaned up translations for version 2.2:**

* `roof:shape=saltbox` has been reimplemented according to the [wiki page](https://wiki.openstreetmap.org/wiki/Tag:roof:shape=saltbox), with asymmetrical sides, but symmetrical heights.

*   **Badminton court markings:**
    *   Added support for rendering badminton court markings on the ground texture.
    *   Professional 13.4m x 6.1m court layout is used, including service lines and singles/doubles boundaries.
    *   Features automatic alignment, 2-meter safety offset, and proportional scaling for smaller areas.

*   **Volleyball pitch markings:**
    *   Added support for rendering volleyball court markings on the ground texture.
    *   Standard 18m x 9m court layout is used, including the center line and attack (3-meter) lines.
    *   Follows the same logic as other sports: automatic alignment along the longest side, 2-meter safety offset, and proportional scaling for smaller areas.

*   **Documentation update:**
    *   Added a new section to [features.md](docs/features.md) documenting all supported sport pitch markings (soccer, tennis, volleyball, and badminton).

*   **Improved Ground Plane Control [#41](https://github.com/Zkir/UrbanEye3D/issues/41):**
    *   Added a new preference "Use satellite imagery for ground plane" to allow independent control of 3D ground imagery.
    *   Implemented a keyboard shortcut (`Shift+E`) to quickly toggle between satellite imagery and MapCSS-based imagery in the 3D window.
    *   This allows users to keep satellite imagery in the 2D window for editing while seeing own Urban Eye rendering style in 3D.
	
*   `roof:shape=many` is rendered as `hipped` (anyway better than just flat) for buildings, but not for building parts. 
*   A new check is added to validator to warn user that roof:shape=many does not make much sense for a building part. 
	
*   **Sports pitch markings:**
    *   Added rendering of lines for soccer pitches and tennis courts on the ground texture.
    *   Markings are automatically aligned along the longest side and fit inside the object with a 2-meter offset from the edge.
    *   For small pitches (school or training grounds), markings are proportionally scaled down to stay within boundaries.
    *   Supported both simple ways and multipolygons.


*   **Significantly improved the `OverlappingWallsCheck` validator accuracy.**
    *   Implemented a "visibility check" logic: the validator now ignores overlaps for walls that are completely hidden inside a building (e.g., when two building parts are joined "back-to-back"). This eliminates a large class of false positive warnings.
    *   Added protection against incomplete data: the validator now automatically skips buildings with missing geometry, preventing incorrect reports caused by partially loaded OSM data.
    *   Refined wall height calculations to account for non-flat roofs, ensuring that sloped roof edges do not trigger false Z-fighting errors.
    *   Verified the stability of the entire project with 54 automated tests, all of which passed successfully.

* **Implemented `OverlappingWallsCheck` validator.**
    * New validator detects coplanar 3D walls that cause Z-fighting (flickering).
    * The algorithm uses oriented segments and Z-height ranges to distinguish between actual overlaps and valid touching walls.


*   **Support for `side_half-hipped` Roof Shape:**
    *   Implemented the `side_half-hipped` roof shape in `MesherSideHalfHipped`, providing a transition between a half-hipped end and a vertical gabled end.
    *   Ensured correct slope alignment by calculating the ridge setback as `b/4` for the half-hipped side, maintaining geometric consistency with the eaves.
    *   Fully supported `roof:direction` to allow users to specify which side should be half-hipped.

*   **Graphics Pipeline Optimization - Frustum Culling:**
    *   Implemented **Frustum Culling** in `Renderer3D` to improve rendering performance.
    *   Added bounding box computation to the `Mesh` class to enable efficient culling checks.
    *   The renderer now extracts 6 frustum planes from the ModelView-Projection matrix and skips objects entirely outside the view, while still respecting the distance-based `visibleArea` culling.
    *   This reduces the number of draw calls and vertex data transfers for complex scenes.

*   **Scene Statistics Overlay:**
    *   Implemented a statistics overlay in the top-left corner of the 3D scene, displaying the number of objects, total face count, and average frame rendering time (moving average over the last 60 frames).
    *   Added a new preference setting "Show scene statistics" in the plugin settings to toggle this display.
    *   Ensured full internationalization (i18n) support for the new strings, including updated Russian translations.
    *   Calculated object and face counts efficiently during scene updates.

*   **Improved Forest Generation (Poisson Disk Sampling):**
    *   Replaced the uniform random tree placement with **Poisson Disk Sampling** using Robert Bridson's O(N) algorithm.
    *   This ensures a more natural and organic distribution of trees, preventing unnatural clusters and overlapping trunks.
    *   Implemented the new `PoissonDiskSampler` utility class.
    *   Configured the minimum distance between trees to be $R = \text{DEFAULT\_TREE\_HEIGHT} / 3.0$ at maximum density, with dynamic scaling based on the user's forest density setting.
    *   Integrated JTS `PreparedGeometry` for highly efficient point-in-polygon filtering during generation.

*   **Automatic Forest Generation:**
    *   Implemented automatic population of `natural=wood` and `landuse=forest` polygons with 3D tree models.
    *   Integrated JTS (Java Topology Suite) for robust polygon triangulation, handling complex forest footprints with holes.
    *   Added a "Forest density" slider to the plugin preferences, allowing users to control the tree density in real-time.
    *   Updated `FlatEarth` utility with inverse coordinate conversion and `toJTSPolygon` helper in `Contour`.
    *   Enhanced `SceneTest` with comprehensive unit tests for forest generation.
    *   Updated `TagInfoGeneratorTest` and `textures.cfg` to support and document the new forest tags.



## Version 2.1.3 (May 01, 2026)
* Fixed bug in tag validator with height value unit (see [gh issue 52](https://github.com/Zkir/UrbanEye3D/issues/52))

## Version 2.1.2 (April 23, 2026)
* French translation by **Lejun** has been added
* [Cornish](https://en.wikipedia.org/wiki/Cornish_language) translation by @linfindel has been added

## Version 2.1.1 (April 21, 2026)
* Added Indonesian translation 
* Indonesian translation added (by @FajrAlim)
* Slovak translation updated — (by @aceman444)
* Russian translation updated 
* A bug with `roof:shape=half-hipped` and `roof:direction` fixed (#49)

## Version 2.1.0 (April 18, 2026)
* Internationalization
	* **i18n** infrastructure
		* All strings in the Java code are wrapped in the `tr()` function to support translations.
		* To collect strings and create the translation template (`.pot`), JOSM uses external tools (xgettext and the `i18n.pl` Perl script). We support this process via [i18n.bat](i18n.bat).
		* In order to support a pure Java/Maven-compatible build and remove external dependencies, we implemented our own tooling: `ru.zkir.easytext`. It supports both sides of the process:
			* Collecting strings from the source code and creating the `.pot` file.
			* Compiling `.lang` files from `.po` files (Yes, JOSM uses its own format here).
		* An autotest was created to check `.pot`/`.po` files, verify the existence of generated `.lang` files, and produce a [translation status report](docs/translation-status.md).
	* Translations
		* Added Russian translation (AI-generated).
		* Added Slovak translation by @aceman444.
		* Added Italian translation (AI-generated).
		* Added German translation (AI-generated, reviewed by @fraggle-DE).
* Documentation	
	* [Contributing guide](CONTRIBUTING.md) added with instructions for potential contributors.
	* Created [documentation for the MapCSS style](src/main/resources/mapcss-styles/README.md). This document explains the design philosophy, file structure, and key technical considerations for the project's MapCSS stylesheets.


## Version 2.0.0 (April 01, 2026)

* Required JOSM version uplifted to 19555 (released on March 31)

* **Implemented partial update** for the ground plane tiles.
    * Unfortunately it does not solve all the problems, JOSM mapcss engine is really dumb and single-treaded.

*   **Improved UI Responsiveness with Background Processing.**
    *   Refactored the core scene generation logic to execute on a background thread, preventing the UI from freezing during data updates.
    *   Introduced a single-threaded `ExecutorService` in `DialogWindow3D` to manage a queue of scene update tasks. New requests cancel pending (non-running) tasks, ensuring the view reflects the latest data state without unnecessary calculations.
    *   The `Scene` class was updated to separate the heavy computational logic (`calculateUpdate`) from the lightweight state application (`applyUpdate`), which runs on the EDT.

*   **Fixed bug with active layer for ground plane rendering.**
    *   The 2D ground plane was incorrectly rendered using the `DataSet` from the top-most layer even when an active layer was changed.
    *   The `Layer2dInfo` class was enhanced to include `dataSetName` for MapCSS type layers. This allows `GroundPlane` to detect when the active `DataSet` changes and trigger a full refresh of tiles, clearing caches and forcing a redraw with current data.
	
*   **Fixed bug with image resources in MapCSS-styles.**
    * PNG images referenced in mapcss styles moved to `src/main/resources/images` folder, where JOSM can find them.   

*   **Conducted a major refactoring** to improve separation of duties and make mesh generation logic more understandable.
    *   The monolithic `RenderableElement` was split. A new `BuildingRecipe` class now acts as a parameter object for mesh generation for buildings/parts.
    *   `RenderableElement` was simplified into a lightweight data container, responsible only for holding the final mesh and origin.
    *   A new `OsmDataWasher` utility class was created to centralize all OSM tag parsing logic.

*   `TagInfoGeneratorTest` has been refactored to collect tags from the new source: /textures/textures.cfg

*   ** Best matching texture is applied to a tree.**
    *   `TextureManager` no longer uses hardcoded paths. It now parses a configuration file (`/textures/textures.cfg`) to load a list of available texture definitions, each with its own set of tags.
    *   Implemented a new method `findTextureName()` which selects the best texture for an object by scoring how well the object's OSM tags match the tags of each texture definition. 

*   **Successfully implemented `natural=tree` rendering.**
    *   Refactored `RenderableBuildingElement` into a universal `RenderableElement` class to handle various object types.
    *   Created `TextureManager` for centralized loading and caching of textures.
    *   `Renderer3D` now processes both colored (buildings) and textured (trees) objects from a single list.
    *   Fixed transparency issues for tree billboards by enabling `GL_ALPHA_TEST`.

*   **Completed a major refactoring** of the core geometry and rendering pipeline.
    *   The `Mesh` class now supports a universal data-driven structure with separate material (color) and texture coordinate (UV) attributes for each face.
    *   The `Renderer3D` has been updated with a universal `drawMesh()` method that can render both colored (buildings) and textured objects.
    *   `GroundTile` rendering has been successfully migrated to this new universal mechanism, removing special-case code from the renderer.
    *   This work lays a complete foundation for adding new textured objects. The immediate next step is to implement `natural=tree`.

* Creation of `taginfo.json` has been improved. It turned out that there is .hasTag(key, value) method in JOSM, so we can find exact tags (key=value) in the source code.

*   **Implemented a MapCSS validation autotest.** This test ensures:
    * All image resources (e.g., `.png` files referenced within the MapCSS files) exist in the project's resources.
	* It does NOT really check syntactical correctness of MapCSS files -- JOSM's internal MapCSS parser eats the exceptions!

* **Fixed a major lifecycle bug** that caused multiple "ghost" instances of the 3D dialog to be created. This resolves long-standing issues with event handlers firing multiple times and improves overall stability.
    * Implemented the canonical JOSM pattern for managing dialogs by correctly using `mapFrameInitialized` to destroy old dialog instances before creating new ones.
    * As a major benefit, this fix significantly reduces redundant calls to expensive operations like geometry recalculation (`updateData`), improving plugin responsiveness.

* The **automatic download of incomplete multipolygons** (and building relation members) has been implemented. Without this feature, the rendered 3D map can appear as if a natural disaster has struck, with buildings destroyed and water overflowing, due to missing geometric components.  The feature is controllable via a new "Automatically download incomplete multipolygons" checkbox in the plugin settings panel.

* Workaround has been found for crash: change future.cancel(true) --> future.cancel(false); // Prevents task from starting, but doesn't interrupt running tasks. Obviously it requires a proper fix in JOSM

        (2026-02-14 03:27:35.601 SEVERE: Exception raised in EDT: java.lang.InterruptedException
        at org.openstreetmap.josm.gui.util.GuiHelper.runInEDTAndWait(GuiHelper.java:228)
        at org.openstreetmap.josm.gui.NavigatableComponent.fireZoomChanged(NavigatableComponent.java:152))

* Initial implementation for "realistic" 2d road styles: [urbaneye2d.roads.mapcss](src/main/resources/mapcss-styles/urbaneye2d.roads.mapcss) has been created. Roads are rendered in gray asphalt colour. Lanes and width tags are ignored for now. Non-ugly implementation for lanes maybe impossible with current MapCSS capabilities.

* Own, self-rendered MapCSS-based 2D layer is displayed by default, if no satellite imagery is selected

### JOSM patches
*  [[PATCH] MapCSS style cache should be dependent on ElemStyles instance](https://josm.openstreetmap.de/ticket/24637). -- **DONE**	
*  [[PATCH] Possibility to specify custom MapPaintSettings](https://josm.openstreetmap.de/ticket/24678) -- **DONE**	



## Version 1.9.2 (March 16, 2026)
*   **Fixed a rendering bug for `man_made` objects.**
    *   Previously, if a `man_made` object (e.g., a `man_made=tower` polygon) contained `building:part` polygons, both the container object and its parts were rendered, causing visual duplication.
    *   The logic has been corrected to suppress the rendering of any `man_made` object that has building parts inside it, ensuring that only the parts are displayed. This resolves GitHub [issue #36]((https://github.com/Zkir/UrbanEye3D/issues/36)).

## Version 1.9.1 (March 11, 2026)

* Fixed bug with deleted man-made multipolyong, see [github issue #37](https://github.com/Zkir/UrbanEye3D/issues/37).
* Creation of `taginfo.json` is updated. It is now a part of the Maven build process and ensures tags from both Java source code and [features.md](docs/features.md) are synchronized and documented. Changes:
    * Code rewritten from Python to Java. 
    * The file is updated only if there are some changes in tags, not just file creation date. 

## Version 1.9.0 (February 19, 2026)
*   Implemented support for experimental `shape=hyperboloid` tag.
    *   Added `MesherHyperboloid` to generate 3D meshes for hyperboloid-shaped buildings and man_mades.
    *   Utilized precise mathematical parametric equations for hyperboloids of revolution, incorporating `hyperboloid:top_rate` and `hyperboloid:middle_rate` to define top and middle radii (scaling factors).
    *   Added specific topology tests for the new shape in `RoofGeneratorTopologyTest.java` to verify correctness under various parameter configurations.
*   Re-implemented highlighting of selected OSM primitives in the 3D view using a red, thick wireframe.

See the release notes: https://github.com/Zkir/UrbanEye3D/releases/tag/v1.9.0, they are nice!

## Version 1.8.1 (February 12, 2026)
* User defined TMS/BING layers (which do not have own ID) are now also supported. ([JOSM ticket #24630](https://josm.openstreetmap.de/ticket/24630))

## Version 1.8.0 (February 1, 2026)
* Implemented rendering of satellite imagery as a ground plane in the 3D viewer. Limited set of JOSM layers is supported: public TMS layers and BING. Other layers is not feasible to support now, due to comlpicated JOSM API.

## Version 1.7.0 (January 21, 2026)
* Building parts with building:part=roof tag are rendered in the same way as in F4: for building parts with building:part=roof and roof shapes gabled, round, gambrel , saltbox and skillion walls are not generated.

## Version 1.6.1 (December 06, 2025)
* Crash fixed: validator no longer fails on self-intersecting ways.

## Version 1.6.0 (October 25, 2025) 
* Added a keyboard shortcut (`Ctrl+Shift+N`) to reset the camera view to the default North-facing orientation.
* Added menu item and keyboard shortcut to open current view in F4

## Version 1.5.2 (October 11, 2025)
* Crash fixed: validator no longer fails on self-crossing ways.

## Version 1.5.1 (October 10, 2025)
* Implemented a "Fix" button for the `Missing tag: roof:shape=skillion without 'roof:direction'` validation error. The fix automatically calculates and applies a default direction based on the building's geometry.
* Support for min_height and default colour/materilas for barriers has been added.
* Underground buildings (with location=underground or layer<0) excluded from the rendering. 

## Version 1.5.0 ( September 27, 2025)
* Implemented a new validator that warns for case skillion and side_hipped roofs without roof:direction.
* Implemented a new validator that checks for roof:direction and roof:orientation valid values.
* Implemented rendering of `barrier=*` tags, including `barrier=wall`, `barrier=hedge`, `barrier=fence`, and `barrier=city_wall`. This includes using the JTS buffer operation to create 3D meshes from linear OSM ways.
* Implemented a new validator that compares the height of a building with the maximum height of its parts and warns if the difference is more than 10%.
* Implemented a new validator for buildings and building parts, that checks for height and coverage.


## Version 1.4.1 (September 15, 2025)
* **Bugfix**:  3D window is updated when the _collapsed_ docked window is undocked.

## Version 1.4.0 (September 2, 2025)
* Possibility to toggle "fake" Ambient Occlusion off and on.
* New parameter in Preferences UI to control Ambien Occlusion mode.
* Infer building:colour and roof:colour from materials, if specified.
* Support for roof:shape=side_hipped (for quadrangular bases only!)
* Support for `roof:shape=apse_gabled` added.
* Added support for `roof:shape=crosspitched` as a synonym for `cross_gabled`.


## Version 1.3.1 (August 28, 2025)
* Support for `roof:shape=equal_hipped` as a synonym for `roof:shape=hipped`
* The tag `roof:levels=0` defaults rather for 'half a level' for non-flat roofs. ([gh #22](https://github.com/Zkir/UrbanEye3D/issues/22))
* Version updated to 1.3.1

## Version 1.3.0 (August 27, 2025)
* Small "preferences" button added to the panel header ([gh #13](https://github.com/Zkir/UrbanEye3D/issues/13))
* Bug with roof:levels=0 fixed ([gh #22](https://github.com/Zkir/UrbanEye3D/issues/22))
* Inheritance of height from building to parts turned off, buildings and parts are now processed uniformly. ([gh #14](https://github.com/Zkir/UrbanEye3D/issues/14))
* Support of `building:part=steps` for non-convex bases.   
* More or less proper implementation of `building:part=steps` for quadrangular bases.
* Implemented support for `roof:shape=hipped` on buildings with complex (non-rectangular) footprints using the `campskeleton` library (based on Straight Skeleton algorithm).
* More traditional folder structure for resources
* Minimize button in the 3D panel fixed.
* Performance optimization: when the 3D window is closed or minimized, there is no need to update data.

## Version 1.2.0 (August 13, 2025)
* Support of roof:shape=cone added, as the synonym to pyramidal ([#15](https://github.com/Zkir/UrbanEye3D/issues/15))
* Support of arbitrary (quasi-quadrangle) bases for Linear Profile roofs: gabled, gambrel, round and saltbox. (completed)
* More proper algorithm for building outline simplification (related to [github issue #12](https://github.com/Zkir/UrbanEye3D/issues/12))
* More proper spatial containment check for multipolygons with holes (related to [github issue #12](https://github.com/Zkir/UrbanEye3D/issues/12))
* primitiveId  added to RenderableBuildingElement
* Serious autotest fix: more accurate normals check, which work properly even for non-convex meshes.
* Small refactorings
* [pythonic script](collect_tags.py) to collect actually used tags has been created. taginfo.json has been submited to taginfo projects.
* Support of skillion roof for multipolygons with holes.

## Version 1.1.0 (August 7, 2025)

* Building part now inherits height from parent building as a default value. Also it solves the problem with disappearing buildings (see gh. issue [#1](https://github.com/Zkir/UrbanEye3D/issues/1))
* In case  height<min_height, height is set to min_height, to avoid upside-down buildings.
* Support of missed tags `roof:levels` and `building:min_level`. Related to [issue #5](https://github.com/Zkir/UrbanEye3D/issues/5) and [issue #1](https://github.com/Zkir/UrbanEye3D/issues/1)
* Refactoring: `Contour` class is now located in the `utils` package
* Autotest for Scene.updateData() -- proved to be very usefull
* Tags related to color and material (`building:colour`, `building:material`, `roof:colour`, `roof:material`) are inherited from building to parts. This improves colors significantly.
* Check whether building part belongs to a building imporved. Actual contour is tested, not only bbox.
* [enh] Support of **roof:shape=cross_gabled:** New mesher implemented.
* [bugfix] Handling of defaults for height improved.

## Version 1.0.0 (July 29, 2025)
* Name has been decided: we will go with **"Urban Eye 3D"**
* License has been decided: **GNU GPL v3**
* Both buildings and building parts are rendered. The most simple algorithm is used to decide what to display is used: comparison by bbox.
* Some last minute refactoring: Scene class introduced.
* Version **1.0.0** has been **released**!

### July 28, 2025
* **Support of "linear profile" roof shapes:** `round`, `gambrel`, `saltbox` roofs are supported. Obviously, for quadrangle bases only. 
* **Stub icons for dialog, preferences and plugin itself**
* **Return of fake AO.**  Even this simple type of shading make picture much better.
* **Proper registration of Wireframe mode shortcut.** Pressing "Z" now works also when 3d window is docked.
* **Debugging**: More informative message for "Tesselation error, combine callback needed"  
* **Support of `half-hipped` roofs:** one of the popular shapes for buildings, maybe not so usefull for building parts. 
* **Wireframe mode improved:** original edges are displayed, before tesselation. With the exception of building with holes, but this case seems to be unfixible.


### July 27, 2025
* **Huge refactoring:**  class RoofGeometryGenerator split into several classes (meshers). Autotests' structure also improved
* **Zero-Length Edge Validation:** Added a new unit test assertion, `assertNoZeroLengthEdges`, to prevent the creation of degenerate edges in meshes. This test was integrated into the main test suite, improving the geometric integrity of all generated roof shapes.
* **No-Wall Case Validation:** Refactored the `MesherFlat`,  `MesherSkillion`, `MesherGabled`, and `MesherHipped` classes to correctly generate roof geometry when no walls are present (`roof:height = height - min_height`). 
* **Height/MinHeight Validation:** New autotests to check that generated mesh vertices match initial height/min_height.
* **Masard roof support**. Thank to autotests, success from the first try.


### July 26, 2025 (testing)
* **Unit Testing Framework:** Established a robust unit testing environment using JUnit 5.
* **Geometry Validation Tests:** Created a suite of unit tests for the `RoofGeometryGenerator` class. These tests automatically validate two critical properties of the generated 3D meshes:
    *   **Watertightness:** Ensures that the mesh is a closed, solid object with no holes by verifying that every edge is shared by exactly two faces.
    *   **Normal Direction:** Confirms that all face normals point outwards, which is essential for correct lighting and rendering.
* **Test-Driven Development for Roofs:** The test suite now covers `pyramidal`, `gabled`, `skillion`, `dome`, and `onion` roof shapes, solidifying their implementation and providing a clear framework for developing new roof types. 

### July 26, 2025                                                            
* **Gabled Roof Support:** Implemented support for `roof:shape=gabled` on quadrilateral buildings. 
The implementation correctly identifies gable ends based on the shortest or longest sides of the building footprint, controlled by the
`roof:orientation=along/across` tag. The gable walls are generated  as single pentagonal faces, ensuring correct geometry and appearance.
* **Сonsistent naming** : Plugin file is called z3dviewer 
* **Split multipolygons**. If a building contour has several outer rings, but no inner ones, it is separated into several RenderableBuildingElements. Not precisely correct, because origin is not moved, but at least such objects rendered.
* **Hipped Roof Support:** Implemented support for `roof:shape=hipped` on quadrilateral buildings. 
* **Mesh generation for FLAT/multipolygon**. However, FAKE AO suffered. Should be returned ASAP.


### July 25, 2025
* **Dome, half-dome and onion roofs:** support added for "conical" roofs.
* **Skillion roof support:** Implemented support for `roof:shape=skillion`, including `roof:direction`. The implementation correctly generates trapezoidal walls and handles non-convex polygons using tessellation.
* **Complex Multipolygon Support:** Implemented robust support for multipolygon relations, including those with multiple outer rings and inner holes. The logic now correctly assembles complex geometries and uses OpenGL tessellation for proper rendering. Buildings with multiple rings now correctly default to a flat roof, regardless of `roof:shape` tags.
* **Local coords for buildings.** Objects are created in local coordinate system, with origin at building centroid. This should allow some performance improvement.

### July 24, 2025
* **Initial roof support:** Implemented support for `roof:shape=pyramidal`, as the most simple one. Pyramids are created with correct centroid, even better then in blosm!
* **Flat roof support:** Yes! If a flat roof has a defined height (roof:shape=flat+roof:heigh=*), we create fascia (vertical side faces) in the roof color. No one has done this before. We did it!
* **Wireframe rendering mode:** A new preference setting allows users to toggle between solid and wireframe rendering for buildings.
* **Removing of redundant nodes:** A lot of nodes, which belong to building parts, are not really needed for rendering. Removing them is a huge optimization!


### July 23, 2025
* **Initial support for relations/multipolygons.** At least they work somehow. Several bugs expected.
* **Bug with xy/z proportions fixed**. xy coordinates are calculated in proper meters, in the same scale as height.
* **Rendering of non-convex polygons.**  It turned out that  gl.glBegin(GL2.GL_POLYGON) properly renders CONVEX polygons only, which is not always the case for building contours. We use tessellation to handle that.
* **Rendering made more interesting.**  Parallel light (sun) has been introduced along with curent Fake AO  shading. 
* **Panning in 3D Window.** Pan is now supported in 3D window. Map window is panned accordingly
* **Cursor icons.** When user presses the left mouse button in the plugin window, the cursor changes to hand (thus expressing the Orbiting mode), and when user presses the 
right mouse button, it changes to crossed arrows (expressing the movement of the map), as it is in JOSM.

###  July 22, 2025

*   **Map Movement Sync:** The 3D viewer now correctly responds to panning and zooming events on the main JOSM map, redrawing the scene as the viewport changes.
*   **Z-Up Coordinate System:** The rendering engine was refactored to use a Z-up coordinate system, which is more conventional for 3D architectural visualization. The Z-axis is now vertical.
*   **Lifecycle and Bug Fixes:**
    *   Resolved critical `NullPointerException` and `IllegalArgumentException` crashes related to improper listener management when layers were removed or the application was closed.
    *   Removed the redundant, manually-created "Windows" menu item, relying on JOSM's native handling for toggle dialogs. This also fixed a startup crash when no data was loaded.

###  July 21, 2025
* **Start of the project** : plugin is working and building parts are rendered as extruded bodies via OpenGL (JOGL library) 