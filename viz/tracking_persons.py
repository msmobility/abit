import os
import pandas as pd
import numpy as np

# =========================================================
# FILE PATHS
# =========================================================
BASE_LEGS_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\base-scenario-0-percent-telework\legs.csv"
BASE_PERSON_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\base-scenario-0-percent-telework\person_summary.csv"
BASE_ACTIVITIES_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\base-scenario-0-percent-telework\activities.csv"

SCENARIO_NAME = "tele80"
SCENARIO_LEGS_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\scenario-3-80-percent-telework\legs.csv"
SCENARIO_PERSON_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\scenario-3-80-percent-telework\person_summary.csv"
SCENARIO_ACTIVITIES_CSV = r"C:\Users\Nawidullah\IdeaProjects\abit_standalone\output\scenario-3-80-percent-telework\activities.csv"

OUTPUT_DIR = r"C:\Users\Nawidullah\IdeaProjects\abit\viz\zone_increase_new_workers_one_scenario"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# =========================================================
# SETTINGS
# =========================================================
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

# legs
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

# activities
ACT_PERSON = "person_id"
ACT_DAY = "day"
ACT_START = "start_time_min"
ACT_END = "end_time_min"
ACT_PURPOSE = "purpose"
ACT_ZONE = "zone_id"   # if missing in your file, script handles it

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

def safe_join_unique(series, sep=" | "):
    vals = [str(v) for v in series.dropna().astype(str) if str(v).strip() != ""]
    vals = list(dict.fromkeys(vals))
    return sep.join(vals)

# =========================================================
# PERSONS
# =========================================================
def load_and_filter_persons(person_csv, label):
    persons = load_csv(person_csv, f"{label} persons")

    required = [PERSON_ID_COL, OCCUPATION_COL, CAN_TELEWORK_COL]
    missing = [c for c in required if c not in persons.columns]
    if missing:
        raise ValueError(f"Missing required columns in {label} person file: {missing}")

    persons = clean_str(persons, PERSON_ID_COL)
    persons = clean_str(persons, OCCUPATION_COL)
    persons = clean_str(persons, HABITUAL_MODE_COL)
    persons = clean_str(persons, GENDER_COL)

    persons[OCCUPATION_COL] = persons[OCCUPATION_COL].str.upper()
    persons[CAN_TELEWORK_COL] = normalize_bool_series(persons[CAN_TELEWORK_COL])
    persons[PERSON_ID_COL] = persons[PERSON_ID_COL].astype(str)

    if ONLY_EMPLOYED:
        persons = persons[persons[OCCUPATION_COL] == "EMPLOYED"].copy()

    if ONLY_CAN_TELEWORK:
        persons = persons[persons[CAN_TELEWORK_COL] == True].copy()

    print(f"{label}: workers kept = {len(persons)}")
    return persons

