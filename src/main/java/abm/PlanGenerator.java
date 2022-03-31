package abm;

import abm.data.DataSet;
import abm.data.geo.MicroscopicLocation;
import abm.data.plans.*;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.data.travelTimes.SimpleTravelTimes;
import abm.io.output.OutputWriter;
import abm.models.activityGeneration.frequency.FrequencyGenerator;
import abm.models.activityGeneration.frequency.SimpleFrequencyGenerator;
import abm.models.activityGeneration.splitByType.SimpleSplitByType;
import abm.models.activityGeneration.splitByType.SimpleSplitStopType;
import abm.models.activityGeneration.splitByType.SplitByType;
import abm.models.activityGeneration.splitByType.SplitStopType;
import abm.models.activityGeneration.time.*;
import abm.models.destinationChoice.DestinationChoice;
import abm.models.destinationChoice.SimpleDestinationChoice;
import abm.models.modeChoice.HabitualModeChoice;
import abm.models.modeChoice.SimpleHabitualModeChoice;
import abm.models.modeChoice.SimpleTourModeChoice;
import abm.models.modeChoice.TourModeChoice;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlanGenerator {
    HabitualModeChoice habitualModeChoice = new SimpleHabitualModeChoice();
    FrequencyGenerator frequencyGenerator = new SimpleFrequencyGenerator();

    DestinationChoice destinationChoice = new SimpleDestinationChoice();
    TourModeChoice tourModeChoice = new SimpleTourModeChoice();
    DayOfWeekMandatoryAssignment dayOfWeekMandatoryAssignment = new SimpleDayOfWeekMandatoryAssignment();

    DayOfWeekDiscretionaryAssignment dayOfWeekDiscretionaryAssignment = new SimpleDayOfWeekDiscretionaryAssignment();
    TimeAssignment timeAssignment = new SimpleTimeAssignment();


    SplitByType splitByType = new SimpleSplitByType();
    SplitStopType stopSplitType = new SimpleSplitStopType();

    PlanTools planTools; ;

    private final DataSet dataSet;


    public PlanGenerator(DataSet dataSet) {
        this.dataSet = dataSet;
        this.planTools = new PlanTools(dataSet.getTravelTimes());
    }

    public void run() {
        for (Household household : dataSet.getHouseholds().values()) {
            for (Person person : household.getPersons()){
                createPlanForOnePerson(person);
            }
        }
    }

    private void createPlanForOnePerson(Person person) {

        Plan plan = Plan.initializePlan(person);

        habitualModeChoice.chooseHabitualMode(person);

        for (Purpose purpose : Purpose.getMandatoryPurposes()) {
            int numAct = frequencyGenerator.calculateNumberOfActivitiesPerWeek(person, purpose);
            DayOfWeek[] dayOfWeeks = dayOfWeekMandatoryAssignment.assignDaysOfWeek(numAct, purpose);

            for (DayOfWeek day : dayOfWeeks) {
                Activity activity = new Activity(person, purpose);
                destinationChoice.selectMainActivityDestination(person, activity);
                activity.setDayOfWeek(day);
                timeAssignment.assignTime(activity);
                planTools.addMainTour(plan, activity);
            }
        }


        List<Activity> discretionaryActivities = new ArrayList<>();
        for (Purpose purpose : Purpose.getDiscretionaryPurposes()) {
            int numAct = frequencyGenerator.calculateNumberOfActivitiesPerWeek(person, purpose);
            for (int i = 0; i <= numAct; i++) {
                Activity activity = new Activity(person, purpose);
                discretionaryActivities.add(activity);
            }
        }

        Collections.shuffle(discretionaryActivities, Utils.random);

        List<Activity> stopsOnMandatory = new ArrayList<>();
        List<Activity> primaryDiscretionaryActivities = new ArrayList<>();
        List<Activity> stopsOnDiscretionaryTours = new ArrayList<>();

        for (Activity activity : discretionaryActivities) {
            DiscretionaryActivityType discretionaryActivityType = splitByType.assignActivityType(activity, person);
            activity.setDiscretionaryActivityType(discretionaryActivityType);
            switch (discretionaryActivityType) {
                case ON_MANDATORY_TOUR:
                    stopsOnMandatory.add(activity);
                    break;
                case PRIMARY:
                    primaryDiscretionaryActivities.add(activity);
                    break;
                case ON_DISCRETIONARY_TOUR:
                    stopsOnDiscretionaryTours.add(activity);
                    break;
            }
        }

        stopsOnMandatory.forEach(activity -> {

                    Tour selectedTour = planTools.findMandatoryTour(plan);
                    activity.setDayOfWeek(selectedTour.getMainActivity().getDayOfWeek());

                    StopType stopType = stopSplitType.getStopType(person, activity);

                    if (stopType.equals(StopType.BEFORE)) {
                        double tempTime = selectedTour.getActivities().firstKey();
                        Activity firstActivity = selectedTour.getActivities().get(tempTime);
                        destinationChoice.selectStopDestination(person, plan.getHomeActivities().get(plan.getHomeActivities().firstKey()), activity, firstActivity);
                        timeAssignment.assignTimeToStop(activity); //till this step, we should know whether the current trip is before or after mandatory activity
                        planTools.addStopBefore(plan, activity, selectedTour);
                    } else {
                        double tempTime = selectedTour.getActivities().lastKey();
                        Activity lastActivity = selectedTour.getActivities().get(tempTime);
                        destinationChoice.selectStopDestination(person, plan.getHomeActivities().get(plan.getHomeActivities().firstKey()), activity, lastActivity);
                        timeAssignment.assignTimeToStop(activity); //till this step, we should know whether the current trip is before or after mandatory activity
                        planTools.addStopAfter(plan, activity, selectedTour);
                    }
                }
        );

        primaryDiscretionaryActivities.forEach(activity -> {

            destinationChoice.selectMainActivityDestination(person, activity);
            dayOfWeekDiscretionaryAssignment.assignDayOfWeek(activity);
            timeAssignment.assignTime(activity);
            planTools.addMainTour(plan, activity);

        });

        stopsOnDiscretionaryTours.forEach(activity -> {

            Tour selectedTour = planTools.findDiscretionaryTour(plan);
            activity.setDayOfWeek(selectedTour.getMainActivity().getDayOfWeek());

            StopType stopType = stopSplitType.getStopType(person, activity);

            if (stopType.equals(StopType.BEFORE)) {
                //Todo in MOP we consider the first stop as primary activity, so the analysis is different from the code implementation here
                double tempTime = selectedTour.getActivities().firstKey();
                Activity firstActivity = selectedTour.getActivities().get(tempTime);
                destinationChoice.selectStopDestination(person, plan.getHomeActivities().get(plan.getHomeActivities().firstKey()), activity, firstActivity);
                timeAssignment.assignTimeToStop(activity); //till this step, we should know whether the current trip is before or after mandatory activity
                planTools.addStopBefore(plan, activity, selectedTour);
            } else {
                double tempTime = selectedTour.getActivities().lastKey();
                Activity lastActivity = selectedTour.getActivities().get(tempTime);
                destinationChoice.selectStopDestination(person, plan.getHomeActivities().get(plan.getHomeActivities().firstKey()), activity, lastActivity);
                timeAssignment.assignTimeToStop(activity); //till this step, we should know whether the current trip is before or after mandatory activity
                planTools.addStopAfter(plan, activity, selectedTour);
            }

        });

        plan.getTours().values().forEach(tour -> {
            tourModeChoice.chooseMode(person, tour);
        });

    }


}
