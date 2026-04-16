# Contributing to Urban Eye 3D

First off, thank you for considering contributing! Urban Eye 3D is currently a one-person project, and your help is greatly appreciated. Every contribution, whether it's code, a bug report, or a new texture, makes a difference.

This document provides guidelines for contributing to the project.

## How Can I Contribute?

### Reporting Bugs

If you spot some bugs, please let me know by opening a new issue on our [GitHub repository](https://github.com/Zkir/UrbanEye3D/issues).

To help me resolve the issue quickly, please include step to reproduce and a sample `.osm` file .

### Suggesting Enhancements

If you have an idea for a new feature or an improvement to an existing one, please open an issue to discuss it. We can then talk about the feasibility and design.

### Code Contributions

Code contributions are welcome, but please open an [issue on github](https://github.com/Zkir/UrbanEye3D/issues) to discuss suggested changes, especially in case of complex features, to avoid wasted efforts.

As artificial intelligence reshapes development in 2026, [GEMINI.md](GEMINI.md) contains insights valuable to both silicon-based and protein-based programmers.


### Translations
Translate the plugin messages in your favorite language. Luckily there are only few of them.

Currently we do not have a web translation interface (like Launchpad or Crowdin). The translation files live in the [`/po`](po/) directory – a `.pot` template and sample `.po` files.

To translate:
- Use any text editor or a dedicated tool like [Poedit](https://poedit.net/).
- Edit the `.po` file for your language (or create a new one from the `.pot` template).

Send your translated `.po` file wherever it's convenient for you:
- Open an **issue** on GitHub
- Submit a **Pull Request**
- Send me and **[OSM-message](https://www.openstreetmap.org/messages/new/Zkir)**

Please mention the language and your name (if you want to be credited). Any amount of translation is welcome.


### Improve 2D MapCSS style

2D ground plane is rendered using MapCSS. If you have a knack for styling, we welcome improvements. You can find the style files and a detailed explanation in the [`src/main/resources/mapcss-styles/`](https://github.com/Zkir/UrbanEye3D/tree/master/src/main/resources/mapcss-styles) directory.


### Create new tree textures

We are always looking for more variety, especially for tree species.  All contributed assets must have a free license (like CC0, CC-BY, CC-BY-SA) compatible with the plugin's GPL v3 license. Contributor credentials will be gratefully included in the project documentation, see [ASSET-LIST.md](ASSET-LIST.md).

Here's what is needed for tree textures, which are rendered using the billboarding method (two crossing planes):
*   **Format:** Square PNG images with a transparent background (alpha channel).
*   **Resolution:** Moderate resolution is sufficient, e.g., 256x256 or 512x512 pixels. Higher resolutions are not necessary.
*   **Style:** The image should be of a single, complete tree, viewed from the side.
*   **Variety:** We welcome images for different tree types to match OSM tags like `leaf_type=*`, `genus=*`, `species=*`, and even for different life stages (e.g., young vs. old trees).

**Examples:**

<img src="https://community-cdn.openstreetmap.org/uploads/default/original/3X/a/2/a2297115c196cd20b2ab0e99526ddb54bc78796c.jpeg" alt="Example Tree texture" height="300px"></img>
<img src="https://community-cdn.openstreetmap.org/uploads/default/original/3X/9/d/9d0c402d681c2e3cb2cffe757256852159a0d090.png" alt="Example Tree rendering" height="300px"></img>


Textured low-poly 3D models (similar to those used by other viewers) can also be considered as an alternative to billboard textures.


### Spread the Word !
If you like Urban Eye 3D, give it a [star on GitHub](https://github.com/Zkir/UrbanEye3D) and tell your friends and fellow mappers about it! 3D should rule the world!

