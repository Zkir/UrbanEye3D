import json
from datetime import date

INPUT_FILE = "data/40_wind_generators/generators.csv"
OUTPUT_FILE = "data/40_wind_generators/turbine_models.json"
OUTPUT_FILE2 = "data/40_wind_generators/turbine_rules.json"

PROB_THRESHOLD = 0.1



from  tag_inference import analyze_CSV, build_inference_rules, get_washed_value 

    
def enrich_row(row):
    height =  get_washed_value(row, "height")
    hub_height = get_washed_value(row, "height:hub")
    rotor_diameter = get_washed_value(row, "rotor:diameter")
    if not height and hub_height and rotor_diameter:
        row["height"] = str(round(hub_height  + 0.5 * rotor_diameter))
    return row    
    
def validate_row(row):
    height = get_washed_value(row, "height")
    rotor_diameter = get_washed_value(row, "rotor:diameter")
    
    if rotor_diameter and height:  
        if rotor_diameter>= height:
            return False     
    return True
    
def create_md_report(rules):
    lines_s=[]
    lines_s.append("# Wind Turbine Models")
    lines_s.append("This report lists all wind turbine models known to the UrbanEye3D plugin. ")       
    lines_s.append("For these models, the plugin automatically determines values for the `height` and `rotor:diameter` tags  based on the data below if these tags are missing in OSM.")     
    lines_s.append("")     
    lines_s.append("This report is created automatically by the `misc/analyze_wind_turbines.py` script, together with the inference rules. Do not alter it manually.")     
    
    for predictor_tag, predictor_val in rules.items():
        lines_s.append(f"## {predictor_tag}")
        lines_s.append("| Value | Count | Height | Rotor Diameter | Power | ")
        lines_s.append("| :--- | :--- | :--- | :--- |:--- | ")
        
        n=0

        for k, v in sorted(predictor_val.items()):
                      
            if "generator:output:electricity" in v["targets"]:
                power = v["targets"]["generator:output:electricity"]["value"]
            elif predictor_tag == "generator:output:electricity":
                power = f"{k} kW"
            else: 
                power = None    
                
            if "height" in v["targets"] and "rotor:diameter" in v["targets"] and power:
                lines_s.append(f"{k} | {v["count"]} | {v["targets"]["height"]["value"]} | {v["targets"]["rotor:diameter"]["value"]} | {power}")
                n += 1
                
        lines_s.append(f"\nTotally {n} records\n") 
        
    lines_s.append("---")
    lines_s.append(f"Created {date.today().strftime("%Y-%m-%d")}\n")
    lines_s.append("The Urban Eye is watching you!")
    
    with open("data/40_wind_generators/turbine_models.md", 'w', encoding='utf-8') as f:
        f.write("\n".join(lines_s))
    

def main():
    
    predictor_tags = ("manufacturer+model", "generator:output:electricity")   
    target_tags =    ("height", "rotor:diameter", "generator:output:electricity")
    
    statistic_data = analyze_CSV(INPUT_FILE, predictor_tags, target_tags, enrich_row, validate_row )
    
    with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
        json.dump(statistic_data, f, ensure_ascii=False, indent=4)
    
    rules = build_inference_rules(statistic_data, predictor_tags, target_tags, PROB_THRESHOLD)
    
    #TODO: return this to functioning
    """
    n=0
    n1=0
    n2=0
    if row["manufacturer"] and row["model"]: 
                n1 += 1
                
            if (row["height"] or row["height:hub"]) and row["rotor:diameter"]:
                n2 += 1
                
    
    print(f"{n1} objects processed")    
    #print(f"{n1} objects processed, {len(statistic_data)} models found")    
    print(f"{round(n2/n*100,2)}% objects with height/rotor:diameter")    
    print(f"{round(n1/n*100,2)}% objects with model")    
    """
    
    with open(OUTPUT_FILE2, 'w', encoding='utf-8') as f:
        json.dump(rules, f, ensure_ascii=False, indent=4)
        
    create_md_report(rules["rules"])    

main()        