import json
import os

def load_json(path):
    if not os.path.exists(path):
        return None
    with open(path, 'r', encoding='utf-8') as f:
        return json.load(f)

def find_missing_tags():
    # Paths relative to the script location (misc/)
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    
    taginfo_path = os.path.join(project_root, "docs", "taginfo.json")
    popular_tags_path = os.path.join(script_dir, "data/20_tags", "popular_tags.json")
    output_report_path = os.path.join(project_root, "docs", "dev", "popular_missing_tags.md")

    taginfo_data = load_json(taginfo_path)
    popular_tags = load_json(popular_tags_path)

    if not taginfo_data or not popular_tags:
        print("Error: Could not load input files.")
        return

    supported_specific = set()
    supported_wildcard = set()

    for tag in taginfo_data.get("tags", []):
        key = tag.get("key")
        value = tag.get("value")
        if value:
            supported_specific.add(f"{key}={value}")
        else:
            supported_wildcard.add(key)

    missing_tags = []
    for p_tag in popular_tags:
        key = p_tag['key']
        value = p_tag['value']
        tag_str = f"{key}={value}"
        
        # Check if the tag is supported either specifically or by wildcard key
        if tag_str in supported_specific:
            continue
        #if key in supported_wildcard:
        #    continue
            
        missing_tags.append(p_tag)

    # Generate Markdown
    lines = [
        "# Popular tags NOT implemented in Urban Eye 3D",
        "",
        "This report lists popular OSM tags (for nodes) that are currently not documented as supported in `taginfo.json`.",
        "These are candidates for future implementation.",
        "",
        "| Object | Additional tags | Notes | Count | Implemented |",
        "| :--- | :--- | :--- | :--- | :--- |"
    ]

    for m_tag in missing_tags:
        key = m_tag['key']
        value = m_tag['value']
        count = m_tag['count']
        # Wiki links for tags usually follow the Tag:key=value pattern
        link = f"[{key}={value}](https://wiki.openstreetmap.org/wiki/Tag:{key}%3D{value})"
        lines.append(f"| {link} | | | {count} | |")

    with open(output_report_path, 'w', encoding='utf-8') as f:
        f.write("\n".join(lines))

    print(f"Report generated: {output_report_path}")
    print(f"Total missing popular tags found: {len(missing_tags)}")

if __name__ == "__main__":
    find_missing_tags()
