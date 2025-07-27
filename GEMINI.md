# JOSM 3D Viewer Plugin

## Goal

Create a JOSM plugin that displays loaded buildings (including `building:part=*`) in a separate 3D window.

### [Desired] Features:
* Just rendering of loaded building parts, no editing, nothing else.
* Support of [Simple 3D Buildings](https://wiki.openstreetmap.org/wiki/Simple_3D_Buildings) specification
* 3D rendering via openGL (jogl library)
* Orbiting around scene centre via mouse left button, zooming using mouse wheel 
* Simple support of colours (osm tags building:colour and roof:colour)
* Update in real time: when editing in 2d, changes are reflected in the 3d view window.


## Next steps
1. **Split RoofGeometryGenerator into several classes, one per roof:shape or class**
2. **Introduce 2 new autotests: check mesh verticies for height/min_height**
3. **Reintroduce FAKE AO **
4. **Continue with roof:shape support.** See  Plan for roof:shape implementation section   
5. **Support of materials** (tags building:material  and roof:material). Note: material does not affect color, it affects procedurial texture and metalness.
6. **Correct icons for menus and windows.** We are currently using the default "up" and "shortcuts.svg" icons, just to make the plugin work; proper custom icons must be created and placed in the appropriate resource folders.


## Recent Accomplishments 
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
* **Start of the project** : plugin is working and building parts are rendered  as extruded bodies via OpenGL (JOGL library) 
 
 
## Misc 
 ### Build environment
 
 To build project we use maven: `mvn package`
 
 ### External source code

 * JOSM source code can be found in d:\z3dViewer\ext_sources\josm_source
 * Blosm (aka blender-osm) source code can be found in d:\z3dViewer\ext_sources\blosm_source

## Unit Testing

A unit testing suite has been set up using JUnit 5. To run the tests, execute `mvn package` from the project root.

The primary test class is `RoofGeometryGeneratorTest.java`, which focuses on validating the 3D geometry produced for different roof shapes.

### Adding New Roof Shape Tests

The existing test suite serves as a powerful tool for Test-Driven Development (TDD) when adding support for new roof shapes. To add a test for a new shape (e.g., `hipped`):

1.  **Create a Failing Test:** Add a new `@Test` method to `RoofGeometryGeneratorTest.java`, for example, `testHippedRoof()`. This method should call the (not-yet-implemented) generation logic (e.g., `RoofGeometryGenerator.generateHippedRoof(...)`) and then assert its validity using the provided helper functions:
    ```java
    @Test
    void testHippedRoof() {
        List<Point2D> base = createRectangularBase(10, 20);
        RoofGeometryGenerator.Mesh mesh = RoofGeometryGenerator.generateHippedRoof(base, 0, 5, 10);
        assertNotNull(mesh);
        assertWatertight(mesh);
        assertNormalsOutward(mesh);
    }
    ```
2.  **Implement the Feature:** Create the `generateHippedRoof` method in `RoofGeometryGenerator.java`.
3.  **Iterate:** Run `mvn package` repeatedly. The test results will guide the implementation:
    *   A failure in `assertWatertight` indicates a geometric error (a "hole" in the mesh).
    *   A failure in `assertNormalsOutward` indicates an incorrect vertex winding order for a face.
4.  **Succeed:** Continue refining the implementation until all tests pass.

## Plan for roof:shape implementation

### Overview

The `roof:shape` tag in OpenStreetMap is used to describe the shape of a building's roof. This plan outlines the steps to implement support for this tag in the 3D viewer plugin.

Already supported: 
* 'flat'     
* 'pyramidal'
* 'dome'
* 'onion'
* 'half-dome'
* 'skillion' 
* 'gabled'  - for quadrilateral polygons.
* 'hipped' - for quadrilateral polygons.

Yet to be implemented:
* 'half-hipped'
* 'round'
* 'gambrel'
* 'cross_gabled'
* 'saltbox' 
* 'mansard' 
* 'zakomar'
* 'gabled'  - for arbitrary polygons. there is quite complex algorithm for this in blosm, but it handles only rectangular-like buildings (just with more verticies). I am not aware of proper implementation of gabled roof for Г-shaped or П-shaped buildings.


Reference implementation from patched blosm blender addon should be reused, see:

* d:\z3dViewer\ext_sources\blosm_source\\building\renderer.py
* d:\z3dViewer\ext_sources\blosm_source\\building\roof\__init__.py
* d:\z3dViewer\ext_sources\blosm_source\\building\roof\zakomar.py
* d:\z3dViewer\ext_sources\blosm_source\\building\roof\profile.py
* d:\z3dViewer\ext_sources\blosm_source\\building\roof\pyramidal.py
* d:\z3dViewer\ext_sources\blosm_source\\building\roof\skillion.py
* d:\z3dViewer\ext_sources\blosm_source\\building\roof\half_hipped.py
* d:\z3dViewer\ext_sources\blosm_source\\building\roof\hipped.py
* d:\z3dViewer\ext_sources\blosm_source\\building\roof\mansard.py
* d:\z3dViewer\ext_sources\blosm_source\\building\roof\conic_profile.py
    
4.  **Default behavior:** If `roof:shape` is unknown or missing, render a `flat` roof.

## Learnings

*   **Gabled Roof Geometry:** For gabled roofs, the triangular gable end should not be a separate face from the rectangular wall below it. The entire gable wall should be generated as a single, continuous polygon (a pentagon in the case of a simple gable) to ensure correct rendering, lighting, and a seamless appearance. This is consistent with how `skillion` roofs are handled.
*   **Tessellation for Complex Polygons:** The `GLU.gluTess` functions are essential for correctly rendering non-convex polygons, which are common in building footprints. Relying on `GL_QUADS` or `GL_POLYGON` is insufficient for complex shapes.
*   **Skillion Roof Geometry:** A skillion roof is not just a sloped plane. It requires the generation of trapezoidal side walls that connect the roof edge to the building's base, colored with the wall color.
*   **JOSM API for Panning:** The correct way to programmatically pan the map is via `NavigatableComponent.zoomTo(EastNorth newCenter)`. This method is inherited by `MapView` and is the reliable way to control the map view using geographic coordinates.
*   **Coordinate System Transformation:** For an intuitive 3D panning experience that controls the 2D map, a careful transformation of the mouse movement vector is required. This involves: 1) Inverting the vector for a "drag" feel, 2) Aligning the screen's Y-down coordinate system with the map's North-up system, and 3) Rotating the final vector by the camera's current angle (`camY_angle`) to ensure the pan direction always matches the user's perspective.
*   **User-Centric Panning Sensitivity:** Panning speed should be tied to the user's context. Linking the pan sensitivity to the 3D camera's distance (`cam_dist`) provides a more natural and intuitive interaction than linking it to the 2D map's scale.
*   **JOSM Preference API Complexity:** The JOSM Preference API (specifically `PreferenceSetting` and `TabPreferenceSetting`) is more complex and version-sensitive than initially anticipated. Direct implementation of `TabPreferenceSetting` requires careful adherence to all interface methods (`addGui`, `ok`, `isExpert`, `getIconName`, `getTitle`, `getTooltip`, `getDescription`, `addSubTab`, `registerSubTab`, `getSubTab`, `getSelectedSubTab`, `selectSubTab`, `getHelpContext`, `getSubTabs`), and their exact signatures (return types, parameters).
*   **`addGui` Method Usage:** For `TabPreferenceSetting`, the `addGui` method is used to add the GUI components to the tab's `PreferencePanel` (obtained via `gui.createPreferenceTab(this, false)`), not to create the tab itself. The `PreferencePanel` uses `GridBagLayout`.
*   **Icon Naming Conventions:** JOSM's `ImageProvider` expects specific icon names, often including a path (e.g., `preferences/dialogs/preferences.svg` or `shortcuts.svg`). Using generic names like "up" will result in `JosmRuntimeException`.
*   **GridBagLayout for UI Alignment:** To align components to the top in `GridBagLayout`, `weighty = 0.0` should be set for the components, and a "vertical glue" (`JPanel` with `weighty = 1.0`) should be added at the end to absorb remaining vertical space.