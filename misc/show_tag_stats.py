import csv
import os
import sys
from collections import Counter

# Import normalization logic from the existing pipeline
from datawash import to_binomial1 as to_binomial, is_cultivar

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
INPUT_FILE = os.path.join(BASE_DIR, 'data', '10_trees', 'trees.csv')
VALID_SPECIES_FILE = os.path.join(BASE_DIR, 'data', '15_trees_output', 'tree_species.csv')

def draw_bar(percentage, length=40):
    """Generates an ASCII bar chart string."""
    filled = int((percentage / 100) * length)
    empty = length - filled
    return "█" * filled + "░" * empty

def main():
    if not os.path.exists(INPUT_FILE):
        print(f"Error: Raw data file not found at {INPUT_FILE}")
        return
    if not os.path.exists(VALID_SPECIES_FILE):
        print(f"Error: Valid species list not found at {VALID_SPECIES_FILE}")
        return

    # 1. Load valid species (normalized) into a set for fast lookup
    print("Loading valid species list...")
    valid_species_set = set()
    with open(VALID_SPECIES_FILE, mode='r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            species = row.get('species')
            if species:
                # We normalize here to be extra safe, though tree_species.csv 
                # should already contain normalized/canonical names.
                valid_species_set.add(to_binomial(species))

    # 2. Process all trees from raw CSV
    print("Processing trees.csv (this may take a minute)...")
    valid_trees_count = 0
    invalid_trees_count = 0
    
    # Using sets for unique tags to handle absolute counts of tags
    valid_tags = set()
    invalid_tags = set()
    
    # Frequency counter for invalid tags to find Top 5
    invalid_tag_freq = Counter()

    try:
        with open(INPUT_FILE, 'r', newline='', encoding='utf-8') as infile:
            reader = csv.DictReader(infile)
            k = 0
            for row in reader:
                k += 1
                if k % 100000 == 0:
                    print(f"  Processed {k:,} trees...", end='\r')
                
                species_raw = row.get("species")
                if not species_raw:
                    continue
                
                # Normalize using the same logic as find_invalid_species.py
                species_norm = to_binomial(species_raw)
                
                is_valid = (species_norm in valid_species_set) or is_cultivar(species_raw)
                
                if is_valid:
                    valid_trees_count += 1
                    valid_tags.add(species_raw)
                else:
                    invalid_trees_count += 1
                    invalid_tags.add(species_raw)
                    invalid_tag_freq[species_raw] += 1
            
            print(f"  Processed {k:,} trees total.      ")
    except Exception as e:
        print(f"\nError reading {INPUT_FILE}: {e}")
        return

    # 3. Calculate metrics
    total_trees = valid_trees_count + invalid_trees_count
    total_unique = len(valid_tags) + len(invalid_tags)
    
    perc_valid_trees = (valid_trees_count / total_trees * 100) if total_trees > 0 else 0
    perc_invalid_trees = (invalid_trees_count / total_trees * 100) if total_trees > 0 else 0
    
    perc_valid_unique = (len(valid_tags) / total_unique * 100) if total_unique > 0 else 0
    perc_invalid_unique = (len(invalid_tags) / total_unique * 100) if total_unique > 0 else 0

    top_5_invalid = invalid_tag_freq.most_common(5)

    # 4. Final Output
    print("\n" + "="*60)
    print("--- Species Tag Statistics ---".center(60))
    print("="*60 + "\n")
    
    print(f"Total Trees with 'species' tag: {total_trees:,}")
    print(f"Valid:   {valid_trees_count:>12,} ({perc_valid_trees:5.1f}%)")
    print(f"Invalid: {invalid_trees_count:>12,} ({perc_invalid_trees:5.1f}%)")
    print(draw_bar(perc_valid_trees))
    
    print(f"\nTotal Unique Species Tags: {total_unique:,}")
    print(f"Valid:   {len(valid_tags):>12,} ({perc_valid_unique:5.1f}%) ")
    print(f"Invalid: {len(invalid_tags):>12,} ({perc_invalid_unique:5.1f}%)")
    print(draw_bar(perc_valid_unique))
    
    print("\nTop 5 Invalid Species (by tree count):")
    for i, (tag, count) in enumerate(top_5_invalid, 1):
        print(f"  {i}. \"{tag}\" ({count:,} trees)")
    print("\n" + "="*60)

if __name__ == "__main__":
    main()
