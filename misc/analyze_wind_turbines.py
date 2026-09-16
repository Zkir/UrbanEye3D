import csv
import json
from tqdm import tqdm
INPUT_FILE = "data/40_wind_generators/generators.csv"
OUTPUT_FILE = "data/40_wind_generators/turbine_models.json"
OUTPUT_FILE2 = "data/40_wind_generators/turbine_rules.json"

PROB_THRESHOLD = 0.1

import re

pattern = re.compile(r'(\d+(?:\.\d+)?)\s*(MW|kW)?', re.IGNORECASE)

def to_kw(s):
    s=s.replace(",",".")
    m = pattern.search(s)
    if not m:
        return None
    value = float(m.group(1))
    unit = (m.group(2) or "kW").lower()
    value = value * 1000 if unit == 'mw' else value 
    return f"{int(value)} kW"
   

def wash_height(height):
    height = height.replace(" m", "")
    height = height.replace("m", "")
    height = height.replace(",", ".")
    
    try:
        height = round(float(height))
    except:
        #if height:
        #    print(height, "\n")  
        height =  None

    return height

    
def wash_manufaturer(s):
    d={
        "ENERCON":                 "Enercon",
        "Enercon GmbH":            "Enercon",
        "ENERCON GmbH":            "Enercon",
        "NORDEX":                  "Nordex",
        "Nordex Germany GmbH":     "Nordex",
        "SENVION":                 "Senvion",
        "SIEMENS":                 "Siemens Gamesa",
        "Siemens":                 "Siemens Gamesa",
        "Siemens Wind Power A/S":  "Siemens Gamesa",
        "Siemens Gamesa Renewable Energy S.A.": "Siemens Gamesa",
        "Gamesa":                  "Siemens Gamesa",
        "Sgre":                    "Siemens Gamesa",
        "SGRE":                    "Siemens Gamesa",
        "VESTAS":                  "Vestas",
        "Vestas Wind Systems A/S": "Vestas",
      } 
    
    for k,v in d.items():
        if s==k:
            s=v
    
    return s
    
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

def get_washed_value(row, field_name):
    result = None
    if field_name in ("height", "hub:height","height:hub", "rotor:diameter"): 
        result = wash_height(row[field_name])
        
    elif field_name == "generator:output:electricity":
        result = to_kw(row[field_name])
        
    elif field_name == "manufacturer+model": 
        
        manufacturer = wash_manufaturer(row["manufacturer"])
        
        if row["model"]:
            model_name = row["model"]        
        else:
            model_name = row["manufacturer:type"]         
            
        if model_name:
            model_name = (manufacturer + " " + model_name).strip()
        result = model_name    
        
    return result    

def analyze_QQQ(input_file, predictor_tags, target_tags):
    statistic_data = {}
    n=0
    n1=0
    n2=0
    with open(input_file, mode='r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        
        for row in tqdm(reader):
            n += 1 
            row = enrich_row(row)
                
            for predictor_tag in predictor_tags:
                predictor_value = get_washed_value(row, predictor_tag)
                
                if not predictor_value: 
                    continue       
                
                if not validate_row(row):
                    continue
                
                if predictor_tag == "generator:output:electricity": 
                    predictor_value = int(predictor_value.replace(" kW", ""))
                    if predictor_value==0:
                        continue 
                    
                #start of statistics collecting    
                
                if predictor_tag not in statistic_data:
                    statistic_data[predictor_tag] = {}                    
                
                if predictor_value not in statistic_data[predictor_tag]:
                    statistic_data[predictor_tag][predictor_value] = {}
                    statistic_data[predictor_tag][predictor_value]["count"] = 0
                    for target_key in target_tags:
                        statistic_data[predictor_tag][predictor_value][target_key] = {}
                    
                statistic_data[predictor_tag][predictor_value]["count"] += 1 
                
                for target_key in target_tags:
                    target_value = get_washed_value(row, target_key)
                
                    if target_value:
                        if target_value not in statistic_data[predictor_tag][predictor_value][target_key]: 
                            statistic_data[predictor_tag][predictor_value][target_key][target_value] = 0 
                        statistic_data[predictor_tag][predictor_value][target_key][target_value] += 1 
                    
                    
            if row["manufacturer"] and row["model"]: 
                n1 += 1
                
            if (row["height"] or row["height:hub"]) and row["rotor:diameter"]:
                n2 += 1
                
    
    print(f"{n1} objects processed")    
    #print(f"{n1} objects processed, {len(statistic_data)} models found")    
    print(f"{round(n2/n*100,2)}% objects with height/rotor:diameter")    
    print(f"{round(n1/n*100,2)}% objects with model")    
    
    
    
    return statistic_data      
    
def most_common(x):
    best = ''
    rate = None
    for k, v in x.items():
        if not rate or v>rate:
            best = k
            rate = v
        
    return best, rate   

def build_inference_rules(stats):
    rules = {}    
    
    for predictor_tag in stats:
        rules[predictor_tag] = {}
        for predictor_val, targets in stats[predictor_tag].items():
            qq = {}
            qq["count"]= targets["count"]
            qq["targets"]= {}
            
            for target_key, target_values in targets.items():
                if target_key == "count":
                    continue
            
                total = sum(target_values.values())
                if total < 2:
                    continue
                    
                most_common_value, most_common_count = most_common(target_values)
                prob = most_common_count / total
                
                # Only keep high-confidence rules
                if prob >= PROB_THRESHOLD and target_key!=predictor_tag: # we must exclude case when the single predictor is equal target. 
                    qq["targets"][target_key] = {
                        "value": most_common_value,
                        "prob": round(prob, 2),
                        "count": total
                    }
            if qq["count"]>=10 and len(qq["targets"])>0:  
                rules[predictor_tag][predictor_val] = qq   
            
    return rules
    
def create_md_report(rules):
    lines_s=[]
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
    
    with open("data/40_wind_generators/turbine_models.md", 'w', encoding='utf-8') as f:
        f.write("\n".join(lines_s))
    

def main():
    
    predictor_tags = ("manufacturer+model", "generator:output:electricity")   
    target_tags =    ("height", "rotor:diameter", "generator:output:electricity")
    
    statistic_data = analyze_QQQ(INPUT_FILE, predictor_tags, target_tags)
    
    with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
        json.dump(statistic_data, f, ensure_ascii=False, indent=4)
    
    rules = build_inference_rules(statistic_data)
    
    n=0;
    for k, v in rules.items():
        n += len(v)
    rules = {"meta": {
                        "description": "Inference rules of the UrbanEye3D project. Based on global OSM statistics",
                        "copyright": "Based on original map data by Openstreetmap contrubutors, ODBL",
                        "predictor tags": predictor_tags,
                        "target tags": target_tags,
                        "number of rules": n,
                     },
             "rules": rules,
            }
    
    with open(OUTPUT_FILE2, 'w', encoding='utf-8') as f:
        json.dump(rules, f, ensure_ascii=False, indent=4)
        
    create_md_report(rules["rules"])    

main()        