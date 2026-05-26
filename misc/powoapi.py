"""
POWO API
"""

import os
import json
import time
import requests

from datawash import to_binomial as normalize_and_binomial

BASE_DIR =             os.path.dirname(os.path.abspath(__file__))
CACHE_DIR =            os.path.join(BASE_DIR, 'data', '03_powo_cache')

# Ensure cache directory exists
if not os.path.exists(CACHE_DIR):
    os.makedirs(CACHE_DIR)
    
    
def check_powo_status(species_name):
    """
    Queries POWO API to check taxonomic status.
    Returns: (status, accepted_name)
    Statuses: 'Accepted', 'Synonym', 'Not found', 'Error'
    """
    # Use a sanitized filename for caching
    safe_name = "".join([c if c.isalnum() or c in " _-" else "_" for c in species_name]).strip()
    cache_file = os.path.join(CACHE_DIR, f"{safe_name}.json")

    if os.path.exists(cache_file):
        with open(cache_file, 'r', encoding='utf-8') as f:
            data = json.load(f)
    else:
        data = None

    if data is None:
        url = "https://powo.science.kew.org/api/2/search"
        params = {"q": species_name}
        headers = {"User-Agent": "Mozilla/5.0 (UrbanEye3D Botany Bot)"}

        # Small delay to be polite to the API
        time.sleep(0.1)
        resp = requests.get(url, params=params, headers=headers, timeout=10)
        if resp.status_code != 200:
            print( resp.status_code, species_name )
            exit(1)
            return 'Error', None
        
        data = resp.json()
        # Save to cache
        with open(cache_file, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)

    
    results = data.get('results', [])
    if not results:
        return 'Not found', None
    
    norm_input = normalize_and_binomial(species_name)
    
    for res in results:
        res_name = res.get('name', '')
        norm_res = normalize_and_binomial(res_name)
        
        is_exact = (norm_res == norm_input)
        is_typo = (not is_exact and norm_res.replace(' × ', ' ') == norm_input)
        
        if is_exact or is_typo:
            if res.get('accepted'):
                # If it's accepted, we only return the name if it was a typo 
                # (to show the correct spelling with ×)
                return ('Accepted' if is_exact else 'Synonym'), (res_name if is_typo else None)
            else:
                # If it's a synonym, follow to the accepted name
                syn_of = res.get('synonymOf', {})
                if syn_of:
                    # We return the ultimate accepted name regardless of whether 
                    # the input was a typo or a direct synonym.
                    return ('Synonym' if is_exact else 'Synonym'), syn_of.get('name')
    
    return 'Not found', None
    