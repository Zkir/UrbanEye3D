import os
import csv
import xml.etree.ElementTree as ET

INPUT_OSM = 'data/05_extracts/trees.osm'
#SYNONYMS_CSV = 'data/15_trees_output/changes.csv'
SYNONYMS_CSV = 'tree_typos.csv'
#SYNONYMS_CSV = 'tree_typos_2.csv'
OUTPUT_DIR = 'data/16_trees_fixes'
LIMIT = 15000

CHANGE_ENGLISH_NAME = 'Name:en'
CHANGE_GENUS_OMITTED = 'Genus omitted'
CHANGE_ONLY_GENUS = 'Genus sp.'
allowed_types= (CHANGE_ENGLISH_NAME, 'Typo', 'Formatting', 'Species name omitted', 'Nonsense', CHANGE_GENUS_OMITTED, CHANGE_ONLY_GENUS)

WD_GENUS=('Q132557', 'Q132557', 'Q127849', 'Q104819', 'Q163025','Q190545','Q157017', 'Q36050', 'Q434','Q189393')

def print_expected_change_report(synonyms, change_type,expected_counts):
    filename = os.path.join(OUTPUT_DIR, "proposed_changes.md")
    out_f = open(filename, 'w', encoding='utf-8')
    out_f.write('# Proposed changes \n')
    out_f.write("|  Source | Correction | Count |\n")
    out_f.write("| :--- | :--- | :--- | \n")
    
    for species in synonyms:
        if species[0]=='#':
            continue
        if change_type[species]==CHANGE_ENGLISH_NAME:
            out_f.write(f"|`species={species}`| `species={synonyms[species]}` + `species:en={species}` | {expected_counts[species]} |\n")
        elif change_type[species]==CHANGE_GENUS_OMITTED: 
            genus=synonyms[species].split(" ")[0]
            out_f.write(f"|`species={species}` + `genus={genus}`| `species={synonyms[species]}` | {expected_counts[species]} |\n")
        elif change_type[species]==CHANGE_ONLY_GENUS:
            genus=species.split(" ")[0]
            out_f.write(f"|`species={species}` | `species=` + `genus={genus}` | {expected_counts[species]} |\n")
        else:
            out_f.write(f"|`species={species}`| `species={synonyms[species]}` | {expected_counts[species]} |\n")
    out_f.close()


