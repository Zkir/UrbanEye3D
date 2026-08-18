import xml.etree.ElementTree as ET
from collections import defaultdict, Counter
import os
import json

def analyze_flags(osm_file, output_json):
    """
    Parses the OSM file, calculates correlations, and saves them to a JSON file.
    """
    # stats[predictor_tag][value] = Counter({color: count})
    stats = defaultdict(lambda: defaultdict(Counter))
    
    predictor_tags = {'flag:name', 'subject', 'subject:wikidata', 'flag:wikidata', 'country', 'operator', 'brand'}
    
    count = 0
    try:
        context = ET.iterparse(osm_file, events=('start', 'end'))
        context = iter(context)
        _, root = next(context)
        
        current_tags = {}
        for event, elem in context:
            if event == 'end' and elem.tag == 'node':
                color = current_tags.get('flag:colour')
                if color:
                    for k, v in current_tags.items():
                        if k in predictor_tags:
                            stats[k][v][color] += 1
                
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
                    
    except Exception as e:
        print(f"Error parsing {osm_file}: {e}")

    # Build the rules dictionary
    rules = defaultdict(dict)
    
    for tag in stats:
        for val, colors in stats[tag].items():
            total = sum(colors.values())
            if total < 2:
                continue
                
            most_common_color, most_common_count = colors.most_common(1)[0]
            prob = most_common_count / total
            
            # Only keep high-confidence rules
            if prob >= 0.7:
                rules[tag][val] = {
                    "colour": most_common_color,
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
    output_path = os.path.join(base_dir, 'data', '25_flags_output', 'flag_rules.json')
    
    if os.path.exists(flags_path):
        analyze_flags(flags_path, output_path)
    else:
        print(f"Error: {flags_path} not found.")
