package abm.models;

import abm.data.DataSet;
import abm.data.plans.*;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.models.activityGeneration.frequency.FrequencyGenerator;
import abm.models.activityGeneration.frequency.SimpleFrequencyGenerator;
import abm.models.activityGeneration.splitByType.*;
import abm.models.activityGeneration.time.*;
import abm.models.destinationChoice.DestinationChoice;
import abm.models.destinationChoice.SimpleDestinationChoice;
import abm.models.modeChoice.HabitualModeChoice;
import abm.models.modeChoice.SimpleHabitualModeChoice;
import abm.models.modeChoice.SimpleTourModeChoice;
import abm.models.modeChoice.TourModeChoice;
import abm.utils.PlanTools;
import org.apache.log4j.Logger;


import java.time.DayOfWeek;
import java.util.*;

public class PlanGenerator {

    private static Logger logger = Logger.getLogger(PlanGenerator.class);

    private HabitualModeChoice habitualModeChoice;
    private FrequencyGenerator frequencyGenerator;
    private DestinationChoice destinationChoice;
    private TourModeChoice tourModeChoice;
    private DayOfWeekMandatoryAssignment dayOfWeekMandatoryAssignment;
    private DayOfWeekDiscretionaryAssignment dayOfWeekDiscretionaryAssignment = new SimpleDayOfWeekDiscretionaryAssignment();
    private TimeAssignment timeAssignment;
    private SplitByType splitByType;
    private SplitStopType stopSplitType;

    PlanTools planTools;

    {
        stopSplitType = new SimpleSplitStopTypeWithTimeAvailability();
        splitByType = new SimpleSplitByType();
        timeAssignment = new SimpleTimeAssignmentWithTimeAvailability();
        dayOfWeekMandatoryAssignment = new SimpleDayOfWeekMandatoryAssignment();
        destinationChoice = new SimpleDestinationChoice();
        tourModeChoice = new SimpleTourModeChoice();
        habitualModeChoice = new SimpleHabitualModeChoice();
        frequencyGenerator = new SimpleFrequencyGenerator();
    }


    private final DataSet dataSet;


    public PlanGenerator(DataSet dataSet) {
        this.dataSet = dataSet;
        this.planTools = new PlanTools(dataSet.getTravelTimes());
    }

    public void run() {
        int counter = 0;
        for (Household household : dataSet.getHouseholds().values()) {
            for (Person person : household.getPersons()) {
                createPlanForOnePerson(person);
                counter++;
                if ((counter % 1000) == 0){
                    logger.info("Completed " + counter + " persons.");
                }
            }
        }
    }

