# UrbanEye3D: One Year of Making JOSM More Three-Dimensional

It’s hard to believe, but a year has already passed since I launched the **UrbanEye3D** project. We’ve managed to achieve a lot together, and there’s still much ahead, so today I’d like to summarize the year and outline our goals for the future.

## The Year in Numbers

*   **52,959 downloads** in total.
*   Estimated **2,000–3,000 regular users** (based on version-specific download peaks).
*   **33 releases** shipped.
*   **36 bugs** squashed.
*   **69 stars** on GitHub.
*   **5** contributors on GitHub.
*   **7 languages** (German, French, Indonesian, Italian, Cornish, Russian, Slovak) fully supported with 100% translation coverage.

## Standing on the Shoulders of Giants

UrbanEye3D didn't appear out of thin air. I had wonderful teachers. Nevertheless, the ancient Greeks said that a student who could not surpass their teacher is simply pathetic.

*   **F4Map:** The gold standard for 3D visualization. F4Map remains a role model; it seems we are approaching it in terms of object richness, and I hope one day we will get close in terms of image quality as well. Its main drawback is the slow update rate. After all, it is a showcase, not an editor. Editing tags blindly and then waiting a day to see the result in F4 was frustrating. Many of my 3D models were created this way, though.

*   **Kendzi3D:** The first 3D plugin for JOSM. Many probably still remember it. Unfortunately, it stopped working quite a while ago, leaving the JOSM 3D plugin spot vacant. Initially, I just wanted to fix Kendzi3D, but it turned out that rewriting everything from scratch was much more interesting.

*   **Blosm (Blender-OSM):** A plugin for Blender 3D. A wonderful tool, but for editing building models, it is too cumbersome. Editing in JOSM, exporting an OSM file, and loading it into Blender just to see the result is quite slow. Moreover, Blender is complex with a high entry barrier. I borrowed part of the roof generation code from Blosm, which required rewriting it from Python to Java.

*   **osm2world:** Another role model. It has textured buildings with windows, which we don't have yet. However, I still don't quite understand its navigation system.

## Where we are (The Wins)

Here are the main achievements over the last 12 months:

*   **High-quality S3DB support:** We seem to have the most complete and, most importantly, high-quality support for the Simple 3D Buildings standard. Complex roof shapes and footprint contours are supported with minimal errors or artifacts. The principle chosen at the beginning has paid off: every generated mesh for a building or `building:part` must have correct topology and be "watertight."

*   **Intelligent ground rendering:** Flat objects—such as lawns, roads, and parking lots—are drawn directly onto the ground surface texture. This approach avoids "Z-conflicts" (flickering) and looks clean. We use JOSM's built-in MapCSS engine for this, which allowed us to implement it without reinventing the wheel.

*   **Trees and Botanical Database:** While we currently only have three tree models (conifer, broadleaf, and palm), we want them to appear correctly. Using the `leaf_type` tag is easy, but what if only the `species` is specified? We had to write an entire data pipeline that extracts tree statistics from OSM and POWO (Plants of the World Online). It feels like the effort was worth it.

*   **Street Furniture:** With a variety of street objects—lamps, benches, bins, and even bus stops—the virtual world becomes much more alive. Micromapping enthusiasts should be pleased.

## Where we want to go (The Future)

Not everything went smoothly. Some features are hard to capture, while others haven't had enough time or enthusiasm yet.

*   **Windows and Facades:** This is perhaps the most important thing. Buildings with windows and, especially, doors are much more interesting than simple grey boxes. I almost succeeded in making an intelligent texturing system for houses (with suitable textures for floors, doors on the first, dormers on the last)—similar to what exists in X-plane. It almost worked, but then I encountered performance drops and—surprisingly for Java—memory leaks. I hope to resolve these in the near future, and we might see buildings with windows in version 3.0.

*   **Building Passages:** For passages through buildings, there is a flat tag `building=passage`, but we couldn't support it yet. Making holes in 3D bodies is not as easy as it seems. Even in Blender, boolean operations between bodies are glitchy, let alone in home-grown libraries.

* **Industrial Architecture:** Chimneys (`man_made=chimney`) usually have the shape of a truncated cone, but this shape simply isn't in S3DB. Cylinders look boring and unrealistic. So chimneys are waiting for me to get to them.

* **Procedural Trees:** I would like to have trees generated based on both `height` and `circumference`. While professional tools like SpeedTree exist, there are also active open-source projects like [ez-tree](https://github.com/dgreenheck/ez-tree) and [SeedThree](https://github.com/SkyeShark/SeedThree). However, no one yet has a ready-to-use library of low-poly models or billboards covering the top 100 most common species. If any tree enthusiasts are willing to reach out to the authors of these libraries, perhaps they would be interested in collaborating with UrbanEye3D!

* **Better Roads:** Currently, our road rendering is quite primitive—I’ve mostly adapted the Carto style, which works for a first approximation but lacks depth. The main issue is that road width (`width`, `lanes`) is completely ignored. Drawing transitions between, say, a 3-lane and a 2-lane section is a geometric nightmare that results in ugly "steps," and JOSM's MapCSS engine simply isn't built for this. However, the authors of [OSMPIE](https://github.com/kuzinmv/osmpie-doc) seem to have mastered some form of "dark magic" to generate stunning, near-perfect polygonal roads from OSM graphs. The catch? Their code is currently closed-source, and they aren't in a hurry to share their secrets. I am keeping a close eye on their progress, hoping that one day this technology becomes available for integration.

## AI usage disclosure

While it’s common to criticize LLMs and "AI slop," I have to admit that without **Google Gemini**, this project likely wouldn't have been possible. An LLM can't write a non-trivial program from a single prompt; it quickly hits a dead end without constant guidance and correction. However, when a programmer knows exactly what they are doing, the efficiency of this tool is staggering. After all, I’ve spent the last twenty years doing essentially the same thing: writing prompts and technical specifications for "protein-based" developers.

## Why I do this

The goal remains the same: to make the 3D side of OpenStreetMap visible and editable directly where the mapping happens. When you can see the roof shape or the street furniture you just tagged, you catch errors faster and feel more connected to the data.

Huge thanks to the contributors, those who have tested the plugin, reported bugs, or just sent a word of encouragement.
 