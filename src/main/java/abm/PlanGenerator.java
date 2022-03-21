package abm;

import abm.data.geo.MicroscopicLocation;
import abm.data.plans.*;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.data.travelTimes.SimpleTravelTimes;
import abm.models.activityGeneration.frequency.FrequencyGenerator;
import abm.models.activityGeneration.frequency.SimpleFrequencyGenerator;
import abm.models.activityGeneration.splitByType.SimpleSplitByType;
import abm.models.activityGeneration.splitByType.SplitByType;
import abm.models.activityGeneration.time.*;
import abm.models.destinationChoice.DestinationChoice;
import abm.models.destinationChoice.SimpleDestinationChoice;
import abm.models.modeChoice.HabitualModeChoice;
import abm.models.modeChoice.SimpleHabitualModeChoice;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlanGenerator {
    HabitualModeChoice habitualModeChoice = new SimpleHabitualModeChoice();
    FrequencyGenerator frequencyGenerator = new SimpleFrequencyGenerator();

    DestinationChoice destinationChoice = new SimpleDestinationChoice();
    DayOfWeekMandatoryAssignment dayOfWeekMandatoryAssignment = new SimpleDayOfWeekMandatoryAssignment();


    DayOfWeekDiscretionaryAssignment dayOfWeekDiscretionaryAssignment = new SimpleDayOfWeekDiscretionaryAssignment();
    TimeAssignment timeAssignment = new SimpleTimeAssignment();

    SplitByType splitByType = new SimpleSplitByType();

    PlanTools planTools = new PlanTools(new SimpleTravelTimes());

    public static void main(String[] args) {
        PlanGenerator planGenerator = new PlanGenerator();
        planGenerator.run();
    }

    public void run() {

        MicroscopicLocation homeLocation = new MicroscopicLocation(112, 123);
        Household household = new Household(1, homeLocation);
        Person person = new Person(1, household);

        createPlanForOnePerson(person);


    }

    private void createPlanForOnePerson(Person person) {

        Plan plan = Plan.initializePlan(person);

        habitualModeChoice.chooseHabitualMode(person);

        for (Purpose purpose : Purpose.getMandatoryPurposes()) {
            int numAct = frequencyGenerator.calculateNumberOfActivitiesPerWeek(person, purpose);
            DayOfWeek[] dayOfWeeks = dayOfWeekMandatoryAssignment.assignDaysOfWeek(numAct, purpose);

            for (DayOfWeek day : dayOfWeeks) {
                Activity activity = new Activity(purpose);
                destinationChoice.selectMainActivityDestination(person, activity);
                activity.setDayOfWeek(day);
                timeAssignment.assignTime(activity);
                planTools.addMainTour(plan, activity);
            }
        }


        List<Activity> discretionaryActivities = new ArrayList<>();
        for (Purpose purpose : Purpose.getDiscretionaryPurposes()) {
            int numAct = frequencyGenerator.calculateNumberOfActivitiesPerWeek(person, purpose);
            for (int i = 0; i <= numAct; i++){
                Activity activity = new Activity(purpose);
                discretionaryActivities.add(activity);
            }
        }

        Collections.shuffle(discretionaryActivities);

        for (Activity activity : discretionaryActivities){

            DiscretionaryActivityType discretionaryActivityType = splitByType.assignActivityType(activity, person);
            if (discretionaryActivityType.equals(DiscretionaryActivityType.ON_MANDATORY_TOUR)){

                //Todo select a mandatory tour
                Tour selectedTour = findMandatoryTour(plan);
                activity.setDayOfWeek(selectedTour.getMainActivity().getDayOfWeek());
                timeAssignment.assignTime(activity); //till this step, we should know whether the current trip is before or after mandatory activity

                if (activity.getStartTime_s() > selectedTour.getMainActivity().getStartTime_s()){
                    double tempTime = selectedTour.getActivities().firstKey();
                    Activity firstActivity = selectedTour.getActivities().get(tempTime);
                    destinationChoice.selectStopDestination(person, plan.getHomeActivities().get(0), activity, firstActivity);
                    planTools.addStopBefore(plan, activity, firstActivity);
                }{
                    double tempTime = selectedTour.getActivities().lastKey();
                    Activity lastActivity = selectedTour.getActivities().get(tempTime);
                    destinationChoice.selectStopDestination(person, plan.getHomeActivities().get(0), activity, lastActivity);
                    planTools.addStopAfter(plan, activity, lastActivity);
                }
            }else{



            }

        }



    }

    private Tour findMandatoryTour(Plan plan) {
        return null;
    }

}
