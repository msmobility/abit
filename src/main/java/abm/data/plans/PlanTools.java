package abm.data.plans;

import abm.Utils;
import abm.data.travelTimes.TravelTimes;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


public class PlanTools {

    private final TravelTimes travelTimes;

    public PlanTools(TravelTimes travelTimes) {
        this.travelTimes = travelTimes;
    }


    /**
     * Adds a main activity tour. Cuts the home activity into two pieces, one before the tour and another after the tour
     * Adds trips
     *
     * @param mainTourActivity
     */
    public void addMainTour(Plan plan, Activity mainTourActivity) {
        //find the home activity
        Activity homeActivity = null;

        for (Activity candidateActivity : plan.getHomeActivities().values()) {
            if (mainTourActivity.getStartTime_min() > candidateActivity.getStartTime_min() && mainTourActivity.getEndTime_min() < candidateActivity.getEndTime_min()) {
                homeActivity = candidateActivity;
                break;
            }
        }
        //add a tour - adds the new activity there
        if (homeActivity != null) {
            Tour tour = new Tour(mainTourActivity);
            mainTourActivity.setTour(tour);
            plan.getTours().put(mainTourActivity.getStartTime_min(), tour);
            int timeToMainActivity = travelTimes.getTravelTimeInSeconds(homeActivity.getLocation(), mainTourActivity.getLocation(), Mode.UNKNOWN, mainTourActivity.getStartTime_min());
            int previousEndOfHomeActivity = homeActivity.getEndTime_min();
            homeActivity.setEndTime_min(mainTourActivity.getStartTime_min() - timeToMainActivity);
            tour.getLegs().put(homeActivity.getEndTime_min(), new Leg(homeActivity, mainTourActivity));
            Activity secondHomeActivity = new Activity(homeActivity.getPerson(), Purpose.HOME);
            secondHomeActivity.setStartTime_min(mainTourActivity.getEndTime_min() + timeToMainActivity);
            secondHomeActivity.setEndTime_min(previousEndOfHomeActivity);
            secondHomeActivity.setLocation(homeActivity.getLocation());
            plan.getHomeActivities().put(secondHomeActivity.getStartTime_min(), secondHomeActivity);
            tour.getLegs().put(mainTourActivity.getEndTime_min(), new Leg(mainTourActivity, secondHomeActivity));
            plan.getAvailableTimeOfDay().blockTime(previousEndOfHomeActivity,secondHomeActivity.getStartTime_min());
        } else {
            //there is no home activity because, e.g. a main tour is placed at the same time (before time availability is considered)
            //System.out.println("Two tours at the same time not possible");


        }
    }

    /**
     * Adds a main activity subtour. Cuts the main activity into two pieces, one before the tour and another after the tour
     * Adds trips
     *
     * @param subTourActivity
     */
    public void addSubtour(Plan plan, Activity subTourActivity) {
        Activity mainActivity = null;
        Tour tour = null;
        //the search in the following may be not necessary or need to be adapted later
        for (Tour candidateTour : plan.getTours().values()) {
            Activity candidateActivity = candidateTour.getMainActivity();
            if (subTourActivity.getStartTime_min() > candidateActivity.getStartTime_min() && subTourActivity.getEndTime_min() < candidateActivity.getEndTime_min()) {
                mainActivity = candidateActivity;
                tour = candidateTour;
                break;
            }
        }
        //add subtour
        Tour subtour = new Tour(subTourActivity);
        tour.getSubtours().put(subTourActivity.getStartTime_min(), subtour);
        //add the new activity and break the main activity of the tour


        if (mainActivity != null) {
            int timeToSubTourActivity = travelTimes.getTravelTimeInSeconds(mainActivity.getLocation(), subTourActivity.getLocation(), Mode.UNKNOWN, subTourActivity.getStartTime_min());
            int previousEndOfMainActivity = mainActivity.getEndTime_min();
            mainActivity.setEndTime_min(subTourActivity.getStartTime_min() - timeToSubTourActivity);
            Leg previousLegFromMainActivity = tour.getLegs().get(mainActivity);
            subtour.getLegs().put(mainActivity.getEndTime_min(), new Leg(mainActivity, subTourActivity));
            Activity secondMainActivity = new Activity(mainActivity.getPerson(), mainActivity.getPurpose());
            secondMainActivity.setStartTime_min(subTourActivity.getEndTime_min() + timeToSubTourActivity);
            secondMainActivity.setEndTime_min(previousEndOfMainActivity);
            secondMainActivity.setLocation(mainActivity.getLocation());
            tour.getActivities().put(secondMainActivity.getStartTime_min(), secondMainActivity);
            subtour.getLegs().put(subTourActivity.getEndTime_min(), new Leg(subTourActivity, secondMainActivity));
            tour.getLegs().remove(mainActivity);
            tour.getLegs().put(secondMainActivity.getEndTime_min(), new Leg(secondMainActivity, previousLegFromMainActivity.getNextActivity()));
        } else {
            //trying to add a subtour without having a tour!
        }

    }

