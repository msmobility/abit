package abm.models.planConsistency;

import abm.data.plans.Activity;
import abm.data.plans.Leg;
import abm.data.plans.Plan;
import abm.data.plans.Subtour;
import abm.data.plans.Tour;
import abm.data.travelInformation.TravelTimes;
import abm.utils.PlanTools;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * Runs once mode choice has finished for a household, recomputing every leg's travel time from
 * its actually-chosen mode (legs are created earlier in the pipeline with Mode.UNKNOWN, which
 * MitoBasedTravelTimes silently substitutes with 80% of car travel time - a placeholder that
 * mode choice never revisits) and reconciling the resulting timing drift.
 * <p>
 * Corrections on a leg touching a bookend activity (one never given .setTour() - HOME bookends,
 * in-home-WORK bookends, and Subtour's mainActivityPart1/mainActivityPart2 all share this rule)
 * are absorbed into that bookend's own boundary, leaving the real, calibrated activity on the
 * other side of the leg untouched. Corrections on a leg between two tracked activities cascade
 * forward, preserving the shifted activity's own duration. A cascade always terminates at a
 * bookend, since every tour starts and ends at one - that's the only point a collision check
 * against a neighboring tour is needed.
 */
public class PlanConsistencyRepairModel implements PlanConsistencyRepair {

    private static final Logger logger = Logger.getLogger(PlanConsistencyRepairModel.class);

    private final TravelTimes travelTimes;

    public PlanConsistencyRepairModel(TravelTimes travelTimes) {
        this.travelTimes = travelTimes;
    }

    @Override
    public void repairPlan(Plan plan) {
        for (TrackedLeg tracked : flattenLegs(plan)) {
            repairLeg(plan, tracked);
        }
    }

    /**
     * Flattens every leg for this person - every Tour's own legs plus every Subtour's
     * outbound/inbound legs - into one chronologically sorted sequence, tracking each leg's
     * parent Tour (for collision-check bounds, and for re-keying tour.getLegs() when the leg
     * itself lives in that map).
     */
    private List<TrackedLeg> flattenLegs(Plan plan) {
        List<TrackedLeg> tracked = new ArrayList<>();
        for (Tour tour : plan.getTours().values()) {
            for (Leg leg : tour.getLegs().values()) {
                tracked.add(new TrackedLeg(leg, tour, true));
            }
            Subtour subtour = tour.getMainActivity().getSubtour();
            if (subtour != null) {
                tracked.add(new TrackedLeg(subtour.getOutboundLeg(), tour, false));
                tracked.add(new TrackedLeg(subtour.getInboundLeg(), tour, false));
            }
        }
        tracked.sort(Comparator.comparingInt(t -> t.leg.getPreviousActivity().getEndTime_min()));
        return tracked;
    }

    private void repairLeg(Plan plan, TrackedLeg tracked) {
        Leg leg = tracked.leg;
        int newTravelTime = travelTimes.getTravelTimeInMinutes(
                leg.getPreviousActivity().getLocation(), leg.getNextActivity().getLocation(),
                leg.getLegMode(), leg.getPreviousActivity().getEndTime_min());
        int delta = newTravelTime - leg.getTravelTime_min();
        if (delta == 0) {
            return;
        }

        Integer oldKey = tracked.rekeyable ? findLegKey(tracked.parentTour, leg) : null;
        leg.setTravelTime_min(newTravelTime);

        if (leg.getPreviousActivity().getTour() == null) {
            // edge leg, bookend before - absorb by moving the bookend's own end boundary; the
            // "real" nextActivity's timing is untouched
            int newBookendEnd = leg.getNextActivity().getStartTime_min() - newTravelTime;
            int earliestAllowed = priorTourBoundary(plan, tracked.parentTour);
            if (newBookendEnd >= earliestAllowed) {
                leg.getPreviousActivity().setEndTime_min(newBookendEnd);
            } else {
                logger.warn("Plan consistency repair: clipping outbound-leg correction for person "
                        + leg.getPreviousActivity().getPerson().getId()
                        + " - preceding bookend cannot fully absorb the corrected travel time without colliding with an earlier tour.");
                leg.getPreviousActivity().setEndTime_min(earliestAllowed);
            }
        } else if (leg.getNextActivity().getTour() == null) {
            // edge leg, bookend after - absorb by moving the bookend's own start boundary
            int newBookendStart = leg.getPreviousActivity().getEndTime_min() + newTravelTime;
            int latestAllowed = nextTourBoundary(plan, tracked.parentTour);
            if (newBookendStart <= latestAllowed) {
                leg.getNextActivity().setStartTime_min(newBookendStart);
            } else {
                logger.warn("Plan consistency repair: clipping return-leg correction for person "
                        + leg.getPreviousActivity().getPerson().getId()
                        + " - following bookend cannot fully absorb the corrected travel time without colliding with a later tour.");
                leg.getNextActivity().setStartTime_min(latestAllowed);
            }
        } else {
            // internal leg between two tracked activities (stop <-> main, main <-> subtour
            // activity) - cascade forward, preserving the shifted activity's own duration; the
            // next leg in the flattened sequence will see this activity's new position
            Activity next = leg.getNextActivity();
            int duration = next.getDuration();
            next.setStartTime_min(next.getStartTime_min() + delta);
            next.setEndTime_min(next.getStartTime_min() + duration);
        }

        if (tracked.rekeyable && !oldKey.equals(leg.getPreviousActivity().getEndTime_min())) {
            tracked.parentTour.getLegs().remove(oldKey);
            tracked.parentTour.getLegs().put(leg.getPreviousActivity().getEndTime_min(), leg);
        }
    }

    /**
     * Latest already-claimed moment of the tour chronologically before `tour` - the preceding
     * bookend's end may not move earlier than this. Falls back to the start of the week if
     * `tour` is the first tour of the person's plan.
     */
    private int priorTourBoundary(Plan plan, Tour tour) {
        int tourKey = findTourKey(plan, tour);
        SortedMap<Integer, Tour> before = plan.getTours().headMap(tourKey);
        if (before.isEmpty()) {
            return PlanTools.startOfTheWeek();
        }
        Tour priorTour = before.get(before.lastKey());
        return priorTour.getLegs().isEmpty()
                ? priorTour.getMainActivity().getEndTime_min()
                : priorTour.getLegs().get(priorTour.getLegs().lastKey()).getNextActivity().getEndTime_min();
    }

    /**
     * Earliest already-claimed moment of the tour chronologically after `tour` - the following
     * bookend's start may not move later than this. Falls back to the end of the week if `tour`
     * is the last tour of the person's plan.
     */
    private int nextTourBoundary(Plan plan, Tour tour) {
        int tourKey = findTourKey(plan, tour);
        SortedMap<Integer, Tour> after = plan.getTours().tailMap(tourKey + 1);
        if (after.isEmpty()) {
            return PlanTools.endOfTheWeek();
        }
        Tour nextTour = after.get(after.firstKey());
        return nextTour.getLegs().isEmpty()
                ? nextTour.getMainActivity().getStartTime_min()
                : nextTour.getLegs().firstKey();
    }

    private int findTourKey(Plan plan, Tour tour) {
        for (Map.Entry<Integer, Tour> entry : plan.getTours().entrySet()) {
            if (entry.getValue() == tour) {
                return entry.getKey();
            }
        }
        throw new IllegalStateException("Tour not found in its own plan.getTours()");
    }

    private int findLegKey(Tour tour, Leg leg) {
        for (Map.Entry<Integer, Leg> entry : tour.getLegs().entrySet()) {
            if (entry.getValue() == leg) {
                return entry.getKey();
            }
        }
        throw new IllegalStateException("Leg not found in its own tour's legs map");
    }

    private static class TrackedLeg {
        final Leg leg;
        final Tour parentTour;
        final boolean rekeyable;

        TrackedLeg(Leg leg, Tour parentTour, boolean rekeyable) {
            this.leg = leg;
            this.parentTour = parentTour;
            this.rekeyable = rekeyable;
        }
    }
}
