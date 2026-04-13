import os
import math
import pandas as pd
import geopandas as gpd
import numpy as np
import plotly.graph_objects as go
from shapely.geometry import Polygon

# =========================================================
# FILE PATHS
# =========================================================
ZONE_SHP = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\input\geo\zoneShapefile\zones_20231212.shp"

BASE_LEGS_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\base-scenario-0-percent-telework\legs.csv"
TELE20_LEGS_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\scenario-1-20-percent-telework\legs.csv"
TELE40_LEGS_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\scenario-2-40-percent-telework\legs.csv"
TELE80_LEGS_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\scenario-3-80-percent-telework\legs.csv"

OUTPUT_DIR = r"C:\Users\Nawidullah\IdeaProjects\abit\viz\work_destination_hex_compare_plotly"
os.makedirs(OUTPUT_DIR, exist_ok=True)

OUTPUT_BASE_HTML = os.path.join(OUTPUT_DIR, "base_home_to_work_trip_destinations_hexbin.html")
OUTPUT_COMPARE20_HTML = os.path.join(OUTPUT_DIR, "telework20_minus_base_home_to_work_trip_destinations_hexbin.html")
OUTPUT_COMPARE40_HTML = os.path.join(OUTPUT_DIR, "telework40_minus_base_home_to_work_trip_destinations_hexbin.html")
OUTPUT_COMPARE80_HTML = os.path.join(OUTPUT_DIR, "telework80_minus_base_home_to_work_trip_destinations_hexbin.html")

OUTPUT_HEX_COUNTS_CSV = os.path.join(OUTPUT_DIR, "hex_counts_all_scenarios.csv")

# =========================================================
# SCENARIO SWITCHES
# =========================================================
PLOT_BASE = True
PLOT_TELE20 = True
PLOT_TELE40 = True
PLOT_TELE80 = True

# =========================================================
# COLUMN NAMES
# =========================================================
START_X_COL = "start_x"
START_Y_COL = "start_y"
END_X_COL = "end_x"
END_Y_COL = "end_y"

PREV_PURPOSE_COL = "previous_purpose"
NEXT_PURPOSE_COL = "next_purpose"

# =========================================================
# HEX GRID SETTINGS
# =========================================================
HEX_RADIUS_M = 2000       # smaller = more hexes
USE_DENSITY_PER_KM2 = False

# =========================================================
# PLOTLY MAP SETTINGS
# =========================================================
MAP_STYLE = "carto-positron"
BASE_OPACITY = 0.75
CHANGE_OPACITY = 0.72

# Hide outlines by default; set to 0.15 for a faint grid
HEX_LINE_WIDTH = 0.0
HEX_LINE_COLOR = "rgba(0,0,0,0)"

BASE_COLOR_SCALE = [
    [0.00, "rgba(255,255,255,0.00)"],
    [0.15, "rgba(222,235,247,0.12)"],
    [0.35, "rgba(158,202,225,0.28)"],
    [0.55, "rgba(107,174,214,0.46)"],
    [0.75, "rgba(49,130,189,0.68)"],
    [1.00, "rgba(8,81,156,0.90)"],
]

CHANGE_COLOR_SCALE = [
    [0.00, "rgba(59,76,192,0.90)"],
    [0.40, "rgba(175,198,255,0.45)"],
    [0.50, "rgba(255,255,255,0.05)"],
    [0.60, "rgba(247,183,178,0.45)"],
    [1.00, "rgba(180,4,38,0.90)"],
]

# =========================================================
# HELPERS
# =========================================================
def clean_string_col(df, col):
    return df[col].astype(str).str.strip()

def to_num(df, cols):
    for c in cols:
        if c in df.columns:
            df[c] = pd.to_numeric(df[c], errors="coerce")
    return df

