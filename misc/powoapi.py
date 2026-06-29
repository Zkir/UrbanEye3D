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
    
    
def _fetch_powo_data(species_name):
    """Fetches data from cache or POWO API."""
    safe_name = "".join([c if c.isalnum() or c in " _-" else "_" for c in species_name]).strip()
    cache_file = os.path.join(CACHE_DIR, f"{safe_name}.json")

    if os.path.exists(cache_file):
        with open(cache_file, 'r', encoding='utf-8') as f:
            return json.load(f)

    url = "https://powo.science.kew.org/api/2/search"
    params = {"q": species_name}
    headers = {"User-Agent": "Mozilla/5.0 (UrbanEye3D Botany Bot)"}

    # Small delay to be polite to the API
    time.sleep(0.1)
    resp = requests.get(url, params=params, headers=headers, timeout=10)
    if resp.status_code != 200:
        print(resp.status_code, species_name)
        return None
        
    data = resp.json()
    # Save to cache
    with open(cache_file, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        
    return data


def _find_match(species_name, results):
    """Finds the best matching taxonomic result for a species name."""
    if not results:
        return None, False, False
        
    norm_input = normalize_and_binomial(species_name)
    
    for res in results:
        res_name = res.get('name', '')
        norm_res = normalize_and_binomial(res_name)
        
        is_exact = (norm_res == norm_input)
        is_typo = (not is_exact and norm_res.replace(' × ', ' ') == norm_input)
        
        if is_exact or is_typo:
            return res, is_exact, is_typo
            
    return None, False, False


def check_powo_status(species_name):
    """
    Queries POWO API to check taxonomic status.
    Returns: (status, accepted_name)
    Statuses: 'Accepted', 'Synonym', 'Not found', 'Error'
    """
    data = _fetch_powo_data(species_name)
    if data is None:
        return 'Error', None
        
    res, is_exact, is_typo = _find_match(species_name, data.get('results', []))
    
    if res:
        if res.get('accepted'):
            # If it's accepted, we only return the name if it was a typo 
            # (to show the correct spelling with ×)
            return ('Accepted' if is_exact else 'Synonym'), (res.get('name') if is_typo else None)
        else:
            # If it's a synonym, follow to the accepted name
            syn_of = res.get('synonymOf', {})
            if syn_of:
                # We return the ultimate accepted name regardless of whether 
                # the input was a typo or a direct synonym.
                return ('Synonym' if is_exact else 'Synonym'), syn_of.get('name')
    
    return 'Not found', None


def get_powo_family(species_name):
    """
    Queries POWO API to get family name.
    """
    data = _fetch_powo_data(species_name)
    if not data:
        return None
        
    res, _, _ = _find_match(species_name, data.get('results', []))
    
    if res:
        return res.get('family')
        
    return None
    