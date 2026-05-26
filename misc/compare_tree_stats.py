"""
The aim of this script is to find VALID species names that: 
   1. are missing in the curated list 
   2. AND have proper statatistical data to determie leaf_type and leaf_cycle
   those species includes synonims (synonims can be used in OSM, but are not present in the curated list)
"""
import csv
import os
import re
import urllib.parse
import json

from powoapi import check_powo_status
from datawash import to_binomial as normalize_and_binomial

# File paths relative to the project root
BASE_DIR =             os.path.dirname(os.path.abspath(__file__))
SPECIES_FILE =         os.path.join(BASE_DIR, "data", "10_trees", "tree_species_curated.csv")
STATS_FILE =           os.path.join(BASE_DIR, 'data', '10_trees', 'tree_stats_species.csv')
OUTPUT_MD_FILE =       os.path.join(BASE_DIR, 'data', '15_trees_output', 'found_species.md')
OUTPUT_CSV_SYNONYMS =  os.path.join(BASE_DIR, 'data', '15_trees_output', 'tree_synonyms.csv')
#OUTPUT_CSV_NOT_FOUND = os.path.join(BASE_DIR, 'data', '15_trees_output', 'tree_species_not_found.csv')
OUTPUT_CSV_FOUND =     os.path.join(BASE_DIR, 'data', '15_trees_output', 'tree_species_accepted.csv')
OUTPUT_WIKI_FILE =     os.path.join(BASE_DIR, 'data', '15_trees_output', 'tree_suggestions_wiki.txt')


# Threshold for "significant" number of trees
# we make conclusion about leaf_type and leaf_cycle, so there should be enough trees of that species -- not exactly, because even single value is enough for the stat script
THRESHOLD = 4 

def is_genus_sp(name):
    """Checks if the species name is a generic 'Genus sp.'."""
    name = name.lower()
    return name.endswith(" sp.") or name.endswith(" sp") or name.endswith(" spp.") or name.endswith(" n. sp.")

def is_cultivar(name):
    """Checks if the species name is in 'Genus 'Cultivar'' format."""
    return bool(re.match(r"^[A-Z][a-z]+\s+'[A-Z].*'$", name.strip()))

def get_display_name(name):
    """Returns a nicely formatted binomial name."""
    norm = normalize_and_binomial(name)
    if not norm:
        return ""
    # Capitalize the first letter
    return norm[0].upper() + norm[1:]

def parse_float(val):
    try:
        return float(val) if val else 0.0
    except ValueError:
        return 0.0

def get_taginfo_url(species_name):
    """Generates a TagInfo URL for the species."""
    encoded_query = urllib.parse.quote(f"species={species_name}", safe='=')
    return f"https://taginfo.openstreetmap.org/tags/{encoded_query}"