def compute_home_work_points(legs_csv, scenario_name, crs):
    legs = pd.read_csv(legs_csv)
    legs.columns = [c.strip() for c in legs.columns]

    print(f"\nLoaded scenario: {scenario_name}")
    print("CSV path:", legs_csv)
    print("Rows before cleaning:", len(legs))
    print("Columns:", legs.columns.tolist())

    required_cols = [
        PREV_PURPOSE_COL, NEXT_PURPOSE_COL,
        START_X_COL, START_Y_COL, END_X_COL, END_Y_COL
    ]
    missing = [c for c in required_cols if c not in legs.columns]
    if missing:
        raise ValueError(f"Missing required columns in {scenario_name}: {missing}")

    legs[PREV_PURPOSE_COL] = clean_string_col(legs, PREV_PURPOSE_COL).str.upper()
    legs[NEXT_PURPOSE_COL] = clean_string_col(legs, NEXT_PURPOSE_COL).str.upper()
    legs = to_num(legs, [START_X_COL, START_Y_COL, END_X_COL, END_Y_COL])

    legs = legs[
        legs[START_X_COL].notna() &
        legs[START_Y_COL].notna() &
        legs[END_X_COL].notna() &
        legs[END_Y_COL].notna()
        ].copy()

    print("Rows after basic cleaning:", len(legs))

    home_work_legs = legs[
        (legs[PREV_PURPOSE_COL] == "HOME") &
        (legs[NEXT_PURPOSE_COL] == "WORK")
        ].copy()

    print("HOME->WORK legs before no-trip filtering:", len(home_work_legs))

    same_coord_mask = (
            (home_work_legs[START_X_COL] == home_work_legs[END_X_COL]) &
            (home_work_legs[START_Y_COL] == home_work_legs[END_Y_COL])
    )

    removed_count = int(same_coord_mask.sum())
    home_work_legs = home_work_legs[~same_coord_mask].copy()

    print("Removed HOME->WORK same-coordinate no-trip cases:", removed_count)
    print("HOME->WORK legs kept:", len(home_work_legs))

    points = gpd.GeoDataFrame(
        home_work_legs[["person_id"]].copy(),
        geometry=gpd.points_from_xy(home_work_legs[END_X_COL], home_work_legs[END_Y_COL]),
        crs=crs
    )

    print("Point GeoDataFrame size:", len(points))
    return points

def make_hexagon(cx, cy, r):
    angles_deg = [0, 60, 120, 180, 240, 300]
    coords = []
    for ang in angles_deg:
        theta = math.radians(ang)
        x = cx + r * math.cos(theta)
        y = cy + r * math.sin(theta)
        coords.append((x, y))
    return Polygon(coords)

def build_hex_grid(mask_gdf, hex_radius):
    xmin, ymin, xmax, ymax = mask_gdf.total_bounds

    dx = 1.5 * hex_radius
    dy = math.sqrt(3) * hex_radius

    hexes = []
    hex_ids = []
    col = 0
    x = xmin - 2 * hex_radius
    hex_id = 0
    union_geom = mask_gdf.unary_union

    while x < xmax + 2 * hex_radius:
        y_offset = 0 if col % 2 == 0 else dy / 2.0
        y = ymin - 2 * dy + y_offset

        while y < ymax + 2 * dy:
            hex_poly = make_hexagon(x, y, hex_radius)
            if hex_poly.intersects(union_geom):
                hexes.append(hex_poly)
                hex_ids.append(hex_id)
                hex_id += 1
            y += dy

        x += dx
        col += 1

    hex_gdf = gpd.GeoDataFrame({"hex_id": hex_ids}, geometry=hexes, crs=mask_gdf.crs)
    hex_gdf["hex_area_km2"] = hex_gdf.geometry.area / 1_000_000.0

    print("\nBuilt hex grid")
    print("Number of hexes:", len(hex_gdf))
    print("Hex area km² summary:")
    print(hex_gdf["hex_area_km2"].describe())

    return hex_gdf

def count_points_in_hexes(points_gdf, hex_gdf, count_col):
    joined = gpd.sjoin(
        points_gdf,
        hex_gdf[["hex_id", "geometry"]],
        how="left",
        predicate="within"
    )

    counts = joined.groupby("hex_id").size().reset_index(name=count_col)
    return counts

def add_hex_metric(hex_gdf, count_col, metric_col):
    hex_gdf = hex_gdf.copy()
    if USE_DENSITY_PER_KM2:
        hex_gdf[metric_col] = hex_gdf[count_col] / hex_gdf["hex_area_km2"]
    else:
        hex_gdf[metric_col] = hex_gdf[count_col]
    return hex_gdf

