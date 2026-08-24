package abm.utils;

import abm.data.geo.Location;
import abm.data.geo.MicroLocation;
import abm.data.plans.*;
import abm.data.timeOfDay.BlockedTimeOfWeekLinkedList;
import abm.data.travelInformation.TravelDistances;
import abm.data.travelInformation.TravelTimes;
import abm.properties.InternalProperties;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


public class PlanTools {

    private final TravelTimes travelTimes;

    public PlanTools(TravelTimes travelTimes, TravelDistances travelDistances) {
        this.travelTimes = travelTimes;
        this.travelDistances = travelDistances;
    }

    public static int endOfTheWeek() {
        return 7 * 24 * 60;
    }

    public static int startOfTheWeek() {
        return 0;
    }

    /**
     * Coordinate-level location match (not zone-level, since a large zone can contain both a
     * household and a job without them being the same place). Falls back to zone-id comparison
     * only if either location doesn't carry coordinates.
     */
    public static boolean locationsMatch(Location a, Location b) {
        if (a instanceof MicroLocation && b instanceof MicroLocation) {
            return ((MicroLocation) a).getCoordinate().equals2D(((MicroLocation) b).getCoordinate());
        }
        return a.getZoneId() == b.getZoneId();
    }

    private final TravelDistances travelDistances;

    /**
     * Adds a main activity tour. Cuts the home activity into two pieces, one before the tour and another after the tour
     * Adds trips
     *
     * @param mainTourActivity
     */
    public void addMainTour(Plan plan, Activity mainTourActivity) {

        //Todo is there already a tour in the chosen day?
        DayOfWeek dayOfWeek = mainTourActivity.getDayOfWeek();
        int startTimeOfTheDay_min = dayOfWeek.ordinal() * 24 * 60;
        int endTimeOfTheDay_min = (dayOfWeek.ordinal() + 1) * 24 * 60 - 1;
        int numberOfExistingTourOfTheDay = (int) plan.getTours().keySet().stream()
                .filter(tourStartTime -> tourStartTime >= startTimeOfTheDay_min && tourStartTime <= endTimeOfTheDay_min)
                .count();

        if (numberOfExistingTourOfTheDay == 0) {
            addTheFirstTourOfTheDay(plan, mainTourActivity, startTimeOfTheDay_min, endTimeOfTheDay_min);
        } else if (numberOfExistingTourOfTheDay > 0) {
            addOtherToursOfTheDay(plan, mainTourActivity, startTimeOfTheDay_min, endTimeOfTheDay_min);
        } else {
            System.out.println("Error in calculating number of tours of a day");
        }

    }

