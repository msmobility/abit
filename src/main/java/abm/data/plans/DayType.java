package abm.data.plans;

/**
 * Per-day work-status classification for a single day within a person's simulated week - see
 * Plan.getDayType(DayOfWeek).
 */
public enum DayType {
    WFH,
    WFO,
    NON_WORKING
}
