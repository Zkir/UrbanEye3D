import os
import csv
import xml.etree.ElementTree as ET

INPUT_OSM = 'data/05_extracts/trees.osm'
#SYNONYMS_CSV = 'data/15_trees_output/tree_synonyms.csv'
SYNONYMS_CSV = 'tree_typos.csv'
OUTPUT_DIR = 'data/16_trees_fixes'
LIMIT = 5000

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
    CHANGE_ENGLISH_NAME = 'English name instead of Latin name'
    allowed_types= (CHANGE_ENGLISH_NAME, 'Typo', 'Formatting', 'Species name omitted', 'Nonsense')
    
    with open(SYNONYMS_CSV, mode='r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            species = row['species']
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
                print(f"Processed {i} elements...")

            if elem.tag in ('node', 'way', 'relation'):
                modified = False
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
                            
                            if change_type[old_species] == CHANGE_ENGLISH_NAME:
                                # Check if species:en already exists
                                en_tag = None
                                for t in elem.findall('tag'):
                                    if t.get('k') == 'species:en':
                                        en_tag = t
                                        break
                                
                                if en_tag is not None:
                                    en_tag.set('v', old_species)
                                else:
                                    # Create new tag
                                    new_tag = ET.Element('tag', k='species:en', v=old_species)

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
                            actual_counts[old_species] += 1
                
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
    except Exception as e:
        print(f"Error during parsing: {e}")

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
