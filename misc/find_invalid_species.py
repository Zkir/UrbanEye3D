"""
Find invalid species
"""
import os
import csv
from  datawash import to_binomial1 as to_binomial, is_cultivar
from powoapi import check_powo_status

BASE_DIR      = os.path.dirname(os.path.abspath(__file__))
INPUT_FILE    = os.path.join(BASE_DIR, 'data', '10_trees', 'trees.csv')
VALID_SPECIES = os.path.join(BASE_DIR, 'data', '15_trees_output', 'tree_species.csv')
OUTPUT_CHANGES_FILE = os.path.join(BASE_DIR, 'data', '15_trees_output', 'changes.csv')

THRESHOLD = 100


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


    print(f"Loaded {len(valid_species)} species names and {len(valid_genus)} genus names from valid species list.")
    return valid_species, valid_genus 


def find_invalid_species(input_filename, valid_species):
    
    invalid_species = {}
    k=0
    with open(input_filename, 'r', newline='', encoding='utf-8') as infile:
        reader = csv.DictReader(infile)
        for row in reader:
            k += 1
            species = row.get("species")
            if species:
                species_normalized = to_binomial(species)
                
                if species_normalized not in valid_species:
                    if species not in invalid_species:
                        invalid_species[species] = 0
                        
                    invalid_species[species] += 1    
                
                
    print (f"{k} record read, {len(invalid_species)} invalid species name found") 
    invalid_species_sorted = sorted(invalid_species.items(), key=lambda item: item[1], reverse=True)
    
    return invalid_species_sorted


def report_invalid_species(invalid_species, valid_species, valid_genus):
    
    suggested_changes = []

    
    print(f'{"SPECIES":<35} | {"STATUS":<11} | {"COUNT":>6}') 
    for species, count in invalid_species:
        if count<THRESHOLD:
            break
            
        status = ""
        renormalized = species[0].upper() + species[1:].lower()
        if renormalized in valid_species:
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
                if is_cultivar(species):
                    status = 'Cultivar' 
                elif status not in ('Accepted', 'Synonym'):
                    status = 'Typo'
                    suggested_changes += [{"species":species, "species_new":"????", "status":status, "count":count }]            
                
        # print the line out    
        if status not in ('Accepted', 'Synonym', 'Cultivar', 'Formatting', 'Genus sp.'):    
            print(f"{species:<35} | {status:<11} | {count:>6}")    
            
                
    with open(OUTPUT_CHANGES_FILE, mode='w', encoding='utf-8') as f:
        f.write("species,accepted_name,status,count\n")
        for s in suggested_changes:
            if s['status'] not in ('Formatting', 'Genus sp.'): #temporary exlcusion for already uploaded changes
                f.write(f"{s['species']},{s['species_new']},{s['status']},{s['count']}\n")
        


if __name__ == "__main__":
    valid_species, valid_genus = load_accepted_species(VALID_SPECIES)
    invalid_species = find_invalid_species(INPUT_FILE, valid_species)
    report_invalid_species(invalid_species, valid_species, valid_genus)
    
    