package abm.io.output;

import abm.data.DataSet;
import abm.data.plans.Activity;
import abm.data.plans.Leg;
import abm.data.plans.Purpose;
import abm.data.plans.Tour;
import abm.data.pop.Person;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.time.DayOfWeek;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class ActivityPrinter {

    private final DataSet dataSet;

    public ActivityPrinter(DataSet dataSet) {
        this.dataSet = dataSet;
    }


    public void print(String fileName) throws FileNotFoundException {

        PrintWriter pw = new PrintWriter(fileName);

        pw.println(Activity.getHeader());


        for (Person person : dataSet.getPersons().values()) {

            if (person.getHousehold().getSimulated()) {

                if (person.getPlan() != null) {

                    if (person.getPlan().getTours().size() == 0) {

                        for (DayOfWeek day : DayOfWeek.values()) {

                            Activity homeAct = new Activity(person, Purpose.HOME);
                            homeAct.setStartTime_min(day.ordinal() * 60 * 24);
                            homeAct.setEndTime_min((day.ordinal() + 1) * 60 * 24 - 1);
                            homeAct.setLocation(person.getHousehold().getLocation());
                            homeAct.setDayOfWeek(day);

                            pw.println(homeAct);
                        }


                    } else {
                        // identity-based (not equals()-based) dedup: a chained in-home-WORK split
                        // can leave the same Activity object referenced as the return bookend of
                        // one tour and the departure bookend of another - print it once
                        Set<Activity> printedActivities = Collections.newSetFromMap(new IdentityHashMap<>());
                        for (Tour tour : person.getPlan().getTours().values()) {

                            if (tour.getLegs().isEmpty()) {
                                // leg-less (in-home WORK) tour - no legs to walk, print the main activity directly
                                for (Activity activity : tour.getActivities().values()) {
                                    if (printedActivities.add(activity)) {
                                        pw.println(activity.toString());
                                    }
                                }
                                continue;
                            }

                            for (Leg leg : tour.getLegs().values()) {
                                if (printedActivities.add(leg.getPreviousActivity())) {
                                    pw.println(leg.getPreviousActivity().toString());
                                }
                                if (tour.getLegs().get(tour.getLegs().lastKey()).equals(leg)) {
                                    if (printedActivities.add(leg.getNextActivity())) {
                                        pw.println(leg.getNextActivity().toString());
                                    }
                                }
                            }

                        }
                    }


                }
            } else {

            }


        }

        pw.close();


    }

}
