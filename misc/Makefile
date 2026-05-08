# GNU Makefile for the tree analysis project

.PHONY: all clean

# Define the different analysis groupings we want to perform
ANALYSIS_TYPES := species genus leaf_type

# Generate the list of target CSV files from the analysis types
TREE_STATS_FILES := $(patsubst %,data/10_trees/tree_stats_%.csv,$(ANALYSIS_TYPES))

# Default goal, executed when 'make' is run without arguments
#all: $(TREE_STATS_FILES) data/building_analysis_full.csv 
all: $(TREE_STATS_FILES) data/15_trees_output/tree_species.csv

# Define the columns to be extracted for the trees CSV
TREE_COLUMNS = id lat lon height circumference age start_date \
               species genus species:wikidata leaf_type leaf_cycle

BUILDING_COLUMNS = id building height building:levels roof:shape roof:height roof:levels 

data/00_planet/planet-latest.osm.pbf: 
	if not exist data\00_planet mkdir data\00_planet
	cd data\00_planet && aria2c https://planet.openstreetmap.org/pbf/planet-latest.osm.pbf.torrent --seed-time=0
	cd data\00_planet && ren planet-*.osm.pbf planet-latest.osm.pbf

data/05_extracts/trees.osm: data/00_planet/planet-latest.osm.pbf ## Rule to generate the source OSM file from the planet data
	osmosis --rbf data/00_planet/planet-latest.osm.pbf --tf accept-nodes natural=tree --tf reject-ways --tf reject-relations --wx data/05_extracts/trees.osm

# Generic pattern rule to generate any analysis file.
# The stem (%) will be 'species', 'genus', or 'leaf_type'.
data/10_trees/tree_stats_%.csv: data/10_trees/trees.csv 
	python analyze_trees.py --group-by $*

data/10_trees/trees.csv: data/05_extracts/trees.osm | data/10_trees ## Generate trees CSV from the source OSM file
	python osm_to_csv.py --input $< --output $@ --columns $(TREE_COLUMNS)

data/10_trees/tree_species_curated.csv: ##fetches 'curated' list of tree species from osm-wiki
	python fetch_tree_species.py
	
data/15_trees_output/tree_synonyms.csv : data/10_trees/tree_species_curated.csv  data/10_trees/tree_stats_species.csv | data/15_trees_output	 ## Creates additions to curated list based on actual osm data
	python compare_tree_stats.py
	
data/15_trees_output/tree_species.csv: data/10_trees/tree_species_curated.csv data/15_trees_output/tree_synonyms.csv
	python merge_tree_data.py
	copy /b/v/y	 data\15_trees_output\tree_species.csv ..\src\main\resources\data\
	
data/buildings.csv: data/buildings.osm
	python osm_to_csv.py --input $< --output $@ --columns $(BUILDING_COLUMNS)

data/building_analysis_full.csv: data/buildings.csv
	python analyze_buildings.py

	
data/buildings.osm: | data ## Rule to generate the source OSM file from the planet data
	buildings_extract.bat	
	
data:
	@mkdir data
	
data/10_trees :	
	@mkdir data\10_trees
	
data/15_trees_output: | data	
	@mkdir data\15_trees_output

# Rule to clean up all generated files.
# The '-' prefix ignores errors if files do not exist.
clean:
	rm -f data/*.csv data/*.json data/*.cfg data/*.osm
