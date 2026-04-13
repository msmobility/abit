import os
import pandas as pd
import numpy as np

# =========================================================
# FILE PATHS
# =========================================================
BASE_LEGS_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\base-scenario-0-percent-telework\legs.csv"
TELE20_LEGS_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\scenario-1-20-percent-telework\legs.csv"
TELE40_LEGS_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\scenario-2-40-percent-telework\legs.csv"
TELE80_LEGS_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\scenario-3-80-percent-telework\legs.csv"

BASE_PERSON_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\base-scenario-0-percent-telework\person_summary.csv"
TELE20_PERSON_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\scenario-1-20-percent-telework\person_summary.csv"
TELE40_PERSON_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\scenario-2-40-percent-telework\person_summary.csv"
TELE80_PERSON_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\scenario-3-80-percent-telework\person_summary.csv"

OUTPUT_DIR = r"C:\Users\Nawidullah\IdeaProjects\abit\viz\zone_increase_new_workers"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# =========================================================
# SETTINGS
# =========================================================
SCENARIOS = {
    "base": {
        "legs_csv": BASE_LEGS_CSV,
        "person_csv": BASE_PERSON_CSV,
    },
    "tele20": {
        "legs_csv": TELE20_LEGS_CSV,
        "person_csv": TELE20_PERSON_CSV,
    },
    "tele40": {
        "legs_csv": TELE40_LEGS_CSV,
        "person_csv": TELE40_PERSON_CSV,
    },
    "tele80": {
        "legs_csv": TELE80_LEGS_CSV,
        "person_csv": TELE80_PERSON_CSV,
    },
}

DAYS_TO_KEEP = list(range(1, 8))
REMOVE_SAME_COORD_HOME_WORK_LEGS = True

ONLY_EMPLOYED = True
ONLY_CAN_TELEWORK = False

# =========================================================
# COLUMN NAMES
# =========================================================
PERSON_ID_COL = "Person"
OCCUPATION_COL = "Occupation"
CAN_TELEWORK_COL = "canTelework"
HABITUAL_MODE_COL = "HabitualMode"
AGE_COL = "Age"
GENDER_COL = "Gender"

LEG_PERSON = "person_id"
LEG_PREV = "previous_purpose"
LEG_NEXT = "next_purpose"
LEG_START = "start_time_min"
LEG_MODE = "mode"
LEG_TIME = "time_min"
LEG_START_X = "start_x"
LEG_START_Y = "start_y"
LEG_END_X = "end_x"
LEG_END_Y = "end_y"
LEG_END_ZONE = "end_zone"

# =========================================================
# HELPERS
# =========================================================
def load_csv(path, name):
    df = pd.read_csv(path)
    df.columns = [c.strip() for c in df.columns]
    print(f"Loaded {name}: {path}")
    print(f"Rows: {len(df)}")
    print(f"Columns: {df.columns.tolist()}")
    print()
    return df

def clean_str(df, col):
    if col in df.columns:
        df[col] = df[col].astype(str).str.strip()
    return df

def to_num(df, cols):
    for c in cols:
        if c in df.columns:
            df[c] = pd.to_numeric(df[c], errors="coerce")
    return df

def normalize_bool_series(s):
    return (
        s.astype(str)
        .str.strip()
        .str.lower()
        .map({"true": True, "false": False})
    )

def normalize_purpose(s):
    if pd.isna(s):
        return np.nan
    return str(s).strip().upper()

