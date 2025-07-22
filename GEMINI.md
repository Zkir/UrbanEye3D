# JOSM 3D Viewer Plugin

## Goal

Create a JOSM plugin that displays loaded buildings (including `building:part=*`) in a separate 3D window.

# Features:
* Just rendering of loaded building parts, no editing, nothing else.
* Support of Simple 3D Building specification


## Next steps

1. Make 3d rendering via OpenGL, support orbitiong around scene centre via mouse left button, zooming using mouse wheel, and panning using right mouse button.
2. support min_height tag. 
2. Support colors and materials (osm tags building:colour and building:material). Note: material does not affect color, it affects procedurial texture.
3. Support roof shapes via roof:shape. reference implementation from patched blosm blender addon should be reused.
see:

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



## josm source code

 Josm source code can be found in D:/z3dViewer/josm_source
 
## Misc 
 to build project we use maven

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
    *   **`flat`:** Draw a single polygon connecting the top vertices of the walls (current implementation).
    *   **`gabled`:**
        *   Find the two longest, parallel sides of the building outline.
        *   Calculate the ridge line between them.
        *   Raise the ridge by `roof:height` (or a default value).
        *   Draw two inclined planes and two gables.
    *   **`hipped`:**
        *   Calculate the central ridge of the roof.
        *   Connect the building's vertices to the ridge, forming four inclined planes.
    *   **`pyramidal`:**
        *   Find the center point above the building outline.
        *   Raise the center point by `roof:height`.
        *   Connect each vertex of the building outline to the center point, forming a series of triangles.
    *   **`skillion`:**
        *   Determine the "upper" side of the roof (from `roof:direction` or by choosing the longest side).
        *   Raise the vertices of the upper side by `roof:height`.
        *   Draw a single inclined plane.
    *   **Other shapes (`dome`, `round`, etc.):** Initially, render as `flat`. More complex shapes can be approximated with a mesh of triangles in the future.
4.  **Default behavior:** If `roof:shape` is unknown or missing, render a `flat` roof.
