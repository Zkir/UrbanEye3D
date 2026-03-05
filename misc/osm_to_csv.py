import xml.etree.ElementTree as ET
import csv
import sys
import argparse

def get_tags(elem):
    """Extracts tags from an XML element into a dictionary."""
    tags = {}
    for tag in elem.findall('tag'):
        tags[tag.attrib['k']] = tag.attrib['v']
    return tags

def filter_nodes(xml_file):
    """
    Generator that yields attribute and tag dictionaries for nodes that are
    trees and have additional tags. Clears elements from memory after processing.
    """
    context = ET.iterparse(xml_file, events=('end',))
    for event, elem in context:
        if elem.tag in ('node', 'way', 'relation'):
            tags = get_tags(elem)
            if len(tags) > 0:
                yield (elem.attrib, tags)
            elem.clear()

def main(xml_file, csv_file, columns):
    """
    Converts an OSM XML file to a CSV, extracting a predefined, ordered set of columns.
    """
    print(f"Starting conversion to '{csv_file}'...")
    print(f"Columns: {', '.join(columns)}")
    
    node_count = 0
    with open(csv_file, 'w', newline='', encoding='utf-8') as f:
        # Use extrasaction='ignore' to discard any tags not in our fixed header
        writer = csv.DictWriter(f, fieldnames=columns, extrasaction='ignore')
        writer.writeheader()
        
        for attribs, tags in filter_nodes(xml_file):
            node_count += 1
            
            # Combine node attributes and tags into one dictionary for the writer
            row_data = {
                'id': attribs.get('id'),
                'lat': attribs.get('lat'),
                'lon': attribs.get('lon'),
            }
            row_data.update(tags)
            
            writer.writerow(row_data)

            if node_count % 10000 == 0:
                print(f"Processed {node_count} nodes...", end='\r')

    print(f"\nFinished. Wrote {node_count} records to {csv_file}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Convert OSM XML data to a CSV file.")
    parser.add_argument('--input', type=str, default='data/trees.osm',
                        help='The path to the input OSM XML file.')
    parser.add_argument('--output', type=str, default='data/trees.csv',
                        help='The path to the output CSV file.')
    parser.add_argument('--columns', nargs='+', required=True,
                        help='A space-separated list of columns to include in the CSV.')
    
    args = parser.parse_args()

    main(args.input, args.output, args.columns)
