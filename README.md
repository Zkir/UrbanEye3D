# Urban Eye 3D – JOSM 3D Viewer Plugin
[![release](https://img.shields.io/github/v/release/Zkir/UrbanEye3D)](https://github.com/Zkir/UrbanEye3D/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/Zkir/UrbanEye3D/latest/total.svg)](https://tooomm.github.io/github-release-stats/?username=Zkir&repository=UrbanEye3D)
[![Downloads](https://img.shields.io/github/stars/Zkir/UrbanEye3D)](https://github.com/Zkir/UrbanEye3D/stargazers)

**Urban Eye 3D** is a JOSM plugin that provides a dedicated 3D view (dockable or floating) to visualize loaded buildings and building parts (`building=*` and `building:part=*`).

![Docked window](docs/images/pic1.png)

## Benefits for Mappers

While 3D visualization of OSM data has existed for years through external tools, the absence of a dedicated 3D viewer in JOSM (particularly since the discontinuation of [Kenzi 3D](https://github.com/kendzi/kendzi3d)) has been a notable gap. 

Urban Eye 3D solves this problem! Mappers and 3D building enthusiasts can now preview their edits directly within JOSM before commiting changes to OpenStreetMap.

## Key Features
* Support for the [Simple 3D Buildings](https://wiki.openstreetmap.org/wiki/Simple_3D_Buildings) specification
* Visualization only  – no editing or export functionality
* Intuitive navigation, similar to modern 3D editors:  
  - Orbit (left mouse drag)  
  - Zoom (mouse wheel)
  - Pan (right mouse drag)
* Basic colour support for `building:colour` and `roof:colour` tags
* Two modes: *solid* and *wireframe*. Press 'z' to switch between them
* Real-time updates: Changes made in JOSM instantly reflect in the 3D view

See [features.md](docs/features.md) for the list of supported tags and roof shapes.


### Limitations
* Several roof shapes (`mansard`, `half-hipped` and `cross_gabled`) are supported for quadrilateral polygons only. 
Support for those roof shapes on arbitrary non-convex polygons may be added in future versions.

## How to install
Please install Urban Eye 3D just like any other plugin in JOSM:

![how to activate](docs/images/pic3_how_to_activate.png)

1. In JOSM, open the menu **Edit → Preferences**, find the **Plugins** tab.
2. If needed, hit the **"Download list"** button,
3. Select the **"Available"** radio button,
4. Type **"urba..."** in the search field,
5. Don’t forget to **check the box**,
6. Enjoy!


## Supported platforms
As a JOSM plugin written in *pure Java*, it works perfectly on **Windows**, **Mac**, and **Linux**. 
Some users have reported problems with graphics drivers on certain Linux distributions, but with Linux, you're on your own :)

## Licensing
Inspired by the GNU GPL-licensed [Blosm](https://github.com/vvoovv/blosm) project and following JOSM's [plugin licensing recommendations](https://josm.openstreetmap.de/wiki/DevelopersGuide/DevelopingPlugins#LegalStuff), this code is licensed under [GNU GPL v3](LICENSE).

The licensing for third-party assets used in this project (such as textures) is detailed in the [ASSET-LIST.md](ASSET-LIST.md) file.

## Contributing
Contributions are welcome!  
* See the [the Contribution guide](CONTRIBUTING.md) in case you would like to contribute code or artwork.
* You can also contribute to this project by giving us a star :)

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=Zkir/UrbanEye3D&type=Date&theme=dark" />
  <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=Zkir/UrbanEye3D&type=Date" />
  <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=Zkir/UrbanEye3D&type=Date" />
</picture>

---

The Urban Eye is watching!  

<img src="docs/images/pic2.jpg" alt="Urban Eye" width="250px" />
