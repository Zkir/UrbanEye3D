#!/usr/bin/env python3
"""Coverage report: OSM species vs SeedThree presets (data/17_seedthree_textures/coverage.md).

Top N (default 150) most frequent species from data/10_trees/tree_species_all.csv vs the
baked SeedThree presets: which OSM species already have a texture (directly or via a
POWO synonym) and what is still missing. Matching uses the same normalization as
make_tree_assets.py. Re-run after make_tree_assets.py; the Makefile target does both.
"""
import argparse
import csv
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from make_tree_assets import (DATA, REPO, formatted, latin_slug,
                              load_master_keys, load_synonyms, norm_key)

SPECIES_JSON = DATA / "intermediates" / "species.json"
TREE_SPECIES_ALL_CSV = REPO / "misc" / "data" / "10_trees" / "tree_species_all.csv"
REPORT = DATA / "coverage.md"
# Image links are relative to the Makefile's copy at docs/seedthree_coverage.md; they are dead in misc.
IMG_PREFIX = "../src/main/resources/textures/trees/"
IMG_W = 128


def img(texture):
    return f'<img src="{IMG_PREFIX}{texture}" width="{IMG_W}" alt="{texture}">' if texture else ""


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--top", type=int, default=150, help="rows of the OSM table")
    args = parser.parse_args()
    for path in (SPECIES_JSON, TREE_SPECIES_ALL_CSV):
        if not path.exists():
            sys.exit(f"{path} not found: run earlier pipeline stages first")
    with open(SPECIES_JSON, encoding="utf-8") as f:
        presets = json.load(f)
    rows = []
    with open(TREE_SPECIES_ALL_CSV, encoding="utf-8-sig") as f:
        for r in csv.DictReader(f):
            name = (r.get("species") or "").strip()
            if name:
                rows.append((name, int(r["count"])))
    rows.sort(key=lambda x: -x[1])

    preset_key = {norm_key(p.get("latin") or p["name"]): p for p in presets}
    syn = load_synonyms()
    known = load_master_keys()

    def texture_of(p):
        t = f"{latin_slug(p.get('latin') or p['name'])}_01.png"
        return t if (DATA / t).exists() else ""

    def match(name):
        """(preset, how) for an OSM species name, or (None, '')."""
        k = norm_key(name)
        if k in preset_key:
            return preset_key[k], "exact"
        for alt in syn.get(k, []):
            if alt in preset_key:
                return preset_key[alt], f"synonym -> {formatted(alt)}"
        return None, ""

    top = rows[:args.top]
    total_all = sum(c for _, c in rows)
    total_top = sum(c for _, c in top)
    covered_top = sum(c for n, c in top if match(n)[0])

    def build_lines():
        out = ["# OSM species vs [SeedThree](https://github.com/SkyeShark/SeedThree) coverage", ""]
        out.append(f"Top {len(top)} OSM species cover {total_top * 100 // total_all}% of all "
                   f"{len(rows)} species names in OSM tree data.")
        n_top_cov = sum(1 for n, _ in top if match(n)[0])
        out.append(f"Of them {n_top_cov} already have a SeedThree texture "
                   f"(weighted by occurrences: {covered_top * 100 // total_top}% of the top-{len(top)} "
                   f"tag occurrences).")
        out.append("")
        out.append(f"## Top {len(top)} OSM species")
        out.append("")
        out.append("| # | species (OSM) | occurrences | SeedThree coverage | texture |")
        out.append("|---:|---|---:|---|---|")
        for i, (name, count) in enumerate(top, 1):
            p, how = match(name)
            cov = f"yes, {how} (`{p['key']}`)" if p else "no"
            out.append(f"| {i} | {name} | {count} | {cov} | {img(texture_of(p)) if p else ''} |")
        out.append("")
        out.append("## All SeedThree presets, by OSM occurrences")
        out.append("")
        out.append("| # | Latin | OSM occurrences | preset | biome / foliage | MapCSS rule | texture |")
        out.append("|---:|---|---:|---|---|---|---|")
        preset_rows = sorted(((sum(c for n, c in rows if match(n)[0] is p), p,
                               norm_key(p.get("latin") or p["name"])) for p in presets),
                             key=lambda x: -x[0])
        for i, (osm_count, p, k) in enumerate(preset_rows, 1):
            rule = "yes" if k in known else "no (not in tree_species.csv)"
            biome = f"{p.get('biome', '?')} / {p.get('foliageType', '?')}"
            out.append(f"| {i} | {p.get('latin') or p['name']} | {osm_count} | `{p['key']}` "
                       f"| {biome} | {rule} | {img(texture_of(p))} |")
        out.append("")
        return out

    with open(REPORT, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(build_lines()) + "\n")
    print(f"report written: {REPORT}")


if __name__ == "__main__":
    main()