    private void addOtherToursOfTheDay(Plan plan, Activity mainTourActivity, int startTimeOfTheDay_min, int endTimeOfTheDay_min) {

        int travelTimeToMainActivity_min = travelTimes.getTravelTimeInMinutes(plan.getDummyHomeActivity().getLocation(), mainTourActivity.getLocation(), Mode.UNKNOWN, mainTourActivity.getStartTime_min());
        double travelDistanceToMainAct_m = travelDistances.getTravelDistanceInMeters(plan.getDummyHomeActivity().getLocation(), mainTourActivity.getLocation(), Mode.UNKNOWN, mainTourActivity.getStartTime_min());
        int timeLeavingHome_min = mainTourActivity.getStartTime_min() - travelTimeToMainActivity_min;
        int travelTimeBackFromMainActivity_min = travelTimes.getTravelTimeInMinutes(mainTourActivity.getLocation(), plan.getDummyHomeActivity().getLocation(), Mode.UNKNOWN, mainTourActivity.getStartTime_min());
        double travelDistanceBackFromMainActivity_m = travelDistances.getTravelDistanceInMeters(mainTourActivity.getLocation(), plan.getDummyHomeActivity().getLocation(), Mode.UNKNOWN, mainTourActivity.getStartTime_min());
        int timeArrivingHome_min = mainTourActivity.getEndTime_min() + travelTimeBackFromMainActivity_min;


        if (plan.getBlockedTimeOfDay().isAvailable(timeLeavingHome_min, timeArrivingHome_min)) {

            //Todo select tours of the day
            List<Integer> timeIndexOfPotentialAffectedTours = plan.getTours().keySet().stream()
                    .filter(tourStartTime -> tourStartTime >= startTimeOfTheDay_min && tourStartTime <= endTimeOfTheDay_min)
                    .collect(Collectors.toList());


            //Todo Identify which tour and which part of the tour will be affected (before the exisiting tour or after)
            for (Integer timeIndex : timeIndexOfPotentialAffectedTours) {

                Tour tempTour = plan.getTours().get(timeIndex);
                int indexOfFirstLeg = tempTour.getLegs().firstKey();
                int indexOfLastLeg = tempTour.getLegs().lastKey();
                Activity firstHomeAct = tempTour.getLegs().get(indexOfFirstLeg).getPreviousActivity();
                Activity secondHomeAct = tempTour.getLegs().get(indexOfLastLeg).getNextActivity();

                if (firstHomeAct.getStartTime_min() <= mainTourActivity.getStartTime_min() && firstHomeAct.getEndTime_min() >= mainTourActivity.getEndTime_min()) {

                    //Todo add activities and legs to the new tour
                    Activity homeActBeforeMainAct = new Activity(mainTourActivity.getPerson(), Purpose.HOME);
                    homeActBeforeMainAct.setStartTime_min(startTimeOfTheDay_min);
                    homeActBeforeMainAct.setEndTime_min(timeLeavingHome_min);
                    homeActBeforeMainAct.setLocation(plan.getDummyHomeActivity().getLocation());
                    homeActBeforeMainAct.setDayOfWeek(mainTourActivity.getDayOfWeek());

                    final Leg firstLeg = new Leg(homeActBeforeMainAct, mainTourActivity);
                    firstLeg.setTravelTime_min(travelTimeToMainActivity_min);
                    firstLeg.setDistance(travelDistanceToMainAct_m);

                    Activity homeActAfterMainAct = new Activity(mainTourActivity.getPerson(), Purpose.HOME);
                    homeActAfterMainAct.setStartTime_min(timeArrivingHome_min);
                    homeActAfterMainAct.setEndTime_min(firstHomeAct.getEndTime_min());
                    homeActAfterMainAct.setLocation(plan.getDummyHomeActivity().getLocation());
                    homeActAfterMainAct.setDayOfWeek(mainTourActivity.getDayOfWeek());

                    final Leg secondLeg = new Leg(mainTourActivity, homeActAfterMainAct);
                    secondLeg.setTravelTime_min(travelTimeToMainActivity_min);
                    secondLeg.setDistance(travelDistanceToMainAct_m);

                    //Todo modify home act on the selected tour
                    firstHomeAct.setStartTime_min(timeArrivingHome_min);

                    Tour tour = new Tour(mainTourActivity, plan.getTours().size() + 1);
                    tour.getLegs().put(timeLeavingHome_min, firstLeg);
                    tour.getLegs().put(mainTourActivity.getEndTime_min(), secondLeg);
                    mainTourActivity.setTour(tour);
                    plan.getBlockedTimeOfDay().blockTime(mainTourActivity.getStartTime_min(), mainTourActivity.getEndTime_min());
                    plan.getTours().put(mainTourActivity.getStartTime_min(), tour);
                    break;

                }

                if (secondHomeAct.getStartTime_min() <= mainTourActivity.getStartTime_min() && secondHomeAct.getEndTime_min() >= mainTourActivity.getEndTime_min()) {

                    //Todo add activities and legs to the new tour
                    Activity homeActBeforeMainAct = new Activity(mainTourActivity.getPerson(), Purpose.HOME);
                    homeActBeforeMainAct.setStartTime_min(secondHomeAct.getStartTime_min());
                    homeActBeforeMainAct.setEndTime_min(timeLeavingHome_min);
                    homeActBeforeMainAct.setLocation(plan.getDummyHomeActivity().getLocation());
                    homeActBeforeMainAct.setDayOfWeek(mainTourActivity.getDayOfWeek());

                    final Leg firstLeg = new Leg(homeActBeforeMainAct, mainTourActivity);
                    firstLeg.setTravelTime_min(travelTimeToMainActivity_min);
                    firstLeg.setDistance(travelDistanceToMainAct_m);

                    Activity homeActAfterMainAct = new Activity(mainTourActivity.getPerson(), Purpose.HOME);
                    homeActAfterMainAct.setStartTime_min(timeArrivingHome_min);
                    homeActAfterMainAct.setEndTime_min(endTimeOfTheDay_min);
                    homeActAfterMainAct.setLocation(plan.getDummyHomeActivity().getLocation());
                    homeActAfterMainAct.setDayOfWeek(mainTourActivity.getDayOfWeek());

                    final Leg secondLeg = new Leg(mainTourActivity, homeActAfterMainAct);
                    secondLeg.setTravelTime_min(travelTimeToMainActivity_min);
                    secondLeg.setDistance(travelDistanceToMainAct_m);


                    //Todo modify home act on the selected tour
                    secondHomeAct.setEndTime_min(timeLeavingHome_min);

                    Tour tour = new Tour(mainTourActivity, plan.getTours().size() + 1);
                    tour.getLegs().put(timeLeavingHome_min, firstLeg);
                    tour.getLegs().put(mainTourActivity.getEndTime_min(), secondLeg);
                    mainTourActivity.setTour(tour);
                    plan.getBlockedTimeOfDay().blockTime(mainTourActivity.getStartTime_min(), mainTourActivity.getEndTime_min());
                    plan.getTours().put(mainTourActivity.getStartTime_min(), tour);
                    break;

                }
            }

        } else {
            plan.addUnmetActivities(mainTourActivity.getStartTime_min(), mainTourActivity);
        }
    }

