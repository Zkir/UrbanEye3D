import csv
import os
import re

# File paths
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CURATED_FILE = os.path.join(BASE_DIR, 'data', '10_trees', 'tree_species_curated.csv')
SYNONYMS_FILE = os.path.join(BASE_DIR, 'data', '15_trees_output', 'tree_synonyms.csv')
FINAL_OUTPUT =  os.path.join(BASE_DIR, 'data', '15_trees_output', 'tree_species.csv')
#FINAL_OUTPUT = os.path.join(os.path.dirname(BASE_DIR), 'src', 'main', 'resources', 'data', 'tree_species.csv')


def to_binomial(name):
    """
    Extracts the binomial core (Genus species) or trinomial for hybrids (Genus × species).
    Used for fuzzy matching against the curated list.
    """
    if not name:
        return ""
    # Standardize hybrid sign for parsing
    n = name.replace('×', ' × ').replace(' x ', ' × ')
    n = re.sub(r'\s+', ' ', n).strip().lower()
    parts = n.split(' ')
    
    if not parts:
        return ""
    
    # 1. Hybrid starts with ×: × Genus species
    if parts[0] == '×' and len(parts) >= 3:
        return " ".join(parts[:3])
    # 2. Hybrid in middle: Genus × species
    if len(parts) >= 3 and parts[1] == '×':
        return " ".join(parts[:3])
    # 3. Standard binomial: Genus species
    if len(parts) >= 2:
        return " ".join(parts[:2])
    
    return n

def main():
    if not os.path.exists(CURATED_FILE) or not os.path.exists(SYNONYMS_FILE):
        print(f"Error: Required files not found:\n{CURATED_FILE}\n{SYNONYMS_FILE}")
        return

    # 1. Load curated species
    curated_data = {}
    binomial_map = {} # Map binomial core -> full curated row
    
    with open(CURATED_FILE, mode='r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            species = row['species']
            curated_data[species] = row
            
            # Index by binomial core to match against subspecies/varieties
            core = to_binomial(species)
            if core not in binomial_map:
                binomial_map[core] = row

    print(f"Loaded {len(curated_data)} species from curated list.")

    # 2. Process synonyms and inherit properties
    merged_list = list(curated_data.values())
    added_count = 0
    
    with open(SYNONYMS_FILE, mode='r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            synonym_name = row['species']
            accepted_name = row['accepted_name']
            
            # Try to find a match for the accepted name
            match = None
            if accepted_name in curated_data:
                match = curated_data[accepted_name]
            else:
                # Try matching by binomial core (to catch subspecies/varieties)
                core = to_binomial(accepted_name)
                if core in binomial_map:
                    match = binomial_map[core]
            
            if match:
                # Genus from the synonym itself
                genus = synonym_name.split(' ')[0].replace('×', '').strip().capitalize()
                if not genus and len(synonym_name.split(' ')) > 1:
                     genus = synonym_name.split(' ')[1].capitalize()

                merged_list.append({
                    'species': synonym_name,
                    'genus': genus,
                    'species:wikidata': match.get('species:wikidata', ''),
                    'leaf_cycle': match.get('leaf_cycle', 'deciduous'),
                    'leaf_type': match.get('leaf_type', 'broadleaved')
                })
                added_count += 1
            else:
                pass
                # Nobody can read this warning here
                #print(f"Warning: Accepted name '{accepted_name}' (core: '{to_binomial(accepted_name)}') for synonym '{synonym_name}' not found in curated list. Skipping.")

    # 3. Sort
    merged_list.sort(key=lambda x: x['species'].lower())

    # 4. Write final
    target_columns = ["species", "genus", "species:wikidata", "leaf_cycle", "leaf_type"]
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
