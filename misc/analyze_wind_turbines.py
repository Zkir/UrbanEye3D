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
        "ENERCON":   "Enercon",
        "NORDEX":    "Nordex",
        "SENVION":   "Senvion",
        "SIEMENS":   "Siemens",
        "Siemens Wind Power A/S": "Siemens",
        "Siemens Gamesa Renewable Energy S.A.": "Siemens Gamesa",
        "Sgre":      "Siemens Gamesa",
        "SGRE":      "Siemens Gamesa",
        "VESTAS":    "Vestas",
        "Vestas Wind Systems A/S": "Vestas",
      } 
    
    for k,v in d.items():
        if s==k:
            s=v
    
    return s
    


def analyze_QQQ(predictor_tags, target_tags):
    statistic_data = {}
    n=0
    n1=0
    n2=0
    with open(INPUT_FILE, mode='r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        
        for row in tqdm(reader):
            n += 1  
            
            manufacturer = wash_manufaturer(row["manufacturer"])
            
            if row["model"]:
                model_name = row["model"]        
            else:
                model_name = row["manufacturer:type"]    
          
            height         = wash_height(row["height"])
            hub_height     = wash_height(row["hub:height"])
            rotor_diameter = wash_height(row["rotor:diameter"])
            
            power = to_kw(row["generator:output:electricity"])
                            
                
            for predictor_tag in predictor_tags:
                
                    if predictor_tag == "generator:output:electricity": 
                        if not power: #hack!!
                            continue       #hack!!
                        predictor_value = int(power.replace(" kW",""))
                        
                    elif predictor_tag == "manufacturer+model":    
                        if model_name:
                            model_name = (manufacturer + " " + model_name).strip()
                        predictor_value = model_name
                    else:
                        print(predictor_tag)
                        exit(1)
                    
                    if not predictor_value: 
                        continue       
                    
                    
                    if row["generator:output:electricity"] and row["generator:output:electricity"] not in ("yes", "yes/kW", "no", "small_installation") and not power:
                        print("\n")
                        print(f"unexpected power value {row["generator:output:electricity"]} for object {row["id"]}")
                        #exit(1)
                    
                    
                    if not height and hub_height and rotor_diameter :
                        height =  round(hub_height + 0.5 * rotor_diameter)
                        
                    if rotor_diameter and height:    
                        if rotor_diameter>= height:
                            #print (height, hub_height, rotor_diameter)
                            continue
                    
                    if predictor_tag not in statistic_data:
                        statistic_data[predictor_tag] = {}                    
                    
                    if predictor_value not in statistic_data[predictor_tag]:
                        statistic_data[predictor_tag][predictor_value] = {}
                        statistic_data[predictor_tag][predictor_value]["count"] = 0
                        statistic_data[predictor_tag][predictor_value]["height"] = {}
                        #statistic_data[predictor_tag][predictor_value]["hub:height"] = {}
                        statistic_data[predictor_tag][predictor_value]["rotor:diameter"] = {}
                        statistic_data[predictor_tag][predictor_value]["generator:output:electricity"] = {}
                        
                    statistic_data[predictor_tag][predictor_value]["count"] += 1 
                    
                    if height:
                        if height not in statistic_data[predictor_tag][predictor_value]["height"]: 
                            statistic_data[predictor_tag][predictor_value]["height"][height] = 0 
                        statistic_data[predictor_tag][predictor_value]["height"][height] += 1 

                        
                    if rotor_diameter:
                        if rotor_diameter not in statistic_data[predictor_tag][predictor_value]["rotor:diameter"]: 
                            statistic_data[predictor_tag][predictor_value]["rotor:diameter"][rotor_diameter] = 0 
                        statistic_data[predictor_tag][predictor_value]["rotor:diameter"][rotor_diameter] += 1     
                        
                    if power:
                        if power not in statistic_data[predictor_tag][predictor_value]["generator:output:electricity"]: 
                            statistic_data[predictor_tag][predictor_value]["generator:output:electricity"][power] = 0 
                        statistic_data[predictor_tag][predictor_value]["generator:output:electricity"][power] += 1     
                    
                    
            if model_name and manufacturer: 
                n1 += 1
                
            if (row["height"] or row["hub:height"]) and row["rotor:diameter"]:
                n2 += 1
                
    

    with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
        json.dump(statistic_data, f, ensure_ascii=False, indent=4)
    
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
                if prob >= PROB_THRESHOLD:
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
            if len(v["targets"])==0:
                print(predictor_tag, k, v)
                #exit(1)
                
            if "height" in v["targets"] and "rotor:diameter" in v["targets"] and "generator:output:electricity" in v["targets"]:
                lines_s.append(f"{k} | {v["count"]} | {v["targets"]["height"]["value"]} | {v["targets"]["rotor:diameter"]["value"]} | {v["targets"]["generator:output:electricity"]["value"]}")
                n += 1
                
        lines_s.append(f"\nTotally {n} records\n")        
    
    with open("data/40_wind_generators/turbine_models.md", 'w', encoding='utf-8') as f:
        f.write("\n".join(lines_s))
    

def main():
    
    predictor_tags = ("manufacturer+model", "generator:output:electricity" )   
    #predictor_tags = ("generator:output:electricity",)   
    target_tags =    ("height", "rotor_diameter", "generator:output:electricity")
    
    statistic_data = analyze_QQQ(predictor_tags, target_tags)
    
    rules = build_inference_rules(statistic_data)
    with open(OUTPUT_FILE2, 'w', encoding='utf-8') as f:
        json.dump(rules, f, ensure_ascii=False, indent=4)
        
    create_md_report(rules)    

main()        