    /**
     * Adds one stop before the next main activity, modifying the home activity accordingly
     * Changes the outbound trip and splits it into two subtrips
     */
    public void addStopBefore(Plan plan, Activity stopBefore, Tour tour) {

        //find the first activity and the first leg
        Activity firstActivityInExistingTour = tour.getActivities().get(tour.getActivities().firstKey());

        //remove the leg
        //the key in legs is the previous activity!
        Activity previousHomeActivity;
        final int firstKey = tour.getLegs().firstKey();
        previousHomeActivity = tour.getLegs().get(firstKey).getPreviousActivity();

        tour.getLegs().remove(firstKey);

        //find a start time for the stop before
        int timeForFirstLeg = travelTimes.getTravelTimeInSeconds(previousHomeActivity.getLocation(),
                stopBefore.getLocation(), Mode.UNKNOWN, previousHomeActivity.getEndTime_min());
        //note that the following calculation does not know yet about the departure time, so it uses the arrival time as departur time
        int timeForSecondLeg = travelTimes.getTravelTimeInSeconds(stopBefore.getLocation(), firstActivityInExistingTour.getLocation(),
                Mode.UNKNOWN, firstActivityInExistingTour.getEndTime_min());

        final int duration = stopBefore.getDuration();
        int stopBeforeStartTime_s = firstActivityInExistingTour.getStartTime_min() - duration - timeForSecondLeg;

        stopBefore.setStartTime_min(stopBeforeStartTime_s);
        stopBefore.setEndTime_min(stopBeforeStartTime_s + duration);

        //add the new stop before
        tour.getActivities().put(stopBeforeStartTime_s, stopBefore);


        Leg firstLeg = new Leg(previousHomeActivity, stopBefore);
        Leg secondLeg = new Leg(stopBefore, firstActivityInExistingTour);

        tour.getLegs().put(firstLeg.getPreviousActivity().getEndTime_min(), firstLeg);
        tour.getLegs().put(secondLeg.getPreviousActivity().getEndTime_min(), secondLeg);

        previousHomeActivity.setEndTime_min(stopBefore.getStartTime_min() - timeForFirstLeg);

        stopBefore.setTour(tour);

    }


    /**
     * Adds one stop before the previous main activity, modifying the home activity accordingly
     * Changes the inbound trip and splits it into two subtrips
     *
     * @param stopAfter
     * @param stopAfter
     * @param tour
     */
    public void addStopAfter(Plan plan, Activity stopAfter, Tour tour) {
        //find the last activity and the last leg
        final Integer lastKey = tour.getLegs().lastKey();
        Activity lastActivityInExistingTour = tour.getLegs().get(lastKey).getPreviousActivity();

        //remove the leg
        //the key in legs is the previous activity!
        //find a start time for the stop before

        final Activity followingHomeActivity = tour.getLegs().get(lastKey).getNextActivity();
        tour.getLegs().remove(lastKey);

        int timeForFirstLeg = travelTimes.getTravelTimeInSeconds(lastActivityInExistingTour.getLocation(),
                stopAfter.getLocation(), Mode.UNKNOWN, followingHomeActivity.getEndTime_min());

        int stopAfterStart_s = lastActivityInExistingTour.getEndTime_min() + timeForFirstLeg;
        int stopAfterDuration = stopAfter.getDuration();

        stopAfter.setStartTime_min(stopAfterStart_s);
        stopAfter.setEndTime_min(stopAfterStart_s + stopAfterDuration);

        int timeForSecondLeg = travelTimes.getTravelTimeInSeconds(stopAfter.getLocation(), followingHomeActivity.getLocation(),
                Mode.UNKNOWN, stopAfter.getEndTime_min());

        //add the new stop before
        tour.getActivities().put(stopAfterStart_s, stopAfter);

        Leg firstLeg = new Leg(lastActivityInExistingTour, stopAfter);
        Leg secondLeg = new Leg(stopAfter, followingHomeActivity );

        tour.getLegs().put(firstLeg.getPreviousActivity().getEndTime_min(), firstLeg);
        tour.getLegs().put(secondLeg.getPreviousActivity().getEndTime_min(), secondLeg);

        followingHomeActivity.setStartTime_min(stopAfter.getEndTime_min() + timeForSecondLeg);

        stopAfter.setTour(tour);
    }

    public static Tour findMandatoryTour(Plan plan) {
        final List<Tour> tourList = plan.getTours().values().stream().filter(tour -> Purpose.getMandatoryPurposes().contains(tour.getMainActivity().getPurpose())).collect(Collectors.toList());
        Collections.shuffle(tourList, Utils.getRandomObject());
        return tourList.stream().findFirst().orElse(null);
    }

    public static Tour findDiscretionaryTour(Plan plan) {
        final List<Tour> tourList = plan.getTours().values().stream().filter(tour -> Purpose.getDiscretionaryPurposes().contains(tour.getMainActivity().getPurpose())).collect(Collectors.toList());
        Collections.shuffle(tourList, Utils.getRandomObject());
        return tourList.stream().findFirst().orElse(null);
    }
}
