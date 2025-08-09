# Recent Accomplishments

## Version 1.1.0

### August 7, 2025

* Building part now inherits height from parent building as a default value. Also it solves the problem with disappearing buildings (see gh. issue [#1](https://github.com/Zkir/UrbanEye3D/issues/1))
* In case  height<min_height, height is set to min_height, to avoid upside-down buildings.

### August 6, 2025.

* Support of missed tags `roof:levels` and `building:min_level`. Related to [issue #5](https://github.com/Zkir/UrbanEye3D/issues/5) and [issue #1](https://github.com/Zkir/UrbanEye3D/issues/1)

### August 3, 2025
* Refactoring: `Contour` class is now located in the `utils` package

### July 31, 2025
* Autotest for Scene.updateData() -- proved to be very usefull
* Tags related to color and material (`building:colour`, `building:material`, `roof:colour`, `roof:material`) are inherited from building to parts. This improves colors significantly.
* Check whether building part belongs to a building imporved. Actual contour is tested, not only bbox.

### July 30, 2025
* [enh] Support of **roof:shape=cross_gabled:** New mesher implemented.
* [bugfix] Handling of defaults for height improved.

## Version 1.0.0

### July 29, 2025
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
* **Start of the project** : plugin is working and building parts are rendered  as extruded bodies via OpenGL (JOGL library) 