def to_latlon_geojson(gdf_projected):
    gdf_ll = gdf_projected.to_crs("EPSG:4326").copy()
    geojson = gdf_ll.__geo_interface__
    center = gdf_ll.geometry.unary_union.centroid
    return gdf_ll, geojson, {"lat": center.y, "lon": center.x}

def make_hover(metric_label, id_col="hex_id"):
    return (
            "<b>Hex %{location}</b><br>"
            + metric_label
            + ": %{z:.2f}<extra></extra>"
    )

def plot_plotly_hex_map(gdf_ll, geojson, center, z_col, title, output_html,
                        colorscale, zmin=None, zmax=None, opacity=0.7,
                        colorbar_title="Value"):
    fig = go.Figure(
        go.Choroplethmap(
            geojson=geojson,
            locations=gdf_ll["hex_id"],
            z=gdf_ll[z_col],
            featureidkey="properties.hex_id",
            colorscale=colorscale,
            zmin=zmin,
            zmax=zmax,
            marker=dict(
                line=dict(width=HEX_LINE_WIDTH, color=HEX_LINE_COLOR),
                opacity=opacity,
            ),
            colorbar=dict(
                title=colorbar_title,
                thickness=18,
                len=0.75,
            ),
            hovertemplate=make_hover(colorbar_title),
        )
    )

    fig.update_layout(
        title=title,
        map=dict(
            style=MAP_STYLE,
            center=center,
            zoom=6.6,
        ),
        margin=dict(l=0, r=0, t=40, b=0),
    )

    fig.write_html(output_html)
    fig.show()
    print(f"Saved interactive html: {output_html}")

# =========================================================
# 1. LOAD ZONES
# =========================================================
zones = gpd.read_file(ZONE_SHP)
print("Zone CRS:", zones.crs)
print("Zones shapefile columns:")
print(zones.columns.tolist())

# =========================================================
# 2. BUILD COMMON HEX GRID
# =========================================================
hexes = build_hex_grid(zones, HEX_RADIUS_M)

# =========================================================
# 3. COMPUTE COUNTS FOR EACH SCENARIO
# =========================================================
base_points = compute_home_work_points(BASE_LEGS_CSV, "base", zones.crs) if PLOT_BASE else None
tele20_points = compute_home_work_points(TELE20_LEGS_CSV, "telework_20", zones.crs) if PLOT_TELE20 else None
tele40_points = compute_home_work_points(TELE40_LEGS_CSV, "telework_40", zones.crs) if PLOT_TELE40 else None
tele80_points = compute_home_work_points(TELE80_LEGS_CSV, "telework_80", zones.crs) if PLOT_TELE80 else None

base_counts = count_points_in_hexes(base_points, hexes, "base_count") if PLOT_BASE else None
tele20_counts = count_points_in_hexes(tele20_points, hexes, "tele20_count") if PLOT_TELE20 else None
tele40_counts = count_points_in_hexes(tele40_points, hexes, "tele40_count") if PLOT_TELE40 else None
tele80_counts = count_points_in_hexes(tele80_points, hexes, "tele80_count") if PLOT_TELE80 else None

# =========================================================
# 4. MERGE COUNTS INTO HEX GRID
# =========================================================
hex_compare = hexes.copy()

if PLOT_BASE:
    hex_compare = hex_compare.merge(base_counts, how="left", on="hex_id")
if PLOT_TELE20:
    hex_compare = hex_compare.merge(tele20_counts, how="left", on="hex_id")
if PLOT_TELE40:
    hex_compare = hex_compare.merge(tele40_counts, how="left", on="hex_id")
if PLOT_TELE80:
    hex_compare = hex_compare.merge(tele80_counts, how="left", on="hex_id")

for col in ["base_count", "tele20_count", "tele40_count", "tele80_count"]:
    if col not in hex_compare.columns:
        hex_compare[col] = 0
    hex_compare[col] = hex_compare[col].fillna(0)

hex_compare = add_hex_metric(hex_compare, "base_count", "base_metric")
hex_compare = add_hex_metric(hex_compare, "tele20_count", "tele20_metric")
hex_compare = add_hex_metric(hex_compare, "tele40_count", "tele40_metric")
hex_compare = add_hex_metric(hex_compare, "tele80_count", "tele80_metric")

