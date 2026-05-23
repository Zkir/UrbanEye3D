"""
Find invalid species
"""
import os
import csv
from datawash import to_binomial1 as to_binomial, is_cultivar
from powoapi import check_powo_status

BASE_DIR      = os.path.dirname(os.path.abspath(__file__))
INPUT_FILE    = os.path.join(BASE_DIR, 'data', '10_trees', 'trees.csv')
VALID_SPECIES = os.path.join(BASE_DIR, 'data', '15_trees_output', 'tree_species.csv')
OUTPUT_FILE =   os.path.join(BASE_DIR, 'data', '15_trees_output', 'tree_species_invalid.csv')

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


    #print(f"Loaded {len(valid_species)} species names and {len(valid_genus)} genus names from valid species list.")
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
                
                if (species_normalized not in valid_species) and (not is_cultivar(species)):
                    if species not in invalid_species:
                        invalid_species[species] = 0
                        
                    invalid_species[species] += 1    
                
                
    print (f"{k} record read, {len(invalid_species)} invalid species name found") 
    invalid_species_sorted = sorted(invalid_species.items(), key=lambda item: item[1], reverse=True)
    
    return invalid_species_sorted


def save_invalid_species(output_filename, invalid_species):
    
    with open(OUTPUT_FILE, mode='w', newline='', encoding='utf-8') as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames=['species', 'count'])
        writer.writeheader()
        for s in invalid_species:
            writer.writerow({
                'species': s[0],
                'count': s[1],
            })




if __name__ == "__main__":
    valid_species, valid_genus = load_accepted_species(VALID_SPECIES)
    invalid_species = find_invalid_species(INPUT_FILE, valid_species)
    save_invalid_species(OUTPUT_FILE, invalid_species)
    
    
