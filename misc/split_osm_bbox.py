import os
import xml.etree.ElementTree as ET
import argparse

def split_osm_bbox(input_file, outdir, max_size):
    print(f"Reading {input_file}...")
    try:
        tree = ET.parse(input_file)
        root = tree.getroot()
    except Exception as e:
        print(f"Error parsing XML: {e}")
        return

    nodes = []
    for node in root.findall('node'):
        lat = float(node.get('lat'))
        lon = float(node.get('lon'))
        nodes.append({
            'lat': lat,
            'lon': lon,
            'element': node
        })

    if not nodes:
        print("No nodes found in the input file.")
        return

    print(f"Total nodes: {len(nodes)}")

    # Calculate initial global bounding box based on actual points
    min_lat = min(n['lat'] for n in nodes)
    max_lat = max(n['lat'] for n in nodes)
    min_lon = min(n['lon'] for n in nodes)
    max_lon = max(n['lon'] for n in nodes)

    # Use a slightly expanded box to avoid edge cases with floats
    bbox = (min_lat - 0.000001, max_lat + 0.000001, min_lon - 0.000001, max_lon + 0.000001)

    if not os.path.exists(outdir):
        os.makedirs(outdir)

    base_name = os.path.splitext(os.path.basename(input_file))[0]
    
    def process_quadrant(current_nodes, current_bbox, quadkey):
        min_lt, max_lt, min_ln, max_ln = current_bbox
        width = max_ln - min_ln
        height = max_lt - min_lt

        # Split condition: if width or height > max_size (e.g., 1 degree)
        if width <= max_size and height <= max_size:
            # Save these nodes
            output_path = os.path.join(outdir, f"{base_name}_bbox_q{quadkey}.osm")
            save_nodes(current_nodes, output_path)
            print(f"Saved {len(current_nodes)} nodes to {output_path} (Size: {width:.4f}x{height:.4f})")
            return

        # Split
        mid_lt = (min_lt + max_lt) / 2
        mid_ln = (min_ln + max_ln) / 2

        # 0: NW, 1: NE, 2: SW, 3: SE
        quadrants = [
            ([], (mid_lt, max_lt, min_ln, mid_ln)), # 0: NW
            ([], (mid_lt, max_lt, mid_ln, max_ln)), # 1: NE
            ([], (min_lt, mid_lt, min_ln, mid_ln)), # 2: SW
            ([], (min_lt, mid_lt, mid_ln, max_ln))  # 3: SE
        ]

        for n in current_nodes:
            lt, ln = n['lat'], n['lon']
            if lt >= mid_lt:
                if ln < mid_ln:
                    quadrants[0][0].append(n)
                else:
                    quadrants[1][0].append(n)
            else:
                if ln < mid_ln:
                    quadrants[2][0].append(n)
                else:
                    quadrants[3][0].append(n)

        for i, (q_nodes, q_bbox) in enumerate(quadrants):
            if q_nodes:
                process_quadrant(q_nodes, q_bbox, quadkey + str(i))

    process_quadrant(nodes, bbox, "")

def save_nodes(nodes, output_path):
    new_root = ET.Element('osm', version='0.6', generator='split_osm_bbox.py')
    for n in nodes:
        new_root.append(n['element'])
    
    new_tree = ET.ElementTree(new_root)
    ET.indent(new_tree, space="  ", level=0)
    new_tree.write(output_path, encoding='UTF-8', xml_declaration=True)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Split OSM nodes file using BBox size.")
    parser.add_argument("--input", required=True, help="Input .osm file path")
    parser.add_argument("--outdir", default="data/17_trees_fixes_split", help="Output directory")
    parser.add_argument("-s", "--size", type=float, default=1.0, help="Max bbox side in degrees")
    
    args = parser.parse_args()
    
    split_osm_bbox(args.input, args.outdir, args.size)
