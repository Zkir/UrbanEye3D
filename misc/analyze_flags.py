import xml.etree.ElementTree as ET
from collections import defaultdict, Counter
import os
import json

def analyze_flags(target_tag, predictor_tags, osm_file, output_json):
    """
    Parses the OSM file, calculates correlations, and saves them to a JSON file.
    """
    # stats[predictor_tag][value] = Counter({color: count})
    stats = defaultdict(lambda: defaultdict(Counter))
    
    count = 0
    
    context = ET.iterparse(osm_file, events=('start', 'end'))
    context = iter(context)
    _, root = next(context)
    
    current_tags = {}
    for event, elem in context:
        if event == 'end' and elem.tag == 'node':
            target_value = current_tags.get(target_tag)
            if target_value:
                for k, v in current_tags.items():
                    if k in predictor_tags:
                        if ";" in k + v +target_value:
                            vs=v.split(";")
                            tvs=target_value.split(";")
                            
                            if len(vs) == len (tvs):
                                for i in range(len(vs)):
                                    stats[k][vs[i].strip()][tvs[i].strip()] += 1                                
                        else:
                            stats[k][v.strip()][target_value.strip()] += 1                                
            
            current_tags = {}
            root.clear()
            count += 1
            if count % 50000 == 0:
                print(f"Processed {count} nodes...")
        elif event == 'start' and elem.tag == 'tag':
            k = elem.get('k')
            v = elem.get('v')
            if k:
                current_tags[k] = v
                    

    # Build the rules dictionary
    rules = defaultdict(dict)
    
    for tag in stats:
        for val, target_values in stats[tag].items():
            total = sum(target_values.values())
            if total < 2:
                continue
                
            most_common_value, most_common_count = target_values.most_common(1)[0]
            prob = most_common_count / total
            
            # Only keep high-confidence rules
            if prob >= 0.7:
                rules[tag][val] = {
                    "value": most_common_value,
                    "prob": round(prob, 2),
                    "count": total
                }

    # Save to JSON
    os.makedirs(os.path.dirname(output_json), exist_ok=True)
    with open(output_json, 'w', encoding='utf-8') as f:
        json.dump(rules, f, ensure_ascii=False, indent=2)
    
    print(f"\nSuccessfully generated {output_json}")
    print(f"Total rules extracted: {sum(len(v) for v in rules.values())}")

if __name__ == "__main__":
    base_dir = os.path.dirname(__file__)
    flags_path = os.path.join(base_dir, 'data', '05_extracts', 'flags.osm')
    output_path1 = os.path.join(base_dir, 'data', '25_flags_output', 'flag_rules_colour.json')
    output_path2 = os.path.join(base_dir, 'data', '25_flags_output', 'flag_rules_wd_pre.json')
    
    if not os.path.exists(flags_path):
        print(f"Error: source file {flags_path} not found.")
        exit(1)
        
    target_tag = 'flag:colour'
    predictor_tags = {'flag:name', 'subject', 'subject:wikidata', 'flag:wikidata', 'country', 'operator', 'brand'}
    analyze_flags(target_tag, predictor_tags, flags_path, output_path1)
    
    target_tag = 'flag:wikidata'
    predictor_tags = {'flag:name', 'subject', 'subject:wikidata', 'country',  'brand'}    
    analyze_flags(target_tag, predictor_tags, flags_path, output_path2)
