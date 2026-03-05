import re
import csv
import math
from collections import Counter
PATTERN_VALUE_WITH_UNIT = r'^(-?\d+(?:\.\d+)?)\s*([a-zA-Z]+|["\']|см|м)$'
PATTERN_VALUE_RANGE = r'^(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)$'

def clean_height_or_circumference(value_str: str):   
    if value_str in ["less than 2m", "less than 2 m"]: value_str="1.5"
    if value_str=="greater than 2m and less than 3m": value_str="2.5"
    if value_str=="greater than 3m": value_str="3.25"
    if value_str=="< 3": value_str="2.75"
    if value_str==">24": value_str="25"
    if "," in value_str: value_str=value_str.split(",")[0]
    if ";" in value_str: value_str=value_str.split(";")[0]
    if value_str and value_str[0]=="~": value_str=value_str[1:]
    unit = None
    matches = re.findall(PATTERN_VALUE_WITH_UNIT, value_str)
    if matches:
        value = matches[0][0]
        unit = matches[0][1].lower()
        if unit in ["m", "meter", "metros", "metres", "meters", "mt", "mts", "metri", "м"]:
            value_str=value
        elif unit in ["cm", "см"]:
            value_str=str(float(value) * 0.01)
        elif unit in ["ft", "foot", "feet", "'"]:
            value_str=str(float(value) * 0.3048)
        elif unit in ["in", "inch", "inches", "''", "\""]:
            value_str=str(float(value) * 0.0254)
        elif unit in ["estimation", "et", "ca", "t", "med", "jacaranda", "o", "mueller", "s", "ss", "pedro", "rr", "arecaceae", "w"]:
            return None, unit
        else: 
            print("unknown unit:", value, unit)
            #exit(1)
    matches = re.findall(PATTERN_VALUE_RANGE, str(value_str))
    if matches:
        value1 = float(matches[0][0])
        value2 = float(matches[0][1])
        value_str=str(((value1) + float(value2))/2)    
        
    try:    
        value = math.log(float(value_str), 2)    
    except (ValueError): 
        #print ("unexpected value: " , value_str)        
        return None, unit
        
        
    return str(value), unit  

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
            value_counter = Counter()
            for row in reader:
                value_str = row.get(column_name)
                _, value_str = clean_height_or_circumference(value_str)
                
                value_counter[value_str] += 1

            
        print("-" * 30)
        print(f"Found {len(value_counter)} unique non-empty values:")
        print("-" * 30)
        
        # Print sorted by the most common
        for value, count in value_counter.most_common():
            print(f"`{value}`: {count}")

    except FileNotFoundError:
        print(f"Error: File '{filename}' not found.")
    except Exception as e:
        print(f"An unexpected error occurred: {e}")

if __name__ == "__main__":
    INPUT_FILE = 'data/trees.csv'
    COLUMN_TO_INSPECT = 'circumference'
    inspect_column_values(INPUT_FILE, COLUMN_TO_INSPECT)