def replace_species():
    """
    Reads trees.osm, updates species tags based on synonyms,
    and writes modified elements to JOSM-compatible .osm files.
    """
    print(f"Loading synonyms from {SYNONYMS_CSV}...")
    synonyms = {}
    expected_counts = {}
    actual_counts = {}
    change_type = {}
    
    
    with open(SYNONYMS_CSV, mode='r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            species = row['species']
            if species[0]=='#':
                continue
            synonyms[species] = row['accepted_name']
            change_type[species] = row['status']
            if change_type[species] not in allowed_types:
                print("Unknown change type '"+change_type[species]+"'")
                print("Exiting")
                exit(1)
            expected_counts[species] = int(row.get('count', 0))
            actual_counts[species] = 0
    
    print(f"Loaded {len(synonyms)} synonyms.")
    

    if not os.path.exists(OUTPUT_DIR):
        os.makedirs(OUTPUT_DIR)
        print(f"Created directory {OUTPUT_DIR}")
        
    print_expected_change_report(synonyms, change_type, expected_counts)    

    file_count = 1
    element_count = 0
    total_modified = 0
    out_f = None

    def start_new_file():
        nonlocal out_f, file_count, element_count
        if out_f:
            out_f.write('</osm>\n')
            out_f.close()
        
        filename = os.path.join(OUTPUT_DIR, f'update_{file_count:03d}.osm')
        print(f"Starting new output file: {filename}")
        out_f = open(filename, 'w', encoding='utf-8')
        out_f.write("<?xml version='1.0' encoding='UTF-8'?>\n")
        out_f.write("<osm version='0.6' generator='replace_species.py'>\n")
        file_count += 1
        element_count = 0

    start_new_file()

    print(f"Parsing {INPUT_OSM} (8GB file, this may take a while)...")
    # Parse OSM iteratively to save memory
    context = ET.iterparse(INPUT_OSM, events=('end',))
    
    try:
        for i, (event, elem) in enumerate(context):
            if i % 1000000 == 0 and i > 0:
                print(f"Processed {i} elements...", end='\r')

            if elem.tag in ('node', 'way', 'relation'):
                modified = False
                have_to_skip = False
                tags_to_remove = []
                for tag in elem.findall('tag'):
                    if tag.get('k') == 'species':
                        old_species = tag.get('v')
                        if old_species in synonyms:
                            new_species = synonyms[old_species]
                            if new_species:
                                tag.set('v', new_species)
                                modified = True
                            else:
                                # Mark tag for removal
                                tags_to_remove.append(tag)
                                modified = True
                            
                            if change_type[old_species] in (CHANGE_ENGLISH_NAME, CHANGE_ONLY_GENUS):
                                
                                tag_to_add = 'species:en'
                                value_to_add = old_species 
                                
                                if change_type[old_species] == CHANGE_ONLY_GENUS:
                                    tag_to_add = 'genus'
                                    value_to_add = old_species.split(" ")[0]
                                    value_to_add = value_to_add[0].upper() + value_to_add[1:].lower()
                                    #we need to check presence of species:wikidata or species:wikipedia
                                    for t in elem.findall('tag'):
                                        if t.get('k') in ('species:wikidata', 'species:wikipedia') :
                                            if t.get('v') in WD_GENUS:
                                                #this is genus, and it's ok
                                                break
                                                
                                            print(f"fuck! wikidata/wikipedia tag is present. Old value: '{old_species}', New value: '{new_species}', Genus: '{value_to_add}', tag: {t.get('v')}")
                                            have_to_skip = True
                                            break
                                            
                                        if t.get('k') in ('genus') :
                                            if t.get('v')!=value_to_add:
                                                print(f"fuck! genus tag is present and does not match . Old value: '{old_species}', New value: '{new_species}', Genus: '{value_to_add}', tag: {t.get('v')}")
                                                have_to_skip = True
                                                break    
                                            
                                if have_to_skip:
                                    break                                    
                                
                                # Check if species:en already exists
                                en_tag = None
                                for t in elem.findall('tag'):
                                    if t.get('k') == tag_to_add :
                                        en_tag = t
                                        break
                                
                                if en_tag is not None:
                                    en_tag.set('v', value_to_add)
                                else:
                                    # Create new tag
                                    new_tag = ET.Element('tag', k=tag_to_add, v=value_to_add)

                                    # Try to preserve formatting by copying tail from existing tags
                                    existing_tags = elem.findall('tag')
                                    if existing_tags:
                                        # Copy the tail of the last tag (indentation for the next tag or closing tag)
                                        last_tag = existing_tags[-1]
                                        new_tag.tail = last_tag.tail

                                        # Determine standard tag indentation (usually from elem.text or the first tag)
                                        # If not found, use default 4 spaces
                                        tag_indent = elem.text if elem.text else "\n    "
                                        last_tag.tail = tag_indent

                                    elem.append(new_tag) 
                                    
                            if change_type[old_species] == CHANGE_GENUS_OMITTED:
                                # this change can be only appied if there is already matching genus tag
                                # Check if genus tag exists
                                genus_tag = None
                                genus = ""
                                for t in elem.findall('tag'):
                                    if t.get('k') == 'genus':
                                        genus_tag = t
                                        break
                                        
                                if genus_tag is not None:
                                    genus=genus_tag.get('v')
                                    if new_species.split(" ")[0]!=genus:
                                        print(f"fuck! genus does not match. Old value: '{old_species}', New value: '{new_species}', Genus: '{genus}'")
                                        #exit(1)    
                                else:    
                                    print(f"fuck! genus does not match. Old value: '{old_species}', New value: '{new_species}', Genus: '{genus}'")
                                    #exit(1)    
                            actual_counts[old_species] += 1
                            
                if have_to_skip:            
                    continue
                
                for tag in tags_to_remove:
                    elem.remove(tag)
                
                if modified:
                    elem.set('action', 'modify')
                    # Convert element to string and write to file
                    xml_str = ET.tostring(elem, encoding='unicode')
                    out_f.write(xml_str + '\n')
                    element_count += 1
                    total_modified += 1
                    
                    if element_count >= LIMIT:
                        start_new_file()
                
                # Clear element from memory to avoid 8GB heap
                elem.clear()
    except EOFError:
        print("End of file reached unexpectedly or file truncated.")
        exit(1)
    except Exception as e:
        print(f"Error during parsing: {e}")
        exit(1)

    if out_f:
        out_f.write('</osm>\n')
        out_f.close()
    
    # If the last file was empty (only header/footer), delete it
    if element_count == 0 and file_count > 1:
        last_filename = os.path.join(OUTPUT_DIR, f'update_{file_count-1:03d}.osm')
        if os.path.exists(last_filename):
            os.remove(last_filename)
            print(f"Removed empty file: {last_filename}")

    print(f"\nDone! Total modified elements: {total_modified}")
    print(f"Created {file_count - 1} files in {OUTPUT_DIR}")
    
    print("\nReplacement Report:")
    header = f"{'Species (Original)':<35} | {'Expected':>10} | {'Actual':>10} | {'Diff':>10}"
    print(header)
    print("-" * len(header))
    for spec in synonyms:
        exp = expected_counts[spec]
        act = actual_counts[spec]
        diff = act - exp
        print(f"{spec:<35} | {exp:>10} | {act:>10} | {diff:>10}")

if __name__ == '__main__':
    replace_species()
