# UrbanEye3D: One Year of Making JOSM More Three-Dimensional

Hard to believe, but it’s already been a year since I kicked off the **UrbanEye3D** project. What started as an ambitious idea to make 3D editing in JOSM more intuitive (and fun) has grown into a fairly capable tool.

UrbanEye3D is a one-man open-source passion project, though it wouldn't be where it is today without the help of 5 other contributors who have pitched in along the way.

As I hit this milestone, I wanted to share a quick "state of the union" on what’s changed, what’s working, and what I’m still fighting with.

### The Year in Numbers

*   **52,959 downloads** in total.
*   Estimated **2,000–3,000 regular users** (based on version-specific download peaks).
*   **33 releases** shipped.
*   **36 bugs** squashed.
*   **69 stars** on GitHub.
*   **7 languages** (German, French, Indonesian, Italian, Cornish, Russian, Slovak) fully supported with 100% translation coverage.

### Standing on the Shoulders of Giants

UrbanEye3D didn't appear out of thin air. I hold the pioneers of 3D OSM visualization in high regard, even as I strive to push the boundaries further. As the ancient Greeks suggested, a student who does not surpass their teacher is a pitiful one!

*   **Kendzi3D:** The legend that started it all for many JOSM users. Though it is now discontinued, I’ve focused on making UrbanEye3D more user-friendly and integrated for modern mapping workflows.
*   **F4Map:** Their richness of detail is a constant inspiration. I am rapidly closing the gap in object diversity—and if I eventually manage to implement animated fountains, I might just set a new standard!
*   **osm2world:** Still the gold standard for architectural fidelity. Their support for textured facades and detailed windows remains my primary role model as I work toward more realistic buildings.

### Where it stands now (The Wins)

I’ve spent the last 12 months trying to move beyond simple grey boxes. Here are some of the highlights:

*   **Top-tier S3DB Support:** I’ve made Simple 3D Buildings a priority. My implementation handles complex roof shapes on even more complex building footprints with significantly fewer artifacts and bugs than many other viewers. This reliability comes from a core principle: every mesh generated must be **watertight** and pass rigorous automated topology testing before it ever hits the screen.
*   **Smart Ground Rendering (MapCSS):** I render flat objects—like lawns, roads, and parking lots—directly onto the ground surface textures. By utilizing JOSM's native MapCSS engine for this, I've ensured the rendering is both highly configurable and completely free of the "Z-fighting" (flickering) that plagues many 3D engines.
*   **The Botanical Engine:** I didn't just want "trees"; I wanted the *right* trees. The plugin features a botanical database that normalizes species names and infers leaf types. Thanks to spatial statistics, vegetation looks local and realistic by default.
*   **Power Lines & Infrastructure:** A major recent milestone. I now render power lines with realistic parabolic sagging, multi-wire systems, and automated tower orientation, turning high-voltage corridors into believable infrastructure.
*   **Street Life:** Support for a growing list of street furniture—benches (with correct orientation), bus stops (with glass transparency!), recycling containers, fire hydrants, and information boards.
*   **Performance:** A pixel-based culling system automatically manages level-of-detail. The engine decides what to render based on its projected screen size, keeping the framerate smooth even in the densest urban environments.

### The "Still in the Oven" List (The Future)

Not everything has been a straight line. There are a few big features I’m still working on:

*   **Windows and Facades:** This is the "big one." Buildings with windows and, especially, doors are infinitely more interesting than plain grey boxes, even those with correct roofs. I’ve actually come quite close to making this work, but I ran into significant performance drops and—surprisingly for Java—memory leaks. I’m hopeful these can be resolved in the near future, likely marking the jump to version 3.0.
*   **Building Passages (`building=passage`):** I really want these to work, but the geometry is tricky. It’s a high-priority goal for realistic urban navigation.
*   **Industrial Details:** Chimneys (`man_made=chimney`) and more complex shapes like frustums are still in the planning phase.
*   **Forest Density:** While the plugin supports `natural=wood`, I want to make it smarter—subtracting road corridors and handling manual tree placements more gracefully.

### AI usage disclosure

While it’s common to criticize LLMs and "AI slop," I have to admit that without **Google Gemini**, this project likely wouldn't have been possible. An LLM can't write a non-trivial program from a single prompt; it quickly hits a dead end without constant guidance and correction. However, when a programmer knows exactly what they are doing, the efficiency of this tool is staggering. After all, I’ve spent the last twenty years doing essentially the same thing: writing prompts and technical specifications for "protein-based" developers.

### Why I do this

The goal remains the same: to make the 3D side of OpenStreetMap visible and editable directly where the mapping happens. When you can see the roof shape or the power line traverse you just tagged, you catch errors faster and feel more connected to the data.

Huge thanks to the contributors, those who have tested the plugin, reported bugs, or just sent a word of encouragement. 