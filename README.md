# JOSM 3D Viewer Plugin

## Goal

Create a JOSM plugin that displays loaded buildings (including `building:part=*`) in a separate 3D window.

## Benefits for Mappers

3D OSM has been around for years. There are also various external tools to visualize it. However, ironically (since the demise of [Kenzi 3D](https://github.com/kendzi/kendzi3d)), there has been no plugin to see 3D buildings directly in JOSM.

This problem is now solved! Cartographers and 3D building enthusiasts will be able to see the results of their work in the editor they edit the data in BEFORE uploading it to OSM!


## Features:
* Just rendering of loaded building parts, no editing, no exporting, no e.t.c.
* Support of [Simple 3D Buildings](https://wiki.openstreetmap.org/wiki/Simple_3D_Buildings) specification.
* 3D rendering via openGL (jogl library)
* Orbiting around scene centre via left mouse button, zooming using mouse wheel 
* Simple support of colours (osm tags building:colour and roof:colour)
* Update in real time: when editing in 2d, changes are reflected in the 3d view window.


## Licensing

Currently [MIT LICENSE](LICENSE)

## Contributing

Any help is appreciated. However, please discuss significant code changes with me before creating a PR.