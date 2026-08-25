package abm.data.plans;

import abm.utils.AbitUtils;

/**
 * A leg-less, non-traveling "tour" - an uninterrupted in-home (WFH) WORK activity that never
 * left home, so it has no legs and never will (nothing adds legs to one after construction).
 * Distinguishes itself from a real Tour purely by type, replacing the earlier
 * tour.getLegs().isEmpty() convention.
 */
public class HomeEpisode extends Tour {

    public HomeEpisode(Activity mainActivity, int id) {
        super(mainActivity, id);
    }

    /**
     * Tour.toString() reads its two time columns off legs.firstKey()/lastKey(), which throws on
     * a HomeEpisode's always-empty legs map - override with the same column layout, sourcing
     * start/end from the main activity itself instead.
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(getMainActivity().getPerson().getId()).append(AbitUtils.SEPARATOR);
        builder.append(getMainActivity().getPerson().getHabitualMode().toString()).append(AbitUtils.SEPARATOR);
        builder.append(getId()).append(AbitUtils.SEPARATOR);
        builder.append(getMainActivity().getDayOfWeek().getValue()).append(AbitUtils.SEPARATOR);
        builder.append(getMainActivity().getStartTime_min()).append(AbitUtils.SEPARATOR);
        builder.append(getMainActivity().getEndTime_min()).append(AbitUtils.SEPARATOR);
        builder.append(getTourMode() == null ? "null" : getTourMode().toString()).append(AbitUtils.SEPARATOR);
        builder.append(getMainActivity().getPurpose().toString()).append(AbitUtils.SEPARATOR);
        builder.append(getMainActivity().getPerson().getHousehold().getId()).append(AbitUtils.SEPARATOR);
        builder.append(getCar() == null ? -1 : getCar().getId()).append(AbitUtils.SEPARATOR);
        builder.append(getActivities().size()).append(AbitUtils.SEPARATOR);
        builder.append(getLegs().size()).append(AbitUtils.SEPARATOR);
        builder.append(getSubtours().size());
        return builder.toString();
    }

}