    private void addTheFirstTourOfTheDay(Plan plan, Activity mainTourActivity, int startTimeOfTheDay_min, int endTimeOfTheDay_min) {

        int travelTimeToMainActivity_min = travelTimes.getTravelTimeInMinutes(plan.getDummyHomeActivity().getLocation(), mainTourActivity.getLocation(), Mode.UNKNOWN, mainTourActivity.getStartTime_min());
        double travelDistanceToMainActivity_m = travelDistances.getTravelDistanceInMeters(plan.getDummyHomeActivity().getLocation(), mainTourActivity.getLocation(), Mode.UNKNOWN, mainTourActivity.getStartTime_min());
        int timeLeavingHome_min = mainTourActivity.getStartTime_min() - travelTimeToMainActivity_min;

        Activity homeActBeforeMainAct = new Activity(mainTourActivity.getPerson(), Purpose.HOME);
        homeActBeforeMainAct.setStartTime_min(startTimeOfTheDay_min);
        homeActBeforeMainAct.setEndTime_min(timeLeavingHome_min);
        homeActBeforeMainAct.setLocation(plan.getDummyHomeActivity().getLocation());
        homeActBeforeMainAct.setDayOfWeek(mainTourActivity.getDayOfWeek());

        final Leg firstLeg = new Leg(homeActBeforeMainAct, mainTourActivity);
        firstLeg.setTravelTime_min(travelTimeToMainActivity_min);
        firstLeg.setDistance(travelDistanceToMainActivity_m);

        int travelTimeBackFromMainActivity_min = travelTimes.getTravelTimeInMinutes(mainTourActivity.getLocation(), plan.getDummyHomeActivity().getLocation(), Mode.UNKNOWN, mainTourActivity.getStartTime_min());
        int timeArrivingHome_min = mainTourActivity.getEndTime_min() + travelTimeBackFromMainActivity_min;

        Activity homeActAfterMainAct = new Activity(mainTourActivity.getPerson(), Purpose.HOME);
        homeActAfterMainAct.setStartTime_min(timeArrivingHome_min);
        homeActAfterMainAct.setEndTime_min(endTimeOfTheDay_min);
        homeActAfterMainAct.setLocation(plan.getDummyHomeActivity().getLocation());
        homeActAfterMainAct.setDayOfWeek(mainTourActivity.getDayOfWeek());

        final Leg secondLeg = new Leg(mainTourActivity, homeActAfterMainAct);
        secondLeg.setTravelTime_min(travelTimeToMainActivity_min);
        secondLeg.setDistance(travelDistanceToMainActivity_m);

        if (plan.getBlockedTimeOfDay().isAvailable(timeLeavingHome_min, timeArrivingHome_min)) {
            Tour tour = new Tour(mainTourActivity, plan.getTours().size() + 1);
            tour.getLegs().put(timeLeavingHome_min, firstLeg);
            tour.getLegs().put(mainTourActivity.getEndTime_min(), secondLeg);
            mainTourActivity.setTour(tour);
            plan.getBlockedTimeOfDay().blockTime(mainTourActivity.getStartTime_min(), mainTourActivity.getEndTime_min());
            plan.getTours().put(mainTourActivity.getStartTime_min(), tour);
        } else {
            plan.addUnmetActivities(mainTourActivity.getStartTime_min(), mainTourActivity);
        }
    }


