import xml.etree.ElementTree as ET
from collections import defaultdict, Counter
import os
import json
import csv
import requests

WIKIDATA_DIRECTORY   = 'data/04_wikidata'
FLAG_IMAGE_DIRECTORY = 'data/25_flags_output/images'
USER_AGENT = "UrbanEye3D Data pipeline/1.0 (https://github.com/Zkir/UrbanEye3D; zkir@zkir.ru)"

known_flag_instances = ['Q14660',    # flag
                        'Q69506823', # flag design  
                        'Q186516',   # national flag
                        'Q22807280', # flag of a country subdivision
                        'Q21850100', # municipal flag
                        'Q2067046',  # ensign 
                        'Q27077627', # naval ensign
                        'Q4881776',  # civil ensign
                        'Q2915873',  # royal standard
                        'Q602300',   # war flag
                        'Q75790054', # design of the United States flag
                        'Q75792376', # 13-star flag of the United States
                        'Q74051479', # commercial flag
                        'Q83302753', # religious flag 
                        'Q97486724', # flag of a county of the United States (municipal flag)
                        'Q718583',   # ethnic flag 
                        'Q62920376', # diocesan flag 
                        ] 

def get_wikidata(qid):
    headers = {"User-Agent": USER_AGENT}
    wikidata = None
    wdfilename = os.path.join(WIKIDATA_DIRECTORY,  qid + '.json')
    
    if os.path.exists(wdfilename):
        with open(wdfilename,'r', encoding='utf-8') as f:
            wikidata = json.load(f)
    else:
        url = "https://www.wikidata.org/w/api.php?action=wbgetentities&ids="+qid+"&format=json" 
        r = requests.get(url, headers=headers)
        if r.status_code != 200:
            err_str = f'Error {r.status_code} from wikimedia API for url {url} '
            print(err_str)
            return None
            #raise Exception(err_str)
            
        wikidata=json.loads(r.content.decode('utf-8'))
        
        with open(wdfilename, 'w', encoding='utf-8') as f:
            json.dump(wikidata, f, ensure_ascii=False, indent=4)
    if 'entities' in wikidata:
        return wikidata['entities'][qid]
    else:
        return None
        
def get_from_wikimedia_api(url):
    headers = {"User-Agent": USER_AGENT}
    r = requests.get(url, headers=headers)
    
    if r.status_code != 200:
        err_str = f'Error {r.status_code} from wikimedia API for url {url} '
        print(err_str)
        raise Exception(err_str)    
    
    response=json.loads(r.content.decode('utf-8'))
    return(response)        
        
def extract_image_name(wikidata):        
    """
    If there are several images, we need to find the latest (by P582 end time property)
    Also we should prefer SVG.
    """
    start_date = None
    image = None
    if 'claims' in wikidata and 'P18' in wikidata['claims']:
        for image_record in wikidata['claims']['P18']:
            
            if  'datavalue' in image_record['mainsnak']:     
                current_image = image_record['mainsnak']['datavalue']['value']

            # record with preferred rank has highest priority                 
            if 'rank' in image_record and image_record['rank']=='preferred':
                image = current_image
                break                
                
            # if there is no record with prefered rank, let's find the most 'recent' one                     
            if "qualifiers" in image_record and "P580" in image_record["qualifiers"]:    
                current_start_date = image_record["qualifiers"]["P580"][0]["datavalue"]['value']['time']
                    
                if not start_date:
                    start_date = current_start_date
                    image = current_image
                    
                if current_start_date >  start_date:  
                    start_date = current_start_date
                    image = current_image
            else:
                # if there is no start date, we assume that it is at least one of the acceptable modern variants
                image = current_image
                if image.endswith('.svg'):
                    break
                

    return image        
    
def extract_logo_image_name(wikidata):        
    image = None
    if 'claims' in wikidata:
        if 'P154' in wikidata['claims'] and 'datavalue' in wikidata['claims']['P154'][0]['mainsnak']:     
            image = wikidata['claims']['P154'][0]['mainsnak']['datavalue']['value']
    return image            
    
def extract_instance_of(wikidata):        
    value = None
    if 'P31' in wikidata['claims']:
        value = wikidata['claims']['P31'][0]['mainsnak']['datavalue']['value']['id']
    return value     

def extract_instance_of2(wikidata):        
    value = []
    if 'P31' in wikidata['claims']:
        for qq in wikidata['claims']['P31']:
            x = qq['mainsnak']['datavalue']['value']['id']
            value+=[x]
        
    return value  

def extract_label(wikidata):
    value = None
    if 'labels' in wikidata: 
        if  'en' in wikidata['labels']:
            value = wikidata['labels']['en']['value']
    return value        
        
