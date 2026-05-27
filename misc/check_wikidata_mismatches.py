import os
import csv
import xml.etree.ElementTree as ET
import re

# File paths
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
INPUT_OSM = os.path.join(BASE_DIR, 'data', '05_extracts', 'trees.osm')
CURATED_CSV = os.path.join(BASE_DIR, 'data', '10_trees', 'tree_species_curated.csv')
OUTPUT_DIR = os.path.join(BASE_DIR, 'data', '16_trees_fixes')
OUTPUT_CSV = os.path.join(OUTPUT_DIR, 'wikidata_mismatches.csv')
OUTPUT_MD = os.path.join(OUTPUT_DIR, 'wikidata_mismatches.md')

species_to_analyze = ('Malus sp.',)


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

def capitalize_species(name):
    """Capitalizes the first letter (Genus) while keeping the rest as is."""
    if not name:
        return ""
    return name[0].upper() + name[1:]

def check_wikidata_mismatches():
    print(f"Loading curated species from {CURATED_CSV}...")
    curated_wikidata = {}
    curated_wikidata_r = {}
    
    if not os.path.exists(CURATED_CSV):
        print(f"Error: {CURATED_CSV} not found.")
        return

    with open(CURATED_CSV, mode='r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            species = row['species']
            wikidata = row.get('species:wikidata', '').strip()
            if species and wikidata:
                # Store by normalized name to ensure consistent matching
                curated_wikidata[species] = wikidata
                curated_wikidata_r[wikidata] = species
    
    print(f"Loaded {len(curated_wikidata)} curated species mapping.")

    if not os.path.exists(OUTPUT_DIR):
        os.makedirs(OUTPUT_DIR)

    mismatches = []
    total_elements = 0
    checked_elements = 0

    print(f"Parsing {INPUT_OSM}...")
    if not os.path.exists(INPUT_OSM):
        print(f"Error: {INPUT_OSM} not found.")
        return

    context = ET.iterparse(INPUT_OSM, events=('end',))
    

    for i, (event, elem) in enumerate(context):
        if i % 1000000 == 0 and i > 0:
            print(f"Processed {i} elements...", end='\r')

        if elem.tag in ('node', 'way', 'relation'):
            total_elements += 1
            tags = {tag.get('k'): tag.get('v') for tag in elem.findall('tag')}
            
            osm_species = tags.get('species')
            osm_wikidata = tags.get('species:wikidata')
            
            if (osm_species in species_to_analyze) and osm_wikidata:
                checked_elements += 1
                norm_species = to_binomial(osm_species)
                
                target_wikidata = '' #curated_wikidata[norm_species]
                expected_name = curated_wikidata_r.get(osm_wikidata)

                mismatches.append({
                    'type': elem.tag,
                    'id': elem.get('id'),
                    'osm_species_raw': osm_species,
                    'osm_species_norm': capitalize_species(norm_species),
                    'osm_wikidata': osm_wikidata.strip(),
                    'expected_name': expected_name
                })
            
            # Clear element from memory
            elem.clear()


    print(f"\nDone! Checked {checked_elements} elements with both species and wikidata tags.")
    print(f"Found {len(mismatches)} mismatches.")

    if mismatches:
        # Write CSV
        with open(OUTPUT_CSV, mode='w', encoding='utf-8', newline='') as f:
            fieldnames = ['type', 'id', 'osm_species_raw', 'osm_species_norm', 'osm_wikidata', 'expected_name']
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writeheader()
            for m in mismatches:
                writer.writerow(m)
        print(f"CSV report written to {OUTPUT_CSV}")

        # Write Markdown
        with open(OUTPUT_MD, mode='w', encoding='utf-8') as f:
            f.write("# Wikidata Mismatches Report\n\n")
            f.write(f"Checked elements: {checked_elements}\n")
            f.write(f"Mismatches found: {len(mismatches)}\n\n")
            f.write("| # | Species (Raw) | Species (Norm) | OSM Wikidata | Expected Name |\n")
            f.write("| :--- | :--- | :--- | :--- | :--- |\n")
            
            # Limit MD report to first 1000 to keep it readable, or just write all if it's not too many
            # 6700 might be too many for a single MD page, but let's write all for now
            for m in mismatches:
                osm_link = f"[...](https://www.openstreetmap.org/{m['type']}/{m['id']})"
                osm_wd_link = f"[{m['osm_wikidata']}](https://www.wikidata.org/wiki/{m['osm_wikidata']})"
                #curated_wd_link = f"[{m['curated_wikidata']}](https://www.wikidata.org/wiki/{m['curated_wikidata']})"
                f.write(f"| {osm_link} | {m['osm_species_raw']} | {m['osm_species_norm']} | {osm_wd_link} | {m['expected_name']} |\n")
        
        print(f"Markdown report written to {OUTPUT_MD}")
    else:
        print("No mismatches found.")


if __name__ == '__main__':
    check_wikidata_mismatches()