    /**
     * Adds a main activity subtour. Cuts the main activity into two pieces, one before the tour and another after the tour
     * Adds trips
     *
     * @param subTourActivity
     */
    public void addSubtour(Activity subTourActivity, Tour tour) {
        Activity mainActivity = tour.getMainActivity();

        int timeToSubTourActivity = travelTimes.getTravelTimeInMinutes(mainActivity.getLocation(), subTourActivity.getLocation(), Mode.UNKNOWN, subTourActivity.getStartTime_min());
        double travelDistanceToSubTourActivity = travelDistances.getTravelDistanceInMeters(mainActivity.getLocation(), subTourActivity.getLocation(), Mode.UNKNOWN, subTourActivity.getStartTime_min());

        final Subtour subtour = new Subtour(mainActivity, subTourActivity, timeToSubTourActivity);
        mainActivity.setSubtour(subtour);
        subtour.getOutboundLeg().setTravelTime_min(timeToSubTourActivity);
        subtour.getOutboundLeg().setDistance(travelDistanceToSubTourActivity);
        subtour.getInboundLeg().setTravelTime_min(timeToSubTourActivity);
        subtour.getInboundLeg().setDistance(travelDistanceToSubTourActivity);




       /* //add the new activity and break the main activity of the tour
        if (mainActivity != null) {
            int timeToSubTourActivity = travelTimes.getTravelTimeInMinutes(mainActivity.getLocation(), subTourActivity.getLocation(), Mode.UNKNOWN, subTourActivity.getStartTime_min());
            int previousEndOfMainActivity = mainActivity.getEndTime_min();
            Leg previousLegFromMainActivity = tour.getLegs().get(mainActivity);

            //cut the main activity to its first part
            mainActivity.setEndTime_min(subTourActivity.getStartTime_min() - timeToSubTourActivity);

            //adds a leg to the subtour
            tour.getLegs().put(mainActivity.getEndTime_min(), new Leg(mainActivity, subTourActivity));

            //adds the subtour activity
            tour.getActivities().put(subTourActivity.getStartTime_min(), subTourActivity);

            //adds the second part of the main activity
            Activity secondMainActivity = new Activity(mainActivity.getPerson(), mainActivity.getPurpose());
            secondMainActivity.setStartTime_min(subTourActivity.getEndTime_min() + timeToSubTourActivity);
            secondMainActivity.setEndTime_min(previousEndOfMainActivity);
            secondMainActivity.setLocation(mainActivity.getLocation());
            tour.getActivities().put(secondMainActivity.getStartTime_min(), secondMainActivity);


            //adds the return leg from the subtour
            final Leg secondSubtourLeg = new Leg(subTourActivity, secondMainActivity);
            tour.getLegs().put(subTourActivity.getEndTime_min(), secondSubtourLeg);

            //adds the final leg (and remove the older one, since it is indexed by the previous main activity)
            tour.getLegs().remove(mainActivity);
            tour.getLegs().put(secondMainActivity.getEndTime_min(), new Leg(secondMainActivity, previousLegFromMainActivity.getNextActivity()));
        } else {
            //trying to add a subtour without having a tour!
        }
*/
    }