def download_image(url, path_to_save):
    headers = {"User-Agent": USER_AGENT}
    response = requests.get(url, headers=headers)

    if response.status_code == 200:

        with open(path_to_save, 'wb') as file:
            file.write(response.content)
    else:
        print(f"Error during download: {response.status_code}")
        

def save_stats_markdown(stats2, output_file="report.md"):
    """
    Saves statistics to a Markdown file with a table sorted by count descending,
    with links to Wikidata items.
    
    Args:
        stats2 (list): list of [QID, count] pairs
        output_file (str): output .md file path
    """
    # Sort by count (second element) descending
    sorted_data = sorted(stats2, key=lambda x: x[1], reverse=True)

    with open(output_file, 'w', encoding='utf-8') as f:
        f.write("# Flags Statistics\n\n")
        f.write(f"**Total records:** {len(sorted_data)}\n\n")
        f.write("| # | QID | Count | Flag Type | Flag Name | Image |\n")
        f.write("|---|-----|-------|-----------|-----------|---------------|\n")
        for idx, (qid, count, flagtype, flagname) in enumerate(sorted_data, start=1):
            link = f"[{qid}](https://www.wikidata.org/wiki/{qid})"
            image = f"![{qid}](images-png/{qid}.png)"
            f.write(f"| {idx} | {link} | {count:,} | {flagtype} | {flagname} | {image} |\n")
        
        f.write("\n---\n")
        f.write("*Report generated automatically.*")

def save_errors_markdown(stats2, output_file):
    sorted_data = sorted(stats2, key=lambda x: x[1], reverse=True)
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write("# Flags Errors\n\n")
        f.write(f"**Total records:** {len(sorted_data)}\n\n")
        f.write("| # | QID | Count | Name | Message |\n")
        f.write("|---|-----|-------|------|---------|\n")
        for idx, (qid, count, flagname, message) in enumerate(sorted_data, start=1):
            link_wd = f"[{qid}](https://www.wikidata.org/wiki/{qid})"
            link_ti = f"[{count}](https://taginfo.openstreetmap.org/tags/flag%3Awikidata={qid})"
            
            f.write(f"| {idx} | {link_wd} | {link_ti} | {flagname} | {message} |\n")
        
        f.write("\n---\n")
        f.write("*Report generated automatically.*")
        
        
def obtain_flag_wikidata_stats(osm_file):
    count = 0
    stats  = {}
    statsX = defaultdict(lambda: defaultdict(Counter))
    target_tag = "flag:wikidata"
    
    context = ET.iterparse(osm_file, events=('start', 'end'))
    context = iter(context)
    _, root = next(context)
    
    current_tags = {}
    for event, elem in context:
        if event == 'end' and elem.tag == 'node':
            target_values = current_tags.get(target_tag)
            
            if target_values:
                target_values=target_values.split(";")
                for target_value in target_values:
                    if target_value.strip() not in stats:
                        stats[target_value.strip()] = 0
                        
                    stats[target_value.strip()] += 1
                    
                    for k, v in current_tags.items():
                        if k in ['flag:name','flag:type','subject:wikidata']:
                            statsX[target_value.strip()][k][v] += 1

                
            
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
                
    stats2 = {}

    for qid in stats:
        flagname=''
        flagtype=''
        max_count = 0 
        for value, count in statsX[qid]['flag:name'].items():
            if count>max_count:
                max_count = count
                flagname = value
       
        #print(, '\n', statsX[qid]['flag:type'])
        
        max_count = 0 
        for value, count in statsX[qid]['flag:type'].items():
            if count > max_count:
                max_count = count
                flagtype = value
            
        
        stats2[qid] = (qid, stats[qid], flagtype, flagname)    
                
    return stats2             
    

