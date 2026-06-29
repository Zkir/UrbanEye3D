import csv
import json
import math
import re
import os
import argparse
from startdate import parseStartDateValue
from collections import defaultdict, Counter
from datawash import clean_height_or_circumference

def clean_leaf_cycle(value: str) -> str | None:
    if not value: return None
    mapping = {
        'deciduous': 'deciduous', 'dedious': 'deciduous', 'kes├дvihantakausivihanta': 'deciduous',
        'evergreen': 'evergreen', 'coniferous': 'evergreen', 'palms': 'evergreen',
        'mixed': 'mixed', 'semi_deciduous': 'mixed', 'semi_evergreen': 'mixed', 'deciduous;evergreen': 'mixed',
    }
    if value == 'broadleaved': return None
    return mapping.get(value)

def clean_leaf_type(value: str) -> str | None:
    if not value: return None
    mapping = {
        'broadleaved': 'broadleaved', 'broadleved': 'broadleaved', 'Ancha': 'broadleaved', 'ulivo': 'broadleaved', 'Olivier': 'broadleaved',
        'needleleaved': 'needleleaved', 'conifer': 'needleleaved', 'coniferous': 'needleleaved',
        'palm': 'palm', 'palms': 'palm', 'Coconut Tree': 'palm', 'Coconut': 'palm',
        'mixed': 'mixed', 'broadleaved;needleleaved': 'mixed', 'broadleaved/needleleaved': 'mixed', 'needleleaved;broadleaved': 'mixed',
        'leafless': 'leafless',
    }
    ignore_list = {'tree', 'trees', 'deciduous', 'b', 'Ornamental', 'isolated trees', 'different tree', 's', 'ARBRE', 'narrowleaved'}
    if value in ignore_list: return None
    return mapping.get(value)

    
def clean_age(age, start_date):
    """ we convert age and start_date to stage and age """
    #age=None #this column is stupid, ignore it
        
    stage = None
    age1 = None
    if age:
        if age=="Jovem":
            age="young"
        if age=="adult":
            age="mature"
        if age=="young adult":
            age="semi-mature"
        if age=="veteran":
            age="mature"            
        
        if age.lower() in ["new planting", "young", "early-mature", "semi-mature", "mature"]:
            stage = age
        else:
            try:        
                age1=float(age)
            except (ValueError ):
                print("unrecognized age value: " + str(age))
                age1=None
        
    if start_date:
        try:
            year=float(parseStartDateValue(start_date))
            if year<=2026:
                age1 = 2026 - year
            else:
                age1=None            
        except (ValueError):
            print("unrecognized start date: " + str(start_date))
            pass
            
    if age1:
        if age1<0:
            print("age="+age +", start_date=" + start_date)            
            exit(1)
            
        #log was needed for log-normal correlation. comment it out for now    
        #age1 = math.log(age1, 2)    
        
        
    return stage, age1   
    

