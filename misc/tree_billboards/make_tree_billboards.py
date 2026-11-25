#!/usr/bin/env python3
"""Entry point of the SeedThree tree-billboard pipeline (UrbanEye3D misc, stage 17).

The species list is NOT kept here: it is asked from SeedThree itself
(`bake.mjs --list-species`, which evaluates SeedThree's own `listSpecies()`), so the
keys, English names and Latin binomials always match the checkout being baked.

Bakes N seed variants (default seeds 1-4) of every known species straight to
final-quality transparent PNGs (default 512 px tall), named after the Latin
binomial, e.g. `Quercus_alba_01.png`, into misc/data/17_seedthree_textures/ -
together with manifest.json (provenance: SeedThree rev, seed, real metres, alpha
histogram) and the preliminary report tree_billboards.md in the same folder.

A defective individual is excluded with `--ban specieskey:seed` (remaining
variants are renumbered). Mapping textures to OSM species/synonyms is deliberately
NOT done here - that happens elsewhere and produces MapCSS rules elsewhere.
"""
import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
BAKE_MJS = HERE / "bake.mjs"
DATA = REPO / "misc" / "data" / "17_seedthree_textures"  # pipeline outputs (misc/data convention)
OUT = DATA / "intermediates"  # jobs.json, species.json, Chrome profile (misc/data is git-ignored)
MANIFEST = DATA / "manifest.json"
REPORT = DATA / "tree_billboards.md"


def latin_slug(name):
    """Malus domestica -> Malus_domestica (texture name stem)."""
    return re.sub(r"[^A-Za-z0-9]+", "_", name.strip())


def run(cmd):
    print("+ " + " ".join(str(c) for c in cmd))
    subprocess.run(cmd, cwd=str(HERE), check=False)


def seedthree_species():
    """Ask SeedThree which species it knows: [{key, name, latin, biome, ...}]."""
    OUT.mkdir(parents=True, exist_ok=True)
    species_json = OUT / "species.json"
    run(["node", str(BAKE_MJS), "--list-species", str(species_json), "--out", str(DATA)])
    if not species_json.exists():
        sys.exit(f"SeedThree species registry not produced at {species_json}")
    with open(species_json, encoding="utf-8") as f:
        return json.load(f)


def parse_bans(ban_args):
    bans = {}
    for item in ban_args or []:
        key, _, seeds = item.partition(":")
        if not seeds:
            sys.exit(f"bad --ban {item!r}: expected specieskey:seed[,seed]")
        bans.setdefault(key, set()).update(int(s) for s in seeds.split(","))
    return bans


def bake(args, species, bans, default_seeds):
    only = set(args.only.split(",")) if args.only else None
    known = {sp["key"] for sp in species}
    if only and not only <= known:
        sys.exit(f"--only has unknown keys: {', '.join(sorted(only - known))}")
    if bans.keys() - known:
        sys.exit(f"--ban has unknown keys: {', '.join(sorted(bans.keys() - known))}")
    jobs = []
    for sp in species:
        if only and sp["key"] not in only:
            continue
        slug = latin_slug(sp.get("latin") or sp["name"])
        kept = [s for s in default_seeds if s not in bans.get(sp["key"], set())]
        for index, seed in enumerate(kept, start=1):
            jobs.append({
                "id": f"{slug}_{index:02d}",
                "species": sp["key"],
                "seed": seed,
                "size": args.size,
                "views": [0],
                "controls": {},
                "lod": {},
            })
    if not jobs:
        sys.exit("nothing to bake: --only/--ban filtered out every species")
    OUT.mkdir(parents=True, exist_ok=True)
    jobs_file = OUT / "jobs.json"
    with open(jobs_file, "w", encoding="utf-8") as f:
        json.dump({"dilate": 4, "jobs": jobs}, f, indent=2)
        f.write("\n")
    run(["node", str(BAKE_MJS), "--jobs", str(jobs_file),
         "--out", str(DATA), "--manifest", str(MANIFEST)])
    if not MANIFEST.exists():
        sys.exit(f"bake produced no manifest at {MANIFEST}")
    with open(MANIFEST, encoding="utf-8") as f:
        manifest = json.load(f)
    prune_stale(manifest, {j["species"] for j in jobs}, {j["id"] for j in jobs})
    write_report(species, manifest, default_seeds, bans)