def analyze_flags(osm_file, output_json):
    
    rules_filename = "data/25_flags_output/flag_rules_wd_pre.json"
    rules_output_filename = "data/25_flags_output/flag_rules_wd.json"
    
    os.makedirs(FLAG_IMAGE_DIRECTORY, exist_ok=True)
    
    #read pre-made country codes --> flag QIDS map
    with open('data/25_flags_output/wd_national_flags.csv', 'r', encoding='utf-8') as f:
        national_flags = list(csv.DictReader(f))   
    
    # read rules from json file 
    with open(rules_filename, 'r', encoding='utf-8') as f:
        rules = json.load(f)
    
    
    # patch rules with pre-calculated country codes. 
    
    for row in national_flags:
        country_code = row['isoAlpha2']
        quid =  row['flag'] 
        if quid:
            if country_code in rules['country']:
                if  rules['country'][country_code]["value"] == quid:
                    rules['country'][country_code]["prob"] = 1.0 # if this item is present, probability is 1.0
                else:
                    print(f"flag qids differ for country {country_code}: {rules['country'][country_code]['value']} vs {quid}")
            else:    
                rules['country'][country_code] =  {"value": quid, "prob":1.0, "count":0}
            
    with open(rules_output_filename, 'w', encoding='utf-8') as f:
        json.dump(rules, f, ensure_ascii=False, indent=2)        
    
    
    # Collect actual osm usage statistics for flag:wikidata=*
    stats = obtain_flag_wikidata_stats(osm_file)
    
    #with open(rules_filename+'_stats', 'w', encoding='utf-8') as f:
    #    json.dump(stats, f, ensure_ascii=False, indent=2)
    
    # patch statistics to include all the national flags. 
    for row in national_flags:
        qid = row['flag'] 
        if qid in stats:
            stats[qid] =  (stats[qid][0], stats[qid][1], 'national(iso)', stats[qid][3])
        else:
            stats[quid] = (qid, 0, 'national(iso)', row['flagLabel'])    
        
    
    errors = []
        
    stats2 = []
    
    for qid in stats:
        #if qid in rules and stats[qid]>2:
        count =    stats[qid][1]
        flagtype = stats[qid][2]
        flagname = stats[qid][3]
        
        if count>=5 or flagtype=='national(iso)':
            
            stats2 += [(qid, stats[qid][1], flagtype, flagname)]
            
            image_save_path = os.path.join(FLAG_IMAGE_DIRECTORY, qid +'.svg')
            # Let's obtain images of flags from wikidata
            wikidata=get_wikidata(qid)
            if not wikidata:
                print(f'Strange occurence: wikidata EMPTY: {qid}')
                continue
            instance_qids = extract_instance_of2(wikidata)
            
            is_instance_known = False
            for fi in known_flag_instances:
                if fi in instance_qids:
                    is_instance_known = True
                    break
            if not is_instance_known:  
                error_message = ""
                for instance_qid in instance_qids:
                    instance_data = get_wikidata(instance_qid)
                    instance_label = extract_label(instance_data)               
                    print(f"Unexpected type {instance_qid}('{instance_label}') for flag {qid}")
                    if error_message:
                        error_message += ", "
                    error_message += f"`{instance_qid} ('{instance_label}`')"
                    
                errors += [(qid, stats[qid][1], flagname, f"Unexpected type {error_message}")]
                #exit(1)
            if not os.path.exists(image_save_path):
                
                # wikidata does not allow to download image directly, so we need to do it in several steps.
                # 1. obtain image metadata url
                image_name = extract_image_name(wikidata)
                if not image_name:
                    image_name = extract_logo_image_name(wikidata)
                    
                if not image_name:
                    print(f"unable to obtain image name for {qid}")
                    errors += [(qid, stats[qid][1], flagname, f"unable to obtain image name")]
                    continue
                    
                if not image_name.endswith('.svg'):
                    print(f'Unexpected image format {image_name} for {qid}')
                    errors += [(qid, stats[qid][1], flagname, f'Unexpected image format `{image_name}`')]
                    continue    
                 
                image_metadata_url = "https://commons.wikimedia.org/w/api.php?action=query&format=json" +"&prop=imageinfo&iiprop=url&titles=File:" + image_name
                
                # 2. we need to obtain actual download url via api
                image_metadata = get_from_wikimedia_api(image_metadata_url)
                
                for _, yyy in image_metadata["query"]["pages"].items():
                    break # we just need one image, and expect only one
                    
                if "imageinfo" in yyy:
                    image_download_url = yyy["imageinfo"][0]["url"] 
                else:
                    image_download_url = None
                    print('Wikimedia site did not provided url for the image '+ image_metadata_url)
                    errors += [(qid, stats[qid][1], flagname, f'Wikimedia site did not provided url for the image `{image_metadata_url}`')]
                    
                if image_download_url:    
                    
                    print("DOWNLOAD: "+image_download_url)
                    download_image(image_download_url, image_save_path)

    
   
    # Save reports
        
    save_stats_markdown(stats2,  "data/25_flags_output/missing_wikidata_flags.md")  
    save_errors_markdown(errors, "data/25_flags_output/flags_errors.md")    
    
    print(f"\nDone")
    #print(f"Total rules extracted: {sum(len(v) for v in rules.values())}")

if __name__ == "__main__":
    base_dir = os.path.dirname(__file__)
    flags_path = os.path.join(base_dir, 'data', '05_extracts', 'flags.osm')
    output_path = os.path.join(base_dir, 'data', '25_flags_output', 'flag_wikidata.json')
    
    os.makedirs(os.path.dirname(WIKIDATA_DIRECTORY), exist_ok=True)
    
    
    if not os.path.exists(flags_path):
        print(f"Error: source file {flags_path} not found.")
        exit(1)
        

    analyze_flags(flags_path, output_path)
    

