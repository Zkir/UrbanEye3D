# Ideas for furture development

This document contains some plans for features, that we would like to implement. Those plans are detailed a bit more, so they cannot be stored in GEMINI.md


# General ideas

In order [voted](https://community.openstreetmap.org/t/urban-eye-3d-josm-3d-viewer-plugin/133674/240) by the community.

1. Add more objects, e.g. street lights, benches, statues, forests(!) -- **[in progress]**

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

* Explore further the integration with **Osm2World**. 
    * What is a best way to use it? Can it be an alternative rendering engine in the plugin (considering it's limitations)?
      Can it be a separate (manually updatable) window? 
	
* Implement other roof shapes:
	* Implement `zakomar` roof somehow. 
		* It was implemented in Blosm, but that implementation is not suitable for us (not watertight). Probably boolean operation should be tried.
	* Implement  `sawtooth`, `gabled_row` roofs. They say that F4 Map supports them.  
	* Implement `butterfly`  roof. Note that the first attempt to implement it has failed. Just a new profile is not enough. Some significant changes are required in MesherLinerProfile to support such 'inverse' geometry.


## Alternative algorithm for creation of roof shapes

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
4.  **Render:** Bind the SSAO FBO and render a full-screen quad with the SSAO shader.

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

#### Performance test results:
Scene #1, City center ( ~4200 parts):
* GEOMETRY UPDATE TIME: 306 ms 
* Render Time (Average 100 frames avg): 95 ms

Scene #2, Christ the Saviour (921 parts)
* Render Time (100 frame average): 18 ms
* GEOMETRY UPDATE TIME: 78 ms 

Сonclusion: partial scene update is worth efforts