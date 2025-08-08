import re
import json
import os
from jsonschema import validate, ValidationError
from datetime import datetime, timezone

tag_descriptions = {
    "building": "The main tag for identifying a building outline.",
    "building:part": "Identifies a part of a building, which is rendered as a separate 3D element.",
    "building:colour": "Specifies the color of the building's walls.",
    "building:material": "Specifies the material of the building's walls. Used for texturing and shading.",
    "building:height": "An alternative tag for the total height of the building, including the roof, in meters.",
    "building:levels": "The number of floors (levels) in the main part of the building. Used to calculate height if not specified explicitly.",
    "building:min_level": "The number of floors to offset the building from the ground. Used to calculate min_height if not specified explicitly.",
    "height": "The total height of the building, including the roof, in meters.",
    "min_height": "The height of the ground floor of the building from the ground, in meters. Used to model buildings on stilts or slopes.",
    "roof:colour": "Specifies the color of the roof.",
    "roof:direction": "Specifies the direction or orientation of the roof, typically in degrees. Used for directional roof shapes like 'skillion'.",
    "roof:height": "The height of the roof section of the building, in meters.",
    "roof:levels": "The number of floors (levels) within the roof structure. Used to calculate roof:height if not specified explicitly.",
    "roof:material": "Specifies the material of the roof. Used for texturing and shading.",
    "roof:orientation": "Specifies the orientation of the roof ridge, typically 'along' or 'across' the longer axis of the building.",
    "roof:shape": "Defines the shape of the roof, which determines the geometry generation algorithm."
}

tags = set()
pattern = re.compile(r'(?:getTagStr|getTagD|get|hasKey)\s*\(\s*"([a-zA-Z0-9:_.-]+)"\s*[,)]')
pattern2 = re.compile(r'inheritableKeys\s*=\s*Arrays.asList\(([^)]+)\)')

for root, _, files in os.walk('.'):
    for file in files:
        if file.endswith('.java'):
            file_path = os.path.join(root, file)
            try:
                with open(file_path, 'r', encoding='utf-8') as f:
                    content = f.read()
                    found_tags1 = pattern.findall(content)
                    tags.update(found_tags1)

                    found_tags2 = pattern2.findall(content)
                    for block in found_tags2:
                        string_literals = re.findall(r'"([a-zA-Z0-9:_.-]+)"', block)
                        tags.update(string_literals)

            except Exception as e:
                print(f"Error processing file {file_path}: {e}")

output_data = {
    "data_format": 1,
    "data_updated": datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ'),
    "project": {
        "name": "Urban Eye 3D",
        "description": "JOSM plugin for 3D visualization of buildings",
        "project_url": "https://github.com/Zkir/UrbanEye3D",
        "doc_url": "https://github.com/Zkir/UrbanEye3D/blob/master/README.md",
        "icon_url": "https://raw.githubusercontent.com/Zkir/UrbanEye3D/refs/heads/master/images/dialogs/urbaneye3d.svg",
        "contact_name": "Zkir",
        "contact_email": "zkir@zkir.ru"
    },
    "tags": []
}

#Add found tags and add descriptions
for tag in sorted(list(tags)):
    tag_object = {"key": tag}
    if tag in tag_descriptions:
        tag_object["description"] = tag_descriptions[tag]
    else:
        raise Exception(f"description for tag {tag} is missing")
    output_data["tags"].append(tag_object)

#validate schema    
with open('docs/taginfo-project-schema.json', 'r', encoding='utf-8') as schema_file:
    schema = json.load(schema_file)
    
try:
    validate(instance=output_data, schema=schema)
    print("✅ JSON matches the schema")
    # save to file
    with open('docs/taginfo.json', 'w') as f:
        json.dump(output_data, f, indent=4)

    print("✅ Generated taginfo.json with descriptions.")
except ValidationError as e:
    print(f"❌ Schecma validation error: {e.message}")
    


