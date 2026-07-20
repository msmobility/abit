import os
import pandas as pd
import geopandas as gpd
import matplotlib.pyplot as plt
import numpy as np
from matplotlib.colors import BoundaryNorm, LinearSegmentedColormap, ListedColormap

# =========================================================
# FILE PATHS
# =========================================================
ZONE_SHP = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\input\geo\zoneShapefile\zones_20231212.shp"

BASE_LEGS_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\50pct-sample-size\base-scenario-0-00pct-telework-50pct-sample-size\legs.csv"
TELE20_LEGS_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\50pct-sample-size\scenario-1-20pct-telework-50pct-sample-size\legs.csv"
TELE40_LEGS_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\50pct-sample-size\scenario-2-40pct-telework-50pct-sample-size\legs.csv"
TELE80_LEGS_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\50pct-sample-size\scenario-3-80pct-telework-50pct-sample-size\legs.csv"

OUTPUT_DIR = r"/viz/work_destination_density_switchable_50pct_sample"
os.makedirs(OUTPUT_DIR, exist_ok=True)

OUTPUT_BASE_PNG = os.path.join(OUTPUT_DIR, "base_density.png")
OUTPUT_COMPARE20_PNG = os.path.join(OUTPUT_DIR, "tele20_minus_base_density.png")
OUTPUT_COMPARE40_PNG = os.path.join(OUTPUT_DIR, "tele40_minus_base_density.png")
OUTPUT_COMPARE80_PNG = os.path.join(OUTPUT_DIR, "tele80_minus_base_density.png")

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
ZONE_AREA_COL_SHP = "Area"   # assumed to be in square meters
DEST_ZONE_COL_CSV = "end_zone"

PREV_PURPOSE_COL = "previous_purpose"
NEXT_PURPOSE_COL = "next_purpose"

# =========================================================
# USER SETTINGS
# =========================================================
AREA_UNIT = "km2"         # "m2" or "km2"

BASE_CLASSIFICATION = "quantile"      # "log", "equal", "quantile", "stddev"
CHANGE_CLASSIFICATION = "quantile"    # "log", "equal", "quantile", "stddev"

BASE_N_CLASSES = 6
CHANGE_N_CLASSES = 11     # must be odd: 5 blue + 1 white + 5 red
STD_STEP = 1.0
PNG_DPI = 600

# Width of neutral white bin for change maps.
# Increase if you want a broader white area around zero.
ZERO_BIN_HALF_WIDTH = 1e-9

TITLE_SIZE = 14
FIGSIZE = (12, 10)
EDGE_COLOR = "#D0D0D0"
EDGE_WIDTH = 0.04
AREA_EPSILON_M2 = 1e-9

TITLE_SUFFIX = "(50% sample size)"

# =========================================================
# COLORS
# =========================================================
NEG_COLOR = "#005792"
ZERO_COLOR = "#FFFFFF"
POS_COLOR = "#D5451B"

BASE_SEQ_COLORS = [
    "#FFFFFF",
    "#EAF3F8",
    "#D2E6F0",
    "#A9D0E3",
    "#73AFCB",
    "#3A87AD",
    "#005792",
]

# =========================================================
# HELPERS
# =========================================================
def clean_string_col(df, col):
    return df[col].astype(str).str.strip()

def get_area_divisor(area_unit):
    if area_unit == "m2":
        return 1.0
    elif area_unit == "km2":
        return 1_000_000.0
    else:
        raise ValueError("AREA_UNIT must be 'm2' or 'km2'")

def get_area_label(area_unit):
    if area_unit == "m2":
        return "m²"
    elif area_unit == "km2":
        return "km²"
    else:
        raise ValueError("AREA_UNIT must be 'm2' or 'km2'")

