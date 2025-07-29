# Urban Eye 3D -- JOSM 3D Viewer Plugin

## Goal

Create a JOSM plugin that displays loaded buildings (including `building:part=*`) in a separate 3D window, docked or free floating.

![Docked window](docs/images/pic1.png)

## Benefits for Mappers

3D OSM has been around for years. There are also various external tools to visualize it. However, ironically (since the demise of [Kenzi 3D](https://github.com/kendzi/kendzi3d)), there has been no plugin to see 3D buildings directly in JOSM.

This problem is now solved! Cartographers and 3D building enthusiasts will be able to see the results of their work in the editor they edit the data in BEFORE uploading it to OSM!


## Features:
* Support of [Simple 3D Buildings](https://wiki.openstreetmap.org/wiki/Simple_3D_Buildings) specification.
* Just rendering of loaded buildings and parts, no editing, no exporting, no e.t.c.
* Orbiting around scene centre via left mouse button, zooming using mouse wheel 
* 3D rendering via openGL (jogl library)
* Simple support of colours (osm tags `building:colour` and `roof:colour`)
* Update in real time: when editing in main JOSM window, changes are reflected in the 3d view window instantly.


## Licensing

Currently [MIT LICENSE](LICENSE)

## Contributing

Any help is appreciated. However, please discuss significant code changes with me before creating a PR.


### AI usage

In year 2025 Artificial Intelligence is already here and rules the world! [GEMINI.md](GEMINI.md) contains notes usefull for both silicon and protein programmers.

The Urban Eye is looking at You!

<img src="docs/images/pic2.jpg" alt="Urban Eye" width="250px" />