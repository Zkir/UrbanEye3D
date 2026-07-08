"""
Generates spatial statistics for trees (leaf_type probabilities and top species)
based on a geographic grid.
"""

import csv
import json
import math
import os
import argparse
from collections import defaultdict, Counter

from datawash import to_binomial
from analyze_trees import clean_leaf_type

# --- Constants & Paths ---
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
INPUT_FILE = os.path.join(BASE_DIR, 'data', '10_trees', 'trees.csv')
SPECIES_DB_FILE = os.path.join(BASE_DIR, 'data', '15_trees_output', 'tree_species.csv')
OUTPUT_DIR = os.path.join(BASE_DIR, 'data', '15_trees_output')

def get_grid_index(lat: float, lon: float, grid_size: float) -> str:
    """
    Formats the grid index as +lat+lon or -lat-lon.
    Latitude is formatted to 2 digits, Longitude to 3 digits.
    Example: 55 lat, 35 lon -> +55+035
    """
    lat_bin = math.floor(lat / grid_size) * grid_size
    lon_bin = math.floor(lon / grid_size) * grid_size
    
    # Format signs and padding
    lat_str = f"{int(lat_bin):+03d}"
    lon_str = f"{int(lon_bin):+04d}"
    
    return f"{lat_str}{lon_str}"

def load_species_db(file_path):
    """Loads species to leaf_type mapping from tree_species.csv."""
    db = {}
    if not os.path.exists(file_path):
        print(f"Warning: Species database file {file_path} not found. Inference will be disabled.")
        return db
    
    print(f"Loading species database from {file_path}...")
    with open(file_path, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            db[row['species']] = row['leaf_type']
    print(f"Loaded {len(db)} species definitions.")
    return db

def process_spatial_stats(input_file: str, output_file: str, grid_size: float, top_n_species: int = 10):
    if not os.path.exists(input_file):
        print(f"Error: Input file {input_file} not found.")
        return

    species_db = load_species_db(SPECIES_DB_FILE)

    # Structure: dict[grid_index] -> {'leaf_types': Counter, 'species': Counter, 'total_trees': int}
    grid_data = defaultdict(lambda: {
        'leaf_types': Counter(),
        'species': Counter(),
        'total_trees': 0
    })

    print(f"Reading data from {input_file} (Grid size: {grid_size}x{grid_size} degrees)...")
    
    processed_count = 0
    with open(input_file, 'r', newline='', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            processed_count += 1
            if processed_count % 1000000 == 0:
                print(f"Processed {processed_count} trees...", end='\r')

            try:
                lat_str = row.get('lat')
                lon_str = row.get('lon')
                if not lat_str or not lon_str:
                    continue
                lat = float(lat_str)
                lon = float(lon_str)
            except (ValueError, TypeError):
                continue

            grid_idx = get_grid_index(lat, lon, grid_size)
            cell_data = grid_data[grid_idx]
            cell_data['total_trees'] += 1

            # Process species first to use it for inference
            raw_species = row.get('species')
            binomial = to_binomial(raw_species) if raw_species else None
            
            # Process leaf_type
            cleaned_leaf_type = None
            # first of all we use leaf_type from species_db, because we trust it even more than raw osm data.
            if binomial:
                cleaned_leaf_type = species_db.get(binomial)
                
            # if leaf_type cannot be determined from species, use raw osm data     
            if not cleaned_leaf_type:
                raw_leaf_type = row.get('leaf_type')
                cleaned_leaf_type = clean_leaf_type(raw_leaf_type)

            if cleaned_leaf_type and cleaned_leaf_type != "mixed":
                cell_data['leaf_types'][cleaned_leaf_type] += 1

            # Process species (only if it's in our DB)
            if binomial and binomial in species_db:
                cell_data['species'][binomial] += 1

    print(f"\nFinished reading. Calculating probabilities for {len(grid_data)} grid cells...")

    # Final output structure
    final_output = {}

    for grid_idx, data in grid_data.items():
        total_leaf_type_tags = sum(data['leaf_types'].values())
        
        # Calculate probabilities
        leaf_type_prob = {}
        if total_leaf_type_tags > 0:
            for lt, count in data['leaf_types'].items():
                leaf_type_prob[lt] = round(count / total_leaf_type_tags, 4)

        # Get top species
        top_species = [species for species, count in data['species'].most_common(top_n_species)]

        # Only add to output if there is meaningful data
        if leaf_type_prob or top_species:
             final_output[grid_idx] = {
                 "total_trees": data['total_trees'],
                 "leaf_type_prob": leaf_type_prob,
                 "top_species": top_species
             }

    os.makedirs(os.path.dirname(output_file), exist_ok=True)
    print(f"Saving results to {output_file}...")
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(final_output, f, indent=2, sort_keys=True)

    print("Done.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate spatial statistics for trees.")
    parser.add_argument('--grid-size', type=float, default=5.0, help='Size of the grid in degrees (default: 5.0)')
    parser.add_argument('--top-species', type=int, default=10, help='Number of top species to retain per grid (default: 10)')
    args = parser.parse_args()

    # Create dynamic filename based on grid size
    grid_size_str = f"{args.grid_size:g}".replace('.', '_')
    output_filename = os.path.join(OUTPUT_DIR, f'spatial_stats_{grid_size_str}x{grid_size_str}.json')

    process_spatial_stats(INPUT_FILE, output_filename, args.grid_size, args.top_species)