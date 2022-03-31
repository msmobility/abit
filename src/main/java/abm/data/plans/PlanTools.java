package abm.data.plans;

import abm.Utils;
import abm.data.travelTimes.TravelTimes;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.stream.Collectors;


public class PlanTools {

    private final TravelTimes travelTimes;

    public PlanTools(TravelTimes travelTimes) {
        this.travelTimes = travelTimes;
    }


    /**
     * Adds a main activity tour. Cuts the home activity into two pieces, one before the tour and another after the tour
     * Adds trip
     *
     * @param mainTourActivity
     */
    public void addMainTour(Plan plan, Activity mainTourActivity) {
        //find the home activity
        Activity homeActivity = null;

        for (Activity candidateActivity : plan.getHomeActivities().values()) {
            if (mainTourActivity.getStartTime_s() > candidateActivity.getStartTime_s() && mainTourActivity.getEndTime_s() < candidateActivity.getEndTime_s()) {
                homeActivity = candidateActivity;
                break;
            }
        }
        //add a tour - adds the new activity there
        if (homeActivity != null) {
            Tour tour = new Tour(mainTourActivity);
            mainTourActivity.setTour(tour);
            plan.getTours().put(mainTourActivity.getStartTime_s(), tour);
            int timeToMainActivity = travelTimes.getTravelTimeInSeconds(homeActivity.getLocation(), mainTourActivity.getLocation(), Mode.UNKNOWN, mainTourActivity.getStartTime_s());
            int previousEndOfHomeActivity = homeActivity.getEndTime_s();
            homeActivity.setEndTime_s(mainTourActivity.getStartTime_s() - timeToMainActivity);
            tour.getLegs().put(homeActivity, new Leg(homeActivity, mainTourActivity));
            Activity secondHomeActivity = new Activity(Purpose.HOME, mainTourActivity.getEndTime_s() + timeToMainActivity, previousEndOfHomeActivity, homeActivity.getLocation());
            plan.getHomeActivities().put(secondHomeActivity.getStartTime_s(), secondHomeActivity);
            tour.getLegs().put(mainTourActivity, new Leg(mainTourActivity, secondHomeActivity));
        } else {
            //there is no home activity because, e.g. a main tour is placed at the same time (before time availability is considered)
            System.out.println("Two tours at the same time not possible");
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
            if (subTourActivity.getStartTime_s() > candidateActivity.getStartTime_s() && subTourActivity.getEndTime_s() < candidateActivity.getEndTime_s()) {
                mainActivity = candidateActivity;
                tour = candidateTour;
                break;
            }
        }
        //add subtour
        Tour subtour = new Tour(subTourActivity);
        tour.getSubtours().put(subTourActivity.getStartTime_s(), subtour);
        //add the new activity and break the main activity of the tour

        //todo here
        if (mainActivity != null) {
            int timeToSubTourActivity = travelTimes.getTravelTimeInSeconds(mainActivity.getLocation(), subTourActivity.getLocation(), Mode.UNKNOWN, subTourActivity.getStartTime_s());
            int previousEndOfMainActivity = mainActivity.getEndTime_s();
            mainActivity.setEndTime_s(subTourActivity.getStartTime_s() - timeToSubTourActivity);
            Leg previousLegFromMainActivity = tour.getLegs().get(mainActivity);
            subtour.getLegs().put(mainActivity, new Leg(mainActivity, subTourActivity));
            Activity secondMainActivity = new Activity(mainActivity.getPurpose(), subTourActivity.getEndTime_s() + timeToSubTourActivity, previousEndOfMainActivity, mainActivity.getLocation());
            tour.getActivities().put(secondMainActivity.getStartTime_s(), secondMainActivity);
            subtour.getLegs().put(subTourActivity, new Leg(subTourActivity, secondMainActivity));
            tour.getLegs().remove(mainActivity);
            tour.getLegs().put(secondMainActivity, new Leg(secondMainActivity, previousLegFromMainActivity.getNextActivity()));
        } else {
            //trying to add a subtour without having a tour!
        }

    }