def main():
    if not os.path.exists(SPECIES_FILE) or not os.path.exists(STATS_FILE):
        print(f"Error: One of the files not found:\n{SPECIES_FILE}\n{STATS_FILE}")
        return

    # 1. Read existing species
    existing_rows = []
    existing_species_norm = set()
    existing_genera = set()
    with open(SPECIES_FILE, mode='r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            existing_rows.append(row)
            existing_species_norm.add(normalize_and_binomial(row['species']))
            if row.get('genus'):
                existing_genera.add(row['genus'].lower())
                
    n_curated = len(existing_rows)       

    print(f"Loaded {n_curated} existing species from curated list.")

    # 2. Read stats and find candidates
    candidates = []
    seen_in_stats_norm = set()
    
    with open(STATS_FILE, mode='r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            species_raw = row['species']
            count_str = row.get('total_count', '0')
            count = int(count_str) if count_str and count_str.isdigit() else 0
            
            if count < THRESHOLD:
                continue

            bin_name = normalize_and_binomial(species_raw)
            
            if bin_name not in existing_species_norm and bin_name not in seen_in_stats_norm:
                seen_in_stats_norm.add(bin_name)
                
                parts = bin_name.split(' ')
                if len(parts) < 2:
                    continue 
                
                genus_idx = 0 if parts[0] != '×' else 1
                genus = parts[genus_idx].capitalize()

                # Determine defaults
                leaf_cycle = "deciduous"
                if parse_float(row.get('leaf_cycle_evergreen', 0)) > parse_float(row.get('leaf_cycle_deciduous', 0)):
                    leaf_cycle = "evergreen"
                
                leaf_type = "broadleaved"
                if parse_float(row.get('leaf_type_needleleaved', 0)) > parse_float(row.get('leaf_type_broadleaved', 0)):
                    leaf_type = "needleleaved"

                candidates.append({
                    'species_raw': species_raw,
                    'species': get_display_name(bin_name),
                    'genus': genus,
                    'count': count,
                    'leaf_cycle': leaf_cycle,
                    'leaf_type': leaf_type
                })

    candidates.sort(key=lambda x: x['count'], reverse=True)
    print(f"Found {len(candidates)} potential candidates. Verifying with POWO API...")

    # 3. Verify candidates with POWO
    final_suggestions = []
    for i, c in enumerate(candidates):
        print(f"[{i+1}/{len(candidates)}] Checking {c['species']}..."+" "*10, end='\r')
        status, accepted_name = check_powo_status(c['species'])
        c['powo_status'] = status
        c['powo_accepted'] = accepted_name
        
        # Determine effective status once for all subsequent logic
        raw_name = c['species_raw']
        if is_genus_sp(raw_name):
            c['eff_status'] = 'Genus sp.'
        elif is_cultivar(raw_name):
            genus = raw_name.split(' ')[0].lower()
            if genus in existing_genera:
                c['eff_status'] = 'Cultivars'
            else:
                c['eff_status'] = 'Not found'
        else:
            c['eff_status'] = status
            
        final_suggestions.append(c)
    
    print("Verification complete."+" "*40)

    # 4. Generate Synonyms CSV file
    synonyms = [s for s in final_suggestions if s['eff_status'] == 'Synonym']
    if synonyms:
        with open(OUTPUT_CSV_SYNONYMS, mode='w', newline='', encoding='utf-8') as csvfile:
            writer = csv.DictWriter(csvfile, fieldnames=['species', 'accepted_name', 'genus', 'species:wikidata', 'leaf_cycle', 'leaf_type'])
            writer.writeheader()
            for s in synonyms:
                writer.writerow({
                    'species': s['species'],
                    'accepted_name': s['powo_accepted'] if s['powo_accepted'] else '',
                    'genus': s['genus'],
                    'species:wikidata': '',
                    'leaf_cycle': s['leaf_cycle'],
                    'leaf_type': s['leaf_type']
                    
                    #'status': s['powo_status'],
                    #'count': s['count']
                })
        print(f"Synonyms CSV saved to: {OUTPUT_CSV_SYNONYMS}")
        
        
    # 5. Generate "Accepted" species CSV file
    n_accepted=0
    with open(OUTPUT_CSV_FOUND, mode='w', newline='', encoding='utf-8') as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames=['species', 'genus', 'species:wikidata', 'leaf_cycle', 'leaf_type'])
        writer.writeheader()
        for s in final_suggestions:
            if s['eff_status'] == 'Accepted':
                writer.writerow({
                    'species': s['species'],
                    'genus': s['genus'],
                    'species:wikidata': '',
                    'leaf_cycle': s['leaf_cycle'],
                    'leaf_type': s['leaf_type']
                })
                n_accepted += 1
    print(f"Accepted species CSV saved to: {OUTPUT_CSV_FOUND}")
    
    # 6. Generate Wiki Markup file for all accepted species
    all_wiki_species = existing_rows
    for s in final_suggestions:
        if s['eff_status'] == 'Accepted':
            all_wiki_species.append({
                'species': s['species'],
                'genus': s['genus'],
                'species:wikidata': '',
                'leaf_cycle': s['leaf_cycle'],
                'leaf_type': s['leaf_type']
            })
    
    # Sort alphabetically
    all_wiki_species.sort(key=lambda x: re.sub(r'\s+', ' ', x['species'].replace('×', '')).strip().lower())
    
    with open(OUTPUT_WIKI_FILE, mode='w', encoding='utf-8') as wf:
        wf.write('{| class="wikitable"\n')
        wf.write('! species \n')
        wf.write('!genus|| species:wikidata || {{key|leaf_cycle}} || {{key|leaf_type}}\n')
        wf.write('|-\n')
        
        for s in all_wiki_species:
            wd = s.get('species:wikidata', '')
            if wd and wd.startswith('Q') and '|' not in wd:
                wd = f'[[:d:{wd}|{wd}]]'
                
            wf.write(f"| {s['species']} \n")
            wf.write(f"|{s['genus']}|| {wd} || {s['leaf_cycle']} || {s['leaf_type']}\n")
            wf.write(f"|-\n")
        wf.write('|}\n')
    
    print(f"Complete Wiki table saved to: {OUTPUT_WIKI_FILE}")
    
    # 6. Output Markdown suggestions
    
    with open(OUTPUT_MD_FILE, mode='w', encoding='utf-8') as md:
        md.write(f"# Found Species Report \n")
        md.write(f"* Threshold: {THRESHOLD}\n\n")
        
        md.write(f"|Source | Species| \n")
        md.write(f"| :--- | :--- |\n")
        md.write(f"|Curated list | {n_curated}|\n")
        md.write(f"|Found in OSM data (confimed by POWO) |{n_accepted}|\n")
        md.write(f"|Synonyms (confimed by POWO) | {len(synonyms)}|\n")
        md.write(f"|**TOTAL RECORS** |**{n_curated+n_accepted+len(synonyms)}**|")
        
        
        print(f"Markdown report saved to: {OUTPUT_MD_FILE}")
    

if __name__ == "__main__":
    main()
