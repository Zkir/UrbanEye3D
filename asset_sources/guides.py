#!/usr/bin/env python3
"""
Extract horizontal and vertical guides from an Inkscape SVG file.
Supports very large SVG files by using a parser with huge_tree=True.
"""

import sys
from lxml import etree

# Namespaces used by Inkscape
NAMESPACES = {
    'svg': 'http://www.w3.org/2000/svg',
    'sodipodi': 'http://sodipodi.sourceforge.net/DTD/sodipodi-0.dtd',
    'inkscape': 'http://www.inkscape.org/namespaces/inkscape'
}
DEFAULT_LEVEL_HEIGHT = 3

def get_root(svg_path):
  # Create a parser that can handle huge input lines and deep trees
    parser = etree.XMLParser(huge_tree=True, recover=True)  # recover=True tries to fix malformed XML

    try:
        tree = etree.parse(svg_path, parser)
    except etree.XMLSyntaxError as e:
        print(f"Error parsing SVG file: {e}")
        print("If the file is very large, try running with a more permissive parser.")
        return [], []
    except Exception as e:
        print(f"Unexpected error: {e}")
        return [], []

    root = tree.getroot()
    return root

def extract_guides(svg_path):
    """
    Parse an SVG file and return two lists:
        - horizontal_guides: list of y-coordinates (float)
        - vertical_guides:   list of x-coordinates (float)
    """
  
    root = get_root(svg_path)

    # Find the <sodipodi:namedview> element
    namedview = root.find('.//sodipodi:namedview', namespaces=NAMESPACES)
    if namedview is None:
        print("No <sodipodi:namedview> found. Are you sure this is an Inkscape SVG?")
        return [], []

    horizontal_guides = []
    vertical_guides = []

    # Look for <sodipodi:guide> child elements
    for guide in namedview.findall('sodipodi:guide', namespaces=NAMESPACES):
        orientation = guide.get('orientation')
        position = guide.get('position')

        if orientation is None or position is None:
            continue

        orientation = orientation.strip().lower()
        if orientation == "1,0":
            orientation = "vertical"
        elif orientation == "0,-1":
            orientation = "horizontal"    
        else:
            print("unknown ruler orientation:" + orientation )
            exit (1)
        # The 'position' attribute can be a single number or a comma-separated pair.
        # For horizontal guides, it's the y coordinate; for vertical, it's the x coordinate.
        try:
            if orientation == 'horizontal':
                # If position contains a comma, take the first part (should be y)
                y = float(position.split(',')[1])
                horizontal_guides.append(y)
            elif orientation == 'vertical':
                x = float(position.split(',')[0])
                vertical_guides.append(x)
        except ValueError:
            print(f"Warning: could not parse position '{position}' for {orientation} guide")
            continue

    return horizontal_guides, vertical_guides
    
def get_svg_dimensions(svg_path):
    root = get_root(svg_path)
    
    """
    Extract width, height, and viewBox from the root <svg> element.
    Returns a tuple (width, height, viewBox) where each is a string or None if not present.
    """
    # Namespace-aware attribute lookup
    width = root.get('width')
    height = root.get('height')
    view_box = root.get('viewBox') 
    return  width, height, view_box

def main():
    if len(sys.argv) < 2:
        print("Usage: python extract_guides.py <svg_file>")
        sys.exit(1)
        
    width=0    
    height=0

    svg_file = sys.argv[1]
    
    width_src, height_src, view_box =  get_svg_dimensions(svg_file)
    if view_box:
        width =  float(view_box.split(" ")[2])
        height = float(view_box.split(" ")[3])
    else:      
        width = float(width_src)
        height = float(height_src)        

    print("Image dimensions: " + str(round(width)) + ", " + str(round(height)))
    horiz, vert = extract_guides(svg_file)
    if len(horiz)==0 or len(vert)==0:
        print("rulers are not found, but they are required to make slices")
        exit(1)
    
    print("\n=== Sample .fac file  ===")
    print()
    
    
    print("A")
    print("800")
    print("FACADE")
    print(f"TEXTURE {svg_file.replace(".svg",".png")}")
    print("RING 1")
    print("TWO_SIDED 0")
    print("LOD 0.1 50000.0")
    print("  WALL 1 1000")
    
    
    
    horiz.sort() #reverse=True
    vert.sort()
    
    horiz = [0.0] + horiz + [height]
    vert  = [0.0] + vert +  [width]
    
    levels_in_facade = len(horiz)-1
    print (f"\n    # Estimated scale ({levels_in_facade} levels * {DEFAULT_LEVEL_HEIGHT} m per level)")
    estimated_height_m = DEFAULT_LEVEL_HEIGHT * (levels_in_facade)
    estimated_width_m =  width / height * estimated_height_m
    print (f"    SCALE {estimated_width_m:.1f} {estimated_height_m:.1f}")    

    print("\n    # Vertical slices (based on horizontal guides y positions) :")
    for i in range(len(horiz)-1):
        # for some unknown reason we do not need to reverse y coordinates
        # in inkscape, (0,0) is in top left corner, but in file we see (0,0) in BOTTOM left corner.
        # In .fac file we need exactly that.
        if i==0: 
            command = "BOTTOM"
        elif i==len(horiz)-2:
            command = "TOP   "
        else:     
            command = "MIDDLE" 
        print(f"    {command} {horiz[i]/height:.3f} {horiz[i+1]/height:.3f}") #  ---- {horiz[i]}
        
    print("\n    # Horizontal slices (based on vertical guides X positions):")
    for i in range(len(vert)-1):
        
        if i==0: 
            command = "LEFT  "
        elif i==len(vert)-2:
            command = "RIGHT "
        else:     
            command = "CENTER"            
            
        print(f"    {command} {vert[i]/width:.3f} {vert[i+1]/width:.3f}")
   

if __name__ == "__main__":
    main()