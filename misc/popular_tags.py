import requests
import json
import os
import time

def get_combinations(session, key, value):
    url = "https://taginfo.openstreetmap.org/api/4/tag/combinations"
    params = {
        "key": key,
        "value": value,
        "filter": "nodes",
        "sortname": "together_count",
        "sortorder": "desc",
        "rp": 20 
    }
    try:
        response = session.get(url, params=params, timeout=10)
        data = response.json()
        combs = data.get("data", [])
        
        filtered_combs = []
        for c in combs:
            ok = c.get('other_key')
            ov = c.get('other_value')
            if ok and ov:
                # Basic technical filter, specific project filtering will happen in find_missing_tags.py
                if ok in ('source', 'created_by', 'import', 'note', 'fixme'):
                    continue
                
                filtered_combs.append(f"{ok}={ov}")
            
            if len(filtered_combs) >= 20: # Fetch more for find_missing_tags.py
                break
        return filtered_combs
    except Exception as e:
        print(f"Error fetching combinations for {key}={value}: {e}")
        return []

url = "https://taginfo.openstreetmap.org/api/4/tags/popular?sortname=count_nodes&sortorder=desc"
params = {
    "filter": "nodes",
    "min_count": 1000,
    "in_wiki_only": True
}

print("Fetching popular tags...")
response = requests.get(url, params=params)
data = response.json()

results = []
node_data = data.get("data", [])[:500]
total = len(node_data)

session = requests.Session()

print(f"Fetching combinations for {total} tags...")
for i, t in enumerate(node_data):
    key = t['key']
    value = t['value']
    count = t['count_nodes']

    if (i + 1) % 10 == 0:
        print(f"Processing {i+1}/{total}...")

    combs = get_combinations(session, key, value)

    results.append({
        "key": key,
        "value": value,
        "count": count,
        "combinations": combs
    })
    
    # Be polite to the Taginfo server
    time.sleep(0.1)

output_path = os.path.join(os.path.dirname(__file__), "data/20_tags", "popular_tags.json")
os.makedirs(os.path.dirname(output_path), exist_ok=True)
with open(output_path, 'w', encoding='utf-8') as f:
    json.dump(results, f, ensure_ascii=False, indent=2)

print(f"\nSaved {len(results)} tags with combinations to {output_path}")
