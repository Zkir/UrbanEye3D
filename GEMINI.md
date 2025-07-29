# JOSM 3D Viewer Plugin

## Goal

Create a JOSM plugin that displays loaded buildings (including `building:part=*`) in a separate 3D window.

## Next steps

### Musts for the first release (1.0.0)
* Settle up on naming. Should we go on with **"z3dViewer"** or could it be say *"Simple Building Viewer"* or "Falcon Eye 3D" or **UrbanEye3D** ?
* **Fancy icon for plugin and dialogs.** Currently stub icons are used, but we can improve them.
    * Icon for preferences and plugin   48px*48px svg/png
    * Icon for 3D window                24px*24px svg/png


### Further Development 

* **Continue with roof:shape support.** See  Plan for roof:shape implementation section   
* **Support of materials** (tags building:material  and roof:material). Note: material does not affect color, it affects procedurial texture and metalness.
* **Draw not only building parts, but also building.** Algorithm to decide whether buildings should be drawn is yet to be coined.


## Recent Accomplishments 
### July 28, 2025
* **Support of "linear profile" roof shapes:** `round`, `gambrel`, `saltbox` roofs are supported. Obviously, for quadrangle bases only. 
* **Stub icons for dialog, preferences and plugin itself**
* **Return of fake AO.**  Even this simple type of shading make picture much better.
* **Proper registration of Wireframe mode shortcut.** Pressin "Z" now works also when 3d window is docked.
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
* 'mansard' - for quadrilateral polygons.
* 'round' - for quadrilateral polygons.
* 'gambrel' - for quadrilateral polygons.
* 'saltbox' - for quadrilateral polygons. Also, there is no cosistent opionion about what this shape is.
* 'half-hipped' - for quadrilateral polygons.

Yet to be implemented:

* 'cross_gabled'
* 'zakomar' -- no good implementation in in blosm (does not form watertight mesh)
* Linear profile roof (`gabled`, `round`) for arbitrary polygons.  It is highly needed, since used in existing models from TOP-200.

There is quite complex algorithm to create gabled roofs for n-gons in blosm, but it handles only rectangular-like buildings . 
A rectangular-like  means that the building is basically quadrangular(just with more verticies in contour), and the deviations from a quadrangle, although there are, are insignificant.
I am not aware of proper implementation of gabled roof for Г-shaped or П-shaped buildings.

If we knew how to do Boolean operations on meshes, the algorithm would become trivial.

1) determine the quadrangular base of the roof.
2) construct the roof volume on the quadrangular base using a simple algorithm.
3) find the INTERSECTION between the roof volume and the mass model of the building.
4) find the UNION between the resulting volume and the lower part of the building.
5) that's it!


See taginfo for known values of `roof:shape` tag:
* https://taginfo.openstreetmap.org/keys/roof%3Ashape#values
* https://wiki.openstreetmap.org/wiki/OSM-4D/Roof_table

### Reference implementation of roof:shapes

Reference implementation from patched blosm blender addon should be reused whenewer possible, see:

* d:\z3dViewer\ext_sources\blosm_source\building\renderer.py
* d:\z3dViewer\ext_sources\blosm_source\building\roof\__init__.py
* d:\z3dViewer\ext_sources\blosm_source\building\roof\zakomar.py
* d:\z3dViewer\ext_sources\blosm_source\building\roof\profile.py
* d:\z3dViewer\ext_sources\blosm_source\building\roof\pyramidal.py
* d:\z3dViewer\ext_sources\blosm_source\building\roof\skillion.py
* d:\z3dViewer\ext_sources\blosm_source\building\roof\half_hipped.py
* d:\z3dViewer\ext_sources\blosm_source\building\roof\hipped.py
* d:\z3dViewer\ext_sources\blosm_source\building\roof\mansard.py
* d:\z3dViewer\ext_sources\blosm_source\building\roof\conic_profile.py
   


## Plan for Screen-Space Ambient Occlusion (SSAO) Implementation

Implementing SSAO requires a shift from the immediate-mode rendering pipeline to a modern, shader-based, multi-pass approach.

### Core Requirements
1.  **Shader-based Pipeline:** Transition from `glBegin`/`glEnd` to GLSL shaders for rendering.
2.  **Framebuffer Objects (FBOs):** Use FBOs to render the scene into off-screen textures.
3.  **Multi-Pass Rendering:** The `display` method will execute a sequence of rendering passes instead of a single one.

### Implementation Steps

#### Step 1: G-Buffer Pass
The goal is to render the scene's geometry data into a set of textures called a G-Buffer.

1.  **Configure FBO:** Create an FBO to manage the G-Buffer textures.
2.  **Define G-Buffer Textures:**
    *   **Position Texture:** Stores world-space coordinates (XYZ) for each pixel.
    *   **Normal Texture:** Stores normal vectors (XYZ) for each pixel.
    *   **Depth Texture:** The standard depth buffer.
