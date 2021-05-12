package abm.data.plans;

import abm.data.geo.TravelTimes;

public class PlanUtils {
    /**
     * Adds a main activity tour. Cuts the home activity into two pieces, one before the tour and another after the tour
     * Adds trip
     *
     * @param mainTourActivity
     */
    public static void addMainTour(Plan plan, Activity mainTourActivity) {
        //find the home activity
        Activity homeActivity = null;

        for (Activity candidateActivity : plan.getHomeActivities().values()) {
            if (mainTourActivity.getStartTime_s() > candidateActivity.getStartTime_s() && mainTourActivity.getEndTime_s() < candidateActivity.getEndTime_s()) {
                homeActivity = candidateActivity;
                break;
            }
        }
        //add a tour - adds the new activity there
        Tour tour = new Tour(mainTourActivity);
        plan.getTours().put(mainTourActivity.getStartTime_s(), tour);
        if (homeActivity != null) {
            double timeToMainActivity = TravelTimes.getTravelTimeInSeconds(homeActivity.getLocation(), mainTourActivity.getLocation(), Mode.UNKNOWN, mainTourActivity.getStartTime_s());
            double previousEndOfHomeActivity = homeActivity.getEndTime_s();
            homeActivity.setEndTime_s(mainTourActivity.getStartTime_s() - timeToMainActivity);
            tour.getTrips().put(homeActivity, new Trip(homeActivity, mainTourActivity));
            Activity secondHomeActivity = new Activity(Purpose.H, mainTourActivity.getEndTime_s() + timeToMainActivity, previousEndOfHomeActivity, homeActivity.getLocation());
            plan.getHomeActivities().put(secondHomeActivity.getStartTime_s(), secondHomeActivity);
            tour.getTrips().put(mainTourActivity, new Trip(mainTourActivity, secondHomeActivity));
        }
    }

    /**
     * Adds a main activity subtour. Cuts the main activity into two pieces, one before the tour and another after the tour
     * Adds trips
     *
     * @param subTourActivity
     */
    public static void addSubtour(Plan plan, Activity subTourActivity) {
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
            double timeToSubTourActivity = TravelTimes.getTravelTimeInSeconds(mainActivity.getLocation(), subTourActivity.getLocation(), Mode.UNKNOWN, subTourActivity.getStartTime_s());
            double previousEndOfMainActivity = mainActivity.getEndTime_s();
            mainActivity.setEndTime_s(subTourActivity.getStartTime_s() - timeToSubTourActivity);
            Trip previousTripFromMainActivity = tour.getTrips().get(mainActivity);
            subtour.getTrips().put(mainActivity, new Trip(mainActivity, subTourActivity));
            Activity secondMainActivity = new Activity(mainActivity.getPurpose(), subTourActivity.getEndTime_s() + timeToSubTourActivity, previousEndOfMainActivity, mainActivity.getLocation());
            tour.getActivities().put(secondMainActivity.getStartTime_s(), secondMainActivity);
            subtour.getTrips().put(subTourActivity, new Trip(subTourActivity, secondMainActivity));
            tour.getTrips().remove(mainActivity);
            tour.getTrips().put(secondMainActivity, new Trip(secondMainActivity, previousTripFromMainActivity.getNextActivity()));
        } else {
            //trying to add a subtour without having a tour!
        }

    }

    /**
     * Adds one stop before the next main activity, modifying the home activity accordingly
     * Changes the outbound trip and splits it into two subtrips
     */
    public static void addStopBefore(Plan plan, Activity stopBefore, Activity mainActivity) {

        Activity candidatePreviousActivity = null;
        for (Tour candidateTour : plan.getTours().values()){
            for (Trip candidateTrip : candidateTour.getTrips().values()){
                if (candidateTrip.getNextActivity().equals(mainActivity)){
                    candidatePreviousActivity = candidateTrip.getPreviousActivity();
                }
            }
        }


        Tour tour = null;
        Trip tripToRemove = null;
        for (Tour candidateTour : plan.getTours().values()) {
            for (Trip candidateTrip : candidateTour.getTrips().values()){
                if (candidateTrip.getNextActivity().equals(mainActivity)){
                    tour = candidateTour;
                    tripToRemove = candidateTrip;
                    break;

                }
            }
        }

        tour.getActivities().put(stopBefore.getStartTime_s(), stopBefore);
        tour.getTrips().remove(tripToRemove.getPreviousActivity());

        Trip firstTrip = new Trip(candidatePreviousActivity, stopBefore);
        double timeForFirstTrip = TravelTimes.getTravelTimeInSeconds(candidatePreviousActivity.getLocation(), stopBefore.getLocation(), Mode.UNKNOWN, candidatePreviousActivity.getEndTime_s());
        Trip secondTrip = new Trip(stopBefore, mainActivity);
        double timeForSecondTrip = TravelTimes.getTravelTimeInSeconds(stopBefore.getLocation(), mainActivity.getLocation(), Mode.UNKNOWN, stopBefore.getEndTime_s());
        tour.getTrips().put(firstTrip.getPreviousActivity(), firstTrip);
        tour.getTrips().put(secondTrip.getPreviousActivity(), secondTrip);

        candidatePreviousActivity.setEndTime_s(stopBefore.getStartTime_s() - TravelTimes.getTravelTimeInSeconds(candidatePreviousActivity.getLocation(), stopBefore.getLocation(), Mode.UNKNOWN, stopBefore.getStartTime_s()));

    }


    /**
     * Adds one stop before the previous main activity, modifying the home activity accordingly
     * Changes the inbound trip and splits it into two subtrips
     *
     * @param stopAfter
     * @param mainActivity
     */
    public static void addStopAfter(Plan plan, Activity stopAfter, Activity mainActivity) {
        Activity candidateAfterActivity = null;
        for (Tour candidateTour : plan.getTours().values()){
            for (Trip candidateTrip : candidateTour.getTrips().values()){
                if (candidateTrip.getPreviousActivity().equals(mainActivity)){
                    candidateAfterActivity = candidateTrip.getNextActivity();
                }
            }
        }


        Trip tripToRemove = null;
        Tour tour = null;
        for (Tour candidateTour : plan.getTours().values()){
            for (Trip candidateTrip : candidateTour.getTrips().values()){
                if (candidateTrip.getPreviousActivity().equals(mainActivity)){
                    tour = candidateTour;
                    tripToRemove = candidateTrip;
                    break;
                }

            }
        }

        tour.getActivities().put(stopAfter.getStartTime_s(), stopAfter);
        tour.getTrips().remove(tripToRemove.getPreviousActivity());

        Trip firstTrip = new Trip(mainActivity, stopAfter);
        Trip secondTrip = new Trip(stopAfter, candidateAfterActivity);
        tour.getTrips().put(firstTrip.getPreviousActivity(), firstTrip);
        tour.getTrips().put(secondTrip.getPreviousActivity(), secondTrip);

        candidateAfterActivity.setStartTime_s(stopAfter.getEndTime_s() + TravelTimes.getTravelTimeInSeconds(stopAfter.getLocation(), candidateAfterActivity.getLocation(), Mode.UNKNOWN, stopAfter.getEndTime_s()));
    }
}