    /**
     * Adds one stop before the next main activity, modifying the home activity accordingly
     * Changes the outbound trip and splits it into two subtrips
     * //todo this methods needs to be changed to addStopBefore GIVEN the tour (no need to look for a tour)
     */
    public void addStopBefore(Plan plan, Activity stopBefore, Tour tour) {

        //find the first activity and the first leg
        Activity firstActivityInExistingTour = tour.getActivities().get(tour.getActivities().firstKey());

        //remove the leg
        //the key in legs is the previous activity!
        Activity previousHomeActivity;
        try{
            previousHomeActivity = tour.getLegs().firstKey();
        } catch (NoSuchElementException e){
            previousHomeActivity = null;
        }
        tour.getLegs().remove(previousHomeActivity);

        //find a start time for the stop before
        int timeForFirstLeg = travelTimes.getTravelTimeInSeconds(previousHomeActivity.getLocation(),
                stopBefore.getLocation(), Mode.UNKNOWN, previousHomeActivity.getEndTime_s());
        //note that the following calculation does not know yet about the departure time, so it uses the arrival time as departur time
        int timeForSecondLeg = travelTimes.getTravelTimeInSeconds(stopBefore.getLocation(), firstActivityInExistingTour.getLocation(),
                Mode.UNKNOWN, firstActivityInExistingTour.getEndTime_s());

        final int duration = stopBefore.getDuration();
        int stopBeforeStartTime_s = firstActivityInExistingTour.getStartTime_s() - duration - timeForSecondLeg;

        stopBefore.setStartTime_s(stopBeforeStartTime_s);
        stopBefore.setEndTime_s(stopBeforeStartTime_s + duration);

        //add the new stop before
        tour.getActivities().put(stopBeforeStartTime_s, stopBefore);


        Leg firstLeg = new Leg(previousHomeActivity, stopBefore);
        Leg secondLeg = new Leg(stopBefore, firstActivityInExistingTour);

        tour.getLegs().put(firstLeg.getPreviousActivity(), firstLeg);
        tour.getLegs().put(secondLeg.getPreviousActivity(), secondLeg);

        previousHomeActivity.setEndTime_s(stopBefore.getStartTime_s() - timeForFirstLeg);

        stopBefore.setTour(tour);

    }


    /**
     * Adds one stop before the previous main activity, modifying the home activity accordingly
     * Changes the inbound trip and splits it into two subtrips
     * todo this methods needs to be changed to addStopAfter GIVEN the tour (no need to look for a tour)
     *
     * @param stopAfter
     * @param stopAfter
     * @param tour
     */
    public void addStopAfter(Plan plan, Activity stopAfter, Tour tour) {
        //find the last activity and the last leg
        Activity lastActivityInExistingTour = tour.getLegs().lastKey();

        //remove the leg
        //the key in legs is the previous activity!
        //find a start time for the stop before

        Leg leg = tour.getLegs().get(lastActivityInExistingTour);

        if (leg == null){
            //todo investigate this
            System.out.println("A problem with the keys of leg is found");
            for (Leg thisLeg : tour.getLegs().values()){
                if (thisLeg.getPreviousActivity().equals(lastActivityInExistingTour)){
                    leg = thisLeg;
                }
            }
        }

        final Activity followingHomeActivity = leg.getNextActivity();
        tour.getLegs().remove(lastActivityInExistingTour);

        int timeForFirstLeg = travelTimes.getTravelTimeInSeconds(lastActivityInExistingTour.getLocation(),
                stopAfter.getLocation(), Mode.UNKNOWN, followingHomeActivity.getEndTime_s());

        int stopAfterStart_s = lastActivityInExistingTour.getEndTime_s() + timeForFirstLeg;
        int stopAfterDuration = stopAfter.getDuration();

        stopAfter.setStartTime_s(stopAfterStart_s);
        stopAfter.setEndTime_s(stopAfterStart_s + stopAfterDuration);

        int timeForSecondLeg = travelTimes.getTravelTimeInSeconds(stopAfter.getLocation(), followingHomeActivity.getLocation(),
                Mode.UNKNOWN, stopAfter.getEndTime_s());

        //add the new stop before
        tour.getActivities().put(stopAfterStart_s, stopAfter);

        Leg firstLeg = new Leg(lastActivityInExistingTour, stopAfter);
        Leg secondLeg = new Leg(stopAfter, followingHomeActivity );

        tour.getLegs().put(firstLeg.getPreviousActivity(), firstLeg);
        tour.getLegs().put(secondLeg.getPreviousActivity(), secondLeg);

        followingHomeActivity.setStartTime_s(stopAfter.getEndTime_s() + timeForSecondLeg);

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