# =========================================================
# LEGS
# =========================================================
def load_home_work_legs(legs_csv, label, persons_df):
    worker_ids = set(persons_df[PERSON_ID_COL].astype(str))

    legs = load_csv(legs_csv, f"{label} legs")

    required = [
        LEG_PERSON, LEG_PREV, LEG_NEXT, LEG_START, LEG_MODE, LEG_TIME,
        LEG_END_ZONE, LEG_START_X, LEG_START_Y, LEG_END_X, LEG_END_Y
    ]
    missing = [c for c in required if c not in legs.columns]
    if missing:
        raise ValueError(f"Missing required columns in {label} legs file: {missing}")

    for col in [LEG_PERSON, LEG_PREV, LEG_NEXT, LEG_MODE, LEG_END_ZONE]:
        legs = clean_str(legs, col)

    legs = to_num(legs, [LEG_START, LEG_TIME, LEG_START_X, LEG_START_Y, LEG_END_X, LEG_END_Y])

    legs[LEG_PREV] = legs[LEG_PREV].apply(normalize_purpose)
    legs[LEG_NEXT] = legs[LEG_NEXT].apply(normalize_purpose)
    legs[LEG_MODE] = legs[LEG_MODE].astype(str).str.strip().str.upper()
    legs[LEG_PERSON] = legs[LEG_PERSON].astype(str)

    legs = legs.dropna(subset=[LEG_PERSON, LEG_START, LEG_END_ZONE]).copy()
    legs = legs[legs[LEG_PERSON].isin(worker_ids)].copy()

    legs["day"] = (legs[LEG_START] // 1440).astype(int) + 1
    legs = legs[legs["day"].isin(DAYS_TO_KEEP)].copy()
    legs["dep_hhmm"] = legs[LEG_START].apply(minutes_to_hhmm)

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
            print(f"{label}: removed same-coordinate HOME->WORK no-trip legs = {removed}")
        hw = hw[~same_coord_mask].copy()

    demo_cols = [c for c in [PERSON_ID_COL, AGE_COL, GENDER_COL, OCCUPATION_COL, HABITUAL_MODE_COL, CAN_TELEWORK_COL] if c in persons_df.columns]
    hw = hw.merge(persons_df[demo_cols], left_on=LEG_PERSON, right_on=PERSON_ID_COL, how="left")

    print(f"{label}: HOME->WORK legs kept = {len(hw)}")
    return hw

# =========================================================
# ACTIVITIES
# =========================================================
def load_activities(activities_csv, label, persons_df):
    worker_ids = set(persons_df[PERSON_ID_COL].astype(str))

    acts = load_csv(activities_csv, f"{label} activities")

    required = [ACT_PERSON, ACT_DAY, ACT_START, ACT_END, ACT_PURPOSE]
    missing = [c for c in required if c not in acts.columns]
    if missing:
        raise ValueError(f"Missing required columns in {label} activities file: {missing}")

    acts = clean_str(acts, ACT_PERSON)
    acts = clean_str(acts, ACT_PURPOSE)
    if ACT_ZONE in acts.columns:
        acts = clean_str(acts, ACT_ZONE)

    acts = to_num(acts, [ACT_DAY, ACT_START, ACT_END])

    acts[ACT_PERSON] = acts[ACT_PERSON].astype(str)
    acts[ACT_PURPOSE] = acts[ACT_PURPOSE].apply(normalize_purpose)

    acts = acts.dropna(subset=[ACT_PERSON, ACT_DAY, ACT_PURPOSE]).copy()
    acts[ACT_DAY] = acts[ACT_DAY].astype(int)
    acts = acts[acts[ACT_DAY].isin(DAYS_TO_KEEP)].copy()
    acts = acts[acts[ACT_PERSON].isin(worker_ids)].copy()

    acts["start_hhmm"] = acts[ACT_START].apply(minutes_to_hhmm)
    acts["end_hhmm"] = acts[ACT_END].apply(minutes_to_hhmm)

    demo_cols = [c for c in [PERSON_ID_COL, AGE_COL, GENDER_COL, OCCUPATION_COL, HABITUAL_MODE_COL, CAN_TELEWORK_COL] if c in persons_df.columns]
    acts = acts.merge(persons_df[demo_cols], on=PERSON_ID_COL, how="left")

    return acts

def build_activity_diary(acts_df, label):
    acts = acts_df.copy()

    if ACT_ZONE in acts.columns:
        acts["activity_item"] = (
                acts[ACT_PURPOSE].fillna("NA").astype(str)
                + "(" + acts["start_hhmm"] + "-" + acts["end_hhmm"] + ")"
                + "@zone:" + acts[ACT_ZONE].fillna("NA").astype(str)
        )
    else:
        acts["activity_item"] = (
                acts[ACT_PURPOSE].fillna("NA").astype(str)
                + "(" + acts["start_hhmm"] + "-" + acts["end_hhmm"] + ")"
        )

    diary = (
        acts.sort_values([ACT_PERSON, ACT_DAY, ACT_START, ACT_END])
        .groupby([ACT_PERSON, ACT_DAY])["activity_item"]
        .apply(lambda s: " > ".join(s.tolist()))
        .reset_index(name=f"{label}_activity_diary")
    )
    return diary

# =========================================================
# AGGREGATIONS
# =========================================================
def build_zone_counts(hw_df, label):
    return (
        hw_df.groupby(LEG_END_ZONE)
        .size()
        .reset_index(name=f"{label}_count")
    )

def build_person_zone_counts(hw_df, label):
    return (
        hw_df.groupby([LEG_PERSON, LEG_END_ZONE])
        .size()
        .reset_index(name=f"{label}_person_zone_count")
    )

def build_person_zone_day_details(hw_df, label):
    keep_cols = [
        LEG_PERSON, LEG_END_ZONE, "day", LEG_START, "dep_hhmm",
        LEG_MODE, LEG_TIME, AGE_COL, GENDER_COL, OCCUPATION_COL,
        HABITUAL_MODE_COL, CAN_TELEWORK_COL
    ]
    keep_cols = [c for c in keep_cols if c in hw_df.columns]
    details = hw_df[keep_cols].copy()
    details["scenario"] = label
    return details

# =========================================================
# 1. LOAD BASE + ONE SCENARIO
# =========================================================
base_persons = load_and_filter_persons(BASE_PERSON_CSV, "base")
sc_persons = load_and_filter_persons(SCENARIO_PERSON_CSV, SCENARIO_NAME)

base_hw = load_home_work_legs(BASE_LEGS_CSV, "base", base_persons)
sc_hw = load_home_work_legs(SCENARIO_LEGS_CSV, SCENARIO_NAME, sc_persons)

base_acts = load_activities(BASE_ACTIVITIES_CSV, "base", base_persons)
sc_acts = load_activities(SCENARIO_ACTIVITIES_CSV, SCENARIO_NAME, sc_persons)

base_diary = build_activity_diary(base_acts, "base")
sc_diary = build_activity_diary(sc_acts, SCENARIO_NAME)

# =========================================================
# 2. FIND ZONES WITH INCREASED HOME->WORK TRIPS
# =========================================================
base_zone = build_zone_counts(base_hw, "base")
sc_zone = build_zone_counts(sc_hw, SCENARIO_NAME)

zone_comp = base_zone.merge(
    sc_zone,
    how="outer",
    on=LEG_END_ZONE
)

zone_comp["base_count"] = zone_comp["base_count"].fillna(0).astype(int)
zone_comp[f"{SCENARIO_NAME}_count"] = zone_comp[f"{SCENARIO_NAME}_count"].fillna(0).astype(int)

zone_comp["delta_count"] = zone_comp[f"{SCENARIO_NAME}_count"] - zone_comp["base_count"]
zone_comp["pct_change_vs_base"] = np.where(
    zone_comp["base_count"] > 0,
    100.0 * zone_comp["delta_count"] / zone_comp["base_count"],
    np.nan
)

increased_zones = zone_comp[zone_comp["delta_count"] > 0].copy()
increased_zones = increased_zones.sort_values("delta_count", ascending=False).reset_index(drop=True)

out_zone = os.path.join(OUTPUT_DIR, f"{SCENARIO_NAME}_zones_with_increased_home_work_trips.csv")
increased_zones.to_csv(out_zone, index=False)
print(f"Saved: {out_zone}")

# =========================================================
# 3. FIND NEW PERSON-ZONE LINKS
# =========================================================
base_pz = build_person_zone_counts(base_hw, "base")
sc_pz = build_person_zone_counts(sc_hw, SCENARIO_NAME)

pz_comp = base_pz.merge(
    sc_pz,
    how="outer",
    on=[LEG_PERSON, LEG_END_ZONE]
)

pz_comp["base_person_zone_count"] = pz_comp["base_person_zone_count"].fillna(0).astype(int)
pz_comp[f"{SCENARIO_NAME}_person_zone_count"] = pz_comp[f"{SCENARIO_NAME}_person_zone_count"].fillna(0).astype(int)

pz_comp["is_new_person_zone_link"] = (
        (pz_comp["base_person_zone_count"] == 0) &
        (pz_comp[f"{SCENARIO_NAME}_person_zone_count"] > 0)
).astype(int)

pz_comp["is_increased_existing_person_zone"] = (
        (pz_comp["base_person_zone_count"] > 0) &
        (pz_comp[f"{SCENARIO_NAME}_person_zone_count"] > pz_comp["base_person_zone_count"])
).astype(int)

pz_comp["delta_person_zone_count"] = (
        pz_comp[f"{SCENARIO_NAME}_person_zone_count"] - pz_comp["base_person_zone_count"]
)

increased_zone_ids = set(increased_zones[LEG_END_ZONE].astype(str))
pz_comp[LEG_END_ZONE] = pz_comp[LEG_END_ZONE].astype(str)

pz_relevant = pz_comp[pz_comp[LEG_END_ZONE].isin(increased_zone_ids)].copy()
pz_new = pz_relevant[pz_relevant["is_new_person_zone_link"] == 1].copy()

out_pz = os.path.join(OUTPUT_DIR, f"{SCENARIO_NAME}_person_zone_changes_in_increased_zones.csv")
pz_relevant.to_csv(out_pz, index=False)
print(f"Saved: {out_pz}")

out_pz_new = os.path.join(OUTPUT_DIR, f"{SCENARIO_NAME}_new_person_zone_links_only.csv")
pz_new.to_csv(out_pz_new, index=False)
print(f"Saved: {out_pz_new}")

# =========================================================
# 4. ADD LEG DETAILS + ACTIVITY DIARIES
# =========================================================
base_details = build_person_zone_day_details(base_hw, "base")
sc_details = build_person_zone_day_details(sc_hw, SCENARIO_NAME)

pz_new[LEG_PERSON] = pz_new[LEG_PERSON].astype(str)
pz_new[LEG_END_ZONE] = pz_new[LEG_END_ZONE].astype(str)

sc_details[LEG_PERSON] = sc_details[LEG_PERSON].astype(str)
sc_details[LEG_END_ZONE] = sc_details[LEG_END_ZONE].astype(str)

new_trip_details = sc_details.merge(
    pz_new[[LEG_PERSON, LEG_END_ZONE, f"{SCENARIO_NAME}_person_zone_count", "delta_person_zone_count"]],
    on=[LEG_PERSON, LEG_END_ZONE],
    how="inner"
)

# attach scenario-day activity diary
new_trip_details = new_trip_details.merge(
    sc_diary,
    left_on=[LEG_PERSON, "day"],
    right_on=[ACT_PERSON, ACT_DAY],
    how="left"
)

# optionally attach base-day activity diary for same person/day
new_trip_details = new_trip_details.merge(
    base_diary,
    left_on=[LEG_PERSON, "day"],
    right_on=[ACT_PERSON, ACT_DAY],
    how="left",
    suffixes=("", "_basejoin")
)

# cleanup duplicate join cols
drop_cols = [c for c in [ACT_PERSON, ACT_DAY, f"{ACT_PERSON}_basejoin", f"{ACT_DAY}_basejoin"] if c in new_trip_details.columns]
new_trip_details = new_trip_details.drop(columns=drop_cols, errors="ignore")

new_trip_details = new_trip_details.sort_values([LEG_END_ZONE, LEG_PERSON, "day", LEG_START]).reset_index(drop=True)

out_details = os.path.join(OUTPUT_DIR, f"{SCENARIO_NAME}_new_work_trip_details_with_activities.csv")
new_trip_details.to_csv(out_details, index=False)
print(f"Saved: {out_details}")

# =========================================================
# 5. PERSON SUMMARY
# =========================================================
person_summary = (
    new_trip_details.groupby(LEG_PERSON, as_index=False)
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

out_person_summary = os.path.join(OUTPUT_DIR, f"{SCENARIO_NAME}_persons_causing_new_zone_work_trips_summary.csv")
person_summary.to_csv(out_person_summary, index=False)
print(f"Saved: {out_person_summary}")

print("\nDone.")