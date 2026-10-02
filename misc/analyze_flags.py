import os
import json

from  tag_inference import analyze_CSV, build_inference_rules, get_washed_value 


if __name__ == "__main__":
    base_dir = os.path.dirname(__file__)
    
    INPUT_FILE = os.path.join(base_dir, 'data', '30_flags', 'flags.csv')
    output_path = os.path.join(base_dir, 'data', '30_flags', 'flag_rules_pre.json')
    
    if not os.path.exists(INPUT_FILE):
        print(f"Error: source file {INPUT_FILE} not found.")
        exit(1)
        
    predictor_tags = ('flag:name', 'subject', 'subject:wikidata', 'flag:wikidata', 'country', 'operator', 'brand')   #  flag:wikidata is predictor for flag:colour
    target_tags    = ("flag:wikidata", "flag:colour" )
    
    statistic_data = analyze_CSV(INPUT_FILE, predictor_tags, target_tags, None, None )

    rules = build_inference_rules(statistic_data, predictor_tags, target_tags, 0.7)
    
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(rules, f, ensure_ascii=False, indent=4)            
   
    print(f"\nSuccessfully generated {output_path}")
    print(f"Total rules extracted: {rules["meta"]["number of rules"]}")