def minutes_to_hhmm(x):
    if pd.isna(x):
        return ""
    x = int(round(float(x)))
    hh = (x // 60) % 24
    mm = x % 60
    return f"{hh:02d}:{mm:02d}"

def load_and_filter_persons(person_csv, scenario_name):
    persons = load_csv(person_csv, f"{scenario_name} persons")

    required_person_cols = [PERSON_ID_COL, OCCUPATION_COL, CAN_TELEWORK_COL]
    missing = [c for c in required_person_cols if c not in persons.columns]
    if missing:
        raise ValueError(f"Missing required columns in {scenario_name} person file: {missing}")

    persons = clean_str(persons, PERSON_ID_COL)
    persons = clean_str(persons, OCCUPATION_COL)
    persons = clean_str(persons, HABITUAL_MODE_COL)
    persons = clean_str(persons, GENDER_COL)

    persons[OCCUPATION_COL] = persons[OCCUPATION_COL].str.upper()
    persons[CAN_TELEWORK_COL] = normalize_bool_series(persons[CAN_TELEWORK_COL])

    if ONLY_EMPLOYED:
        persons = persons[persons[OCCUPATION_COL] == "EMPLOYED"].copy()

    if ONLY_CAN_TELEWORK:
        persons = persons[persons[CAN_TELEWORK_COL] == True].copy()

    persons[PERSON_ID_COL] = persons[PERSON_ID_COL].astype(str)
    print(f"{scenario_name}: workers kept from person file = {len(persons)}")
    return persons

def load_home_work_legs(legs_csv, scenario_name, persons_df):
    worker_ids = set(persons_df[PERSON_ID_COL].astype(str))

    legs = load_csv(legs_csv, f"{scenario_name} legs")

    required_cols = [
        LEG_PERSON, LEG_PREV, LEG_NEXT, LEG_START, LEG_MODE, LEG_TIME,
        LEG_END_ZONE, LEG_START_X, LEG_START_Y, LEG_END_X, LEG_END_Y
    ]
    missing = [c for c in required_cols if c not in legs.columns]
    if missing:
        raise ValueError(f"Missing required columns in {scenario_name}: {missing}")

    for col in [LEG_PERSON, LEG_PREV, LEG_NEXT, LEG_MODE, LEG_END_ZONE]:
        legs = clean_str(legs, col)

    legs = to_num(
        legs,
        [LEG_START, LEG_TIME, LEG_START_X, LEG_START_Y, LEG_END_X, LEG_END_Y]
    )

    legs[LEG_PREV] = legs[LEG_PREV].apply(normalize_purpose)
    legs[LEG_NEXT] = legs[LEG_NEXT].apply(normalize_purpose)
    legs[LEG_MODE] = legs[LEG_MODE].astype(str).str.strip().str.upper()
    legs[LEG_PERSON] = legs[LEG_PERSON].astype(str)

    legs = legs.dropna(subset=[LEG_PERSON, LEG_START, LEG_END_ZONE]).copy()
    legs = legs[legs[LEG_PERSON].isin(worker_ids)].copy()

    # derive day from absolute minute
    legs["day"] = (legs[LEG_START] // 1440).astype(int) + 1
    legs = legs[legs["day"].isin(DAYS_TO_KEEP)].copy()
    legs["dep_hhmm"] = legs[LEG_START].apply(minutes_to_hhmm)

    # keep only HOME -> WORK
    hw = legs[
        (legs[LEG_PREV] == "HOME") &
        (legs[LEG_NEXT] == "WORK")
        ].copy()

    if REMOVE_SAME_COORD_HOME_WORK_LEGS:
        same_coord_mask = (
                hw[LEG_START_X].notna() &
                hw[LEG_START_Y].notna() &
                hw[LEG_END_X].notna() &
                hw[LEG_END_Y].notna() &
                (hw[LEG_START_X] == hw[LEG_END_X]) &
                (hw[LEG_START_Y] == hw[LEG_END_Y])
        )
        removed = int(same_coord_mask.sum())
        if removed > 0:
            print(f"{scenario_name}: removed same-coordinate HOME->WORK no-trip legs = {removed}")
        hw = hw[~same_coord_mask].copy()

    # merge demographics
    demo_cols = [
        c for c in [PERSON_ID_COL, AGE_COL, GENDER_COL, OCCUPATION_COL, HABITUAL_MODE_COL, CAN_TELEWORK_COL]
        if c in persons_df.columns
    ]
    hw = hw.merge(persons_df[demo_cols], left_on=LEG_PERSON, right_on=PERSON_ID_COL, how="left")

    print(f"{scenario_name}: HOME->WORK legs kept = {len(hw)}")
    return hw

def build_zone_counts(hw_df, scenario_name):
    counts = (
        hw_df.groupby(LEG_END_ZONE)
        .size()
        .reset_index(name=f"{scenario_name}_count")
    )
    return counts

def build_person_zone_counts(hw_df, scenario_name):
    pz = (
        hw_df.groupby([LEG_PERSON, LEG_END_ZONE])
        .size()
        .reset_index(name=f"{scenario_name}_person_zone_count")
    )
    return pz

def build_person_zone_day_details(hw_df, scenario_name):
    keep_cols = [
        LEG_PERSON, LEG_END_ZONE, "day", LEG_START, "dep_hhmm",
        LEG_MODE, LEG_TIME, AGE_COL, GENDER_COL, OCCUPATION_COL,
        HABITUAL_MODE_COL, CAN_TELEWORK_COL
    ]
    keep_cols = [c for c in keep_cols if c in hw_df.columns]

    details = hw_df[keep_cols].copy()
    details["scenario"] = scenario_name
    return details

# =========================================================
# 1. LOAD ALL SCENARIOS
# =========================================================
scenario_hw = {}
scenario_zone_counts = {}
scenario_person_zone_counts = {}

for scenario_name, paths in SCENARIOS.items():
    persons_df = load_and_filter_persons(paths["person_csv"], scenario_name)
    hw_df = load_home_work_legs(paths["legs_csv"], scenario_name, persons_df)

    scenario_hw[scenario_name] = hw_df
    scenario_zone_counts[scenario_name] = build_zone_counts(hw_df, scenario_name)
    scenario_person_zone_counts[scenario_name] = build_person_zone_counts(hw_df, scenario_name)

# =========================================================
# 2. FIND ZONES WITH INCREASED HOME->WORK TRIPS
# =========================================================
base_zone = scenario_zone_counts["base"].copy()

for sc in ["tele20", "tele40", "tele80"]:
    zone_comp = base_zone.merge(
        scenario_zone_counts[sc],
        how="outer",
        on=LEG_END_ZONE
    )

    zone_comp["base_count"] = zone_comp["base_count"].fillna(0).astype(int)
    zone_comp[f"{sc}_count"] = zone_comp[f"{sc}_count"].fillna(0).astype(int)

    zone_comp["delta_count"] = zone_comp[f"{sc}_count"] - zone_comp["base_count"]
    zone_comp["pct_change_vs_base"] = np.where(
        zone_comp["base_count"] > 0,
        100.0 * zone_comp["delta_count"] / zone_comp["base_count"],
        np.nan
    )

    increased_zones = zone_comp[zone_comp["delta_count"] > 0].copy()
    increased_zones = increased_zones.sort_values("delta_count", ascending=False).reset_index(drop=True)

    out_zone = os.path.join(OUTPUT_DIR, f"{sc}_zones_with_increased_home_work_trips.csv")
    increased_zones.to_csv(out_zone, index=False)
    print(f"Saved: {out_zone}")

# =========================================================
# 3. FIND WHICH PERSONS CAUSED NEW PERSON-ZONE LINKS
# =========================================================
base_pz = scenario_person_zone_counts["base"].copy()

for sc in ["tele20", "tele40", "tele80"]:
    sc_pz = scenario_person_zone_counts[sc].copy()

    pz_comp = base_pz.merge(
        sc_pz,
        how="outer",
        on=[LEG_PERSON, LEG_END_ZONE]
    )

    pz_comp["base_person_zone_count"] = pz_comp["base_person_zone_count"].fillna(0).astype(int)
    pz_comp[f"{sc}_person_zone_count"] = pz_comp[f"{sc}_person_zone_count"].fillna(0).astype(int)

    # A) NEW PERSON-ZONE LINK:
    # person had 0 HOME->WORK trips to that zone in base, but >0 in scenario
    pz_comp["is_new_person_zone_link"] = (
            (pz_comp["base_person_zone_count"] == 0) &
            (pz_comp[f"{sc}_person_zone_count"] > 0)
    ).astype(int)

    # B) INCREASED FREQUENCY TO SAME ZONE:
    pz_comp["is_increased_existing_person_zone"] = (
            (pz_comp["base_person_zone_count"] > 0) &
            (pz_comp[f"{sc}_person_zone_count"] > pz_comp["base_person_zone_count"])
    ).astype(int)

    pz_comp["delta_person_zone_count"] = (
            pz_comp[f"{sc}_person_zone_count"] - pz_comp["base_person_zone_count"]
    )

    # Keep only rows relevant to increased zones at aggregate level
    zone_comp = pd.read_csv(os.path.join(OUTPUT_DIR, f"{sc}_zones_with_increased_home_work_trips.csv"))
    increased_zone_ids = set(zone_comp[LEG_END_ZONE].astype(str))

    pz_comp[LEG_END_ZONE] = pz_comp[LEG_END_ZONE].astype(str)
    pz_relevant = pz_comp[pz_comp[LEG_END_ZONE].isin(increased_zone_ids)].copy()

    out_pz = os.path.join(OUTPUT_DIR, f"{sc}_person_zone_changes_in_increased_zones.csv")
    pz_relevant.to_csv(out_pz, index=False)
    print(f"Saved: {out_pz}")

    # only truly new person-zone links
    pz_new = pz_relevant[pz_relevant["is_new_person_zone_link"] == 1].copy()
    out_pz_new = os.path.join(OUTPUT_DIR, f"{sc}_new_person_zone_links_only.csv")
    pz_new.to_csv(out_pz_new, index=False)
    print(f"Saved: {out_pz_new}")

# =========================================================
# 4. ADD PERSON-DAY DETAILS FOR THE NEW WORK TRIPS
# =========================================================
base_details = build_person_zone_day_details(scenario_hw["base"], "base")

for sc in ["tele20", "tele40", "tele80"]:
    sc_details = build_person_zone_day_details(scenario_hw[sc], sc)

    new_links = pd.read_csv(os.path.join(OUTPUT_DIR, f"{sc}_new_person_zone_links_only.csv"))
    new_links[LEG_PERSON] = new_links[LEG_PERSON].astype(str)
    new_links[LEG_END_ZONE] = new_links[LEG_END_ZONE].astype(str)

    sc_details[LEG_PERSON] = sc_details[LEG_PERSON].astype(str)
    sc_details[LEG_END_ZONE] = sc_details[LEG_END_ZONE].astype(str)

    # scenario-side details for those new links
    new_trip_details = sc_details.merge(
        new_links[[LEG_PERSON, LEG_END_ZONE, f"{sc}_person_zone_count", "delta_person_zone_count"]],
        on=[LEG_PERSON, LEG_END_ZONE],
        how="inner"
    )

    new_trip_details = new_trip_details.sort_values([LEG_END_ZONE, LEG_PERSON, "day", LEG_START]).reset_index(drop=True)

    out_details = os.path.join(OUTPUT_DIR, f"{sc}_new_work_trip_details_by_person_zone.csv")
    new_trip_details.to_csv(out_details, index=False)
    print(f"Saved: {out_details}")

# =========================================================
# 5. OPTIONAL SUMMARY: WHICH PERSONS APPEAR MOST OFTEN AS NEW CONTRIBUTORS
# =========================================================
for sc in ["tele20", "tele40", "tele80"]:
    details = pd.read_csv(os.path.join(OUTPUT_DIR, f"{sc}_new_work_trip_details_by_person_zone.csv"))

    person_summary = (
        details.groupby(LEG_PERSON, as_index=False)
        .agg(
            n_new_trip_rows=("day", "size"),
            n_new_zones=(LEG_END_ZONE, pd.Series.nunique),
            habitual_mode=(HABITUAL_MODE_COL, "first"),
            can_telework=(CAN_TELEWORK_COL, "first"),
            occupation=(OCCUPATION_COL, "first"),
            gender=(GENDER_COL, "first"),
            age=(AGE_COL, "first"),
        )
        .sort_values(["n_new_zones", "n_new_trip_rows"], ascending=False)
    )

    out_person_summary = os.path.join(OUTPUT_DIR, f"{sc}_persons_causing_new_zone_work_trips_summary.csv")
    person_summary.to_csv(out_person_summary, index=False)
    print(f"Saved: {out_person_summary}")

print("\nDone.")