    /**
     * Adds one stop before the next main activity, modifying the home activity accordingly
     * Changes the outbound trip and splits it into two subtrips
     */
    public void addStopBefore(Plan plan, Activity stopBefore, Tour tour) {
        addStopBefore(plan, stopBefore, tour, true);
    }

    /**
     * @param recomputeTiming if true (existing behavior), the stop's start/end time is
     *                        computed relative to the tour's main activity; if false, the
     *                        stop's start/end time is trusted as already assigned by the
     *                        caller (e.g. drawn from the starting distribution) and left as-is.
     */
    public void addStopBefore(Plan plan, Activity stopBefore, Tour tour, boolean recomputeTiming) {

        Activity firstNonHomeActInExistingTour = tour.getActivities().get(tour.getActivities().firstKey());
        final int timeLeavingFromHomeBeforeAddingStop_min = tour.getLegs().firstKey();

        int travelTimeForFirstLeg = travelTimes.getTravelTimeInMinutes(tour.getLegs().get(timeLeavingFromHomeBeforeAddingStop_min).getPreviousActivity().getLocation(),
                stopBefore.getLocation(), Mode.UNKNOWN, timeLeavingFromHomeBeforeAddingStop_min);
        double travelDistanceForFirstLeg = travelDistances.getTravelDistanceInMeters(tour.getLegs().get(timeLeavingFromHomeBeforeAddingStop_min).getPreviousActivity().getLocation(),
                stopBefore.getLocation(), Mode.UNKNOWN, timeLeavingFromHomeBeforeAddingStop_min);
        int travelTimeForSecondLeg = travelTimes.getTravelTimeInMinutes(stopBefore.getLocation(), firstNonHomeActInExistingTour.getLocation(),
                Mode.UNKNOWN, firstNonHomeActInExistingTour.getStartTime_min());
        double travelDistanceForSecondLeg = travelDistances.getTravelDistanceInMeters(stopBefore.getLocation(), firstNonHomeActInExistingTour.getLocation(),
                Mode.UNKNOWN, firstNonHomeActInExistingTour.getStartTime_min());

        final int duration = stopBefore.getDuration();
        int stopBefore_StartTime_min;
        if (recomputeTiming) {
            stopBefore_StartTime_min = (int) (Math.floor((double) firstNonHomeActInExistingTour.getStartTime_min() / InternalProperties.SEARCH_INTERVAL_MIN) * InternalProperties.SEARCH_INTERVAL_MIN - duration - travelTimeForSecondLeg);
            stopBefore.setStartTime_min(stopBefore_StartTime_min);
            stopBefore.setEndTime_min(stopBefore_StartTime_min + duration);
        } else {
            stopBefore_StartTime_min = stopBefore.getStartTime_min();
        }

        Leg firstLeg = new Leg(tour.getLegs().get(timeLeavingFromHomeBeforeAddingStop_min).getPreviousActivity(), stopBefore);
        tour.getLegs().get(timeLeavingFromHomeBeforeAddingStop_min).getPreviousActivity().setEndTime_min(stopBefore_StartTime_min - travelTimeForFirstLeg);
        firstLeg.setTravelTime_min(travelTimeForFirstLeg);
        firstLeg.setDistance(travelDistanceForFirstLeg);
        Leg secondLeg = new Leg(stopBefore, firstNonHomeActInExistingTour);
        secondLeg.setTravelTime_min(travelTimeForSecondLeg);
        secondLeg.setDistance(travelDistanceForSecondLeg);

        BlockedTimeOfWeekLinkedList tempBlockedTimeOfWeek = plan.getBlockedTimeOfDay();
        //tempBlockedTimeOfWeek.setAvailable(timeLeavingFromHomeBeforeAddingStop_min, firstNonHomeActInExistingTour.getStartTime_min());

        if (tempBlockedTimeOfWeek.isAvailable(firstLeg.getPreviousActivity().getEndTime_min(), secondLeg.getPreviousActivity().getEndTime_min() + secondLeg.getTravelTime_min() - InternalProperties.SEARCH_INTERVAL_MIN)) {
            tour.getLegs().remove(timeLeavingFromHomeBeforeAddingStop_min);
            tour.getActivities().put(stopBefore_StartTime_min, stopBefore);
            tour.getLegs().put(stopBefore_StartTime_min - travelTimeForFirstLeg, firstLeg);
            tour.getLegs().put(secondLeg.getPreviousActivity().getEndTime_min(), secondLeg);
            plan.getBlockedTimeOfDay().blockTime(stopBefore.getStartTime_min(), stopBefore.getEndTime_min());
            stopBefore.setTour(tour);
        } else {
            plan.addUnmetActivities(stopBefore_StartTime_min, stopBefore);
        }
    }


