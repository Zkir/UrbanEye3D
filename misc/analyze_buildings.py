import csv
import json
import math
import re
import argparse
from startdate import parseStartDateValue
from collections import defaultdict, Counter
from datawash import clean_height_or_circumference, clean_levels, clean_roof_shape    

def analyze_data(input_filename, json_outfile, csv_outfile, defaults_outfile, group_by_col, numeric_cols, categorical_cols):
    print(f"Reading data from '{input_filename}' in a single pass...")
    stats_agg = defaultdict(lambda: {
        'numeric': defaultdict(lambda: {'count': 0, 'mean': 0.0, 'S': 0.0, 'min': float('inf'), 'max': float('-inf')}),
        'categorical': defaultdict(Counter)
    })
    group_totals = Counter()
    try:
        with open(input_filename, 'r', newline='', encoding='utf-8') as infile:
            reader = csv.DictReader(infile)
            for row in reader:
                summary_key = '*'
                all_keys = [summary_key]
                group_key = row.get(group_by_col)
                if group_key:
                    all_keys.append(group_key)

                # Update totals for all relevant keys
                for key in all_keys:
                    group_totals[key] += 1

                # Process numeric columns
                for col in numeric_cols:
                    value_str = row.get(col)
                    
                    if col in ["height", "roof:height"]:
                        cleaned_str = clean_height_or_circumference(value_str)
                    elif col in ["levels", "building:levels", "roof:levels"]:
                        cleaned_str = clean_levels(value_str)
                    else:
                        cleaned_str = value_str
                    
                    if cleaned_str:
                        try:
                            x = float(cleaned_str)

                            if not math.isfinite(x) or x < 0:
                                continue  # Skip non-finite and negative values for all calculations

                            if (col == "height" and x > 900) or (col == "building:levels" and x > 175):
                                print(f"!!! unexpected value for {col}: {x}, id={row.get('id')}, original={row.get(col)}")
                                continue # Skip outliers for all calculations

                            # Aggregate for all keys
                            for key in all_keys:
                                state = stats_agg[key]['numeric'][col]
                                state['count'] += 1
                                delta = x - state['mean']
                                state['mean'] += delta / state['count']
                                delta2 = x - state['mean']
                                state['S'] += delta * delta2
                                state['min'] = min(state['min'], x)
                                state['max'] = max(state['max'], x)
                        except (ValueError, TypeError):
                            continue
                
                # Process categorical columns
                for col in categorical_cols:
                    original_value = row.get(col)
                    cleaned_value = None
                    if col == "roof:shape":
                        cleaned_value = clean_roof_shape(original_value)
                    else:
                        cleaned_value = original_value
                    
                    if cleaned_value:
                        for key in all_keys:
                            stats_agg[key]['categorical'][col][cleaned_value] += 1
                        
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
        
        print(f"Saving defaults config to '{defaults_outfile}'...")
        with open(defaults_outfile, 'w', encoding='utf-8') as f:
            # Write header
            total_buildings = group_totals.get('*', 0)
            f.write("# This file was generated automatically from the OSM data (planet.osm.pbf)\n")
            f.write(f"# {total_buildings} building(s) was processed.\n\n")

            # Sort group keys by total_count descending
            sorted_groups = sorted(group_totals.items(), key=lambda item: item[1], reverse=True)

            for group_key, total_count in sorted_groups:
                if total_count < 100 and group_key != '*':
                    break # Since the list is sorted, no need to check further

                data = results[group_key]
                f.write(f"{group_by_col}={group_key}\n")
                
                # 1. Collect default values into a dictionary
                defaults_dict = {}
                for col_name in numeric_cols:
                    if col_name in data and data[col_name].get('mean') is not None:
                        defaults_dict[col_name] = data[col_name]['mean']
                
                if 'categorical_frequencies' in data:
                    for col_name in categorical_cols:
                        if col_name in data['categorical_frequencies']:
                            freqs = data['categorical_frequencies'][col_name]
                            if freqs:
                                most_probable_val, freq = max(freqs.items(), key=lambda item: item[1])
                                if freq > 0.4:
                                    defaults_dict[col_name] = most_probable_val
                
                # 2. Apply rounding rules
                for col, value in defaults_dict.items():
                    if col == 'height' or col.endswith(':height'):
                        defaults_dict[col] = round(value, 1)
                    elif col.endswith(':levels'):
                        defaults_dict[col] = int(round(value, 0))

                # 3. Apply correction logic on rounded values
                if defaults_dict.get('roof:shape') == 'flat':
                    if 'roof:levels' in defaults_dict: defaults_dict['roof:levels'] = 0
                    if 'roof:height' in defaults_dict: defaults_dict['roof:height'] = 0.0

                if all(k in defaults_dict for k in ('building:levels', 'roof:levels', 'height')):
                    building_levels = defaults_dict['building:levels']
                    roof_levels = defaults_dict['roof:levels']
                    height = defaults_dict['height']
                    if (building_levels + roof_levels) * 3 > height + 1 and building_levels >= 2:
                        defaults_dict['building:levels'] -= 1
                
                # 4. Format and write the final output string
                output_parts = []
                for col_name in numeric_cols + categorical_cols:
                    if col_name in defaults_dict:
                        value = defaults_dict[col_name]
                        output_parts.append(f"{col_name}={value}")
                
                if output_parts:
                    f.write(f"    {', '.join(output_parts)}\n")

        print(f"Saving analysis to '{json_outfile}'...")
        with open(json_outfile, 'w', encoding='utf-8') as f: json.dump(results, f, indent=4, sort_keys=True)
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

    
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Analyze building data from a CSV file.")
    parser.add_argument('--group-by', type=str, default='building',
                        help='The column to group the analysis by (e.g., building type).')
    parser.add_argument('--defaults-output', type=str, default='data/building_defaults.cfg',
                        help='The path to the output defaults config file.')
    args = parser.parse_args()
    

    GROUP_BY_COLUMN = args.group_by
    
    INPUT_FILE = 'data/buildings.csv'
    JSON_OUTPUT_FILE = f'data/{GROUP_BY_COLUMN}_analysis_full.json'
    CSV_OUTPUT_FILE = f'data/{GROUP_BY_COLUMN}_analysis_full.csv'
    DEFAULTS_OUTPUT_FILE = args.defaults_output
    
    NUMERIC_COLUMNS = ['height', 'building:levels', 'roof:height', 'roof:levels' ]
    
    # When grouping by a categorical column, we should not include it in the inner categorical analysis
    CATEGORICAL_COLUMNS = ['roof:shape' ] # 
    if GROUP_BY_COLUMN in CATEGORICAL_COLUMNS:
        CATEGORICAL_COLUMNS.remove(GROUP_BY_COLUMN)

    print(f"Starting analysis grouped by '{GROUP_BY_COLUMN}'...")
    analyze_data(INPUT_FILE, JSON_OUTPUT_FILE, CSV_OUTPUT_FILE, DEFAULTS_OUTPUT_FILE, GROUP_BY_COLUMN, NUMERIC_COLUMNS, CATEGORICAL_COLUMNS)
