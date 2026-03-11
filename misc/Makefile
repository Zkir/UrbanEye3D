# GNU Makefile for the tree analysis project

.PHONY: all clean

# Define the different analysis groupings we want to perform
ANALYSIS_TYPES := species genus leaf_type

# Generate the list of target CSV files from the analysis types
ANALYSIS_FILES := $(patsubst %,data/tree_stats_%.csv,$(ANALYSIS_TYPES))

# Default goal, executed when 'make' is run without arguments
all: $(ANALYSIS_FILES) data/building_analysis_full.csv | data

# Define the columns to be extracted for the trees CSV
TREE_COLUMNS = id lat lon height circumference age start_date \
               species genus species:wikidata leaf_type leaf_cycle

BUILDING_COLUMNS = id building height building:levels roof:shape roof:height roof:levels 

# Generic pattern rule to generate any analysis file.
# The stem (%) will be 'species', 'genus', or 'leaf_type'.
data/tree_stats_%.csv: data/trees.csv 
	python analyze_trees.py --group-by $*
	
data/building_analysis_full.csv: data/buildings.csv
	python analyze_buildings.py

data/trees.csv: data/trees.osm ## Rule to generate the main CSV from the source OSM file
	python osm_to_csv.py --input $< --output $@ --columns $(TREE_COLUMNS)
	
data/buildings.csv: data/buildings.osm
	python osm_to_csv.py --input $< --output $@ --columns $(BUILDING_COLUMNS)


data/trees.osm: | data ## Rule to generate the source OSM file from the planet data
	trees_extract.bat
	
data/buildings.osm: | data ## Rule to generate the source OSM file from the planet data
	buildings_extract.bat	
	
data:
	@mkdir -p data

# Rule to clean up all generated files.
# The '-' prefix ignores errors if files do not exist.
clean:
	rm -f data/*.csv data/*.json data/*.cfg data/*.osm