    /**
     * Adds one stop after the last activity, modifying the home activity accordingly
     * Changes the inbound trip and splits it into two subtrips
     *
     * @param stopAfter
     * @param stopAfter
     * @param tour
     */
    public void addStopAfter(Plan plan, Activity stopAfter, Tour tour) {
        addStopAfter(plan, stopAfter, tour, true);
    }

    /**
     * @param recomputeTiming if true (existing behavior), the stop's start/end time is
     *                        computed relative to the tour's main activity; if false, the
     *                        stop's start/end time is trusted as already assigned by the
     *                        caller (e.g. drawn from the starting distribution) and left as-is.
     */
    public void addStopAfter(Plan plan, Activity stopAfter, Tour tour, boolean recomputeTiming) {

        Activity lastNonHomeActInExistingTour = tour.getActivities().get(tour.getActivities().lastKey());
        final int timeLeavingToHomeBeforeAddingStop_min = tour.getLegs().lastKey();

        int travelTimeForFirstLeg = travelTimes.getTravelTimeInMinutes(lastNonHomeActInExistingTour.getLocation(),
                stopAfter.getLocation(), Mode.UNKNOWN, lastNonHomeActInExistingTour.getEndTime_min());
        double travelDistanceForFirstLeg = travelDistances.getTravelDistanceInMeters(lastNonHomeActInExistingTour.getLocation(),
                stopAfter.getLocation(), Mode.UNKNOWN, lastNonHomeActInExistingTour.getEndTime_min());
        int travelTimeForSecondLeg = travelTimes.getTravelTimeInMinutes(stopAfter.getLocation(),
                tour.getLegs().get(timeLeavingToHomeBeforeAddingStop_min).getNextActivity().getLocation(),
                Mode.UNKNOWN, stopAfter.getEndTime_min());
        double travelDistanceForSecondLeg = travelDistances.getTravelDistanceInMeters(stopAfter.getLocation(),
                tour.getLegs().get(timeLeavingToHomeBeforeAddingStop_min).getNextActivity().getLocation(),
                Mode.UNKNOWN, stopAfter.getEndTime_min());

        final int duration = stopAfter.getDuration();
        int stopAfter_StartTime_min;
        if (recomputeTiming) {
            stopAfter_StartTime_min = (int) (Math.ceil((double) lastNonHomeActInExistingTour.getEndTime_min() / InternalProperties.SEARCH_INTERVAL_MIN) * InternalProperties.SEARCH_INTERVAL_MIN + travelTimeForFirstLeg);
            stopAfter.setStartTime_min(stopAfter_StartTime_min);
            stopAfter.setEndTime_min(stopAfter_StartTime_min + duration);
        } else {
            stopAfter_StartTime_min = stopAfter.getStartTime_min();
        }

        Leg firstLeg = new Leg(lastNonHomeActInExistingTour, stopAfter);
        firstLeg.setTravelTime_min(travelTimeForFirstLeg);
        firstLeg.setDistance(travelDistanceForFirstLeg);
        Leg secondLeg = new Leg(stopAfter, tour.getLegs().get(timeLeavingToHomeBeforeAddingStop_min).getNextActivity());
        tour.getLegs().get(timeLeavingToHomeBeforeAddingStop_min).getNextActivity().setStartTime_min(stopAfter.getEndTime_min() + travelTimeForSecondLeg);
        secondLeg.setTravelTime_min(travelTimeForSecondLeg);
        secondLeg.setDistance(travelDistanceForSecondLeg);

        BlockedTimeOfWeekLinkedList tempBlockedTimeOfWeek = plan.getBlockedTimeOfDay();
        //tempBlockedTimeOfWeek.setAvailable(lastNonHomeActInExistingTour.getEndTime_min(), timeLeavingToHomeBeforeAddingStop_min);

        if (tempBlockedTimeOfWeek.isAvailable(firstLeg.getNextActivity().getStartTime_min() - firstLeg.getTravelTime_min() + InternalProperties.SEARCH_INTERVAL_MIN, secondLeg.getNextActivity().getStartTime_min())) {
            tour.getLegs().remove(timeLeavingToHomeBeforeAddingStop_min);
            tour.getActivities().put(stopAfter_StartTime_min, stopAfter);
            tour.getLegs().put(firstLeg.getPreviousActivity().getEndTime_min(), firstLeg);
            tour.getLegs().put(secondLeg.getPreviousActivity().getEndTime_min(), secondLeg);
            plan.getBlockedTimeOfDay().blockTime(stopAfter.getStartTime_min(), stopAfter.getEndTime_min());
            stopAfter.setTour(tour);
        } else {
            plan.addUnmetActivities(stopAfter_StartTime_min, stopAfter);
        }
    }

