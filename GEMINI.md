# Urban Eye 3D – JOSM 3D Viewer Plugin

## Goal

Create a JOSM plugin that displays loaded buildings (including `building:part=*`) in a separate 3D window.

## Next Steps

### Musts for the Next Release 

*  TG, none.

### Ideas for the Further Development 
* Implement **partial scene update**. If a primitive is changed, geometry of only related objects should be updated, not of the whole scene. 
* **Continue with roof:shape support.**
    * support linear profile roofs for arbitrary quasi-quadrangular bases. seems to be very tricky.
        * See  Plan for roof:shape implementation section   
    * implement zakomar roof somehow. maybe boolean operation should be tried.    
* **More efficient check** for building/building part belongings based on r-tree  
* **Real Ambient Occlusion.** See "Plan for Screen-Space Ambient Occlusion (SSAO) Implementation" section below
* **Support of materials** (tags building:material  and roof:material). Note: material does not affect color, it affects procedural texture and metalness. 
    * Some more advanced shading is obviously required. 
* **New Icons** We need to ask an artist to draw more interesting icons. Requirements: svg format, size 48x48px
* osm2world supports [windows](https://wiki.openstreetmap.org/wiki/Key:window). we also want that.


## Recent Accomplishments 

### August 9, 2025
* primitiveId  added to RenderableBuildingElement

#### Performance tests:
Scene #1, Сity center ( ~4200 parts):
* GEOMETRY UPDATE TIME: 306 ms 
* Render Time (Average 100 frames avg): 95 ms

Scene #2, Christ the Saviour (921 parts)
* Render Time (100 frame average): 18 ms
* GEOMETRY UPDATE TIME: 78 ms 

Сonclusion: partial scene update is worth efforts

### August 8, 2025
* Version uplifted to 1.2.0
* Small refactorings
* [pythonic script](collect_tags.py) to collect actually used tags has been created. taginfo.json has been submited to taginfo projects.
* Support of skillion roof for multipolygons.

### Earlier
See [Devblog page](DEVBLOG.md)

## Architectural Notes

*   **Core Principle:** All meshes for all roof shapes must be generated as **watertight** bodies with correct **outward-facing normals**. This is enforced by the `assertWatertight` and `assertNormalsOutward` checks in the `RoofGeometryGeneratorTest`.
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
│                   │
│                   ├── roofgenerators           // Logic for creating roof geometries
│                   │   ├── RoofShapes.java      // Enum mapping roof tags to generators
│                   │   ├── RoofGenerator.java   // Interface for all roof generators
│                   │   ├── MesherFlat.java      // Generator for flat roofs
│                   │   └── ...                  // Other roof generator implementations
│                   │
│                   └── utils                    // Helper classes
│                       ├── Contour.java         // data structure and utils for building 2D outline.   
│                       ├── Mesh.java            // 3D mesh data structure
│                       ├── Point2D.java         // 2D point
│                       └── Point3D.java         // 3D point
│
└── test
    └── java
        └── ru
            └── zkir
                └── urbaneye3d
                    └── RoofGeometryGeneratorTest.java // JUnit tests for roof geometry
```

## Operation instructions

*   **Definition of Done:** A task is considered DONE only when `mvn package` completes successfully without any errors.
*   **Human testing required:** Do not proceed to next task, before previous one is confirmed by a human.
*   **Do not suggest git commits**. Git commits in this project are allowed for protein-based developers only.
*   **JOSM source code** can be found in d:\z3dViewer\ext_sources\josm_source
*   **Blosm (aka blender-osm) source code** can be found in d:\z3dViewer\ext_sources\blosm_source


## Unit Testing

A unit testing suite has been set up using JUnit 5. To run the tests, execute `mvn package` from the project root.

The primary test class is `RoofGeometryGeneratorTest.java`, which focuses on validating the 3D geometry produced for different roof shapes.

### Adding New Roof Shape Tests

The existing test suite serves as a powerful tool for Test-Driven Development (TDD) when adding support for new roof shapes.
When a new roof shape is added to the RoofShapes enum, e.g. RoofShapes.HIPPED, it is added to the test suite AUTOMATICALLY.
No specific code changes are necessary.

New specific test can be added when the new roof shape have some known pecularities in special cases. e.g. have non-default values for roof:direction, roof:orientation e.t.c.

To add a test for a new shape (e.g., `hipped`):

1.  **Create a Failing Test:** Add a new `@Test` method to `RoofGeometryGeneratorTest.java`, for example, `testHippedRoof()`. This method should call the (not-yet-implemented) generation logic (e.g., `MesherHipped.generate(...)`) and then assert its validity using the provided helper functions:
    ```java
    @Test
    void testHippedRoof() {

        ArrayList<Point2D> basePoints = createRectangularBase(14, 10);
        LatLon origin = new LatLon(55,37);
        RenderableBuildingElement.Contour contour = new RenderableBuildingElement.Contour(basePoints);
        RenderableBuildingElement test_building = new RenderableBuildingElement(origin, contour,  10, 0, 6,
                "","", RoofShapes.HIPPED.toString(), "45","" );

        Mesh mesh = RoofShapes.HIPPED.getMesher().generate(test_building);

        //common set of topology checks for a mesh.
        AssertMeshTopology(mesh, test_building.minHeight, test_building.height, RoofShapes.HIPPED.toString());

    }
    ```
2.  **Implement the Feature:** Create the `generate` method in a new `MesherHIPPED.java`.
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
* 'cross_gabled' - for quadrilateral polygons.

Yet to be implemented:

* 'zakomar' -- no good implementation in in blosm (does not form watertight mesh)
* Linear profile roof (`gabled`, `round`) for arbitrary polygons.  It is highly needed, since used in existing models from TOP-200.

There is quite complex algorithm to create gabled roofs for n-gons in blosm, but it handles only rectangular-like buildings .
A rectangular-like  means that the building is basically quadrangular(just with more verticies in contour), and the deviations from a quadrangle, although there are, are insignificant.

NB: F4 has implementation of non-convex (Г-shaped or П-shaped) roofs!
See example: https://demo.f4map.com/#lat=56.3106825&lon=38.1273214&zoom=19&camera.theta=55.313&camera.phi=-14.037

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


## Plan for Performance-Сheck

### Goal

Determine whether it makes sense to invest in partial scene updates. For this, we need to measure and compare the time spent on two key operations:

1.  **Geometry Update:** Calculation and creation of 3D meshes for buildings (`Scene.updateData()`)
2.  **Rendering:** Displaying the already created geometry on the screen (`Renderer3D.display()`)

### Tools

We will use standard Java tools for time measurement — `System.nanoTime()` — and output the results to the console using `System.out.println()`.

---

#### Step 1: Measuring Geometry Update Time

This measures how long it takes to fully recalculate the entire scene after making changes to the OSM data.

1.  **Location:** `DialogWindow3D.java` file.
2.  **Logic:** We will find the `updateData()` method and wrap the `scene3d.updateData()` call in a timer.

    ```java
    // In DialogWindow3D.java, inside the updateData() method

    private void updateData() {
        long startTime = System.nanoTime(); // <--- START

        if (listenedLayer != null) {
            scene3d.updateData(listenedLayer.getDataSet());
        } else {
            scene3d.updateData(null);
        }

        long endTime = System.nanoTime(); // <--- END
        long durationMs = (endTime - startTime) / 1_000_000;
        System.out.println("--- GEOMETRY UPDATE TIME: " + durationMs + " ms ---");

        renderer3D.repaint();
    }
    ```
3.  **What we will see:** After each change in JOSM (moving a node, changing a tag), a single line will appear in the console showing how many milliseconds it took to fully recalculate the geometry.

---

#### Step 2: Measuring the Rendering Time of a Single Frame

This measures how long it takes to render an already prepared scene. This code is executed for each frame (i.e., many times per second).

1.  **Location:** `Renderer3D.java` file.
2.  **Logic:** We will add a timer at the beginning and end of the `display()` method. To avoid cluttering the console, we will output the average time, for example, every 100 frames.

    ```java
    // In the Renderer3D.java file

    private long frameCount = 0;
    private long totalFrameTime = 0;

    @Override
    public void display(GLAutoDrawable drawable) {
        long startTime = System.nanoTime(); // <--- START

        // ... (all existing rendering code: gl.glClear, loop through buildings, etc.)

        long endTime = System.nanoTime(); // <--- END
        totalFrameTime += (endTime - startTime);
        frameCount++;

        if (frameCount == 100) {
            long averageTimeNs = totalFrameTime / 100;
            long averageTimeMs = averageTimeNs / 1_000_000;
            System.out.println("Average Render Time (100 frames): " + averageTimeMs + " ms");
            frameCount = 0;
            totalFrameTime = 0;
        }
    }
    ```
3.  **What we will see:** Messages about the average rendering time will periodically appear in the console. This will show how "heavy" the scene is for the graphics card.

---

#### Step 3: Analysis of the Results

1.  **Launch JOSM** with the plugin and open a test file with a large number of buildings.
2.  **Look at the console output:** You will see a constant stream of messages about the rendering time.
3.  **Make a change:** Move a building node or change a tag.
4.  **Compare the numbers:**
    *   A single large number will appear in the console — the **geometry update time**.
    *   Compare it with the average **rendering time**.

**Conclusion:**

*   **If the geometry update time (e.g., 500 ms) is significantly longer than the rendering time (e.g., 10 ms)**, this is a clear sign that the bottleneck is in the geometry calculation. In this case, **implementing partial scene updates will provide a huge performance boost**, as we will avoid the costly operation.
*   **If the update time is comparable to or less than the rendering time**, the problem is more likely in the complexity of the scene itself, and partial updates will have less effect.

This plan will allow us to obtain clear, measurable data to make a decision.


*   **JOSM Plugin Lifecycle:** `UrbanEye3dPlugin` is the entry point. It initializes `DialogWindow3D`, which is a `ToggleDialog`. JOSM automatically handles the creation of the menu item and the visibility of the dialog.
*   **Event Handling:** The plugin listens for changes in the OSM data (`DataSetListener`) and map view (`MapView.addZoomChangeListener`) to trigger scene updates and redraws.
*   **OpenGL with JOGL:** The rendering is done in `Renderer3D` using the JOGL library, which provides Java bindings for OpenGL. The rendering pipeline is currently a fixed-function pipeline (`glBegin`/`glEnd`), with plans to move to a modern shader-based pipeline for features like SSAO.
*   **Immediate Mode Rendering:** The current rendering approach sends drawing commands to the GPU for each frame directly. While simple, it's less efficient than using Vertex Buffer Objects (VBOs), which would store geometry on the GPU.
*   **Roof Geometry Generation:** The `roofgenerators` package showcases a factory pattern. The `RoofShapes` enum acts as a factory, providing the correct `Mesher` instance for a given `roof:shape` tag. This makes it easy to add new roof shapes without changing the core rendering logic.
*   **Watertight Meshes:** A critical requirement for all generated geometry is that it must be "watertight" (i.e., have no holes). This is crucial for correct rendering and for future features like SSAO or Boolean operations. The `RoofGeometryGeneratorTest` includes checks to enforce this.
*   **TDD for Geometry:** The `RoofGeometryGeneratorTest` is a good example of Test-Driven Development. By creating a test for a new roof shape first, the implementation can be guided by the test results, ensuring correctness from the start.
*   **Coordinate Systems:** The plugin deals with multiple coordinate systems: Latitude/Longitude from OSM, East/North from JOSM's map projection, and the 3D Cartesian coordinates used for rendering. `RenderableBuildingElement` handles the conversion from OSM data to a renderable format.


