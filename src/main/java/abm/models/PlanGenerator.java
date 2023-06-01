package abm.models;

import abm.data.DataSet;
import abm.data.plans.*;
import abm.data.pop.Person;
import abm.models.activityGeneration.frequency.FrequencyGenerator;
import abm.models.activityGeneration.frequency.SubtourGenerator;
import abm.models.activityGeneration.splitByType.*;
import abm.models.activityGeneration.time.*;
import abm.models.destinationChoice.DestinationChoice;
import abm.models.destinationChoice.SubtourDestinationChoice;
import abm.models.modeChoice.HabitualModeChoice;
import abm.models.modeChoice.SubtourModeChoice;
import abm.models.modeChoice.TourModeChoice;
import abm.utils.PlanTools;
import de.tum.bgu.msm.data.person.Occupation;
import org.apache.log4j.Logger;


import java.time.DayOfWeek;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class PlanGenerator implements Callable {

    private static Logger logger = Logger.getLogger(PlanGenerator.class);

    private HabitualModeChoice habitualModeChoice;
    private Map<Purpose, FrequencyGenerator> frequencyGenerators;
    private DestinationChoice destinationChoice;
    private TourModeChoice tourModeChoice;
    private DayOfWeekMandatoryAssignment dayOfWeekMandatoryAssignment;
    private DayOfWeekDiscretionaryAssignment dayOfWeekDiscretionaryAssignment = new SimpleDayOfWeekDiscretionaryAssignment();
    private TimeAssignment timeAssignment;
    private SplitByType splitByType;
    private SplitStopType stopSplitType;

    private SubtourGenerator subtourGenerator;
    private SubtourTimeAssignment subtourTimeAssignment;
    private SubtourDestinationChoice subtourDestinationChoice;


    PlanTools planTools;

    private AtomicInteger counter;
    private AtomicInteger stopWithoutTypecounter;

    private final DataSet dataSet;
    private List<Person> persons;
    private final int thread;
    private final SubtourModeChoice subtourModeChoice;


    public PlanGenerator(DataSet dataSet, ModelSetup modelSetup, int thread) {
        this.dataSet = dataSet;
        this.planTools = new PlanTools(dataSet.getTravelTimes());
        this.thread = thread;

        counter = new AtomicInteger(0);
        stopWithoutTypecounter = new AtomicInteger(0);

        this.stopSplitType = modelSetup.getStopSplitType();
        this.splitByType = modelSetup.getSplitByType();
        this.timeAssignment = modelSetup.getTimeAssignment();
        this.dayOfWeekMandatoryAssignment = modelSetup.getDayOfWeekMandatoryAssignment();
        this.destinationChoice = modelSetup.getDestinationChoice();
        this.tourModeChoice = modelSetup.getTourModeChoice();
        this.habitualModeChoice = modelSetup.getHabitualModeChoice();
        this.frequencyGenerators = modelSetup.getFrequencyGenerator();
        this.subtourGenerator = modelSetup.getSubtourGenerator();
        this.subtourTimeAssignment = modelSetup.getSubtourTimeAssignment();
        this.subtourDestinationChoice = modelSetup.getSubtourDestinationChoice();
        this.subtourModeChoice = modelSetup.getSubtourModeChoice();

    }

    public Callable setPersons(List<Person> persons) {
        this.persons = persons;
        return this;
    }

    private void createPlanForOnePerson(Person person) {

        Plan plan = Plan.initializePlan(person);

        habitualModeChoice.chooseHabitualMode(person);

        //logger.info("Habitual mode completed for " + person.getId());

        for (Purpose purpose : Purpose.getMandatoryPurposes()) {
            int numberOfDaysWithMandatoryAct = frequencyGenerators.get(purpose).calculateNumberOfActivitiesPerWeek(person, purpose);
            DayOfWeek[] dayOfWeeks = dayOfWeekMandatoryAssignment.assignDaysOfWeek(numberOfDaysWithMandatoryAct, purpose, person);

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
            int numAct = frequencyGenerators.get(purpose).calculateNumberOfActivitiesPerWeek(person, purpose);
            for (int i = 0; i < numAct; i++) {
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
                        timeAssignment.assignDurationToStop(activity); //till this step, we should know whether the current trip is before or after mandatory activity
                        StopType stopType = stopSplitType.getStopType(person, activity, selectedTour);

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
            timeAssignment.assignDurationToStop(activity);
            StopType stopType = stopSplitType.getStopType(person, activity, selectedTour);
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
                    planTools.addStopAfter(plan, activity, selectedTour);
                }
            } else {
                //logger.warn("Stops without a valid type: " + stopWithoutTypecounter.incrementAndGet());
            }

        });

        plan.getTours().values().forEach(tour -> {
            tourModeChoice.chooseMode(person, tour);
        });


        List<Tour> mandatoryTours = plan.getTours().values().stream().filter(tour -> Purpose.getMandatoryPurposes().contains(tour.getMainActivity().getPurpose())).collect(Collectors.toList());

        for (Tour tour : mandatoryTours) {
            boolean hasSubtour = subtourGenerator.hasSubtourInMandatoryActivity(tour.getMainActivity());
            if (hasSubtour) {
                Activity subtourActivity = new Activity(person, Purpose.SUBTOUR);
                subtourActivity.setTour(tour);

                subtourTimeAssignment.assignTimeToSubtourActivity(subtourActivity, tour.getMainActivity());
                subtourDestinationChoice.chooseSubtourDestination(subtourActivity, tour.getMainActivity());
                planTools.addSubtour(subtourActivity, tour);
                subtourModeChoice.chooseSubtourMode(subtourActivity, tour.getMainActivity());


            }
        }


    }


    @Override
    public Object call() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);
        for (Person person : this.persons) {
            createPlanForOnePerson(person);
            counter.incrementAndGet();
            final int i = counter.get();
            if ((i % 1000) == 0) {
                logger.info("Completed " + i + " persons by thread " + thread);
            }

        }
        return null;
    }
}
