package abm.data.plans;

import abm.data.pop.Person;
import abm.ScheduleUtils;

import java.util.*;

public class Plan {

    private Person person;
    private int id;
    private SortedMap<Double, Activity> homeActivities;
    private SortedMap<Double, Tour> tours;

    private Plan() {

    }

    public SortedMap<Double, Tour> getTours() {
        return tours;
    }

    public static Plan initializePlan(Person person) {
        Plan plan = new Plan();
        plan.id = person.getId();
        plan.person = person;
        plan.homeActivities = new TreeMap<>();
        plan.homeActivities.put(0., new Activity(Purpose.HOME, ScheduleUtils.startOfTheWeek(), ScheduleUtils.endOfTheWeek(), person.getHousehold().getLocation()));
        plan.tours = new TreeMap<>();
        person.setPlan(plan);
        return plan;
    }


    public Person getPerson() {
        return person;
    }

    public SortedMap<Double, Activity> getHomeActivities() {
        return homeActivities;
    }


    public String logPlan(double interval_s) {
        double time = ScheduleUtils.startOfTheWeek() + interval_s / 2;
        StringBuilder string = new StringBuilder();
        string.append(person.getId()).append(",").append(person.getHousehold().getId()).append(",");
        int size = 0;
        while (time <= ScheduleUtils.endOfTheWeek()) {
            for (Activity a : homeActivities.values()) {
                if (time <= a.getEndTime_s() && time > a.getStartTime_s()) {
                    string.append(a.getPurpose().toString()).append(",");
                    size++;
                    time += interval_s;
                    break;
                }
            }
            for (Tour tour : tours.values()) {
                for (Activity a : tour.getActivities().values()) {
                    if (time <= a.getEndTime_s() && time > a.getStartTime_s()) {
                        string.append(a.getPurpose().toString()).append(",");
                        size++;
                        time += interval_s;
                        break;
                    }
                }
                for (Leg t : tour.getLegs().values()) {
                    if (time >= t.getPreviousActivity().getEndTime_s() && time < t.getNextActivity().getStartTime_s()) {
                        string.append("T" + ",");
                        size++;
                        time += interval_s;
                        break;
                    }
                }
                for (Tour subtour : tour.getSubtours().values()) {
                    for (Activity a : subtour.getActivities().values()) {
                        if (time <= a.getEndTime_s() && time > a.getStartTime_s()) {
                            string.append(a.getPurpose().toString()).append(",");
                            size++;
                            time += interval_s;
                            break;
                        }
                    }
                    for (Leg t : subtour.getLegs().values()) {
                        if (time >= t.getPreviousActivity().getEndTime_s() && time < t.getNextActivity().getStartTime_s()) {
                            string.append("T" + ",");
                            size++;
                            time += interval_s;
                            break;
                        }
                    }
                }

            }



        }
        string.append("H");
        return string.toString();
    }

}