    private void createPlanForOnePerson(Person person) {

        Plan plan = Plan.initializePlan(person);

        habitualModeChoice.chooseHabitualMode(person);

        for (Purpose purpose : Purpose.getMandatoryPurposes()) {
            int numberOfDaysWithMandatoryAct = frequencyGenerator.calculateNumberOfActivitiesPerWeek(person, purpose);
            DayOfWeek[] dayOfWeeks = dayOfWeekMandatoryAssignment.assignDaysOfWeek(numberOfDaysWithMandatoryAct, purpose);

            for (DayOfWeek day : dayOfWeeks) {
                Activity activity = new Activity(person, purpose);
                destinationChoice.selectMainActivityDestination(person, activity);
                activity.setDayOfWeek(day);
                timeAssignment.assignStartTimeAndDuration(activity);
                planTools.addMainTour(plan, activity);
            }
        }


        SortedMap<Purpose, List<Activity>> discretionaryActivitiesMap = new TreeMap<>();
        //List<Activity> discretionaryActivities = new ArrayList<>();
        for (Purpose purpose : Purpose.getDiscretionaryPurposes()) {
            int numAct = frequencyGenerator.calculateNumberOfActivitiesPerWeek(person, purpose);
            for (int i = 0; i <= numAct; i++) {
                Activity activity = new Activity(person, purpose);
                discretionaryActivitiesMap.putIfAbsent(purpose, new ArrayList<>());
                discretionaryActivitiesMap.get(purpose).add(activity);
            }
        }

        //Collections.shuffle(discretionaryActivities, Utils.getRandomObject());

        List<Activity> stopsOnMandatory = new ArrayList<>();
        List<Activity> primaryDiscretionaryActivities = new ArrayList<>();
        List<Activity> stopsOnDiscretionaryTours = new ArrayList<>();

        for (Purpose purpose : discretionaryActivitiesMap.keySet()) {
            for (Activity activity : discretionaryActivitiesMap.get(purpose)) {
                DiscretionaryActivityType discretionaryActivityType = splitByType.assignActivityType(activity, person);
                activity.setDiscretionaryActivityType(discretionaryActivityType);
                switch (discretionaryActivityType) {
                    case ON_MANDATORY_TOUR:
                        stopsOnMandatory.add(activity);
                        Tour selectedTour = planTools.findMandatoryTour(plan);
                        activity.setDayOfWeek(selectedTour.getMainActivity().getDayOfWeek());
                        //the order of time assignment and stopSplitByType is not yet decided
                        StopType stopType = stopSplitType.getStopType(person, activity, selectedTour);
                        timeAssignment.assignDurationToStop(activity); //till this step, we should know whether the current trip is before or after mandatory activity

                        if (stopType != null) {
                            if (stopType.equals(StopType.BEFORE)) {
                                int tempTime = selectedTour.getActivities().firstKey();
                                Activity firstActivity = selectedTour.getActivities().get(tempTime);
                                destinationChoice.selectStopDestination(person, plan.getDummyHomeActivity(), activity, firstActivity);
                                planTools.addStopBefore(plan, activity, selectedTour);
                            } else {
                                int tempTime = selectedTour.getActivities().lastKey();
                                Activity lastActivity = selectedTour.getActivities().get(tempTime);
                                destinationChoice.selectStopDestination(person, plan.getDummyHomeActivity(), activity, lastActivity);
                                //timeAssignment.assignDurationToStop(activity); //till this step, we should know whether the current trip is before or after mandatory activity
                                planTools.addStopAfter(plan, activity, selectedTour);
                            }
                        }
                        break;
                    case PRIMARY:
                        primaryDiscretionaryActivities.add(activity);
                        //need to create the tour here otherwise it will not exist and no stops can be attached!
                        destinationChoice.selectMainActivityDestination(person, activity);
                        dayOfWeekDiscretionaryAssignment.assignDayOfWeek(activity);
                        timeAssignment.assignStartTimeAndDuration(activity);
                        planTools.addMainTour(plan, activity);
                        break;
                    case ON_DISCRETIONARY_TOUR:
                        stopsOnDiscretionaryTours.add(activity);
                        break;
                }
            }

        }

        stopsOnMandatory.forEach(activity -> {


                }
        );

        stopsOnDiscretionaryTours.forEach(activity -> {

            Tour selectedTour = planTools.findDiscretionaryTour(plan);
            activity.setDayOfWeek(selectedTour.getMainActivity().getDayOfWeek());

            StopType stopType = stopSplitType.getStopType(person, activity, selectedTour);
            timeAssignment.assignDurationToStop(activity); //till this step, we should know whether the current trip is before or after mandatory activity

            if (stopType != null) {
                if (stopType.equals(StopType.BEFORE)) {
                    int tempTime = selectedTour.getActivities().firstKey();
                    Activity firstActivity = selectedTour.getActivities().get(tempTime);
                    destinationChoice.selectStopDestination(person, plan.getDummyHomeActivity(), activity, firstActivity);
                    planTools.addStopBefore(plan, activity, selectedTour);
                } else {
                    int tempTime = selectedTour.getActivities().lastKey();
                    Activity lastActivity = selectedTour.getActivities().get(tempTime);
                    destinationChoice.selectStopDestination(person, plan.getDummyHomeActivity(), activity, lastActivity);
                    //timeAssignment.assignDurationToStop(activity); //till this step, we should know whether the current trip is before or after mandatory activity
                    planTools.addStopAfter(plan, activity, selectedTour);
                }
            }

        });

        plan.getTours().values().forEach(tour -> {
            tourModeChoice.chooseMode(person, tour);
        });

    }


}
