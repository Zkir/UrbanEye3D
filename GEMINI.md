# JOSM 3D Viewer Plugin

## Goal

Create a JOSM plugin that displays loaded buildings (including `building:part=*`) in a separate 3D window.

# [Desigred] Features:
* Just rendering of loaded building parts, no editing, nothing else.
* Support of [Simple 3D Buildings](https://wiki.openstreetmap.org/wiki/Simple_3D_Buildings) specification
* 3D rendering via openGL (ogl library)
* Orbiting around scene centre via mouse left button, zooming using mouse wheel 
* Simple support of colours (osm tags building:colour and roof:colour)
* Update in real time: when editing in 2d, changes are reflected in the 3d view window.



## Next steps
1. **Cursor icons** When user presses the left mouse button in the plugin window, the cursor changes to the eye (thus expressing the Orbiting mode), and when user presses the 
right mouse button, it changes to crossed arrows (expressing the movement of the map), as it is in JOSM?
2. **Support multipolygons (relations)**. Support of multipolygons/relations should be improved according to the reference implementation (see  here: D:\z3dViewer\misc\osmGeometry.py, ExtractCloseNodeChainsFromRelation() function.)
Also, additional features should be supported: if there are several outer rings, polygon should be split and several Contour objects should be created.
(??If there are holes, polygon should be cut. ??)

4. **Support roof shapes from roof:shape tag.** See  Plan for roof:shape implementation section   
5. **Support of materials** (tags building:material  and roof:material). Note: material does not affect color, it affects procedurial texture and metalness.


## Recent Accomplishments 

### July 24, 2025
* **Initial support for relations/multipolygons.** At least they work somehow. Several bugs expected.
* **Bug with xy/z proportions fixed **. xy coordinates are calculated in proper meters, in the same scale as height.
* **Rendering of non-convex polygons.**  It turned out that  gl.glBegin(GL2.GL_POLYGON) properly renders CONVEX polygons only, which is not always the case for building contours. We use tessellation to handle that.
* **Rendering made more interesting.**  Parallel light (sun) has been introduced along with curent Fake AO  shading. 

###  July 23, 2025

*   **Map Movement Sync:** The 3D viewer now correctly responds to panning and zooming events on the main JOSM map, redrawing the scene as the viewport changes.
*   **Z-Up Coordinate System:** The rendering engine was refactored to use a Z-up coordinate system, which is more conventional for 3D architectural visualization. The Z-axis is now vertical.
*   **Lifecycle and Bug Fixes:**
    *   Resolved critical `NullPointerException` and `IllegalArgumentException` crashes related to improper listener management when layers were removed or the application was closed.
    *   Removed the redundant, manually-created "Windows" menu item, relying on JOSM's native handling for toggle dialogs. This also fixed a startup crash when no data was loaded.

 
## Misc 
 ### Build environment
 
 To build project we use maven: `mvn package`
 
 ### JOSM source code

 Josm source code can be found in D:/z3dViewer/josm_source

## Plan for roof:shape implementation

### Overview

The `roof:shape` tag in OpenStreetMap is used to describe the shape of a building's roof. This plan outlines the steps to implement support for this tag in the 3D viewer plugin.


### Data Collection

1.  **Extend the `Building` data class:** Add a `roofShape` field (String) to the `Building` class in `Z3dViewerDialog.java`.
2.  **Extract `roof:shape` tags:** In the `updateData()` method, read the `roof:shape` tag from each `Way` object and store it in the new `roofShape` field. Default to `"flat"` if the tag is not present.

### Roof Geometry Generation (in `Renderer3D.java`)

1.  **Create a roof factory method:** Implement a new private method, `drawRoof(GL2 gl, Building building, List<Point3D> basePoints)`, to handle all roof rendering logic.
2.  **Use conditional logic:** Inside `drawRoof`, use a `switch` or `if-else if` statement to select the correct rendering method based on the `building.roofShape` value.
3.  **Implement geometry for each shape:**

Reference implementation from patched blosm blender addon should be reused, see:

* c:\Users\zkir\AppData\Roaming\Blender Foundation\Blender\4.5\scripts\addons\blosm\building\renderer.py
* c:\Users\zkir\AppData\Roaming\Blender Foundation\Blender\4.5\scripts\addons\blosm\building\roof\__init__.py
* c:\Users\zkir\AppData\Roaming\Blender Foundation\Blender\4.5\scripts\addons\blosm\building\roof\zakomar.py
* c:\Users\zkir\AppData\Roaming\Blender Foundation\Blender\4.5\scripts\addons\blosm\building\roof\profile.py
* c:\Users\zkir\AppData\Roaming\Blender Foundation\Blender\4.5\scripts\addons\blosm\building\roof\pyramidal.py
* c:\Users\zkir\AppData\Roaming\Blender Foundation\Blender\4.5\scripts\addons\blosm\building\roof\skillion.py
* c:\Users\zkir\AppData\Roaming\Blender Foundation\Blender\4.5\scripts\addons\blosm\building\roof\half_hipped.py
* c:\Users\zkir\AppData\Roaming\Blender Foundation\Blender\4.5\scripts\addons\blosm\building\roof\hipped.py
* c:\Users\zkir\AppData\Roaming\Blender Foundation\Blender\4.5\scripts\addons\blosm\building\roof\mansard.py
* c:\Users\zkir\AppData\Roaming\Blender Foundation\Blender\4.5\scripts\addons\blosm\building\roof\conic_profile.py
    
4.  **Default behavior:** If `roof:shape` is unknown or missing, render a `flat` roof.