def compute_home_work_trip_counts(legs_csv, dest_zone_col, scenario_name):
    use_cols = [PREV_PURPOSE_COL, NEXT_PURPOSE_COL, dest_zone_col]

    legs = pd.read_csv(
        legs_csv,
        usecols=use_cols,
        dtype=str
    )
    legs.columns = [c.strip() for c in legs.columns]

    print(f"\nLoaded scenario: {scenario_name}")
    print("Rows before cleaning:", len(legs))

    required_cols = [PREV_PURPOSE_COL, NEXT_PURPOSE_COL, dest_zone_col]
    missing = [c for c in required_cols if c not in legs.columns]
    if missing:
        raise ValueError(f"Missing required columns in {scenario_name}: {missing}")

    legs[PREV_PURPOSE_COL] = clean_string_col(legs, PREV_PURPOSE_COL).str.upper()
    legs[NEXT_PURPOSE_COL] = clean_string_col(legs, NEXT_PURPOSE_COL).str.upper()
    legs[dest_zone_col] = clean_string_col(legs, dest_zone_col)

    legs = legs[
        legs[dest_zone_col].notna() &
        (legs[dest_zone_col] != "")
        ].copy()

    home_work_legs = legs[
        (legs[PREV_PURPOSE_COL] == "HOME") &
        (legs[NEXT_PURPOSE_COL] == "WORK")
        ].copy()

    print("HOME->WORK legs kept:", len(home_work_legs))

    zone_counts = (
        home_work_legs.groupby(dest_zone_col)
        .size()
        .reset_index(name="trip_end_count")
    )

    return zone_counts, len(home_work_legs)

def add_area_m2_from_attribute(zones_gdf):
    zones_gdf = zones_gdf.copy()

    if ZONE_ID_COL_SHP not in zones_gdf.columns:
        raise ValueError(f"Missing zone id column in shapefile: {ZONE_ID_COL_SHP}")

    if ZONE_AREA_COL_SHP not in zones_gdf.columns:
        raise ValueError(f"Missing zone area column in shapefile: {ZONE_AREA_COL_SHP}")

    zones_gdf[ZONE_ID_COL_SHP] = zones_gdf[ZONE_ID_COL_SHP].astype(str).str.strip()
    zones_gdf[ZONE_AREA_COL_SHP] = pd.to_numeric(zones_gdf[ZONE_AREA_COL_SHP], errors="coerce")

    zones_gdf["area_m2"] = zones_gdf[ZONE_AREA_COL_SHP].fillna(0.0)
    zones_gdf["area_m2_safe"] = zones_gdf["area_m2"].clip(lower=AREA_EPSILON_M2)
    return zones_gdf

def normalize_count_by_area(zones_gdf, count_col, density_col, area_unit="m2"):
    zones_gdf = zones_gdf.copy()
    divisor = get_area_divisor(area_unit)
    zones_gdf[density_col] = zones_gdf[count_col] / (zones_gdf["area_m2_safe"] / divisor)
    return zones_gdf

# =========================================================
# CLASSIFICATION FUNCTIONS
# =========================================================
def classify_equal_interval(values, n_classes):
    vals = pd.Series(values).replace([np.inf, -np.inf], np.nan).dropna()
    if vals.empty:
        return np.array([0.0, 1.0])

    vmin = float(vals.min())
    vmax = float(vals.max())

    if np.isclose(vmin, vmax):
        return np.array([vmin, vmax + 1e-12])

    return np.linspace(vmin, vmax, n_classes + 1)

def classify_quantile(values, n_classes):
    vals = pd.Series(values).replace([np.inf, -np.inf], np.nan).dropna()
    if vals.empty:
        return np.array([0.0, 1.0])

    probs = np.linspace(0, 1, n_classes + 1)
    breaks = np.quantile(vals, probs)
    breaks = np.unique(breaks)

    if len(breaks) < 2:
        v = float(vals.iloc[0])
        return np.array([v, v + 1e-12])

    return breaks

def classify_log(values, n_classes):
    vals = pd.Series(values).replace([np.inf, -np.inf], np.nan).dropna()
    vals = vals[vals > 0]

    if vals.empty:
        return np.array([0.0, 1.0])

    vmin = float(vals.min())
    vmax = float(vals.max())

    if np.isclose(vmin, vmax):
        return np.array([0.0, vmax])

    positive_breaks = np.geomspace(vmin, vmax, n_classes)
    breaks = np.concatenate(([0.0], positive_breaks))
    breaks = np.unique(breaks)

    if len(breaks) < 2:
        breaks = np.array([0.0, vmax])

    return breaks

