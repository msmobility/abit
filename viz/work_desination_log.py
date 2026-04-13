import os
import pandas as pd
import geopandas as gpd
import matplotlib.pyplot as plt

# =========================================================
# FILE PATHS
# =========================================================
ZONE_SHP = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\input\geo\zoneShapefile\zones_20231212.shp"

BASE_LEGS_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\base-scenario-0-percent-telework\legs.csv"
TELE20_LEGS_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\scenario-1-20-percent-telework\legs.csv"
TELE40_LEGS_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\scenario-2-40-percent-telework\legs.csv"
TELE80_LEGS_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\scenario-3-80-percent-telework\legs.csv"

OUTPUT_DIR = r"C:\Users\Nawidullah\IdeaProjects\abit\viz\work_destination_maps_area_normalized"
os.makedirs(OUTPUT_DIR, exist_ok=True)

OUTPUT_BASE_PNG = os.path.join(OUTPUT_DIR, "base_home_to_work_trip_destinations_by_zone_area_normalized.png")
OUTPUT_COMPARE20_PNG = os.path.join(OUTPUT_DIR, "telework20_minus_base_home_to_work_trip_destinations_by_zone_area_normalized.png")
OUTPUT_COMPARE40_PNG = os.path.join(OUTPUT_DIR, "telework40_minus_base_home_to_work_trip_destinations_by_zone_area_normalized.png")
OUTPUT_COMPARE80_PNG = os.path.join(OUTPUT_DIR, "telework80_minus_base_home_to_work_trip_destinations_by_zone_area_normalized.png")

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
ZONE_ID_COL_SHP = "id"
ZONE_AREA_COL_SHP = "area_km2"
DEST_ZONE_COL_CSV = "end_zone"

START_X_COL = "start_x"
START_Y_COL = "start_y"
END_X_COL = "end_x"
END_Y_COL = "end_y"

PREV_PURPOSE_COL = "previous_purpose"
NEXT_PURPOSE_COL = "next_purpose"

# =========================================================
# AREA NORMALIZATION SETTINGS
# =========================================================
AREA_EPSILON_KM2 = 1e-9

# =========================================================
# STYLE
# =========================================================
BASE_CMAP = "Blues"
CHANGE_CMAP = "coolwarm"

EDGE_COLOR = "#C7C7C7"
EDGE_WIDTH = 0.025
TITLE_SIZE = 14
FIGSIZE = (12, 10)

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

def compute_home_work_trip_counts(legs_csv, dest_zone_col, scenario_name):
    legs = pd.read_csv(legs_csv)
    legs.columns = [c.strip() for c in legs.columns]

    print(f"\nLoaded scenario: {scenario_name}")
    print("CSV path:", legs_csv)
    print("Rows before cleaning:", len(legs))
    print("Columns:", legs.columns.tolist())

    required_cols = [
        PREV_PURPOSE_COL, NEXT_PURPOSE_COL, dest_zone_col,
        START_X_COL, START_Y_COL, END_X_COL, END_Y_COL
    ]
    missing = [c for c in required_cols if c not in legs.columns]
    if missing:
        raise ValueError(f"Missing required columns in {scenario_name}: {missing}")

    legs[PREV_PURPOSE_COL] = clean_string_col(legs, PREV_PURPOSE_COL).str.upper()
    legs[NEXT_PURPOSE_COL] = clean_string_col(legs, NEXT_PURPOSE_COL).str.upper()
    legs[dest_zone_col] = clean_string_col(legs, dest_zone_col)

    legs = to_num(legs, [START_X_COL, START_Y_COL, END_X_COL, END_Y_COL])

    legs = legs[
        legs[dest_zone_col].notna() &
        (legs[dest_zone_col] != "") &
        legs[START_X_COL].notna() &
        legs[START_Y_COL].notna() &
        legs[END_X_COL].notna() &
        legs[END_Y_COL].notna()
        ].copy()

    print("Rows after basic cleaning:", len(legs))

    # Keep only HOME -> WORK trips
    home_work_legs = legs[
        (legs[PREV_PURPOSE_COL] == "HOME") &
        (legs[NEXT_PURPOSE_COL] == "WORK")
        ].copy()

    print("HOME->WORK legs before no-trip filtering:", len(home_work_legs))

    # Remove telework no-trip cases where home and work coordinates are the same
    same_coord_mask = (
            (home_work_legs[START_X_COL] == home_work_legs[END_X_COL]) &
            (home_work_legs[START_Y_COL] == home_work_legs[END_Y_COL])
    )

    removed_count = int(same_coord_mask.sum())
    home_work_legs = home_work_legs[~same_coord_mask].copy()

    print("Removed HOME->WORK same-coordinate no-trip cases:", removed_count)
    print("HOME->WORK legs kept:", len(home_work_legs))

    zone_counts = (
        home_work_legs.groupby(dest_zone_col)
        .size()
        .reset_index(name="trip_end_count")
    )

    total_trips = int(zone_counts["trip_end_count"].sum()) if not zone_counts.empty else 0
    print("Zones with HOME->WORK trip destinations:", len(zone_counts))
    print("Total HOME->WORK trips counted:", total_trips)

    return zone_counts

