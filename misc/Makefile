# GNU Makefile for the tree analysis project

.PHONY: all clean

# Define the different analysis groupings we want to perform
ANALYSIS_TYPES := species genus leaf_type

# Generate the list of target CSV files from the analysis types
TREE_STATS_FILES := $(patsubst %,data/10_trees/tree_stats_%.csv,$(ANALYSIS_TYPES))

# Default goal, executed when 'make' is run without arguments
#all: $(TREE_STATS_FILES) data/building_analysis_full.csv 
all: data/15_trees_output/tree_species.csv data/15_trees_output/tree_species_invalid.md data/15_trees_output/leaf_type_family_violations.csv data/15_trees_output/spatial_stats_5x5.json data/25_flags_output/flag_rules_colour.json data/26_tags/popular_missing_tags.md data/10_trees/correlations.txt

# Define the columns to be extracted for the trees CSV
TREE_COLUMNS = id lat lon height circumference age start_date \
               species genus species:wikidata leaf_type leaf_cycle diameter_crown

BUILDING_COLUMNS = id building height building:levels roof:shape roof:height roof:levels 

data/00_planet/planet-latest.osm.pbf: 
	if not exist data\00_planet mkdir data\00_planet
	cd data\00_planet && aria2c https://planet.openstreetmap.org/pbf/planet-latest.osm.pbf.torrent --seed-time=0
	cd data\00_planet && ren planet-*.osm.pbf planet-latest.osm.pbf
	
data/00_planet/planet-latest-updated.osm.pbf:  data/00_planet/planet-latest.osm.pbf	
	osmupdate data\00_planet\planet-latest.osm.pbf data\00_planet\planet-latest-updated.osm.pbf -v --base-url=https://planet.maps.mail.ru/replication/

data/05_extracts/trees.osm: data/00_planet/planet-latest-updated.osm.pbf |  data/05_extracts ## Rule extract tree nodes
	osmosis --rbf data/00_planet/planet-latest-updated.osm.pbf --tf accept-nodes natural=tree --tf reject-ways --tf reject-relations --wx data/05_extracts/trees.osm
	
data/05_extracts/buildings.osm: data/00_planet/planet-latest-updated.osm.pbf | data/05_extracts ## Rule extract buildings
	osmosis --rbf data/00_planet/planet-latest-updated.osm.pbf --tf reject-nodes --tf accept-ways building=* --tf accept-ways  building:levels=*  height=* --tf reject-ways building=yes building=no --tf reject-relations --wx data/05_extracts/buildings.osm	
	
data/05_extracts/flags.osm: data/00_planet/planet-latest-updated.osm.pbf |  data/05_extracts ## Rule extract flags
	osmosis --rbf data/00_planet/planet-latest-updated.osm.pbf --tf accept-nodes man_made=flagpole --tf reject-ways --tf reject-relations --wx data/05_extracts/flags.osm	

# Generic pattern rule to generate any analysis file.
# The stem (%) will be 'species', 'genus', or 'leaf_type'.
data/10_trees/tree_stats_%.csv: data/10_trees/trees.csv 
	python analyze_trees.py --group-by $*

data/10_trees/trees.csv: data/05_extracts/trees.osm | data/10_trees ## Generate trees CSV from the source OSM file
	python osm_to_csv.py --input $< --output $@ --columns $(TREE_COLUMNS)

data/10_trees/tree_species_curated.csv: data/00_planet/planet-latest-updated.osm.pbf ##fetches 'curated' list of tree species from osm-wiki
	python fetch_tree_species.py
	
data/10_trees/tree_species_all.csv: data/10_trees/trees.csv
	python aggregate_species.py	
	
data/10_trees/correlations.txt: data/10_trees/trees_cleaned.csv
	python cor1.py > $@		
	
data/15_trees_output/tree_synonyms.csv : data/10_trees/tree_species_curated.csv  data/10_trees/tree_stats_species.csv | data/15_trees_output	 ## Creates additions to curated list based on actual osm data
	python compare_tree_stats.py
	
data/15_trees_output/tree_species.csv: data/10_trees/tree_species_curated.csv data/15_trees_output/tree_synonyms.csv
	python merge_tree_data.py
	copy /b/v/y data\15_trees_output\tree_species.csv ..\src\main\resources\data\

data/15_trees_output/spatial_stats_5x5.json: data/10_trees/trees.csv data/15_trees_output/tree_species.csv
	python spatial_tree_stats.py --grid-size 5.0
	copy /b/v/y data\15_trees_output\spatial_stats_5x5.json ..\src\main\resources\data\

data/15_trees_output/tree_species_invalid.md: data/10_trees/tree_species_all.csv  data/15_trees_output/tree_species.csv
	python report_invalid_species.py	

data/15_trees_output/leaf_type_family_violations.csv: data/15_trees_output/tree_species.csv
	python verify_leaf_types.py

data/20_buildings/buildings.csv: data/05_extracts/buildings.osm
	python osm_to_csv.py --input $< --output $@ --columns $(BUILDING_COLUMNS)

data/20_buildings/building_analysis_full.csv: data/20_buildings/buildings.csv
	python analyze_buildings.py

data/25_flags_output/flag_rules_colour.json: data/05_extracts/flags.osm
	python analyze_flags.py
	cmd /c "copy /b /v /y data\25_flags_output\flag_rules_colour.json ..\src\main\resources\data\"
	cmd /c "copy /b /v /y data\25_flags_output\flag_rules_country.json ..\src\main\resources\data\"

data/26_tags/popular_tags.json: data/00_planet/planet-latest.osm.pbf ##it does not really depends on planet.osm, but we want to update it, when we get new planet.
	python get_popular_tags.py	

data/26_tags/popular_missing_tags.md: data/26_tags/popular_tags.json
	python find_missing_tags.py	
	cmd /c "copy /b /v /y data\26_tags\popular_missing_tags.md ..\docs\dev\"
	cmd /c "copy /b /v /y data\26_tags\popular_supported_tags.md ..\docs\dev\"	
	
data:
	@mkdir data
	
data/05_extracts: | data
	@mkdir data\05_extracts
	
data/10_trees :	| data
	@mkdir data\10_trees
	
data/15_trees_output: | data	
	@mkdir data\15_trees_output



# Rule to clean up all generated files.
# The '-' prefix ignores errors if files do not exist.
clean:
	rm -f data/*.csv data/*.json data/*.cfg data/*.osm
