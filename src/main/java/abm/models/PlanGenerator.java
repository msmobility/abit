package abm.models;

import abm.data.geo.MicroscopicLocation;
import abm.data.geo.MitoBasedTravelTimes;
import abm.data.geo.SimpleTravelTimes;
import abm.data.plans.Activity;
import abm.data.plans.Plan;
import abm.data.plans.PlanTools;
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

        PlanTools planTools = new PlanTools(new SimpleTravelTimes());

        final long start = System.currentTimeMillis();

        int numberOfIdenticalHouseholds = 10;
        int interval_s = 30 * 60;

        PrintWriter out = new PrintWriter("output3.csv");
        out.print("person,household,");

        double time = interval_s/2;


        while (time < ScheduleUtils.endOfTheDay()){
            out.print(time + ",");
            time += interval_s;
        }
        double endTime = time+=interval_s;
        out.print(endTime);
        out.println();



        for (int hhId = 0; hhId < numberOfIdenticalHouseholds; hhId++){
            Household hh = new Household(hhId, new MicroscopicLocation(0,0));
            Person person = new Person(0, hh);
            hh.getPersons().add(person);
            Plan plan = Plan.initializePlan(person);

            //out.println(plan.logPlan(interval_s));

            //mandatory main tours
            Activity workActivity = new Activity(Purpose.W, 8 * 3600, 16 * 3600, new MicroscopicLocation(10000, 0));
            planTools.addMainTour(plan, workActivity);
            //out.println(plan.logPlan(interval_s));

            //mandatory main subtours and stops
            planTools.addStopBefore(plan, new Activity(Purpose.A, 7.50 * 3600, 7.85*3600, new MicroscopicLocation(1500,0)), workActivity);
            //out.println(plan.logPlan(interval_s));

            planTools.addSubtour(plan, new Activity(Purpose.O, 12*3600, 12.75 * 3600, new MicroscopicLocation(13000,0)));
            //out.println(plan.logPlan(interval_s));

            //discretionary tours
            Activity shoppingActivity = new Activity(Purpose.S, 18 * 3600, 18.40 * 3600, new MicroscopicLocation(-2000, 0));
            planTools.addMainTour(plan, shoppingActivity);
            //out.println(plan.logPlan(interval_s));

            //discretionary tour stops
            planTools.addStopAfter(plan, new Activity(Purpose.R, 18.5*3600, 19.5 * 3600, new MicroscopicLocation(-4000, 0)), shoppingActivity);
            out.println(plan.logPlan(interval_s));
        }
        out.close();

        final long end = System.currentTimeMillis();

        long runtime = (end - start) / 1000;

        System.out.println("Completed with " + numberOfIdenticalHouseholds + " households of 1 person in " + runtime + " seconds");
    }
}