hex_compare["change_20"] = hex_compare["tele20_metric"] - hex_compare["base_metric"]
hex_compare["change_40"] = hex_compare["tele40_metric"] - hex_compare["base_metric"]
hex_compare["change_80"] = hex_compare["tele80_metric"] - hex_compare["base_metric"]

hex_compare.drop(columns="geometry").to_csv(OUTPUT_HEX_COUNTS_CSV, index=False)
print("Saved hex count table:", OUTPUT_HEX_COUNTS_CSV)

# =========================================================
# 5. CONVERT HEXES TO LAT/LON GEOJSON FOR PLOTLY
# =========================================================
hex_ll, hex_geojson, map_center = to_latlon_geojson(hex_compare)

# =========================================================
# 6. BASE MAP
# =========================================================
if PLOT_BASE:
    zmax_base = max(float(hex_ll["base_metric"].max()), 1.0)
    plot_plotly_hex_map(
        gdf_ll=hex_ll,
        geojson=hex_geojson,
        center=map_center,
        z_col="base_metric",
        title=(
            "Base Scenario: HOME→WORK Trip Destinations (Hex Grid)"
            if not USE_DENSITY_PER_KM2
            else "Base Scenario: HOME→WORK Trip Destination Density (Hex Grid)"
        ),
        output_html=OUTPUT_BASE_HTML,
        colorscale=BASE_COLOR_SCALE,
        zmin=0,
        zmax=zmax_base,
        opacity=BASE_OPACITY,
        colorbar_title=("Trips per hex" if not USE_DENSITY_PER_KM2 else "Trips per km²"),
    )

# =========================================================
# 7. CHANGE MAPS: TRUE SCENARIO - BASE
# =========================================================
max_abs_change = max(
    abs(float(hex_ll["change_20"].min())) if PLOT_TELE20 else 0,
    abs(float(hex_ll["change_20"].max())) if PLOT_TELE20 else 0,
    abs(float(hex_ll["change_40"].min())) if PLOT_TELE40 else 0,
    abs(float(hex_ll["change_40"].max())) if PLOT_TELE40 else 0,
    abs(float(hex_ll["change_80"].min())) if PLOT_TELE80 else 0,
    abs(float(hex_ll["change_80"].max())) if PLOT_TELE80 else 0,
)
max_abs_change = max(max_abs_change, 1.0)

legend_label = (
    "Change in HOME→WORK trips per hex"
    if not USE_DENSITY_PER_KM2
    else "Change in HOME→WORK trips per km²"
)

if PLOT_TELE20:
    plot_plotly_hex_map(
        gdf_ll=hex_ll,
        geojson=hex_geojson,
        center=map_center,
        z_col="change_20",
        title="20% Telework vs Base: Change in HOME→WORK Trip Destinations (Hex Grid)",
        output_html=OUTPUT_COMPARE20_HTML,
        colorscale=CHANGE_COLOR_SCALE,
        zmin=-max_abs_change,
        zmax=max_abs_change,
        opacity=CHANGE_OPACITY,
        colorbar_title=legend_label,
    )

if PLOT_TELE40:
    plot_plotly_hex_map(
        gdf_ll=hex_ll,
        geojson=hex_geojson,
        center=map_center,
        z_col="change_40",
        title="40% Telework vs Base: Change in HOME→WORK Trip Destinations (Hex Grid)",
        output_html=OUTPUT_COMPARE40_HTML,
        colorscale=CHANGE_COLOR_SCALE,
        zmin=-max_abs_change,
        zmax=max_abs_change,
        opacity=CHANGE_OPACITY,
        colorbar_title=legend_label,
    )

if PLOT_TELE80:
    plot_plotly_hex_map(
        gdf_ll=hex_ll,
        geojson=hex_geojson,
        center=map_center,
        z_col="change_80",
        title="80% Telework vs Base: Change in HOME→WORK Trip Destinations (Hex Grid)",
        output_html=OUTPUT_COMPARE80_HTML,
        colorscale=CHANGE_COLOR_SCALE,
        zmin=-max_abs_change,
        zmax=max_abs_change,
        opacity=CHANGE_OPACITY,
        colorbar_title=legend_label,
    )

print("\nDone.")