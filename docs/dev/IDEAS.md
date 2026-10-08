# Ideas for furture development

This document contains some plans for features, that we would like to implement. Those plans are detailed a bit more, so they cannot be stored in GEMINI.md


# General ideas

In order [voted](https://community.openstreetmap.org/t/urban-eye-3d-josm-3d-viewer-plugin/133674/240) by the community.

1. Support the [base:shape proposal](https://community.openstreetmap.org/t/rfc-feature-proposal-3d-tagging-for-building-base-shapes) (#35)
	
2. Improve performance/responsiveness of editing in large scenes.
    * Implement **partial scene update**. If a primitive is changed, geometry of only related objects should be updated, not of the whole scene, as now (`Scene.calculateUpdate()` rebuilds everything; only the ground plane has partial updates). 
        * Performance is not a big issue right now, but it may become important if more complex geometry (e.g. polygonal windows) is generated.
        * See [Performance test results](#performance-test-results) below -- the measurements show that partial scene update is worth the effort.
        * The tricky part is to determine what objects are related. 
			* First of all we need to process OSM primitive hierarchy: if a node is moved, then a parent way is affected. if the way is affected, parent relation is also affected.
			* Secondly, objects may be only spatially related. if a building part is moved outside of it's parent building, the latter may become visible.
			* are any other cases? It would be very embarrassing to miss something here!
			
	
3. Improve rendering, implement **real ambient occlusion** and/or **support materials** (e.g. metal and glass).	
	* **Real Ambient Occlusion.** 
		* Current rendering engine is good enough for the editing plugin. 
		* See [Plan for Screen-Space Ambient Occlusion (SSAO) Implementation](#plan-for-screen-space-ambient-occlusion-ssao-implementation) section below
		* Fake AO already exists (enabled by default, see `ToggleFakeAOAction`); the plan below is about *real* AO.
	* **Support of materials** (tags building:material  and roof:material). 
		* Note: material does not affect color, it affects procedural texture and metalness. 
		* Some more advanced shading is obviously required. 
		* Current state: material tags are used only to derive a default colour (see the `Materials` enum). Procedural textures and metalness are not implemented yet.

4. ~~Implement rendering of building passages (`tunnel=building_passage`).~~ #6 -- **DONE** (Oct 8, 2026, see `BuildingPassage`).
    * The original plan was to use boolean operations on meshes ("difference"), for example with the JCSG library (https://github.com/Zkir/JCSG), but preserving face colors with it was a big unknown.
    * In the end no mesh booleans were needed at all: the passage way is buffered into a corridor polygon in the *local* coordinate system of the building, the corridor is subtracted from the 2D footprint with JTS, and the resulting flat blocks are extruded up to the passage height. The part of the building above the passage is emitted as one more body with `min_height` = passage height, so its roof (and colors, and materials) is preserved automatically by the normal code path.
    * Still open: passages which are not at the ground level (viaducts, ramps, the Gate Tower Building case), arches (`covered=arcade`) and passages cut out of `man_made` objects.
	

Some other wishes:

* Explore further the integration with **Osm2World**. 
    * What is a best way to use it? Can it be an alternative rendering engine in the plugin (considering it's limitations)?
      Can it be a separate (manually updatable) window? 
	
* Implement other roof shapes:
	* Implement `zakomar` roof somehow. 
		* It was implemented in Blosm, but that implementation is not suitable for us (not watertight). Probably boolean operation should be tried.
	* Implement  `sawtooth`, `gabled_row` roofs. They say that F4 Map supports them.  
	* Implement `butterfly`  roof. Note that the first attempt to implement it has failed. Just a new profile is not enough. Some significant changes are required in MesherLinerProfile to support such 'inverse' geometry.


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


## Performance test results

Scene #1, City center ( ~4200 parts):
* GEOMETRY UPDATE TIME: 306 ms 
* Render Time (Average 100 frames avg): 95 ms

Scene #2, Christ the Saviour (921 parts)
* Render Time (100 frame average): 18 ms
* GEOMETRY UPDATE TIME: 78 ms 

Сonclusion: partial scene update is worth efforts