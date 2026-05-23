import os
import csv
from powoapi import check_powo_status
import urllib.parse

BASE_DIR      = os.path.dirname(os.path.abspath(__file__))
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
    
    
def load_invalid_species(input_filename):
    invalid_species = []
    with open(input_filename, mode='r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row['species'] not in invalid_species:
                invalid_species.append((row['species'],int(row['count'])))
    
    return invalid_species
     


def report_invalid_species(invalid_species, valid_species, valid_genus):
    
    suggested_changes = []

    
    print(f'{"SPECIES":<35} | {"STATUS":<11} | {"COUNT":>6}') 
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
                    
                
        # print the line out    
        if status not in ('Cultivar', 'Formatting', 'Genus sp.'):    
            print(f"{species:<35} | {status:<11} | {count:>6}")    
            
                
    with open(OUTPUT_CHANGES_FILE, mode='w', encoding='utf-8') as f:
        f.write("species,accepted_name,status,count\n")
        for s in suggested_changes:
            f.write(f"{s['species']},{s['species_new']},{s['status']},{s['count']}\n")
                
    return suggested_changes            


def report_invalid_species2(suggested_changes):

    with open(OUTPUT_MD_FILE, mode='w', encoding='utf-8') as md:
        md.write(f"# Invalid Species\n\n")
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
    invalid_species = load_invalid_species(INVALID_SPECIES_FILE)
    suggested_changes=report_invalid_species(invalid_species, valid_species, valid_genus)
    report_invalid_species2(suggested_changes)