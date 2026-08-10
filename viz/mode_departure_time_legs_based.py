import os
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

# =========================
# 1. File paths
# =========================
script_dir = os.path.dirname(os.path.abspath(__file__))

BASE_LEGS_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\base-scenario-0-percent-telework\legs.csv"
TELE20_LEGS_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\scenario-1-20-percent-telework\legs.csv"
TELE40_LEGS_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\scenario-2-40-percent-telework\legs.csv"
TELE80_LEGS_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\scenario-3-80-percent-telework\legs.csv"

OUTPUT_DIR = r"/viz/departure_time_legs_20pct_sample"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# =========================
# 2. Scenario switches
# =========================
PLOT_BASE = True
PLOT_TELE20 = True
PLOT_TELE40 = True
PLOT_TELE80 = True

# =========================
# 3. Settings
# =========================
SCENARIO_BASE = "Base"
SCENARIO_TELE20 = "20% Telework"
SCENARIO_TELE40 = "40% Telework"
SCENARIO_TELE80 = "80% Telework"

USE_WEEKDAY_NAMES = True
BIN_MINUTES = 15

# mode groups
MODE_GROUPS = {
    "pt": {"TRAIN", "BUS", "TRAM_METRO"},
    "car": {"CAR_PASSENGER", "CAR_DRIVER"},
}

# palette
COLOR_BASE_FILL = "#E8E2DB"
COLOR_BASE_LINE = "#4D4D4D"
COLOR_TELE20 = "#FAB95B"
COLOR_TELE40 = "#547792"
COLOR_TELE80 = "#1A3263"

LINESTYLE_BASE = "-"
LINESTYLE_TELE20 = "--"
LINESTYLE_TELE40 = "-."
LINESTYLE_TELE80 = ":"

LINEWIDTH_BASE = 1
LINEWIDTH_TELE20 = 1.5
LINEWIDTH_TELE40 = 1.5
LINEWIDTH_TELE80 = 1.5

# =========================
# 4. Helpers
# =========================
def get_day_labels(use_weekday_names=False):
    if use_weekday_names:
        return {
            1: "Mon",
            2: "Tue",
            3: "Wed",
            4: "Thu",
            5: "Fri",
            6: "Sat",
            7: "Sun",
        }
    return {
        1: "Day_1",
        2: "Day_2",
        3: "Day_3",
        4: "Day_4",
        5: "Day_5",
        6: "Day_6",
        7: "Day_7",
    }

def set_time_axis_odd_ticks(ax, values_in_hours, pad_hours=0.15):
    vals = pd.Series(values_in_hours).dropna()
    if vals.empty:
        ax.set_xlim(0, 24)
        odd_ticks = np.arange(1, 24, 2)
        ax.set_xticks(odd_ticks)
        ax.set_xticklabels([f"{int(t):02d}:00" for t in odd_ticks], rotation=45)
        return

    xmin = max(0, vals.min() - pad_hours)
    xmax = min(24, vals.max() + pad_hours)
    ax.set_xlim(xmin, xmax)

    odd_ticks = np.arange(1, 24, 2)
    odd_ticks = odd_ticks[(odd_ticks >= np.floor(xmin)) & (odd_ticks <= np.ceil(xmax))]

    if len(odd_ticks) == 0:
        mid = int(np.round((xmin + xmax) / 2.0))
        if mid % 2 == 0:
            mid += 1
        odd_ticks = np.array([min(max(mid, 1), 23)])

    ax.set_xticks(odd_ticks)
    ax.set_xticklabels([f"{int(t):02d}:00" for t in odd_ticks], rotation=45)

