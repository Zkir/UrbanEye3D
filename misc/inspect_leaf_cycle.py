
import csv
from collections import Counter

def inspect_column_values(filename, column_name):
    """
    Reads a CSV file and prints the frequency of each unique value
    in a specified column.
    """
    print(f"Inspecting unique values in column '{column_name}' from '{filename}'...")
    
    try:
        with open(filename, 'r', newline='', encoding='utf-8') as infile:
            reader = csv.DictReader(infile)
            if column_name not in reader.fieldnames:
                print(f"Error: Column '{column_name}' not found in file.")
                return
            
            # Use a Counter to efficiently count frequencies
            value_counter = Counter(row[column_name] for row in reader if row[column_name])
            
        print("-" * 30)
        print(f"Found {len(value_counter)} unique non-empty values:")
        print("-" * 30)
        
        # Print sorted by the most common
        for value, count in value_counter.most_common():
            print(f"'{value}': {count}")

    except FileNotFoundError:
        print(f"Error: File '{filename}' not found.")
    except Exception as e:
        print(f"An unexpected error occurred: {e}")

if __name__ == "__main__":
    INPUT_FILE = 'trees.csv'
    COLUMN_TO_INSPECT = 'leaf_type'
    inspect_column_values(INPUT_FILE, COLUMN_TO_INSPECT)