def analyze_data(input_filename, json_outfile, csv_outfile, group_by_col, numeric_cols, categorical_cols):
    print(f"Reading data from '{input_filename}' in a single pass...")
    stats_agg = defaultdict(lambda: {
        'numeric': defaultdict(lambda: {'count': 0, 'mean': 0.0, 'S': 0.0, 'min': float('inf'), 'max': float('-inf')}),
        'categorical': defaultdict(Counter)
    })
    group_totals = Counter()
    circumference_rejected_values = 0
    errors = []
    try:
        with open(input_filename, 'r', newline='', encoding='utf-8') as infile:
            reader = csv.DictReader(infile)
            for row in reader:
                group_key = row.get(group_by_col)
                if not group_key: continue
                group_totals[group_key] += 1
                for col in numeric_cols:
                    value_str = row.get(col)
                    
                    if col in ["height", "circumference"]:
                        value_str=clean_height_or_circumference(value_str)
                        
                    if col == 'age':
                        # we would like to find age, but we need to check two columns.
                        stage, age = clean_age(row.get('age'), row.get('start_date')) 
                        value_str = age           
                            
                    if value_str:    
                        try:
                            x = float(value_str)
                            if x < 0:
                                print ("unexpected value for ", col, ": ", value_str, row.get("id"), row.get(col)); 
                                exit(1)
                                continue
                            if x > 45 and col=="circumference":
                                osm_id = row.get("id")
                                message =  "Circumference is too large. Known thickest tree is just 43 m in circumference"
                                errors += [{"id":osm_id, "key":col, "value":row.get(col), "message": message}]
                                circumference_rejected_values += 1 
                                continue
                            state = stats_agg[group_key]['numeric'][col]
                            state['count'] += 1
                            
                            delta = x - state['mean']
                            state['mean'] += delta / state['count']
                            delta2 = x - state['mean']
                            state['S'] += delta * delta2
                            
                            state['min'] = min(state['min'], x)
                            state['max'] = max(state['max'], x)
                        except (ValueError, TypeError):
                            if col=="age" or value_str in ["half",]:
                                continue
                            else: 
                                pass
                                #print ("unexpected value for ", col, ": ", value_str)
                            continue
                           
                            
                for col in categorical_cols:
                    original_value = row.get(col)
                    cleaned_value = None
                    if col == 'leaf_cycle':
                        cleaned_value = clean_leaf_cycle(original_value)
                    elif col == 'leaf_type': 
                        cleaned_value = clean_leaf_type(original_value)
                    else: 
                        cleaned_value = original_value
                    if cleaned_value:
                        stats_agg[group_key]['categorical'][col][cleaned_value] += 1
                        
        print("Invalid values for circumference rejected:", circumference_rejected_values)
        
        print(f"Saving errors to '{json_outfile}'...")
        with open(json_outfile, 'w', encoding='utf-8') as f:
            json.dump(errors, f, indent=4, sort_keys=False)
        
        
        print("Aggregation complete. Finalizing statistics...")
        results = defaultdict(dict)
        for group_key, collected_data in stats_agg.items():
            for field_name, state in collected_data['numeric'].items():
                if state['count'] > 0:
                    variance = state['S'] / (state['count'] - 1) if state['count'] > 1 else None
                    results[group_key][field_name] = {
                        'count': state['count'], 'min': state['min'], 'mean': round(state['mean'], 2), 
                        'max': state['max'], 'variance': round(variance, 2) if variance is not None else None
                    }
            cat_frequencies = {}
            for field_name, counter in collected_data['categorical'].items():
                total = sum(counter.values())
                if total > 0:
                    cat_frequencies[field_name] = { value: round(count / total, 2) for value, count in counter.items() }
            if cat_frequencies: results[group_key]['categorical_frequencies'] = cat_frequencies
        
            
        print(f"Flattening data for '{csv_outfile}'...")
        flat_data = []; all_csv_headers = set(['total_count', group_by_col])
        for group_key, stats in results.items():
            row = {'total_count': group_totals[group_key], group_by_col: group_key}
            for field, num_stats in stats.items():
                if field == 'categorical_frequencies':
                    for cat_field, freqs in num_stats.items():
                        for val, freq in freqs.items():
                            header_name = f"{cat_field}_{val}"; row[header_name] = freq; all_csv_headers.add(header_name)
                else:
                    for stat_name, val in num_stats.items():
                        header_name = f"{field}_{stat_name}"; row[header_name] = val; all_csv_headers.add(header_name)
            flat_data.append(row)
        sorted_headers = sorted(list(all_csv_headers - {'total_count', group_by_col}))
        final_header = [group_by_col, 'total_count'] + sorted_headers
        print(f"Saving flat analysis to '{csv_outfile}'...")
        with open(csv_outfile, 'w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=final_header)
            writer.writeheader()
            writer.writerows(flat_data)
        print("Done.")
    except FileNotFoundError:
        print(f"Error: Input file '{input_filename}' not found.")
        
def extract_height_circ(input_filename, csv_outfile, numeric_cols):
    flat_data = [];
    with open(input_filename, 'r', newline='', encoding='utf-8') as infile:
        reader = csv.DictReader(infile)
        for row in reader:
            row1 = {}
            for col in numeric_cols:
                value_str = row.get(col)
                
                if col in ["height", "circumference"]:
                    value_str=clean_height_or_circumference(value_str)
                    if col in ["circumference"]:
                        if value_str:
                            x=float(value_str)
                            if x>45:
                                value_str = str(x/100)
                        
                    
                if col == 'age':
                    # we would like to find age, but we need to check two columns.
                    stage, age = clean_age(row.get('age'), row.get('start_date')) 
                    value_str = age
                    
                row1[col]=value_str
                
            if (row1["height"] and row1["circumference"]) or (row1["height"] and row1["age"]):
                flat_data.append(row1)
            
    final_header = ["height", "circumference", "age"]
                        
    with open(csv_outfile, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=final_header)
        writer.writeheader()
        writer.writerows(flat_data)                    
                        
                        
    
    print("done")
    exit(1)
    
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Analyze tree data from a CSV file.")
    parser.add_argument('--group-by', type=str, default='species',
                        help='The column to group the analysis by (e.g., species, genus, leaf_type).')
    args = parser.parse_args()
    
    

    GROUP_BY_COLUMN = args.group_by
    
    script_dir = os.path.dirname(os.path.abspath(__file__))
    
    INPUT_FILE = os.path.join(script_dir, 'data', '10_trees', 'trees.csv')
    
    JSON_OUTPUT_FILE = os.path.join(script_dir, 'data', '10_trees', f'tree_stats_{GROUP_BY_COLUMN}_errors.json')
    CSV_OUTPUT_FILE = os.path.join(script_dir, 'data', '10_trees', f'tree_stats_{GROUP_BY_COLUMN}.csv')
    
    NUMERIC_COLUMNS = ['height', 'circumference', 'age']
    #extract_height_circ(INPUT_FILE, 'data/trees_cleaned.csv', NUMERIC_COLUMNS)
    
    # When grouping by a categorical column, we should not include it in the inner categorical analysis
    CATEGORICAL_COLUMNS = ['leaf_type', 'leaf_cycle' ] # 'species', 'genus' cannot be categorical columns, there are too many values
    if GROUP_BY_COLUMN in CATEGORICAL_COLUMNS:
        CATEGORICAL_COLUMNS.remove(GROUP_BY_COLUMN)

    print(f"Starting analysis grouped by '{GROUP_BY_COLUMN}'...")
    analyze_data(INPUT_FILE, JSON_OUTPUT_FILE, CSV_OUTPUT_FILE, GROUP_BY_COLUMN, NUMERIC_COLUMNS, CATEGORICAL_COLUMNS)