def add_area_km2_from_attribute(zones_gdf):
    zones_gdf = zones_gdf.copy()

    print("\nZones shapefile columns:")
    print(zones_gdf.columns.tolist())

    if ZONE_ID_COL_SHP not in zones_gdf.columns:
        raise ValueError(f"Missing zone id column in shapefile: {ZONE_ID_COL_SHP}")

    if ZONE_AREA_COL_SHP not in zones_gdf.columns:
        raise ValueError(f"Missing zone area column in shapefile: {ZONE_AREA_COL_SHP}")

    zones_gdf[ZONE_ID_COL_SHP] = zones_gdf[ZONE_ID_COL_SHP].astype(str).str.strip()
    zones_gdf[ZONE_AREA_COL_SHP] = pd.to_numeric(zones_gdf[ZONE_AREA_COL_SHP], errors="coerce")

    zones_gdf["area_km2"] = zones_gdf[ZONE_AREA_COL_SHP].fillna(0.0)
    zones_gdf["area_km2_safe"] = zones_gdf["area_km2"].clip(lower=AREA_EPSILON_KM2)

    print("\nArea summary from area_km2 attribute:")
    print(zones_gdf["area_km2"].describe())

    return zones_gdf

def normalize_count_by_area(zones_gdf, count_col, density_col):
    zones_gdf = zones_gdf.copy()
    zones_gdf[density_col] = zones_gdf[count_col] / zones_gdf["area_km2_safe"]
    return zones_gdf

def plot_base_map(zones_gdf, output_png):
    fig, ax = plt.subplots(figsize=FIGSIZE)

    zones_gdf.plot(
        column="base_density",
        ax=ax,
        legend=True,
        cmap=BASE_CMAP,
        edgecolor=EDGE_COLOR,
        linewidth=EDGE_WIDTH,
        legend_kwds={"label": "HOME→WORK trips ending in zone per km²"}
    )

    ax.set_title("Base Scenario: HOME→WORK Trip Destination Density by Zone", fontsize=TITLE_SIZE)
    ax.set_axis_off()

    plt.tight_layout()
    plt.savefig(output_png, dpi=300, bbox_inches="tight")
    plt.show()

    print(f"\nBase map saved to: {output_png}")

def plot_change_map(zones_gdf, change_col, title, legend_label, output_png, max_abs_change):
    fig, ax = plt.subplots(figsize=FIGSIZE)

    zones_gdf.plot(
        column=change_col,
        ax=ax,
        legend=True,
        cmap=CHANGE_CMAP,
        vmin=-max_abs_change,
        vmax=max_abs_change,
        edgecolor=EDGE_COLOR,
        linewidth=EDGE_WIDTH,
        legend_kwds={"label": legend_label}
    )

    ax.set_title(title, fontsize=TITLE_SIZE)
    ax.set_axis_off()

    plt.tight_layout()
    plt.savefig(output_png, dpi=300, bbox_inches="tight")
    plt.show()

    print(f"Comparison map saved to: {output_png}")

# =========================================================
# 1. LOAD ZONES
# =========================================================
zones = gpd.read_file(ZONE_SHP)
zones = add_area_km2_from_attribute(zones)

# =========================================================
# 2. COMPUTE ABSOLUTE COUNTS
# =========================================================
base_counts = (
    compute_home_work_trip_counts(BASE_LEGS_CSV, DEST_ZONE_COL_CSV, "base")
    .rename(columns={"trip_end_count": "base_count"})
    if PLOT_BASE else None
)

tele20_counts = (
    compute_home_work_trip_counts(TELE20_LEGS_CSV, DEST_ZONE_COL_CSV, "telework_20")
    .rename(columns={"trip_end_count": "tele20_count"})
    if PLOT_TELE20 else None
)

tele40_counts = (
    compute_home_work_trip_counts(TELE40_LEGS_CSV, DEST_ZONE_COL_CSV, "telework_40")
    .rename(columns={"trip_end_count": "tele40_count"})
    if PLOT_TELE40 else None
)

