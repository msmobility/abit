package abm.data.plans;

import abm.data.travelTimes.TravelTimes;


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
        Tour tour = new Tour(mainTourActivity);
        plan.getTours().put(mainTourActivity.getStartTime_s(), tour);
        if (homeActivity != null) {
            double timeToMainActivity = travelTimes.getTravelTimeInSeconds(homeActivity.getLocation(), mainTourActivity.getLocation(), Mode.UNKNOWN, mainTourActivity.getStartTime_s());
            double previousEndOfHomeActivity = homeActivity.getEndTime_s();
            homeActivity.setEndTime_s(mainTourActivity.getStartTime_s() - timeToMainActivity);
            tour.getLegs().put(homeActivity, new Leg(homeActivity, mainTourActivity));
            Activity secondHomeActivity = new Activity(Purpose.HOME, mainTourActivity.getEndTime_s() + timeToMainActivity, previousEndOfHomeActivity, homeActivity.getLocation());
            plan.getHomeActivities().put(secondHomeActivity.getStartTime_s(), secondHomeActivity);
            tour.getLegs().put(mainTourActivity, new Leg(mainTourActivity, secondHomeActivity));
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
            double timeToSubTourActivity = travelTimes.getTravelTimeInSeconds(mainActivity.getLocation(), subTourActivity.getLocation(), Mode.UNKNOWN, subTourActivity.getStartTime_s());
            double previousEndOfMainActivity = mainActivity.getEndTime_s();
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
     */
    public void addStopBefore(Plan plan, Activity stopBefore, Activity mainActivity) {

        Activity candidatePreviousActivity = null;
        for (Tour candidateTour : plan.getTours().values()){
            for (Leg candidateLeg : candidateTour.getLegs().values()){
                if (candidateLeg.getNextActivity().equals(mainActivity)){
                    candidatePreviousActivity = candidateLeg.getPreviousActivity();
                }
            }
        }


        Tour tour = null;
        Leg legToRemove = null;
        for (Tour candidateTour : plan.getTours().values()) {
            for (Leg candidateLeg : candidateTour.getLegs().values()){
                if (candidateLeg.getNextActivity().equals(mainActivity)){
                    tour = candidateTour;
                    legToRemove = candidateLeg;
                    break;

                }
            }
        }

        tour.getActivities().put(stopBefore.getStartTime_s(), stopBefore);
        tour.getLegs().remove(legToRemove.getPreviousActivity());

        Leg firstLeg = new Leg(candidatePreviousActivity, stopBefore);
        double timeForFirstTrip = travelTimes.getTravelTimeInSeconds(candidatePreviousActivity.getLocation(), stopBefore.getLocation(), Mode.UNKNOWN, candidatePreviousActivity.getEndTime_s());
        Leg secondLeg = new Leg(stopBefore, mainActivity);
        double timeForSecondTrip = travelTimes.getTravelTimeInSeconds(stopBefore.getLocation(), mainActivity.getLocation(), Mode.UNKNOWN, stopBefore.getEndTime_s());
        tour.getLegs().put(firstLeg.getPreviousActivity(), firstLeg);
        tour.getLegs().put(secondLeg.getPreviousActivity(), secondLeg);

        candidatePreviousActivity.setEndTime_s(stopBefore.getStartTime_s() - travelTimes.getTravelTimeInSeconds(candidatePreviousActivity.getLocation(), stopBefore.getLocation(), Mode.UNKNOWN, stopBefore.getStartTime_s()));

    }


    /**
     * Adds one stop before the previous main activity, modifying the home activity accordingly
     * Changes the inbound trip and splits it into two subtrips
     *
     * @param stopAfter
     * @param mainActivity
     */
    public void addStopAfter(Plan plan, Activity stopAfter, Activity mainActivity) {
        Activity candidateAfterActivity = null;
        for (Tour candidateTour : plan.getTours().values()){
            for (Leg candidateLeg : candidateTour.getLegs().values()){
                if (candidateLeg.getPreviousActivity().equals(mainActivity)){
                    candidateAfterActivity = candidateLeg.getNextActivity();
                }
            }
        }


        Leg legToRemove = null;
        Tour tour = null;
        for (Tour candidateTour : plan.getTours().values()){
            for (Leg candidateLeg : candidateTour.getLegs().values()){
                if (candidateLeg.getPreviousActivity().equals(mainActivity)){
                    tour = candidateTour;
                    legToRemove = candidateLeg;
                    break;
                }

            }
        }

        tour.getActivities().put(stopAfter.getStartTime_s(), stopAfter);
        tour.getLegs().remove(legToRemove.getPreviousActivity());

        Leg firstLeg = new Leg(mainActivity, stopAfter);
        Leg secondLeg = new Leg(stopAfter, candidateAfterActivity);
        tour.getLegs().put(firstLeg.getPreviousActivity(), firstLeg);
        tour.getLegs().put(secondLeg.getPreviousActivity(), secondLeg);

        candidateAfterActivity.setStartTime_s(stopAfter.getEndTime_s() + travelTimes.getTravelTimeInSeconds(stopAfter.getLocation(), candidateAfterActivity.getLocation(), Mode.UNKNOWN, stopAfter.getEndTime_s()));
    }
}