def prepare_leg_departure_counts(legs_csv, scenario_name, allowed_modes, mode_group_name):
    print(f"\nLoading scenario: {scenario_name} | mode group: {mode_group_name}")
    print("CSV path:", legs_csv)
    print("CSV exists:", os.path.exists(legs_csv))

    df = pd.read_csv(legs_csv)
    df.columns = [c.strip() for c in df.columns]

    print("Rows before cleaning:", len(df))
    print("Columns:", df.columns.tolist())

    required_cols = ["start_time_min", "mode"]
    missing = [c for c in required_cols if c not in df.columns]
    if missing:
        raise ValueError(f"Missing required columns in {scenario_name}: {missing}")

    df["start_time_min"] = pd.to_numeric(df["start_time_min"], errors="coerce")
    df["mode"] = df["mode"].astype(str).str.upper().str.strip()

    df = df.dropna(subset=["start_time_min"]).copy()
    print("Rows after cleaning:", len(df))

    # keep only selected modes
    df = df[df["mode"].isin(allowed_modes)].copy()
    print(f"{mode_group_name.upper()} legs kept:", len(df))
    print("Modes found after filter:", sorted(df["mode"].dropna().unique().tolist()))

    # derive day index from absolute start time
    # day 1 = minutes 0..1439, day 2 = 1440..2879, etc.
    df["day_index"] = (df["start_time_min"] // 1440).astype(int) + 1

    # keep only days 1..7
    df = df[(df["day_index"] >= 1) & (df["day_index"] <= 7)].copy()
    print("Rows after restricting to days 1..7:", len(df))
    print("Derived day indices:", sorted(df["day_index"].unique()))

    df["minute_in_day"] = df["start_time_min"] % 1440
    df["time_bin"] = ((df["minute_in_day"] // BIN_MINUTES) * BIN_MINUTES).astype(int)
    df["scenario"] = scenario_name

    counts = (
        df.groupby(["scenario", "day_index", "time_bin"])
        .size()
        .reset_index(name="n_legs")
    )

    all_days = list(range(1, 8))
    all_times = np.arange(0, 24 * 60, BIN_MINUTES)

    full_index = pd.MultiIndex.from_product(
        [[scenario_name], all_days, all_times],
        names=["scenario", "day_index", "time_bin"]
    )

    counts = (
        counts.set_index(["scenario", "day_index", "time_bin"])
        .reindex(full_index, fill_value=0)
        .reset_index()
    )

    print("Prepared rows for plotting:", len(counts))
    return counts

def add_percent_vs_baseline(day_df, baseline_day_total):
    day_df = day_df.copy()
    if baseline_day_total > 0:
        day_df["pct_vs_base"] = 100.0 * day_df["n_legs"] / baseline_day_total
    else:
        day_df["pct_vs_base"] = 0.0
    return day_df

def load_all_scenarios_for_mode(mode_key, allowed_modes):
    base_counts = prepare_leg_departure_counts(BASE_LEGS_CSV, SCENARIO_BASE, allowed_modes, mode_key) if PLOT_BASE else None
    tele20_counts = prepare_leg_departure_counts(TELE20_LEGS_CSV, SCENARIO_TELE20, allowed_modes, mode_key) if PLOT_TELE20 else None
    tele40_counts = prepare_leg_departure_counts(TELE40_LEGS_CSV, SCENARIO_TELE40, allowed_modes, mode_key) if PLOT_TELE40 else None
    tele80_counts = prepare_leg_departure_counts(TELE80_LEGS_CSV, SCENARIO_TELE80, allowed_modes, mode_key) if PLOT_TELE80 else None
    return base_counts, tele20_counts, tele40_counts, tele80_counts

# =========================
# 5. Plotting
# =========================
def plot_mode_group(mode_key, allowed_modes):
    day_labels = get_day_labels(USE_WEEKDAY_NAMES)

    base_counts, tele20_counts, tele40_counts, tele80_counts = load_all_scenarios_for_mode(mode_key, allowed_modes)

    for d in range(1, 8):
        fig, ax = plt.subplots(figsize=(12, 6))

        print(f"\n=========================")
        print(f"Plotting {mode_key.upper()} day {d} - {day_labels[d]}")
        print(f"=========================")

        plotted_hours = []
        plotted_pcts = []

        baseline_day_total = 0

        if PLOT_BASE:
            base_day = base_counts[base_counts["day_index"] == d].sort_values("time_bin").copy()
            baseline_day_total = int(base_day["n_legs"].sum())

            base_day = add_percent_vs_baseline(base_day, baseline_day_total)
            base_day["time_h"] = base_day["time_bin"] / 60.0

            plotted_hours.append(base_day["time_h"])
            plotted_pcts.append(base_day["pct_vs_base"])

            ax.fill_between(
                base_day["time_h"],
                0,
                base_day["pct_vs_base"],
                color=COLOR_BASE_FILL,
                alpha=0.95,
                label=f"{SCENARIO_BASE} area (daily {mode_key.upper()} legs n={baseline_day_total})"
            )

            ax.plot(
                base_day["time_h"],
                base_day["pct_vs_base"],
                color=COLOR_BASE_LINE,
                linestyle=LINESTYLE_BASE,
                linewidth=LINEWIDTH_BASE
            )

            print(f"{SCENARIO_BASE}: daily {mode_key.upper()} legs total={baseline_day_total}")

        if PLOT_TELE20:
            tele20_day = tele20_counts[tele20_counts["day_index"] == d].sort_values("time_bin").copy()
            total_tele20 = int(tele20_day["n_legs"].sum())
            pct_change_20 = 100.0 * (total_tele20 - baseline_day_total) / baseline_day_total if baseline_day_total > 0 else 0.0

            tele20_day = add_percent_vs_baseline(tele20_day, baseline_day_total)
            tele20_day["time_h"] = tele20_day["time_bin"] / 60.0

            plotted_hours.append(tele20_day["time_h"])
            plotted_pcts.append(tele20_day["pct_vs_base"])

            ax.plot(
                tele20_day["time_h"],
                tele20_day["pct_vs_base"],
                color=COLOR_TELE20,
                linestyle=LINESTYLE_TELE20,
                linewidth=LINEWIDTH_TELE20,
                label=f"{SCENARIO_TELE20} (daily {mode_key.upper()} legs n={total_tele20}, total vs base={pct_change_20:+.1f}%)"
            )

            print(f"{SCENARIO_TELE20}: daily {mode_key.upper()} legs total={total_tele20}")

        if PLOT_TELE40:
            tele40_day = tele40_counts[tele40_counts["day_index"] == d].sort_values("time_bin").copy()
            total_tele40 = int(tele40_day["n_legs"].sum())
            pct_change_40 = 100.0 * (total_tele40 - baseline_day_total) / baseline_day_total if baseline_day_total > 0 else 0.0

            tele40_day = add_percent_vs_baseline(tele40_day, baseline_day_total)
            tele40_day["time_h"] = tele40_day["time_bin"] / 60.0

            plotted_hours.append(tele40_day["time_h"])
            plotted_pcts.append(tele40_day["pct_vs_base"])

            ax.plot(
                tele40_day["time_h"],
                tele40_day["pct_vs_base"],
                color=COLOR_TELE40,
                linestyle=LINESTYLE_TELE40,
                linewidth=LINEWIDTH_TELE40,
                label=f"{SCENARIO_TELE40} (daily {mode_key.upper()} legs n={total_tele40}, total vs base={pct_change_40:+.1f}%)"
            )

            print(f"{SCENARIO_TELE40}: daily {mode_key.upper()} legs total={total_tele40}")

        if PLOT_TELE80:
            tele80_day = tele80_counts[tele80_counts["day_index"] == d].sort_values("time_bin").copy()
            total_tele80 = int(tele80_day["n_legs"].sum())
            pct_change_80 = 100.0 * (total_tele80 - baseline_day_total) / baseline_day_total if baseline_day_total > 0 else 0.0

            tele80_day = add_percent_vs_baseline(tele80_day, baseline_day_total)
            tele80_day["time_h"] = tele80_day["time_bin"] / 60.0

            plotted_hours.append(tele80_day["time_h"])
            plotted_pcts.append(tele80_day["pct_vs_base"])

            ax.plot(
                tele80_day["time_h"],
                tele80_day["pct_vs_base"],
                color=COLOR_TELE80,
                linestyle=LINESTYLE_TELE80,
                linewidth=LINEWIDTH_TELE80,
                label=f"{SCENARIO_TELE80} (daily {mode_key.upper()} legs n={total_tele80}, total vs base={pct_change_80:+.1f}%)"
            )

            print(f"{SCENARIO_TELE80}: daily {mode_key.upper()} legs total={total_tele80}")

        ax.set_title(f"Daily {mode_key.upper()} leg departure times relative to baseline ({BIN_MINUTES}-min bins) - {day_labels[d]}")
        ax.set_xlabel("Departure time")
        ax.set_ylabel(f"{mode_key.upper()} leg departures as share of baseline day total (%)")

        if plotted_hours:
            all_hours = pd.concat(plotted_hours, ignore_index=True)
            set_time_axis_odd_ticks(ax, all_hours, pad_hours=0.15)

        if plotted_pcts:
            all_pcts = pd.concat(plotted_pcts, ignore_index=True)
            ymax = max(5.0, all_pcts.max() * 1.08)
            ax.set_ylim(0, ymax)

        ax.grid(True, alpha=0.3)
        ax.legend(loc="best", fontsize=9, frameon=True)

        plt.tight_layout()

        output_file = os.path.join(
            OUTPUT_DIR,
            f"{mode_key}_leg_departure_share_vs_base_{BIN_MINUTES}min_{day_labels[d]}.png"
        )
        plt.savefig(output_file, dpi=300, bbox_inches="tight")
        print("Saved plot to:", output_file)

        plt.close()

# =========================
# 6. Run
# =========================
for mode_key, allowed_modes in MODE_GROUPS.items():
    plot_mode_group(mode_key, allowed_modes)

print("\nAll daily comparison plots relative to baseline saved successfully.")