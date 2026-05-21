import math, re

PATTERN_VALUE_WITH_UNIT = r'^(-?\d+(?:\.\d+)?)\s*([a-zA-Z]+|["\']|см|м)$'
PATTERN_VALUE_RANGE = r'^(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)$'


def clean_height_or_circumference(value_str: str) -> float | None: 
    
    if value_str in ["less than 2m", "less than 2 m"]:
        value_str="1.5"
    if value_str=="greater than 2m and less than 3m":
        value_str="2.5"
    if value_str=="greater than 3m":
        value_str="3.25"
    if value_str=="< 3":
        value_str="2.75"
    if value_str==">24":
        value_str="25"
    if "," in value_str:
        value_str=value_str.split(",")[0]
    if ";" in value_str:
        value_str=value_str.split(";")[0]
    if value_str and value_str[0]=="~": value_str=value_str[1:]
    
    matches = re.findall(PATTERN_VALUE_WITH_UNIT, value_str)
    if matches:
        value = matches[0][0]
        unit = matches[0][1].lower()
        if unit in ["m", "meter", "metros", "metres", "meters", "mt", "mts", "metri", "м"]:
            value_str=value
        elif unit in ["cm", "см"]:
            value_str=str(float(value) * 0.01)
        elif unit in ["ft", "foot", "feet", "'"]:
            value_str=str(float(value) * 0.3048)
        elif unit in ["in", "inch", "inches", "''", "\""]:
            value_str=str(float(value) * 0.0254)
        elif unit in ["estimation", "et", "ca", "t", "med", "jacaranda", "o", "mueller", "s", "ss", "pedro", "rr", "arecaceae", "w", "q", "qq", "storeys"]:
            return None
        else: 
            print("unknown unit:", value, unit)
            #exit(1)
    matches = re.findall(PATTERN_VALUE_RANGE, str(value_str))
    if matches:
        value1 = float(matches[0][0])
        value2 = float(matches[0][1])
        value_str=str(((value1) + float(value2))/2)    
        
    try:    
        value = float(value_str)
        if value<0:
            raise ValueError("Height value cannot be negative")
            
        if not math.isfinite(value):
            raise ValueError("Height value cannot be infinite (NaN)")
            
    except (ValueError): 
        #print ("unexpected value: " , value_str)        
        return None
        
        
    return str(value)    

def clean_levels(value_str: str) -> str|None:
    try:    
        value = float(value_str) 
        if value<0: 
            raise ValueError("Levels value cannot be negative")
            
        if not math.isfinite(value):
            raise ValueError("Levels value cannot be infinite (NaN)")
            
    except (ValueError): 
        #print ("unexpected value for levels: " , value_str)        
        return None
    return  str(value)   
    
def clean_roof_shape(value_str: str) -> str|None:  
    known_roof_shapes=("flat", "gabled", "hipped", "pyramidal", "skillion", "round", "gambrel", "dome", "half-hipped", "mansard", "saltbox")
    if not value_str:
        return None
        
    if value_str=="cone":
        value_str="pyramidal"
        
    if value_str=="pitched":
        value_str="gabled"    
        
    if value_str in known_roof_shapes:
        return value_str 
    else:    
        #print (value_str)
        return "other" 
        
def to_binomial1(name):
    """
    Extracts the binomial core (Genus species) or trinomial for hybrids (Genus × species).
    Used for fuzzy matching against the curated species list.
    """
    name = name.strip()
    if not name:
        return ""
    # Normalize hybrid symbol 'x' -> '×' (only if it's a separate symbol, not part of a word)
    name = re.sub(r'(^|\s)x(\s|$)', r'\1×\2', name)
    name = re.sub(r'(^|\s)X(\s|$)', r'\1×\2', name)
    # Ensure spaces around '×'
    name = re.sub(r'\s*×\s*', ' × ', name)
    # Clean up multiple spaces
    name = re.sub(r'\s+', ' ', name).strip()
    
    parts = name.split(' ')
    
    if not parts:
        return ""
    
    # 1. Hybrid starts with ×: × Genus species
    if parts[0] == '×' and len(parts) >= 3:
        return " ".join(parts[:3])
    # 2. Hybrid in middle: Genus × species
    if len(parts) >= 3 and parts[1] == '×':
        return " ".join(parts[:3])
    # 3. Standard binomial: Genus species
    if len(parts) >= 2:
        return " ".join(parts[:2])
        
    #one-worder! Return as is
    return name      
    
