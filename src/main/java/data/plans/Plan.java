package data.plans;

import data.geo.Location;
import data.geo.TravelTimes;
import data.pop.Person;
import models.ScheduleUtils;

import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class Plan {

    Person person;
    SortedMap<Double, Activity> activities;
    SortedMap<Activity, Trip> trips;

    private Plan() {

    }


    public static Plan initializePlan(Person person) {
        Plan plan = new Plan();
        plan.person = person;
        plan.activities = new TreeMap<>();
        plan.activities.put(0., new Activity(Purpose.H, ScheduleUtils.startOfTheDay(), ScheduleUtils.endOfTheDay(), person.getHousehold().getLocation()));
        plan.trips = new TreeMap<Activity, Trip>();
        return plan;
    }

    /**
     * Adds a main activity tour. Cuts the home activity into two pieces, one before the tour and another after the tour
     * Adds trip
     *
     * @param mainTourActivity
     */
    public void addMainTour(Activity mainTourActivity) {
        //find the home activity
        Activity homeActivity = null;
        for (Activity candidateActivity : activities.values().stream().filter(a -> a.getPurpose().equals(Purpose.H)).collect(Collectors.toList())) {
            if (mainTourActivity.getStartTime_s() > candidateActivity.getStartTime_s() && mainTourActivity.getEndTime_s() < candidateActivity.getEndTime_s()) {
                homeActivity = candidateActivity;
                break;
            }
        }
        //add the new activity and break the home activity
        if (homeActivity != null) {
            this.activities.put(mainTourActivity.getStartTime_s(), mainTourActivity);
            double timeToMainActivity = TravelTimes.getTravelTimeInSeconds(homeActivity.getLocation(), mainTourActivity.getLocation(), Mode.UNKNOWN, mainTourActivity.getStartTime_s());

            homeActivity.setEndTime_s(mainTourActivity.getStartTime_s() - timeToMainActivity);
            trips.put(homeActivity, new Trip(homeActivity, mainTourActivity));

            Activity secondHomeActivity = new Activity(Purpose.H, mainTourActivity.getEndTime_s() + timeToMainActivity, ScheduleUtils.endOfTheDay(), homeActivity.getLocation());
            activities.put(secondHomeActivity.getStartTime_s(), secondHomeActivity);
            trips.put(mainTourActivity, new Trip(mainTourActivity, secondHomeActivity));
        }
    }

    /**
     * Adds a main activity subtour. Cuts the main activity into two pieces, one before the tour and another after the tour
     * Adds trips
     *
     * @param subTourActivity
     */
    public void addSubtour(Activity subTourActivity) {
        Activity mainActivity = null;
        for (Activity candidateActivity : activities.values().stream().filter(a -> !a.getPurpose().equals(Purpose.H)).collect(Collectors.toList())) {
            if (subTourActivity.getStartTime_s() > candidateActivity.getStartTime_s() && subTourActivity.getEndTime_s() < candidateActivity.getEndTime_s()) {
                mainActivity = candidateActivity;
                break;
            }
        }
        //add the new activity and break the home activity
        if (mainActivity != null) {
            this.activities.put(subTourActivity.getStartTime_s(), subTourActivity);
            double timeToSubTourActivity = TravelTimes.getTravelTimeInSeconds(mainActivity.getLocation(), subTourActivity.getLocation(), Mode.UNKNOWN, subTourActivity.getStartTime_s());
            double previousEndOfMainActivity = mainActivity.getEndTime_s();
            mainActivity.setEndTime_s(subTourActivity.getStartTime_s() - timeToSubTourActivity);
            Trip previousTripFromMainAcitivty = trips.get(mainActivity);
            trips.put(mainActivity, new Trip(mainActivity, subTourActivity));
            Activity secondMainActivity = new Activity(mainActivity.getPurpose(), subTourActivity.getEndTime_s() + timeToSubTourActivity, previousEndOfMainActivity, mainActivity.getLocation());
            activities.put(secondMainActivity.getStartTime_s(), secondMainActivity);
            trips.put(subTourActivity, new Trip(subTourActivity, secondMainActivity));
            trips.put(secondMainActivity, new Trip(secondMainActivity, previousTripFromMainAcitivty.getNextActivity()));
        }
    }

    /**
     * Adds one stop before the next main activity, modifying the home activity accordingly
     * Changes the outbound trip and splits it into two subtrips
     */
    public void addStopBefore(Activity stopBefore, Activity mainActivity) {
        Activity candidatePreviousActivity = activities.get(activities.firstKey());
        Iterator iterator = activities.keySet().iterator();
        iterator.next(); //gets and discards the previous activity
        Activity nextActivity = activities.get(iterator.next());
        while (!nextActivity.equals(mainActivity) && iterator.hasNext()) {
            candidatePreviousActivity = nextActivity;
            nextActivity = activities.get(iterator.next());
        }
        activities.put(stopBefore.getStartTime_s(), stopBefore);
        Trip tripToRemove = trips.get(candidatePreviousActivity);
        trips.remove(tripToRemove.getPreviousActivity());

        Trip firstTrip = new Trip(candidatePreviousActivity, stopBefore);
        Trip secondTrip = new Trip(stopBefore, mainActivity);
        trips.put(firstTrip.getPreviousActivity(), firstTrip);
        trips.put(secondTrip.getPreviousActivity(), secondTrip);

        candidatePreviousActivity.setEndTime_s(stopBefore.getStartTime_s() - TravelTimes.getTravelTimeInSeconds(candidatePreviousActivity.getLocation(), stopBefore.getLocation(), Mode.UNKNOWN, stopBefore.getStartTime_s()));

    }

    /**
     * Adds one stop before the previous main activity, modifying the home activity accordingly
     * Changes the inbound trip and splits it into two subtrips
     *
     * @param stopAfter
     * @param mainActivity
     */
    public void addStopAfter(Activity stopAfter, Activity mainActivity) {
        Activity candidateAfterActivity = activities.get(activities.lastKey());
        Iterator iterator = activities.keySet().stream().sorted(Comparator.reverseOrder()).iterator();
        iterator.next(); //waste the first one
        Activity previousActivity = activities.get(iterator.next());
        while (!previousActivity.equals(mainActivity) && iterator.hasNext()) {
            candidateAfterActivity = previousActivity;
            previousActivity = activities.get(iterator.next());
        }
        activities.put(stopAfter.getStartTime_s(), stopAfter);
        Trip tripToRemove = trips.get(mainActivity);
        trips.remove(tripToRemove.getPreviousActivity());

        Trip firstTrip = new Trip(mainActivity, stopAfter);
        Trip secondTrip = new Trip(stopAfter, candidateAfterActivity);
        trips.put(firstTrip.getPreviousActivity(), firstTrip);
        trips.put(secondTrip.getPreviousActivity(), secondTrip);

        candidateAfterActivity.setStartTime_s(stopAfter.getEndTime_s() + TravelTimes.getTravelTimeInSeconds(stopAfter.getLocation(), candidateAfterActivity.getLocation(), Mode.UNKNOWN, stopAfter.getEndTime_s()));
    }

    public Person getPerson() {
        return person;
    }

    public SortedMap<Double, Activity> getActivities() {
        return activities;
    }

    public SortedMap<Activity, Trip> getTrips() {
        return trips;
    }

    public String logPlan(double interval_s){
        double time = ScheduleUtils.startOfTheDay() + interval_s/2;
        StringBuilder string = new StringBuilder();
        int size = 0;
        while (time <= ScheduleUtils.endOfTheDay()){
            boolean inActivity = false;
            for (Activity a : activities.values()){
                if (time <= a.getEndTime_s() && time > a.getStartTime_s()){
                    string.append(a.getPurpose().toString()).append(",");
                    size++;
                    inActivity = true;
                    break;
                }
            }
            if (!inActivity){
                for (Trip t : trips.values()){
                    if (time > t.getPreviousActivity().getEndTime_s() && time < t.getNextActivity().getStartTime_s()){
                        string.append("T" + ",");
                        size++;
                        break;
                    }
                }
            }
            time += interval_s;

        }
        string.append(size);
        return string.toString();
    }

}
