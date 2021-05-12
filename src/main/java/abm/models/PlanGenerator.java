package abm.models;

import abm.data.geo.Coordinates;
import abm.data.geo.Location;
import abm.data.plans.Activity;
import abm.data.plans.Plan;
import abm.data.plans.PlanUtils;
import abm.data.plans.Purpose;
import abm.data.pop.Household;
import abm.data.pop.Person;

import java.io.FileNotFoundException;
import java.io.PrintWriter;

public class PlanGenerator {

    public static void main(String[] args) throws FileNotFoundException {

        new PlanGenerator().run();
    }


    void run() throws FileNotFoundException {

        Household hh = new Household(0, new Location(new Coordinates(0,0)));
        Person person = new Person(0, hh);
        hh.getPersons().add(person);
        Plan plan = Plan.initializePlan(person);
        int interval_s = 10 * 60;

        PrintWriter out = new PrintWriter("output3.csv");
        double time = interval_s/2;
        while (time < ScheduleUtils.endOfTheDay()){
            out.print(time + ",");
            time += interval_s;
        }
        double endTime = time+=interval_s;
        out.print(endTime);
        out.println();


        out.println(plan.logPlan(interval_s));

        //mandatory main tours
        Activity workActivity = new Activity(Purpose.W, 8 * 3600, 16 * 3600, new Location(new Coordinates(10000, 0)));
        PlanUtils.addMainTour(plan, workActivity);
        out.println(plan.logPlan(interval_s));

        //mandatory main subtours and stops
        PlanUtils.addStopBefore(plan, new Activity(Purpose.A, 7.50 * 3600, 7.85*3600, new Location(new Coordinates(1500,0))), workActivity);
        out.println(plan.logPlan(interval_s));

        PlanUtils.addSubtour(plan, new Activity(Purpose.O, 12*3600, 12.75 * 3600, new Location(new Coordinates(13000,0))));
        out.println(plan.logPlan(interval_s));

        //discretionary tours
        Activity shoppingActivity = new Activity(Purpose.S, 18 * 3600, 18.40 * 3600, new Location(new Coordinates(-2000, 0)));
        PlanUtils.addMainTour(plan, shoppingActivity);
        out.println(plan.logPlan(interval_s));

        //discretionary tour stops
        PlanUtils.addStopAfter(plan, new Activity(Purpose.R, 18.5*3600, 19.5 * 3600, new Location(new Coordinates(-4000, 0))), shoppingActivity);
        out.println(plan.logPlan(interval_s));

        out.close();
    }
}