def to_binomial(name):
    """
    Normalizes name to binomial format (Genus species) and standardizes formatting.
    Example: 'Tilia cordata green spire' -> 'tilia cordata'
    Example: 'Platanus x acerifolia' -> 'platanus × acerifolia'
    """
    if not name:
        return ""
    
    trimmed = name.strip()
    # Check for cultivar format: Genus 'Cultivar Name'
    if re.match(r"^[A-Z][a-z]+\s+'[A-Z].*'$", trimmed):
        return trimmed

    # Remove quotes, extra spaces, and convert to lowercase
    name = name.replace('"', '').strip().lower()
    
    # Normalize hybrid symbol 'x' -> '×' (only if it's a separate symbol, not part of a word)
    name = re.sub(r'(^|\s)x(\s|$)', r'\1×\2', name)
    name = re.sub(r'(^|\s)X(\s|$)', r'\1×\2', name)
    # Ensure spaces around '×'
    name = re.sub(r'\s*×\s*', ' × ', name)
    # Clean up multiple spaces
    name = re.sub(r'\s+', ' ', name).strip()
    
    parts = name.split(' ')
    if len(parts) < 2:
        return name # Too short, return as is
    
    # Binomial nomenclature rule:
    # 1. If starts with '×', take 3 parts: × Genus species
    if parts[0] == '×':
        return " ".join(parts[:3])
    # 2. If the second part is '×', take 3 parts: Genus × species
    if len(parts) > 2 and parts[1] == '×':
        return " ".join(parts[:3])
    # 3. Otherwise, take first 2 parts: Genus species
    return " ".join(parts[:2])    
    
def is_cultivar(name):
    """Checks if the species name is in 'Genus 'Cultivar'' format."""
    return bool(re.match(r"^[A-Z][a-z]+\s+'[A-Z].*'$", name.strip()))    


if __name__ == "__main__":
    
    assert clean_roof_shape("gabled") == "gabled"
    assert clean_roof_shape("many") == "other"
    assert clean_roof_shape("abrakadabra") == "other"
    
    assert clean_levels("-10") == None 
    assert clean_levels("NaN") == None
    assert clean_levels("10") == "10.0" 
    assert clean_levels("20") == "20.0"
    assert clean_levels("2000") == "2000.0"
    
    assert clean_height_or_circumference("-3") == None
    assert clean_height_or_circumference("NaN") == None
    assert clean_height_or_circumference("abrakadabra") == None
    assert clean_height_or_circumference("400") == "400.0"
    assert clean_height_or_circumference("100-200") == "150.0"
    
    assert clean_height_or_circumference("5 m") == "5.0" 
    assert clean_height_or_circumference("530 cm") == "5.3"
    assert clean_height_or_circumference("40'") == "12.192"
    
    assert to_binomial1("Tilia cordata") == "Tilia cordata"
    assert to_binomial1("Tilia cordata green spire") == "Tilia cordata"
    assert to_binomial1("Tilia cordata 'Green Spire'") == "Tilia cordata"
    assert to_binomial1("Citrus ×sinensis")      == "Citrus × sinensis"
    assert to_binomial1("Citrus×sinensis")       == "Citrus × sinensis"
    assert to_binomial1("Citrus x sinensis")     == "Citrus × sinensis"
    assert to_binomial1("Citrus  x   sinensis")  == "Citrus × sinensis"
    assert to_binomial1("Gonystylus xylocarpus") == "Gonystylus xylocarpus"
    assert to_binomial1("Platanus X hispanica")  == "Platanus × hispanica"
    
    assert to_binomial1("X Cupresocyparis leylandii")  == "× Cupresocyparis leylandii"
    assert to_binomial1("x Cupresocyparis leylandii")  == "× Cupresocyparis leylandii"
    
    assert to_binomial1("Prunus 'Accolade'")     == "Prunus 'Accolade'"
    assert to_binomial1("Oak")                   == "Oak"
    
    
   
    print("Tests OK")
    