3.  **Create G-Buffer Shader (GLSL):**
    *   **Vertex Shader:** Transforms vertex positions to screen space.
    *   **Fragment Shader:** Writes the fragment's world-space position and normal to the corresponding G-Buffer textures.
4.  **Render:** In the `display` method, bind the G-Buffer FBO and render all buildings using this shader.

#### Step 2: SSAO Calculation Pass
This pass computes the ambient occlusion factor for each pixel using the G-Buffer data.

1.  **Configure SSAO FBO:** Create a new FBO with a single-channel (grayscale) texture to store the AO results.
2.  **Prepare Uniforms:**
    *   **Sample Kernel:** Generate an array of random sample vectors within a hemisphere, used to sample the area around a fragment.
    *   **Noise Texture:** Create a small, tiling texture with random rotation vectors to eliminate banding artifacts.
3.  **Create SSAO Shader (GLSL):**
    *   **Vertex Shader:** Renders a full-screen quad.
    *   **Fragment Shader:**
        *   For each fragment, retrieve its position and normal from the G-Buffer.
        *   Iterate through the sample kernel, transforming each sample into world space.
        *   Project each sample back to screen space and compare its depth with the value in the position/depth texture.
        *   If a sample is behind the stored fragment, it contributes to the occlusion factor.
        *   The final occlusion value (0.0 to 1.0) is written to the SSAO texture.
4.  **Render:** Bind the SSAO FBO and render a full-screen quad using the SSAO shader.

#### Step 3: Blur Pass
The raw SSAO output is noisy and requires smoothing.

1.  **Configure Blur FBO:** Create a final FBO to hold the smoothed AO texture.
2.  **Create Blur Shader (GLSL):** A simple shader that samples neighboring pixels in the SSAO texture and averages them (e.g., a Gaussian blur).
3.  **Render:** Bind the Blur FBO, use the noisy SSAO texture as input, and render a full-screen quad with the blur shader.

#### Step 4: Final Lighting and Composition Pass
This pass combines the original scene color with the ambient occlusion map.

1.  **Bind Default Framebuffer:** Switch rendering back to the screen.
2.  **Create Final Composite Shader (GLSL):**
    *   **Vertex Shader:** Renders a full-screen quad.
    *   **Fragment Shader:**
        *   Samples the original scene color (from a color texture generated in the G-Buffer pass or calculated anew).
        *   Samples the smoothed AO factor from the blur texture.
        *   Multiplies the scene color by the AO factor (`finalColor = sceneColor * aoFactor`).
        *   Applies any additional lighting (like the directional sun light).
        *   Outputs the final color to the screen.
3.  **Render:** Render a full-screen quad to display the final, beautifully shaded image.

## Operation instructions

*   **Definition of Done:** A task is considered DONE only when `mvn package` completes successfully without any errors.
*   **Human testing required:** Do not proceed to next task, before previous one is confirmed by a human.
*   **Do not suggest git commits**. Git commits in this project are allowed for protein-based developers only.
*   **JOSM source code** can be found in d:\z3dViewer\ext_sources\josm_source
*   **Blosm (aka blender-osm) source code** can be found in d:\z3dViewer\ext_sources\blosm_source


## Learnings

*   **Polygon Merging and Winding Order:** When merging triangles from a tessellator back into a polygon with holes, it's not enough to just find the boundary edges. It is critical to ensure the correct winding order for the resulting polygons: counter-clockwise for the outer boundary and clockwise for all inner holes. This is essential for the face normals to point in the correct direction.
*   **Signed Area for Winding Detection:** A reliable way to programmatically determine and correct the winding order is to calculate the signed area of each reconstructed polygon. This allows for identifying the outer polygon (the one with the largest area) and then reversing the vertex order of any polygon (outer or inner) that has an incorrect orientation.
*   **Limitations of Center-Based Normal Testing:** The unit test `assertNormalsOutward` is flawed for non-convex shapes or shapes with holes. Its logic, which assumes normals should point away from the object's geometric center, fails when the center lies outside the solid volume (e.g., inside a hole). This test needs a more robust implementation for complex geometries.
*   **Gable Coloring on No-Wall Buildings:** A key detail of the Simple 3D Buildings specification is that gable ends are considered part of the wall. When generating a gabled roof on a building with no wall height (i.e., the roof starts at `min_height`), the resulting triangular gable faces must still be treated as wall surfaces and colored accordingly, not as roof surfaces.
*   **Mesh Generation for No-Wall Scenarios:** When a building's `wallHeight` is equal to its `minHeight`, the logic for generating roof geometry must be carefully adapted. To avoid creating degenerate, zero-length edges and to ensure the mesh remains watertight, the generation code must reuse the base vertices instead of creating a duplicate set of vertices at the same location for the top of the "wall". This applies to `flat`, `gabled`, and `hipped` roofs.
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

