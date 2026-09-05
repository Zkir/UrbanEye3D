import csv
import os
import re
from datawash import to_binomial
from powoapi import get_powo_family

# File paths
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CURATED_FILE = os.path.join(BASE_DIR, 'data', '10_trees', 'tree_species_curated.csv')
ACCEPTED_FILE = os.path.join(BASE_DIR, 'data', '15_trees_output', 'tree_species_accepted.csv')
SYNONYMS_FILE = os.path.join(BASE_DIR, 'data', '15_trees_output', 'tree_synonyms.csv')
FINAL_OUTPUT =  os.path.join(BASE_DIR, 'data', '15_trees_output', 'tree_species.csv')
#FINAL_OUTPUT = os.path.join(os.path.dirname(BASE_DIR), 'src', 'main', 'resources', 'data', 'tree_species.csv')

CONIFER_FAMILIES = {
    'Araucariaceae', 'Cephalotaxaceae', 'Cupressaceae',
    'Pinaceae', 'Podocarpaceae', 'Sciadopityaceae', 'Taxaceae'
}

def get_genus(synonym_name):
    genus = synonym_name.split(' ')[0].replace('×', '').strip().capitalize()
    if not genus and len(synonym_name.split(' ')) > 1:
         genus = synonym_name.split(' ')[1].capitalize()
    return genus

def fix_leaf_type(species_name, current_type, family):
    if not family:
        return current_type
    
    if family == 'Arecaceae':
        return 'palm'
    if family in CONIFER_FAMILIES:
        return 'needleleaved'
    
    # If family is found and it's not a palm or conifer, it's broadleaved
    return 'broadleaved'

def main():
    if not os.path.exists(CURATED_FILE) or not os.path.exists(SYNONYMS_FILE) or not os.path.exists(ACCEPTED_FILE):
        print(f"Error: Required files not found:\n{CURATED_FILE}\n{SYNONYMS_FILE}")
        return

    # 1. Load curated species
    species_data = {}
    
    print("Loading and verifying curated list...")
    with open(CURATED_FILE, mode='r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            species = to_binomial(row['species'])
            if species !=row['species']:
                print(f"Strange occurence in the curated file: {row['species']}, skipping")
                continue
            
            # Enforce biological truth even for curated list
            family = get_powo_family(species)
            if family:
                row['leaf_type'] = fix_leaf_type(species, row['leaf_type'], family)
                row['family'] = family
            else:
                print(f"Strange occurence in the curated file: unable to determine family for {row['species']}, skipping")
                continue
                
            species_data[species] = row

    print(f"Loaded {len(species_data)} species from curated list.")
    
    j=0
    print("Loading and verifying powo-confirmed list...")
    with open(ACCEPTED_FILE, mode='r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            species = to_binomial(row['species'])
            
            # Re-verify/enforce leaf_type
            family = get_powo_family(species)
            row['leaf_type'] = fix_leaf_type(species, row['leaf_type'], family)
            row['family'] = family or ''

            if species not in species_data:
                species_data[species] = row
                j+=1
    
    print(f"Loaded {j} species from powo-confirmed list.")

    # 2. Process synonyms and inherit properties
    added_count = 0
    print("Processing synonyms...")
    with open(SYNONYMS_FILE, mode='r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            synonym_name =  to_binomial(row['species'])
            accepted_name = to_binomial(row['accepted_name'])
            
            # Try to find a match for the accepted name
            match = None
            if accepted_name in species_data:
                match = species_data[accepted_name]
                
            if match:
                # if we have found "Proper" species name, we use data from it, otherwise from synonym itself
                #   in future statistics should be combined.
                leaf_type  =  match.get('leaf_type', 'broadleaved')
                leaf_cycle =  match.get('leaf_cycle', 'deciduous')
                wikidata   =  match.get('species:wikidata', '')
                family     =  match.get('family', '')
            else:
                family     =  get_powo_family(synonym_name) or ''
                leaf_type  =  fix_leaf_type(synonym_name, row['leaf_type'], family)
                leaf_cycle =  row['leaf_cycle']
                wikidata   =  ''            
            
            if synonym_name not in species_data:
                species_data[synonym_name] = {
                            'species': synonym_name,
                            'genus': get_genus(synonym_name),
                            'family': family,
                            'species:wikidata': wikidata,
                            'leaf_cycle': leaf_cycle,
                            'leaf_type': leaf_type}
                added_count += 1

            if not match:
                accepted_family = get_powo_family(accepted_name) or family
                species_data[accepted_name] =  {
                            'species': accepted_name,
                            'genus': get_genus(accepted_name),
                            'family': accepted_family,
                            'species:wikidata': '',
                            'leaf_cycle': leaf_cycle,
                            'leaf_type':  leaf_type,}
                added_count += 1
                
                 

    # 3. Sort
    merged_list = list(species_data.values())
    merged_list.sort(key=lambda x: x['species'].lower())

    # 4. Write final
    target_columns = ["species", "genus", "family", "species:wikidata", "leaf_cycle", "leaf_type"]
    os.makedirs(os.path.dirname(FINAL_OUTPUT), exist_ok=True)
    
    with open(FINAL_OUTPUT, mode='w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=target_columns)
        writer.writeheader()
        writer.writerows(merged_list)

    print(f"Added {added_count} synonyms/typos.")
    print(f"Final merged list contains {len(merged_list)} entries.")
    print(f"Saved to: {FINAL_OUTPUT}")

if __name__ == "__main__":
    main()