def classify_stddev(values, step=1.0):
    vals = pd.Series(values).replace([np.inf, -np.inf], np.nan).dropna()
    if vals.empty:
        return np.array([0.0, 1.0])

    mean = float(vals.mean())
    std = float(vals.std(ddof=0))

    if std == 0 or not np.isfinite(std):
        vmin = float(vals.min())
        vmax = float(vals.max())
        if np.isclose(vmin, vmax):
            return np.array([vmin, vmax + 1e-12])
        return np.linspace(vmin, vmax, 5)

    max_dev = max(abs(vals.min() - mean), abs(vals.max() - mean))
    n_steps = int(np.ceil(max_dev / (std * step)))

    breaks = [mean + i * std * step for i in range(-n_steps, n_steps + 1)]
    breaks = np.array(sorted(set(breaks)))

    if breaks[0] > vals.min():
        breaks = np.insert(breaks, 0, vals.min())
    if breaks[-1] < vals.max():
        breaks = np.append(breaks, vals.max())

    return breaks

def get_base_breaks(values, method="log", n_classes=6, std_step=1.0):
    vals = pd.Series(values).replace([np.inf, -np.inf], np.nan).dropna()
    vals = vals[vals >= 0]

    if vals.empty:
        return np.array([0.0, 1.0])

    if method == "log":
        return classify_log(vals, n_classes)
    elif method == "equal":
        return classify_equal_interval(vals, n_classes)
    elif method == "quantile":
        return classify_quantile(vals, n_classes)
    elif method == "stddev":
        return classify_stddev(vals, std_step)
    else:
        raise ValueError("Unknown BASE_CLASSIFICATION")

