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
for t in data.get("data", [])[:300]:
    key = t['key']
    value = t['value']
    count = t['count_nodes']
    tag = key + '=' + value

    if key.startswith("addr:") or key.startswith("source:") or \
       key in ('source', 'created_by', 'place', 'operator', 'operator:wikidata', 'access', 'leaf_cycle', 'level', 'shop', 'opening_hours', 'takeaway', \
               'hiking', 'wheelchair','fee', 'religion', 'denotation', 'material') or \
       tag in ('public_transport=stop_position', 'noexit=yes', 'highway=traffic_signals', 'highway=stop', 'highway=give_way') or  key in ('traffic_signals', 'traffic_signals:direction') or\
       tag in ('direction=forward', 'direction=backward', 'bus=yes', 'foot=yes', 'bicycle=yes') or \
       key in ('crossing', 'crossing_ref') or  key.startswith('crossing:') or tag in ('highway=crossing') or \
       key in ('entrance') or \
       tag in ('railway=switch', 'railway=level_crossing') or \
       tag in ('barrier=kerb', 'kerb=lowered') or \
       tag in ('amenity=restaurant', 'amenity=place_of_worship', 'amenity=cafe', 'amenity=school', 'amenity=fast_food', 'amenity=pharmacy', 'amenity=toilets', 'amenity=fuel', 'amenity=bank' ,'amenity=parking' ) or \
       tag in ('natural=peak'):  
        continue

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