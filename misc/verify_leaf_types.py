"""
Verification of leaf_type based on botanical family.
"""
import csv
import os
from powoapi import get_powo_family

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
INPUT_FILE = os.path.join(BASE_DIR, 'data', '15_trees_output', 'tree_species.csv')
OUTPUT_FILE = os.path.join(BASE_DIR, 'data', '15_trees_output', 'leaf_type_family_violations.csv')

CONIFER_FAMILIES = {
    'Araucariaceae', 'Cephalotaxaceae', 'Cupressaceae',
    'Pinaceae', 'Podocarpaceae', 'Sciadopityaceae', 'Taxaceae'
}

def verify():
    if not os.path.exists(INPUT_FILE):
        print(f"Input file {INPUT_FILE} not found.")
        return

    violations = []
    
    with open(INPUT_FILE, mode='r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        rows = list(reader)
        total = len(rows)
        
        for i, row in enumerate(rows):
            species = row['species']
            leaf_type = row.get('leaf_type')
            
            print(f"[{i+1}/{total}] Checking {species}...", end='\r')
            family = get_powo_family(species)
            
            if not family:
                continue
                
            is_conifer = family in CONIFER_FAMILIES
            
            violation_reason = None
            if is_conifer and leaf_type != 'needleleaved':
                # Some conifers might be 'mixed', but usually they should be 'needleleaved'
                # If it's 'broadleaved', it's definitely a violation
                if leaf_type == 'broadleaved':
                     violation_reason = f"Conifer family ({family}) but leaf_type is {leaf_type}"
            elif not is_conifer and leaf_type == 'needleleaved':
                violation_reason = f"Non-conifer family ({family}) but leaf_type is {leaf_type}"
                
            if violation_reason:
                violations.append({
                    'species': species,
                    'genus': row.get('genus', ''),
                    'family': family,
                    'leaf_type': leaf_type,
                    'species:wikidata': row.get('species:wikidata', '')
                })

    print(f"\nFound {len(violations)} violations.")
    
    with open(OUTPUT_FILE, mode='w', newline='', encoding='utf-8') as f:
        fieldnames = ['species', 'genus', 'family', 'leaf_type', 'species:wikidata']
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for v in violations:
            writer.writerow(v)
            
    print(f"Violations saved to {OUTPUT_FILE}")

if __name__ == "__main__":
    verify()