tele80_counts = (
    compute_home_work_trip_counts(TELE80_LEGS_CSV, DEST_ZONE_COL_CSV, "telework_80")
    .rename(columns={"trip_end_count": "tele80_count"})
    if PLOT_TELE80 else None
)

# =========================================================
# 3. BASE MAP (AREA NORMALIZED)
# =========================================================
if PLOT_BASE:
    zones_base = zones.merge(
        base_counts,
        how="left",
        left_on=ZONE_ID_COL_SHP,
        right_on=DEST_ZONE_COL_CSV
    )

    zones_base["base_count"] = zones_base["base_count"].fillna(0)
    zones_base = normalize_count_by_area(zones_base, "base_count", "base_density")

    plot_base_map(zones_base, OUTPUT_BASE_PNG)

# =========================================================
# 4. CHANGE MAPS (AREA NORMALIZED)
# =========================================================
if PLOT_BASE and (PLOT_TELE20 or PLOT_TELE40 or PLOT_TELE80):
    change = base_counts[[DEST_ZONE_COL_CSV, "base_count"]].copy()

    if PLOT_TELE20:
        change = change.merge(
            tele20_counts[[DEST_ZONE_COL_CSV, "tele20_count"]],
            how="outer",
            on=DEST_ZONE_COL_CSV
        )

    if PLOT_TELE40:
        change = change.merge(
            tele40_counts[[DEST_ZONE_COL_CSV, "tele40_count"]],
            how="outer",
            on=DEST_ZONE_COL_CSV
        )

    if PLOT_TELE80:
        change = change.merge(
            tele80_counts[[DEST_ZONE_COL_CSV, "tele80_count"]],
            how="outer",
            on=DEST_ZONE_COL_CSV
        )

    for col in ["base_count", "tele20_count", "tele40_count", "tele80_count"]:
        if col not in change.columns:
            change[col] = 0

    for col in change.columns:
        if col != DEST_ZONE_COL_CSV:
            change[col] = change[col].fillna(0)

    zones_compare = zones.merge(
        change,
        how="left",
        left_on=ZONE_ID_COL_SHP,
        right_on=DEST_ZONE_COL_CSV
    )

    for col in ["base_count", "tele20_count", "tele40_count", "tele80_count"]:
        if col not in zones_compare.columns:
            zones_compare[col] = 0
        zones_compare[col] = zones_compare[col].fillna(0)

    zones_compare = normalize_count_by_area(zones_compare, "base_count", "base_density")
    zones_compare = normalize_count_by_area(zones_compare, "tele20_count", "tele20_density")
    zones_compare = normalize_count_by_area(zones_compare, "tele40_count", "tele40_density")
    zones_compare = normalize_count_by_area(zones_compare, "tele80_count", "tele80_density")

    zones_compare["change_20_density"] = zones_compare["tele20_density"] - zones_compare["base_density"]
    zones_compare["change_40_density"] = zones_compare["tele40_density"] - zones_compare["base_density"]
    zones_compare["change_80_density"] = zones_compare["tele80_density"] - zones_compare["base_density"]

    max_abs_change = max(
        zones_compare["change_20_density"].abs().max() if PLOT_TELE20 else 0,
        zones_compare["change_40_density"].abs().max() if PLOT_TELE40 else 0,
        zones_compare["change_80_density"].abs().max() if PLOT_TELE80 else 0
    )

    if pd.isna(max_abs_change) or max_abs_change == 0:
        max_abs_change = 1

    if PLOT_TELE20:
        plot_change_map(
            zones_gdf=zones_compare,
            change_col="change_20_density",
            title="20% Telework vs Base: Change in HOME→WORK Trip Destination Density by Zone",
            legend_label="Change in HOME→WORK trips per km² (20% telework - base)",
            output_png=OUTPUT_COMPARE20_PNG,
            max_abs_change=max_abs_change
        )

    if PLOT_TELE40:
        plot_change_map(
            zones_gdf=zones_compare,
            change_col="change_40_density",
            title="40% Telework vs Base: Change in HOME→WORK Trip Destination Density by Zone",
            legend_label="Change in HOME→WORK trips per km² (40% telework - base)",
            output_png=OUTPUT_COMPARE40_PNG,
            max_abs_change=max_abs_change
        )

    if PLOT_TELE80:
        plot_change_map(
            zones_gdf=zones_compare,
            change_col="change_80_density",
            title="80% Telework vs Base: Change in HOME→WORK Trip Destination Density by Zone",
            legend_label="Change in HOME→WORK trips per km² (80% telework - base)",
            output_png=OUTPUT_COMPARE80_PNG,
            max_abs_change=max_abs_change
        )

print("\nDone.")