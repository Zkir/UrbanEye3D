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
	
*   **Buildings** are processed in order to create a file with "smart defaults" values. This part is highly experimental and not yet used by the plugin itself.

*   **Tag Popularity Analysis:**
    *   `misc/popular_tags.py`: Fetches the most popular tags for nodes from Taginfo API, filters out metadata, and saves them to `misc/data/popular_tags.json`.
    *   `misc/find_missing_tags.py`: Compares popular tags with `docs/taginfo.json` and generates a report (`docs/dev/popular_missing_tags.md`) of popular tags not yet supported by the plugin.