def get_change_breaks(values, method="log", n_classes=11, std_step=1.0, zero_bin_half_width=1e-9):
    vals = pd.Series(values).replace([np.inf, -np.inf], np.nan).dropna()

    if vals.empty:
        return np.array([-1.0, -zero_bin_half_width, zero_bin_half_width, 1.0])

    neg = vals[vals < 0]
    pos = vals[vals > 0]

    n_side = max(1, (n_classes - 1) // 2)

    neg_edges = np.array([])
    pos_edges = np.array([])

    if method == "log":
        if len(neg) > 0:
            neg_abs = np.abs(neg)
            neg_edges = -np.geomspace(float(neg_abs.min()), float(neg_abs.max()), n_side + 1)[::-1]

        if len(pos) > 0:
            pos_edges = np.geomspace(float(pos.min()), float(pos.max()), n_side + 1)

    elif method == "equal":
        if len(neg) > 0:
            neg_edges = np.linspace(float(neg.min()), -zero_bin_half_width, n_side + 1)

        if len(pos) > 0:
            pos_edges = np.linspace(zero_bin_half_width, float(pos.max()), n_side + 1)

    elif method == "quantile":
        if len(neg) > 0:
            neg_edges = np.quantile(neg, np.linspace(0, 1, n_side + 1))
            neg_edges[-1] = -zero_bin_half_width

        if len(pos) > 0:
            pos_edges = np.quantile(pos, np.linspace(0, 1, n_side + 1))
            pos_edges[0] = zero_bin_half_width

    elif method == "stddev":
        mean = float(vals.mean())
        std = float(vals.std(ddof=0))

        if std == 0 or not np.isfinite(std):
            if len(neg) > 0:
                neg_edges = np.linspace(float(neg.min()), -zero_bin_half_width, n_side + 1)
            if len(pos) > 0:
                pos_edges = np.linspace(zero_bin_half_width, float(pos.max()), n_side + 1)
        else:
            if len(neg) > 0:
                neg_edges = np.linspace(float(neg.min()), -zero_bin_half_width, n_side + 1)
            if len(pos) > 0:
                pos_edges = np.linspace(zero_bin_half_width, float(pos.max()), n_side + 1)
    else:
        raise ValueError("Unknown CHANGE_CLASSIFICATION")

    if len(neg_edges) == 0:
        neg_edges = np.array([-1.0, -zero_bin_half_width])
    else:
        neg_edges[0] = min(neg_edges[0], float(vals.min()))
        neg_edges[-1] = -zero_bin_half_width

    if len(pos_edges) == 0:
        pos_edges = np.array([zero_bin_half_width, 1.0])
    else:
        pos_edges[0] = zero_bin_half_width
        pos_edges[-1] = max(pos_edges[-1], float(vals.max()))

    breaks = np.concatenate((
        neg_edges[:-1],
        [-zero_bin_half_width, zero_bin_half_width],
        pos_edges[1:]
    ))

    breaks = np.unique(breaks)

    if len(breaks) < 4:
        vmax = max(abs(float(vals.min())), abs(float(vals.max())))
        breaks = np.array([-vmax, -zero_bin_half_width, zero_bin_half_width, vmax])

    return breaks

# =========================================================
# COLORMAPS
# =========================================================
def make_white_to_blue_cmap(n_bins):
    if n_bins <= len(BASE_SEQ_COLORS):
        return ListedColormap(BASE_SEQ_COLORS[:n_bins])
    cmap = LinearSegmentedColormap.from_list("base_white_blue", ["#FFFFFF", "#005792"], N=n_bins)
    return ListedColormap(cmap(np.linspace(0, 1, n_bins)))

def make_diverging_zero_white_cmap():
    colors = [
        "#0B5A8F",  # darkest blue
        "#2E79A8",
        "#5C97BD",
        "#8DB6D1",
        "#BED7E8",
        "#FFFFFF",  # white center
        "#F3D1C3",
        "#E7A88F",
        "#D97857",
        "#C9512C",
        "#A63A16",  # darkest red/orange
    ]
    return ListedColormap(colors)

# =========================================================
# PLOTTING
# =========================================================
def plot_base_map(zones_gdf, output_png, sample_size_text):
    fig, ax = plt.subplots(figsize=FIGSIZE)

    vals = zones_gdf["base_density"].replace([np.inf, -np.inf], np.nan).dropna()
    breaks = get_base_breaks(vals, method=BASE_CLASSIFICATION, n_classes=BASE_N_CLASSES, std_step=STD_STEP)

    n_bins = len(breaks) - 1
    cmap = make_white_to_blue_cmap(n_bins)
    norm = BoundaryNorm(breaks, ncolors=cmap.N, clip=True)

    area_label = get_area_label(AREA_UNIT)

    zones_gdf.plot(
        column="base_density",
        ax=ax,
        legend=True,
        cmap=cmap,
        norm=norm,
        edgecolor=EDGE_COLOR,
        linewidth=EDGE_WIDTH,
        legend_kwds={
            "label": f"HOME→WORK trips per {area_label}",
            "boundaries": breaks,
            "ticks": breaks
        }
    )

    ax.set_title(
        f"HOME→WORK trip destination density by zone {TITLE_SUFFIX}\n"
        f"Base scenario | n = {sample_size_text} | classification = {BASE_CLASSIFICATION}",
        fontsize=TITLE_SIZE
    )
    ax.set_axis_off()

    plt.tight_layout()
    plt.savefig(output_png, dpi=PNG_DPI, bbox_inches="tight")
    plt.show()

    print("\nBase breaks:")
    print(breaks)

def plot_change_map(zones_gdf, change_col, scenario_label, sample_size_text, output_png):
    fig, ax = plt.subplots(figsize=FIGSIZE)

    vals = zones_gdf[change_col].replace([np.inf, -np.inf], np.nan).dropna()
    breaks = get_change_breaks(
        vals,
        method=CHANGE_CLASSIFICATION,
        n_classes=CHANGE_N_CLASSES,
        std_step=STD_STEP,
        zero_bin_half_width=ZERO_BIN_HALF_WIDTH
    )

    cmap = make_diverging_zero_white_cmap()
    norm = BoundaryNorm(breaks, ncolors=cmap.N, clip=True)

    area_label = get_area_label(AREA_UNIT)

    tick_values = [
        b for b in breaks
        if not np.isclose(b, -ZERO_BIN_HALF_WIDTH) and not np.isclose(b, ZERO_BIN_HALF_WIDTH)
    ]

    zones_gdf.plot(
        column=change_col,
        ax=ax,
        legend=True,
        cmap=cmap,
        norm=norm,
        edgecolor=EDGE_COLOR,
        linewidth=EDGE_WIDTH,
        legend_kwds={
            "label": f"Change in HOME→WORK trips per {area_label}",
            "boundaries": breaks,
            "ticks": tick_values
        }
    )

    ax.set_title(
        f"HOME→WORK trip destination density change by zone {TITLE_SUFFIX}\n"
        f"{scenario_label} | n = {sample_size_text} | classification = {CHANGE_CLASSIFICATION}",
        fontsize=TITLE_SIZE
    )
    ax.set_axis_off()

    plt.tight_layout()
    plt.savefig(output_png, dpi=PNG_DPI, bbox_inches="tight")
    plt.show()

    print(f"\nBreaks for {change_col}:")
    print(breaks)

# =========================================================
# LOAD ZONES
# =========================================================
zones = gpd.read_file(ZONE_SHP)
zones = add_area_m2_from_attribute(zones)

# =========================================================
# COMPUTE COUNTS
# =========================================================
base_counts, base_n = (
    compute_home_work_trip_counts(BASE_LEGS_CSV, DEST_ZONE_COL_CSV, "base")
    if PLOT_BASE else (None, 0)
)
if PLOT_BASE:
    base_counts = base_counts.rename(columns={"trip_end_count": "base_count"})

tele20_counts, tele20_n = (
    compute_home_work_trip_counts(TELE20_LEGS_CSV, DEST_ZONE_COL_CSV, "telework_20")
    if PLOT_TELE20 else (None, 0)
)
if PLOT_TELE20:
    tele20_counts = tele20_counts.rename(columns={"trip_end_count": "tele20_count"})

tele40_counts, tele40_n = (
    compute_home_work_trip_counts(TELE40_LEGS_CSV, DEST_ZONE_COL_CSV, "telework_40")
    if PLOT_TELE40 else (None, 0)
)
if PLOT_TELE40:
    tele40_counts = tele40_counts.rename(columns={"trip_end_count": "tele40_count"})

tele80_counts, tele80_n = (
    compute_home_work_trip_counts(TELE80_LEGS_CSV, DEST_ZONE_COL_CSV, "telework_80")
    if PLOT_TELE80 else (None, 0)
)
if PLOT_TELE80:
    tele80_counts = tele80_counts.rename(columns={"trip_end_count": "tele80_count"})

# =========================================================
# BASE MAP
# =========================================================
if PLOT_BASE:
    zones_base = zones.merge(
        base_counts,
        how="left",
        left_on=ZONE_ID_COL_SHP,
        right_on=DEST_ZONE_COL_CSV
    )

    zones_base["base_count"] = zones_base["base_count"].fillna(0)
    zones_base = normalize_count_by_area(zones_base, "base_count", "base_density", AREA_UNIT)

    plot_base_map(zones_base, OUTPUT_BASE_PNG, base_n)

# =========================================================
# CHANGE MAPS
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

    zones_compare = normalize_count_by_area(zones_compare, "base_count", "base_density", AREA_UNIT)
    zones_compare = normalize_count_by_area(zones_compare, "tele20_count", "tele20_density", AREA_UNIT)
    zones_compare = normalize_count_by_area(zones_compare, "tele40_count", "tele40_density", AREA_UNIT)
    zones_compare = normalize_count_by_area(zones_compare, "tele80_count", "tele80_density", AREA_UNIT)

    zones_compare["change_20_density"] = zones_compare["tele20_density"] - zones_compare["base_density"]
    zones_compare["change_40_density"] = zones_compare["tele40_density"] - zones_compare["base_density"]
    zones_compare["change_80_density"] = zones_compare["tele80_density"] - zones_compare["base_density"]

    if PLOT_TELE20:
        plot_change_map(
            zones_compare,
            "change_20_density",
            "20% telework vs base",
            tele20_n,
            OUTPUT_COMPARE20_PNG
        )

    if PLOT_TELE40:
        plot_change_map(
            zones_compare,
            "change_40_density",
            "40% telework vs base",
            tele40_n,
            OUTPUT_COMPARE40_PNG
        )

    if PLOT_TELE80:
        plot_change_map(
            zones_compare,
            "change_80_density",
            "80% telework vs base",
            tele80_n,
            OUTPUT_COMPARE80_PNG
        )

print("\nDone.")