    /**
     * Creates a leg-less tour for an uninterrupted WFH (in-home WORK) day - no bookend/leg
     * computation at all, since there's no travel. tour.getLegs() stays empty, which is the
     * discriminator downstream consumers use to recognize a no-travel tour.
     */
    public void addInHomeTour(Plan plan, Activity mainTourActivity) {
        if (plan.getBlockedTimeOfDay().isAvailable(mainTourActivity.getStartTime_min(), mainTourActivity.getEndTime_min())) {
            mainTourActivity.setAtHome(true);
            Tour tour = new Tour(mainTourActivity, plan.getTours().size() + 1);
            mainTourActivity.setTour(tour);
            plan.getBlockedTimeOfDay().blockTime(mainTourActivity.getStartTime_min(), mainTourActivity.getEndTime_min());
            plan.getTours().put(mainTourActivity.getStartTime_min(), tour);
        } else {
            plan.addUnmetActivities(mainTourActivity.getStartTime_min(), mainTourActivity);
        }
    }

    /**
     * Splits a Purpose.WORK, isAtHome() activity around an overlapping discretionary activity,
     * and spins that activity off as its own real tour, bookended by the two WORK halves - the
     * same pattern Tours already use for HOME bookends (never inserted into tour.getActivities(),
     * never given .setTour()). Chainable: `work` may be an untouched leg-less tour's main
     * activity, or an already-split tour's bookend (a second interruption the same day targets
     * whichever WORK/isAtHome() activity it overlaps).
     * <p>
     * `work`'s own current span is already registered as blocked (from addInHomeTour, or from a
     * prior split's own re-block) - that block is released before checking availability, so the
     * checks below see only genuinely *other* encumbrances, not work's own reservation of the
     * space being subdivided. Both the away window and the resumed-work (workAfter) span are
     * validated; on failure work's original block is restored before giving up.
     */
    public void splitInHomeWorkAroundActivity(Plan plan, Activity work, Activity activity) {
        int originalStart = work.getStartTime_min();
        int originalEnd = work.getEndTime_min();

        int travelToActivity = travelTimes.getTravelTimeInMinutes(work.getLocation(), activity.getLocation(), Mode.UNKNOWN, activity.getStartTime_min());
        int travelFromActivity = travelTimes.getTravelTimeInMinutes(activity.getLocation(), work.getLocation(), Mode.UNKNOWN, activity.getEndTime_min());
        int rawLeaveTime = activity.getStartTime_min() - travelToActivity;
        int rawReturnTime = activity.getEndTime_min() + travelFromActivity;

        // hard reject: leaving before the work period we're interrupting even started makes no
        // sense (work-period boundary check, distinct from the calendar-day clip below)
        if (rawLeaveTime < originalStart) {
            plan.addUnmetActivities(activity.getStartTime_min(), activity);
            return;
        }

        DayOfWeek dayOfWeek = work.getDayOfWeek();
        int dayStartMin = dayOfWeek.ordinal() * 24 * 60;
        int dayEndMin = (dayOfWeek.ordinal() + 1) * 24 * 60 - 1;

        // clip to the calendar day boundary rather than reject
        int leaveTime = Math.max(rawLeaveTime, dayStartMin);
        int returnTime = Math.min(rawReturnTime, dayEndMin);
        int remainingOriginalDuration = originalEnd - leaveTime;
        int workAfterEnd = returnTime + remainingOriginalDuration + activity.getDuration();

        // release work's own current reservation first, so the checks below see only genuinely
        // *other* encumbrances, not the space we're about to subdivide
        plan.getBlockedTimeOfDay().setAvailable(originalStart, originalEnd);

        boolean awayWindowFree = plan.getBlockedTimeOfDay().isAvailable(leaveTime, returnTime);
        boolean resumedWorkFree = plan.getBlockedTimeOfDay().isAvailable(returnTime, workAfterEnd);

        if (!awayWindowFree || !resumedWorkFree) {
            // restore work's original reservation before giving up - leave the day's
            // blocked-time structure exactly as it was found
            plan.getBlockedTimeOfDay().blockTime(originalStart, originalEnd);
            plan.addUnmetActivities(activity.getStartTime_min(), activity);
            return;
        }

        // if `work` is itself the main activity of a still-uninterrupted leg-less tour, that
        // tour entry is retired now - if `work` is already just a bookend of a previously-split
        // tour, there is no tour entry to retire (bookends were never independently tracked)
        plan.getTours().values().removeIf(t -> t.getLegs().isEmpty() && t.getMainActivity() == work);

        Activity workBefore = work; // reuse the existing Activity object
        workBefore.setEndTime_min(leaveTime);
        workBefore.setAtHome(true);

        Activity workAfter = new Activity(work.getPerson(), Purpose.WORK);
        workAfter.setDayOfWeek(work.getDayOfWeek());
        workAfter.setLocation(work.getLocation());
        workAfter.setAtHome(true);
        workAfter.setStartTime_min(returnTime);
        workAfter.setEndTime_min(workAfterEnd);

        Tour newTour = new Tour(activity, plan.getTours().size() + 1);
        Leg outbound = new Leg(workBefore, activity);
        outbound.setTravelTime_min(travelToActivity);
        Leg inbound = new Leg(activity, workAfter);
        inbound.setTravelTime_min(travelFromActivity);
        newTour.getLegs().put(leaveTime, outbound);
        newTour.getLegs().put(activity.getEndTime_min(), inbound);
        activity.setTour(newTour);

        // re-block all three resulting pieces explicitly - the original span was fully
        // released above
        plan.getBlockedTimeOfDay().blockTime(originalStart, leaveTime);
        plan.getBlockedTimeOfDay().blockTime(leaveTime, returnTime);
        plan.getBlockedTimeOfDay().blockTime(returnTime, workAfterEnd);

        plan.getTours().put(activity.getStartTime_min(), newTour);
    }

    public static Tour findMandatoryTour(Plan plan) {
        final List<Tour> tourList = plan.getTours().values().stream()
                .filter(tour -> Purpose.getMandatoryPurposes().contains(tour.getMainActivity().getPurpose()))
                .filter(tour -> !tour.getLegs().isEmpty())
                .collect(Collectors.toList());
        Collections.shuffle(tourList, AbitUtils.getRandomObject());
        return tourList.stream().findFirst().orElse(null);
    }

    public static Tour findDiscretionaryTour(Plan plan) {
        final List<Tour> tourList = plan.getTours().values().stream().filter(tour -> Purpose.getDiscretionaryPurposes().contains(tour.getMainActivity().getPurpose())).collect(Collectors.toList());
        Collections.shuffle(tourList, AbitUtils.getRandomObject());
        return tourList.stream().findFirst().orElse(null);
    }

    public static Tour findDiscretionaryTourByPurpose(Plan plan, Purpose purpose) {
        final List<Tour> tourList = plan.getTours().values().stream().filter(tour -> tour.getMainActivity().getPurpose() == purpose).collect(Collectors.toList());
        Collections.shuffle(tourList, AbitUtils.getRandomObject());
        return tourList.stream().findFirst().orElse(null);
    }
}
