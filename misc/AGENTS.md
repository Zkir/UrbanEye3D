# Subproject: OSM dataprocessing pipeline

## Operation instructions

- **CRITICAL:** Never run `make clean` or delete any files from  `data`. folder. The most files are very expensive to generate.
- If need to test something, run python scripts directly. 
- Always run `pylint -E ` before reporting readiness of python scripts creation/adjustment

## Notes

*    This pipeline starts with downloading and updating of the global `planet-latest.osm.pbf` file.

*   **Tree Species Data Sourcing:**
    *    The master botanical list is primarily formed by scraping the [OSM Wiki List of Species](https://wiki.openstreetmap.org/wiki/Tag:natural%3Dtree/List_of_Species) using the `misc/fetch_tree_species.py` script. 
	* After scraping, it is enriched from the species values occurring in the actual OpenStreetMap data. Species names fetched from OSM data are validated via 
	Plants of the World Online (POWO) API to detect proper taxon names, synonyms, typos and just bullshit (which also can be found in OSM tags).
	* Suggestions for corrections in OSM are made, and osm-xml files for corrections are created (split into 5000-element chunks). However, those corrections are not uploaded to OSM automatically, but should be uploaded manually via JOSM (we do not want to create an automated OSM corrector, just fix problems which bother us).
	* The final species list is placed in the plugin's resources ('src/main/resources/data/tree_species.csv') to drive the 3D rendering engine.
	* The geographic vegetation statistics ('spatial_stats_5x5.json') are visualized as a world map by `make_tree_map.py` and published to `docs/map_leaf_types.png`. The map is a quick sanity check of the statistics: a wrong dominant leaf type in a cell means either bad OSM data or a bug in the analysis.
	
*   **Tree Billboard Texture Generation (`misc/tree_billboards/`, pipeline stage 17):**
    *   Bakes transparent 512 px tree textures from the external [SeedThree](https://github.com/SkyeShark/SeedThree)
        generator; entry point `make_tree_billboards.py` (`--only`, `--seeds`, `--ban key:seed`, `--size`),
        which calls `bake.mjs`/`bake-page.html` (SeedThree `createTree` + `bakeGroupToTextures` in one
        headless Chrome/WebGPU session over CDP). 4 seed variants per species; `--ban` drops bad ones.
        Plan: `docs/dev/SEEDTHREE_TREE_TEXTURES_PLAN.md`.
    *   SeedThree is not vendored. If missing, initialize:
        `git clone https://github.com/SkyeShark/SeedThree D:\src\SeedThree` then `npm install` inside it
        (or point `SEEDTHREE_DIR` at an existing checkout; `CHROME_PATH` overrides the browser).
        Without them the bake target fails.
    *   Species come from SeedThree itself (`bake.mjs --list-species`), Latin included; no species list in repo.
    *   Promotion = Makefile target `data/17_seedthree_textures/assets_trees.mapcss`: `make_tree_assets.py`
        generates rules (exact Latin + synonyms from `tree_synonyms.csv`; no stand-ins) between
        BEGIN/END markers; recipe copies the mapcss to `src/main/resources/` and `*_01.png` to
        `src/main/resources/textures/trees/`. `AssetConfigLoader` appends it after base rules on purpose
        (species rules win over leaf-type fallbacks). It prints `AssetListTest` provenance lines to add.
    *   Outputs in `misc/data/17_seedthree_textures/` (like every stage): textures, `manifest.json`
        (rev/seed/metres/aspect/alpha), `tree_billboards.md`, `coverage.md` (top-150 OSM species vs
        presets, what exists and what is missing), `intermediates/`. `coverage.md` has image links
        relative to its Makefile-copied twin `docs/seedthree_coverage.md` (dead links in the misc
        copy are fine; one report, copied by the recipe, never generated twice).

*   **Buildings** are processed in order to create a file with "smart defaults" values. This part is highly experimental and not yet used by the plugin itself.

*   **Tag Popularity Analysis:**
    *   `misc/popular_tags.py`: Fetches the most popular tags for nodes from Taginfo API, filters out metadata, and saves them to `misc/data/popular_tags.json`.
    *   `misc/find_missing_tags.py`: Compares popular tags with `docs/taginfo.json` and generates a report (`docs/dev/popular_missing_tags.md`) of popular tags not yet supported by the plugin.
