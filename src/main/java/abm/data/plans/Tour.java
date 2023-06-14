package abm.data.plans;

import abm.data.vehicle.Vehicle;

import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

public class Tour {

    private final SortedMap<Integer, Activity> activities;
    private final SortedMap<Integer, Leg> legs;
    private final Activity mainActivity;
    private final SortedMap<Integer, Tour> subtours;
    Mode tourMode;
    private Vehicle car = null;


    public Tour(Activity mainActivity) {
        this.mainActivity = mainActivity;
        activities = new TreeMap<>();
        activities.put(mainActivity.getStartTime_min(), mainActivity);
        legs = new TreeMap<>();
        subtours = new TreeMap<>();
    }

    public SortedMap<Integer, Leg> getLegs() {
        return legs;
    }

    public SortedMap<Integer, Activity> getActivities() {
        return activities;
    }

    public SortedMap<Integer, Tour> getSubtours() {
        return subtours;
    }

    public Activity getMainActivity() {
        return mainActivity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tour tour = (Tour) o;
        return Objects.equals(activities, tour.activities) &&
                Objects.equals(legs, tour.legs) &&
                Objects.equals(mainActivity, tour.mainActivity) &&
                Objects.equals(subtours, tour.subtours) &&
                tourMode == tour.tourMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(activities, legs, mainActivity, subtours, tourMode);
    }

    public Vehicle getCar() {
        return car;
    }

    public void setCar(Vehicle car) {
        this.car = car;
    }

    public Mode getTourMode() {
        return tourMode;
    }
}
