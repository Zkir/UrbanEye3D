import os
import csv
from powoapi import check_powo_status
from datawash import to_binomial1 as to_binomial, is_cultivar
import urllib.parse

BASE_DIR      = os.path.dirname(os.path.abspath(__file__))
ALL_SPECIES_FILE     = os.path.join(BASE_DIR, 'data', '10_trees', 'tree_species_all.csv')
VALID_SPECIES_FILE   = os.path.join(BASE_DIR, 'data', '15_trees_output', 'tree_species.csv')
OUTPUT_CHANGES_FILE  = os.path.join(BASE_DIR, 'data', '15_trees_output', 'changes.csv')
INVALID_SPECIES_FILE = os.path.join(BASE_DIR, 'data', '15_trees_output', 'tree_species_invalid.csv')
OUTPUT_MD_FILE       = os.path.join(BASE_DIR, 'data', '15_trees_output', 'tree_species_invalid.md')

THRESHOLD = 100

def get_taginfo_url(species_name):
    """Generates a TagInfo URL for the species."""
    encoded_query = urllib.parse.quote(f"species={species_name}", safe='=')
    return f"https://taginfo.openstreetmap.org/tags/{encoded_query}"


def load_accepted_species(input_filename):
    valid_species = []
    valid_genus = []
    with open(input_filename, mode='r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row['species'] not in valid_species:
                valid_species.append(row['species'])
                
            if row['genus'] not in valid_genus:    
                valid_genus.append(row['genus'])
    
    return valid_species, valid_genus 
    
    
def load_all_species(input_filename):
    all_species = []
    with open(input_filename, mode='r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row['species'] not in all_species:
                all_species.append((row['species'],int(row['count'])))
    
    return all_species
    
    
def validate_species(all_species, valid_species, valid_genus):
    invalid_species = {}
    
    for species, count in all_species:
        species_normalized = to_binomial(species)
                
        if (species_normalized not in valid_species) and (not is_cultivar(species)):
            invalid_species[species] = count   
    
    invalid_species_sorted = sorted(invalid_species.items(), key=lambda item: item[1], reverse=True)
    return invalid_species_sorted    
     


def report_invalid_species(invalid_species, valid_species, valid_genus):
    
    suggested_changes = []
    
    for species, count in invalid_species:
        if count<THRESHOLD:
            break
            
        status = ""
        renormalized = species[0].upper() + species[1:].lower()
        if species in valid_species:
            status = "Accepted"
        elif renormalized in valid_species:
            status = "Formatting"
            suggested_changes += [{"species":species, "species_new":renormalized, "status":status, "count":count }]
        else:    
            if species.endswith(" sp."):
                status, accepted_name = check_powo_status(species[0:-4])
            else:    
                status, accepted_name = check_powo_status(species)

            # accepted one worders are GENUS    
            if (species.split(" ")[0] in valid_genus and (len(species.split(" "))==1) or species.endswith(" sp.")):
                status = "Genus sp."
                suggested_changes += [{"species":species, "species_new":"", "status":status, "count":count }]            
            else:    
                if status not in ('Accepted', 'Synonym'):
                    status = 'Typo'
                    suggested_changes += [{"species":species, "species_new":"????", "status":status, "count":count }] 
                else:    
                    suggested_changes += [{"species":species, "species_new":accepted_name, "status":status, "count":count }] 
            
                
    with open(OUTPUT_CHANGES_FILE, mode='w', encoding='utf-8') as f:
        f.write("species,accepted_name,status,count\n")
        for s in suggested_changes:
            f.write(f"{s['species']},{s['species_new']},{s['status']},{s['count']}\n")
                
    return suggested_changes            
    
def draw_bar(percentage, length=40):
    """Generates an ASCII bar chart string."""
    filled = int((percentage / 100) * length)
    empty = length - filled
    return "█" * filled + "░" * empty
    


def report_invalid_species2(suggested_changes, all_species, invalid_species):
    
    total_unique = len(all_species)
    invalid_tags_count  = len (invalid_species)
    valid_tags_count = total_unique - invalid_tags_count
    perc_valid_unique = valid_tags_count/total_unique * 100.0
    perc_invalid_unique =  invalid_tags_count/total_unique * 100.0
    
    total_trees = 0
    for _, count in all_species:
        total_trees += count 
        
    invalid_trees_count=0    
    for _, count in invalid_species:
        invalid_trees_count += count         
        
    valid_trees_count= total_trees - invalid_trees_count
    
    perc_valid_trees = valid_trees_count/total_trees*100.00
    perc_invalid_trees = invalid_trees_count/total_trees*100.00

    with open(OUTPUT_MD_FILE, mode='w', encoding='utf-8') as md:
        
        md.write(f"# Species report\n")

        md.write(f"##  Species Tag Statistics \n")
        md.write(f"Only `natural=tree` nodes are included:\n\n")
       
        
        md.write(f"Total Trees with 'species' tag: {total_trees:,}  \n")
        md.write(f"Valid:   {valid_trees_count:>12,} ({perc_valid_trees:5.1f}%)  \n")
        md.write(f"Invalid: {invalid_trees_count:>12,} ({perc_invalid_trees:5.1f}%)  \n")
        md.write(draw_bar(perc_valid_trees))
        md.write(f"\n")
        
        md.write(f"\nTotal Unique Species Tags: {total_unique:,}  \n")
        md.write(f"Valid:   {valid_tags_count:>12,} ({perc_valid_unique:5.1f}%)  \n")
        md.write(f"Invalid: {invalid_tags_count:>12,} ({perc_invalid_unique:5.1f}%)  \n")
        md.write(draw_bar(perc_valid_unique))
        md.write(f"\n")
        md.write(f"\n\n")
        md.write("'Valid' means that  either *binomial part* of the species tag value is reported by [POWO](https://powo.science.kew.org/) as 'accepted' or as 'synonym' or is present in the [osm-wiki species list](https://wiki.openstreetmap.org/wiki/Tag:natural%3Dtree/List_of_Species), or species tag value can be interpreted as cultivar name.\n\n")
        
        md.write(f"## Invalid Species\n\n")
        md.write("The following species are frequent in OpenStreetMap, but missing from the [curated list](https://wiki.openstreetmap.org/wiki/Tag:natural%3Dtree/List_of_Species):\n\n")
        
        
        md.write(f"|Species|Status|Count|\n")
        md.write("| :--- | :--- | :--- |\n")
        for s in suggested_changes:
            
            taginfo_name = s['species']
            display_name = s['species']

            url = get_taginfo_url(taginfo_name)
                
            md.write(f"| [{display_name}]({url})|{s['status']}|{s['count']}|\n")
                
   



if __name__ == "__main__":
    valid_species, valid_genus = load_accepted_species(VALID_SPECIES_FILE)
    all_species = load_all_species(ALL_SPECIES_FILE)
    invalid_species = validate_species(all_species, valid_species, valid_genus)
    suggested_changes=report_invalid_species(invalid_species, valid_species, valid_genus)
    report_invalid_species2(suggested_changes, all_species, invalid_species)
    print(f"{len(invalid_species)} invalid species names found out of {len(all_species)}")        