def prune_stale(manifest, baked_species, current_ids):
    """A species re-baked with fewer variants (e.g. after --ban) must not keep its
    old tail variants: drop their manifest entries and PNG files."""
    stale = [e["id"] for e in list(manifest["entries"].values())
             if e.get("species") in baked_species and e["id"] not in current_ids]
    for pid in stale:
        entry = manifest["entries"].pop(pid)
        for name in (pid + ".png", entry.get("flatPng")):
            if name and (DATA / name).exists():
                (DATA / name).unlink()
    if stale:
        with open(MANIFEST, "w", encoding="utf-8") as f:
            json.dump(manifest, f, indent=2)
            f.write("\n")
        print(f"pruned {len(stale)} stale variant(s): {', '.join(sorted(stale))}")


def species_entries(manifest, key):
    """All manifest entries of one species, ordered by variant name."""
    entries = [e for e in manifest["entries"].values() if e.get("species") == key]
    return sorted(entries, key=lambda e: e["id"])


def write_report(species, manifest, default_seeds, bans):
    """Report covers the whole manifest, not just the last run, so a --only bake
    never shrinks it."""
    size = next((e.get("size") for e in manifest["entries"].values() if e.get("size")), "?")
    lines = [
        "# Tree billboards \u2014 pipeline output (preliminary report)",
        "",
        "Final-quality transparent PNGs baked from SeedThree presets (species list asked from",
        f"SeedThree itself): front view, {size} px tall, seeds "
        f"{', '.join(str(s) for s in default_seeds)} per species.",
        "Ban a defective individual with `--ban specieskey:seed`; remaining variants renumber.",
        "Mapping these textures to OSM species/synonyms happens elsewhere, not in this pipeline.",
        f"SeedThree rev `{manifest.get('rev', '?')}`, run {manifest.get('runAt', '?')}.",
        "Note the bake light is flat white, so bark reads paler than in the plugin \u2014 judge",
        "silhouette density and shape, not color.",
        "",
    ]
    for sp in species:
        mentries = species_entries(manifest, sp["key"])
        if not mentries:
            continue
        lines.append(f"## {sp['name']} \u2014 `{sp['key']}` ({sp.get('latin', '?')}), "
                     f"{sp.get('biome', '?')}")
        if sp["key"] in bans:
            lines.append(f"\nBanned seeds: {', '.join(str(s) for s in sorted(bans[sp['key']]))}")
        lines.append("")
        lines.append("| variant | texture | seed | real height | real width | W:H | "
                     "alpha 0/mid/opaque | bake ms |")
        lines.append("|---|---|---|---|---|---|---|---|")
        for mentry in mentries:
            index = int(mentry["id"].rsplit("_", 1)[1])
            if "error" in mentry:
                lines.append(f"| _{index:02d} | BAKE FAILED: `{mentry['error'].splitlines()[0]}` "
                             f"| {mentry['seed']} | | | | | |")
                continue
            wh = 10.0 * mentry["realWidthMeters"] / mentry["realHeightMeters"]
            alpha = mentry.get("alpha", {})
            lines.append(
                f"| _{index:02d} | ![{mentry['id']}]({mentry['id']}.png) `{mentry['id']}.png` "
                f"| {mentry['seed']} "
                f"| {mentry['realHeightMeters']:.1f} m | {mentry['realWidthMeters']:.1f} m "
                f"| {wh:.1f} "
                f"| {alpha.get('transparent', 0)}/{alpha.get('mid', 0) + alpha.get('high', 0)}"
                f"/{alpha.get('opaque', 0)} | {mentry.get('ms', '?')} |"
            )
        lines.append("")
    lines += [
        "Caveat for future multi-variant use: each variant has its own aspect ratio",
        "(see W:H); a per-node texture choice in the plugin must take it from `manifest.json`.",
        "",
    ]
    with open(REPORT, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines))
    print(f"report: {REPORT}")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--seeds", help="comma-separated seed list per species (default 1,2,3,4)")
    parser.add_argument("--only", help="comma-separated SeedThree keys to restrict")
    parser.add_argument("--ban", action="append", metavar="KEY:SEED[,SEED]",
                        help="exclude a defective seed from a species (repeatable)")
    parser.add_argument("--size", type=int, default=512, help="texture height in px")
    args = parser.parse_args()

    species = seedthree_species()
    default_seeds = [int(s) for s in args.seeds.split(",")] if args.seeds else [1, 2, 3, 4]
    bake(args, species, parse_bans(args.ban), default_seeds)


if __name__ == "__main__":
    main()
