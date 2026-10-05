import json
import re
import csv
import html
import argparse
import os

import matplotlib.pyplot as plt
import matplotlib.patches as mpatches


import cartopy.crs as ccrs
import cartopy.feature as cfeature
from shapely.geometry import box
HAS_CARTOPY = True


CELL_SIZE = 5
TITLE_FONT_SIZE = 22
CREDIT_TEXT = "UrbanEye3D data pipeline,\nbased on OpenStreetMap data"

# Базовые цвета для каждого типа листвы
LEAF_COLORS = {
    "broadleaved": "#40F040",   # зелёный
    "needleleaved": "#4040F0",  # синий
    "palm": "#F04040",          # красный
    "leafless": "#000000",      # чорный
    "unknown": "#cccccc",
}

LEAF_LABELS = {
    "broadleaved": "Broadleaved",
    "needleleaved": "Needleleaved",
    "palm": "Palm",
    "leafless": "Leafless",
    "unknown": "Unknown",
}


# ---------- работа с цветом ----------

def hex_to_rgb(h: str):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def rgb_to_hex(rgb):
    return "#{:02x}{:02x}{:02x}".format(*[max(0, min(255, int(round(c)))) for c in rgb])


BASE_RGB = {k: hex_to_rgb(v) for k, v in LEAF_COLORS.items() if k != "unknown"}


def blend_color(prob: dict) -> str:
    """Возвращает hex-цвет, полученный смешиванием базовых цветов
    пропорционально вероятностям типов листвы."""
    if not prob:
        return LEAF_COLORS["unknown"]

    r = g = b = 0.0
    total_w = 0.0
    for leaf_type, w in prob.items():
        rgb = BASE_RGB.get(leaf_type)
        if rgb is None or w <= 0:
            continue
        r += rgb[0] * w
        g += rgb[1] * w
        b += rgb[2] * w
        total_w += w

    if total_w == 0:
        return LEAF_COLORS["unknown"]

    # Нормализуем на случай, если сумма вероятностей < 1
    return rgb_to_hex((r / total_w, g / total_w, b / total_w))


# ---------- парсинг данных ----------

def parse_key(k: str):
    m = re.match(r"^([+-]\d{2})([+-]\d{3})$", k)
    if not m:
        raise ValueError(f"Не могу разобрать ключ: {k}")
    return int(m.group(1)), int(m.group(2))


def dominant_leaf_type(prob: dict):
    if not prob:
        return "unknown", 0.0
    key, value = max(prob.items(), key=lambda kv: kv[1])
    return key, value


def load_data(path: str):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)

    records = []
    for key, val in data.items():
        lat0, lon0 = parse_key(key)
        prob = val.get("leaf_type_prob", {})
        dom, dom_prob = dominant_leaf_type(prob)

        records.append({
            "key": key,
            "lat0": lat0,
            "lon0": lon0,
            "lat1": lat0 + CELL_SIZE,
            "lon1": lon0 + CELL_SIZE,
            "center_lat": lat0 + CELL_SIZE / 2,
            "center_lon": lon0 + CELL_SIZE / 2,
            "leaf_prob": prob,
            "dominant": dom,
            "dominant_prob": dom_prob,
            "blend_color": blend_color(prob),
            "top_species": val.get("top_species", []),
            "total_trees": val.get("total_trees", 0),
        })
    return records



def make_static(records, out_png: str):
    
    fig = plt.figure(figsize=(16, 9))
    ax = plt.axes(projection=ccrs.PlateCarree())
    #ax = plt.axes(projection=ccrs.EqualEarth())
    ax.set_global()
    #ax.set_extent([-180, 180, -60, 85], crs=ccrs.PlateCarree())

    # Подложка Natural Earth (cartopy сама скачает при первом запуске)
    #ax.add_feature(cfeature.OCEAN, facecolor="#eaf3fb", edgecolor="none", zorder=1)
    #ax.add_feature(cfeature.LAND, facecolor="#f5f3ee", edgecolor="none", zorder=0)
    ax.add_feature(cfeature.LAKES, facecolor="#eaf3fb", edgecolor="none", zorder=1)
    ax.add_feature(cfeature.RIVERS, edgecolor="#c9dff0", linewidth=0.2, zorder=1)
    ax.add_feature(cfeature.COASTLINE, edgecolor="#666666", linewidth=0.7, zorder=2)
    ax.add_feature(cfeature.BORDERS, edgecolor="#bbbbbb", linewidth=0.2, zorder=2)

    # Ячейки с растительностью
    for rec in records:
        geom = box(rec["lon0"], rec["lat0"], rec["lon1"], rec["lat1"])
        ax.add_geometries(
            [geom],
            crs=ccrs.PlateCarree(),
            facecolor=rec["blend_color"],
            edgecolor="#333333",
            linewidth=0.0,
            alpha=0.7,
            zorder=0,
        )

    # Координатная сетка — поверх ячеек, с фиксированным шагом 30°
    gl = ax.gridlines(
        crs=ccrs.PlateCarree(),
        draw_labels=True,
        xlocs=range(-180, 181, 30),
        ylocs=range(-90, 91, 30),
        linewidth=0.6,
        color="#333333",
        alpha=0.7,
        linestyle="-",
        zorder=10,          # ключевой момент: выше ячеек (zorder=3)
    )
    gl.top_labels = False
    gl.right_labels = False
    gl.xlabel_style = {"size": 9, "color": "#222222"}
    gl.ylabel_style = {"size": 9, "color": "#222222"}
    # Отступ подписей от рамки, чтобы не наезжали на береговую линию
    gl.xpadding = 6
    gl.ypadding = 6

    ax.set_title("leaf_type for natural=tree, by 5°×5° grid", fontsize=TITLE_FONT_SIZE)

    # Подпись в правом нижнем углу
    ax.text(
        0.995, 0.012, CREDIT_TEXT,
        transform=ax.transAxes, ha="right", va="bottom",
        fontsize=12, color="#555555", zorder=20,
        bbox={"facecolor": "white", "alpha": 0.7, "edgecolor": "none", "pad": 2.0},
    )

    

    patches = [
        mpatches.Patch(color=LEAF_COLORS[k], label=v)
        for k, v in LEAF_LABELS.items()
        if k != "unknown"
    ]
    ax.legend(
        handles=patches, loc="lower left", fontsize=9,
        framealpha=0.9, title="Base Colours",
    )

    plt.tight_layout()
    plt.savefig(out_png, dpi=150)
    plt.close()
    print(f"Статичная карта сохранена: {out_png}")



def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("json_path", nargs="?", default="spatial_stats_5x5.json")
    parser.add_argument("--png", default="map_leaf_types.png")
    args = parser.parse_args()

    records = load_data(args.json_path)
    #make_interactive(records, args.html)
    make_static(records, args.png)


if __name__ == "__main__":
    main()