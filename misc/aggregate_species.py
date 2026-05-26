"""
This script just aggregates species from trees.csv
"""
import os
import csv

BASE_DIR      = os.path.dirname(os.path.abspath(__file__))
INPUT_FILE    = os.path.join(BASE_DIR, 'data', '10_trees', 'trees.csv')
OUTPUT_FILE = os.path.join(BASE_DIR, 'data', '10_trees', 'tree_species_all.csv')

def aggregate_species(input_filename):
    
    all_species = {}
    k=0
    with open(input_filename, 'r', newline='', encoding='utf-8') as infile:
        reader = csv.DictReader(infile)
        for row in reader:
            k += 1
            species = row.get("species")
            if species:
                if species not in all_species:
                        all_species[species] = 0
                all_species[species] += 1
                
    

    all_species_sorted = sorted(all_species.items(), key=lambda item: item[1], reverse=True)
    print (f"{k} record read, {len(all_species)} species names found") 
    
    return all_species_sorted


def save_species_csv(output_filename, invalid_species):
    
    with open(output_filename, mode='w', newline='', encoding='utf-8') as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames=['species', 'count'])
        writer.writeheader()
        for s in invalid_species:
            writer.writerow({
                'species': s[0],
                'count': s[1],
            })




if __name__ == "__main__":
    all_species = aggregate_species(INPUT_FILE)
    save_species_csv(OUTPUT_FILE, all_species)
    
    
