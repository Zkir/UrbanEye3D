import requests
import json
import os

url = "https://taginfo.openstreetmap.org/api/4/tags/popular?sortname=count_nodes&sortorder=desc"
params = {
    "filter": "nodes",
    "min_count": 1000,  # только теги, которые есть как минимум на 1000 точках
    "in_wiki_only": True
}

response = requests.get(url, params=params)
data = response.json()

results = []
# Вывод первых 200 самых популярных тегов для точек
node_data = data.get("data", [])[:400]
for t in node_data:
    key = t['key']
    value = t['value']
    count = t['count_nodes']

    results.append({
        "key": key,
        "value": value,
        "count": count
    })
    #print(f"{key}={value}: {count} использований")

output_path = os.path.join(os.path.dirname(__file__), "data/20_tags", "popular_tags.json")
os.makedirs(os.path.dirname(output_path), exist_ok=True)
with open(output_path, 'w', encoding='utf-8') as f:
    json.dump(results, f, ensure_ascii=False, indent=2)
    
    

print(f"\nSaved {len(results)} tags to {output_path}")