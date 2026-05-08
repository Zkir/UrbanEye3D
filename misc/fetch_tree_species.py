import csv
import os
import requests
from bs4 import BeautifulSoup

def fetch_and_parse():
    url = "https://wiki.openstreetmap.org/wiki/Tag:natural%3Dtree/List_of_Species"
    script_dir = os.path.dirname(os.path.abspath(__file__))
    output_path = os.path.join(script_dir, "data", "10_trees", "tree_species_curated.csv")
    
    print(f"Fetching {url}...")
    try:
        response = requests.get(url, timeout=30)
        response.raise_for_status()
    except Exception as e:
        print(f"Error fetching the page: {e}")
        return

    print("Parsing table...")
    soup = BeautifulSoup(response.text, 'html.parser')
    
    # The wiki page usually has multiple tables, but the species list is the main one
    # It has a class 'wikitable'
    table = soup.find('table', class_='wikitable')
    if not table:
        print("Could not find the wikitable on the page.")
        return

    headers = []
    for th in table.find_all('th'):
        headers.append(th.text.strip().lower())

    print(f"Detected headers: {headers}")

    # Expected columns we want to map from wiki headers (lower case) to CSV headers
    header_mapping = {
        "species": "species",
        "genus": "genus",
        "species:wikidata": "species:wikidata",
        "leaf_cycle=*": "leaf_cycle",
        "leaf_type=*": "leaf_type"
    }

    rows_data = []
    for tr in table.find_all('tr')[1:]: # Skip header row
        cells = tr.find_all(['td', 'th'])
        if not cells:
            continue
            
        row_dict = {}
        for i, cell in enumerate(cells):
            if i < len(headers):
                raw_header = headers[i]
                csv_header = header_mapping.get(raw_header)
                if not csv_header:
                    continue

                # For leaf_cycle and leaf_type, try to find icons or specific text
                # Icons often have alt/title text like "deciduous" or "needleleaved"
                text = ""
                img = cell.find('img')
                if img and img.get('title'):
                    text = img.get('title').strip()
                elif img and img.get('alt'):
                    text = img.get('alt').strip()
                else:
                    text = cell.get_text(separator=" ").strip()
                
                # Cleanup: remove bracketed references like [1]
                import re
                text = re.sub(r'\[\d+\]', '', text)
                text = ' '.join(text.split()) 
                
                row_dict[csv_header] = text
        
        rows_data.append(row_dict)

    print(f"Found {len(rows_data)} species.")

    # Ensure output directory exists
    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    target_columns = ["species", "genus", "species:wikidata", "leaf_cycle", "leaf_type"]
    
    with open(output_path, 'w', newline='', encoding='utf-8') as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames=target_columns, extrasaction='ignore')
        writer.writeheader()
        for row in rows_data:
            writer.writerow(row)

    print(f"Successfully saved to {output_path}")

if __name__ == "__main__":
    fetch_and_parse()
