package abm.models;

import abm.data.DataSet;
import abm.data.geo.Location;
import abm.data.plans.*;
import abm.data.pop.*;
import abm.io.input.BikeOwnershipReader;
import abm.models.activityGeneration.frequency.FrequencyGenerator;
import abm.models.activityGeneration.frequency.SubtourGenerator;
import abm.models.activityGeneration.splitByType.SplitByType;
import abm.models.activityGeneration.splitByType.SplitStopType;
import abm.models.activityGeneration.time.*;
import abm.models.destinationChoice.DestinationChoice;
import abm.models.destinationChoice.SubtourDestinationChoice;
import abm.models.modeChoice.HabitualModeChoice;
import abm.models.modeChoice.SubtourModeChoice;
import abm.models.modeChoice.TourModeChoice;
import abm.models.planConsistency.PlanConsistencyRepair;
import abm.models.remoteWorkArrangement.DayOfWeekRemoteWorkAssignmentModel;
import abm.models.remoteWorkArrangement.RemoteWorkAllowance;
import abm.models.remoteWorkArrangement.RemoteWorkFrequencyModel;
import abm.utils.AbitUtils;
import abm.utils.PlanTools;
import org.apache.log4j.Logger;

import java.time.DayOfWeek;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PlanGeneratorMuc implements Callable {

    private static Logger logger = Logger.getLogger(PlanGeneratorMuc.class);

    private RemoteWorkAllowance remoteWorkAllowance;
    private RemoteWorkFrequencyModel remoteWorkFrequencyModel;
    // todo remove this temporary assignment
    private final double TELEWORK_PROPENSITY = 0;


    private BikeOwnershipReader bikeOwnershipModel;
    private HabitualModeChoice habitualModeChoice;
    private Map<Purpose, FrequencyGenerator> frequencyGenerators;
    private DestinationChoice destinationChoice;
    private TourModeChoice tourModeChoice;
    private DayOfWeekMandatoryAssignment dayOfWeekMandatoryAssignment;
    private DayOfWeekDiscretionaryAssignment dayOfWeekDiscretionaryAssignment;
    private DayOfWeekRemoteWorkAssignmentModel dayOfWeekRemoteWorkAssignment;
    private TimeAssignment timeAssignment;
    private SplitByType splitByType;
    private SplitStopType stopSplitType;

    private SubtourGenerator subtourGenerator;
    private SubtourTimeAssignment subtourTimeAssignment;
    private SubtourDestinationChoice subtourDestinationChoice;
    private PlanConsistencyRepair planConsistencyRepair;


    private PlanTools planTools;

    private AtomicInteger stopWithoutTypecounter;

    private final DataSet dataSet;
    private List<Household> households;
    private final int thread;
    private final SubtourModeChoice subtourModeChoice;

    private final int TRIALS_RESCHEDULING = -1;


    public PlanGeneratorMuc(DataSet dataSet, ModelSetup modelSetup, int thread) {
        this.dataSet = dataSet;
        this.planTools = new PlanTools(dataSet.getTravelTimes(), dataSet.getTravelDistances());
        this.thread = thread;

        stopWithoutTypecounter = new AtomicInteger(0);

        this.stopSplitType = modelSetup.getStopSplitType();
        this.splitByType = modelSetup.getSplitByType();
        this.timeAssignment = modelSetup.getTimeAssignment();
        this.dayOfWeekMandatoryAssignment = modelSetup.getDayOfWeekMandatoryAssignment();
        this.dayOfWeekDiscretionaryAssignment = modelSetup.getDayOfWeekDiscretionaryAssignment();
        this.destinationChoice = modelSetup.getDestinationChoice();
        this.tourModeChoice = modelSetup.getTourModeChoice();
        this.habitualModeChoice = modelSetup.getHabitualModeChoice();
        this.frequencyGenerators = modelSetup.getFrequencyGenerator();
        this.subtourGenerator = modelSetup.getSubtourGenerator();
        this.subtourTimeAssignment = modelSetup.getSubtourTimeAssignment();
        this.subtourDestinationChoice = modelSetup.getSubtourDestinationChoice();
        this.subtourModeChoice = modelSetup.getSubtourModeChoice();
        this.bikeOwnershipModel = modelSetup.getBikeOwnershipReader();
        this.remoteWorkAllowance = ((ModelSetupMuc) modelSetup).getRemoteWorkAllowance();
        this.remoteWorkFrequencyModel = ((ModelSetupMuc) modelSetup).getRemoteWorkFrequencyModel();
        this.dayOfWeekRemoteWorkAssignment = ((ModelSetupMuc) modelSetup).getDayOfWeekRemoteWorkAssignmentModel();
        this.planConsistencyRepair = ((ModelSetupMuc) modelSetup).getPlanConsistencyRepair();

    }

    public Callable setHouseholds(List<Household> households) {
        this.households = households;
        return this;
    }


    private void createPlanForOneHousehold(Household household) {

        // todo call the chooseTelework method and make the telework information available for employed people within the households
        remoteWorkAllowance.assignRemoteWorkAllowance(household);


        for (Person person : household.getPersons()) {

            createPlanForOnePerson(person);
        }

        //Start: Vehicle assignment and mode choice
        if (household.getNumberOfCars() > 0) {
            for (Purpose purpose : Purpose.getSortedPurposes()) {
                if (purpose == Purpose.WORK) {
                    //Step 1: rank workers by car and transit travel time ratio for their work tour
                    // car/pt ratio the smaller (more poor pt accessibility compared to car), then higher preference to use car
                    //Step 2: check availability and choose mode for Work tours by the order of preference
                    for (Person worker : rankWorkersByCarPreference(household)) {
                        worker.getPlan().getTours().values().forEach(tour -> {
                            if (tour.getMainActivity().getPurpose() == Purpose.WORK && !tour.getLegs().isEmpty()) {
                                tourModeChoice.checkCarAvailabilityAndChooseMode(household, worker, tour, Purpose.WORK);
                            }
                        });
                    }
                } else {
                    //check availability and choose mode for other tours by the order of (education > accompany > other > shopping > recreational)
                    for (Person person : household.getPersons()) {
                        person.getPlan().getTours().values().forEach(tour -> {
                            if (tour.getMainActivity().getPurpose().equals(purpose)) {
                                tourModeChoice.checkCarAvailabilityAndChooseMode(household, person, tour, purpose);
                            }
                        });
                    }
                }
            }
        } else {
            //TODO: for the household has no car, car is still available for mode choice? (e.g. car share, taxi)
            for (Person person : household.getPersons()) {
                person.getPlan().getTours().values().forEach(tour -> {
                    if (!tour.getLegs().isEmpty()) {
                        tourModeChoice.chooseMode(person, tour, tour.getMainActivity().getPurpose(), Boolean.FALSE);
                    }
                });
            }
        }


        for (Person person : household.getPersons()) {
            List<Tour> mandatoryTours = person.getPlan().getTours().values().stream().filter(tour -> Purpose.getMandatoryPurposes().contains(tour.getMainActivity().getPurpose())).collect(Collectors.toList());
            for (Tour tour : mandatoryTours) {
                boolean hasSubtour = subtourGenerator.hasSubtourInMandatoryActivity(tour);
                if (hasSubtour) {
                    Activity subtourActivity = new Activity(person, Purpose.SUBTOUR);
                    subtourActivity.setTour(tour);
                    subtourTimeAssignment.assignTimeToSubtourActivity(subtourActivity, tour.getMainActivity());
                    subtourDestinationChoice.chooseSubtourDestination(subtourActivity, tour.getMainActivity());
                    planTools.addSubtour(subtourActivity, tour);
                    subtourModeChoice.chooseSubtourMode(tour);
                }
            }
        }

        // every leg now has a real, non-UNKNOWN mode assigned - reconcile travel-time drift
        // between the placeholder estimate used at scheduling time and the actual chosen mode
        for (Person person : household.getPersons()) {
            planConsistencyRepair.repairPlan(person.getPlan());
        }
    }

    /**
     * Ranks household workers by car/PT travel-time ratio for their WORK tour, ascending
     * (a smaller ratio means transit is comparatively worse than driving, so that worker
     * gets first pick of a household car).
     */
    private List<Person> rankWorkersByCarPreference(Household household) {
        List<Person> workers = household.getPersons().stream().filter(pp -> pp.hasWorkActivity()).collect(Collectors.toList());
        Map<Person, Double> carUsePreference = new HashMap<>();
        for (Person person : workers) {
            Location jobLocation;
            double startTime;
            if (person.getJob() != null) {
                jobLocation = person.getJob().getLocation();
                startTime = person.getJob().getStartTime_min();
            } else {
                //job location for non-employed person but has a work tour, e.g. student go for interview or internship
                Activity workActivity = person.getPlan().getTours().values().stream().filter(tour -> tour.getMainActivity().getPurpose() == Purpose.WORK).collect(Collectors.toList()).get(0).getMainActivity();
                jobLocation = workActivity.getLocation();
                startTime = workActivity.getStartTime_min();
            }

            int carTravelTime = dataSet.getTravelTimes().getTravelTimeInMinutes(person.getHousehold().getLocation(), jobLocation, Mode.CAR_DRIVER, startTime);
            // TRAIN is used as the transit proxy for this ratio
            int transitTravelTime = dataSet.getTravelTimes().getTravelTimeInMinutes(person.getHousehold().getLocation(), jobLocation, Mode.TRAIN, startTime);
            double carPtRatio = carTravelTime / (double) transitTravelTime;

            carUsePreference.put(person, carPtRatio);
        }

        List<Map.Entry<Person, Double>> sortedPreference = new ArrayList<>(carUsePreference.entrySet());
        Collections.sort(sortedPreference, Map.Entry.comparingByValue());

        return sortedPreference.stream().map(Map.Entry::getKey).collect(Collectors.toList());
    }

    private void createPlanForOnePerson(Person person) {

        Plan plan = Plan.initializePlan(person);

        bikeOwnershipModel.assignBicycleOwnership(person);
        habitualModeChoice.chooseHabitualMode(person);

        for (Purpose purpose : Purpose.getMandatoryPurposes()) {
            int numberOfDaysWithMandatoryAct = frequencyGenerators.get(purpose).calculateNumberOfActivitiesPerWeek(person, purpose);
            //TODO Ana has new job properties, this model needs be killed after updating the sp reader
            DayOfWeek[] dayOfWeeks = dayOfWeekMandatoryAssignment.assignDaysOfWeek(numberOfDaysWithMandatoryAct, purpose, person);

            DayOfWeek[] remoteWorkingDays = new DayOfWeek[0];

            if (purpose.equals(Purpose.WORK)) {

                int numberOfRemoteWorkingDays = remoteWorkFrequencyModel.calculateNumberOfActivitiesPerWeek(person, Purpose.WORK);
                remoteWorkingDays = dayOfWeekRemoteWorkAssignment.assignDaysOfWeek(dayOfWeeks, numberOfRemoteWorkingDays, Purpose.WORK, person);


            }

            for (DayOfWeek day : dayOfWeeks) {

                Activity activity = new Activity(person, purpose);
                activity.setDayOfWeek(day);
                timeAssignment.assignDurationAndThenStartTime(activity);
                assignMandatoryActivityLocation(person, purpose, activity, day, remoteWorkingDays);

                int maxTrial = 0;
                while (!plan.getBlockedTimeOfDay().isAvailable(activity.getStartTime_min(), activity.getEndTime_min()) && maxTrial <= TRIALS_RESCHEDULING) {
                    timeAssignment.assignDurationAndThenStartTime(activity);
                    assignMandatoryActivityLocation(person, purpose, activity, day, remoteWorkingDays);
                    maxTrial += 1;
                }

                boolean isRemoteWorkDay = purpose == Purpose.WORK && Arrays.asList(remoteWorkingDays).contains(day);
                if (isRemoteWorkDay) {
                    planTools.addInHomeTour(plan, activity);
                } else {
                    planTools.addMainTour(plan, activity);
                }
                if (activity.getTour() != null && purpose == Purpose.WORK) {
                    plan.addWorkDay(day);
                    if (isRemoteWorkDay) {
                        plan.addRemoteWorkDay(day);
                    }
                }


            }
        }


        List<Activity> stopsOnMandatory = new ArrayList<>();
        List<Activity> accompanyActsOnDiscretionaryTours = new ArrayList<>();
        List<Activity> shoppingActsOnDiscretionaryTours = new ArrayList<>();
        List<Activity> otherActsOnDiscretionaryTours = new ArrayList<>();
        List<Activity> recreationActsOnDiscretionaryTours = new ArrayList<>();


        for (Purpose purpose : Purpose.getDiscretionaryPurposes()) {
            int numAct = frequencyGenerators.get(purpose).calculateNumberOfActivitiesPerWeek(person, purpose);
            for (int i = 0; i < numAct; i++) {
                Activity activity = new Activity(person, purpose);

                splitByType.assignActType(activity, person);


                switch (activity.getDiscretionaryActivityType()) {
                    case ON_MANDATORY_TOUR:
                        stopsOnMandatory.add(activity);
                        break;
                    case ON_DISCRETIONARY_TOUR:
                        if (activity.getPurpose() == Purpose.ACCOMPANY) {
                            accompanyActsOnDiscretionaryTours.add(activity);
                        } else if (activity.getPurpose() == Purpose.SHOPPING) {
                            shoppingActsOnDiscretionaryTours.add(activity);
                        } else if (activity.getPurpose() == Purpose.OTHER) {
                            otherActsOnDiscretionaryTours.add(activity);
                        } else {
                            recreationActsOnDiscretionaryTours.add(activity);
                        }
                        break;
                }
            }

        }

        stopsOnMandatory.forEach(activity -> {
            Tour selectedTour = planTools.findMandatoryTour(plan);
            activity.setDayOfWeek(selectedTour.getMainActivity().getDayOfWeek());

            //the order of time assignment and stopSplitByType is not yet decided
            timeAssignment.assignDurationToStop(activity); //till this step, we should know whether the current trip is before or after mandatory activity
            StopType stopType = stopSplitType.getStopType(person, activity, selectedTour);

            if (stopType != null) {
                if (stopType.equals(StopType.BEFORE)) {
                    destinationChoice.selectStopDestination(person, selectedTour, activity);
                    planTools.addStopBefore(plan, activity, selectedTour);
                } else {
                    destinationChoice.selectStopDestination(person, selectedTour, activity);
                    planTools.addStopAfter(plan, activity, selectedTour);
                }
            }
        });

        for (Activity activity : accompanyActsOnDiscretionaryTours) {
            splitByType.assignActTypeForDiscretionaryTourActs(activity, person, accompanyActsOnDiscretionaryTours.size());

            if (activity.getDiscretionaryActivityType() == DiscretionaryActivityType.ACCOMPANY_PRIMARY) {
                scheduleAsPrimaryTour(plan, person, activity);
            } else {
                scheduleAsStopOnTour(plan, person, activity, Purpose.ACCOMPANY);
            }
        }

        for (Activity activity : shoppingActsOnDiscretionaryTours) {
            splitByType.assignActTypeForDiscretionaryTourActs(activity, person, shoppingActsOnDiscretionaryTours.size());

            if (activity.getDiscretionaryActivityType() == DiscretionaryActivityType.SHOP_PRIMARY) {
                scheduleAsPrimaryTour(plan, person, activity);
            } else if (activity.getDiscretionaryActivityType() == DiscretionaryActivityType.SHOP_ON_ACCOMPANY) {
                scheduleAsStopOnTour(plan, person, activity, Purpose.ACCOMPANY);
            } else {
                scheduleAsStopOnTour(plan, person, activity, Purpose.SHOPPING);
            }
        }

        for (Activity activity : otherActsOnDiscretionaryTours) {
            splitByType.assignActTypeForDiscretionaryTourActs(activity, person, otherActsOnDiscretionaryTours.size());

            if (activity.getDiscretionaryActivityType() == DiscretionaryActivityType.OTHER_PRIMARY) {
                scheduleAsPrimaryTour(plan, person, activity);
            } else if (activity.getDiscretionaryActivityType() == DiscretionaryActivityType.OTHER_ON_ACCOMPANY) {
                scheduleAsStopOnTour(plan, person, activity, Purpose.ACCOMPANY);
            } else if (activity.getDiscretionaryActivityType() == DiscretionaryActivityType.OTHER_ON_SHOP) {
                scheduleAsStopOnTour(plan, person, activity, Purpose.SHOPPING);
            } else if (activity.getDiscretionaryActivityType() == DiscretionaryActivityType.OTHER_ON_OTHER) {
                scheduleAsStopOnTour(plan, person, activity, Purpose.OTHER);
            }
        }

        for (Activity activity : recreationActsOnDiscretionaryTours) {
            splitByType.assignActTypeForDiscretionaryTourActs(activity, person, otherActsOnDiscretionaryTours.size());

            if (activity.getDiscretionaryActivityType() == DiscretionaryActivityType.RECREATION_PRIMARY) {
                scheduleAsPrimaryTour(plan, person, activity);
            } else if (activity.getDiscretionaryActivityType() == DiscretionaryActivityType.RECREATION_ON_ACCOMPANY) {
                scheduleAsStopOnTour(plan, person, activity, Purpose.ACCOMPANY);
            } else if (activity.getDiscretionaryActivityType() == DiscretionaryActivityType.RECREATION_ON_SHOP) {
                scheduleAsStopOnTour(plan, person, activity, Purpose.SHOPPING);
            } else if (activity.getDiscretionaryActivityType() == DiscretionaryActivityType.RECREATION_ON_OTHER) {
                scheduleAsStopOnTour(plan, person, activity, Purpose.OTHER);
            } else if (activity.getDiscretionaryActivityType() == DiscretionaryActivityType.RECREATION_ON_RECREATION) {
                scheduleAsStopOnTour(plan, person, activity, Purpose.RECREATION);
            }
        }
    }

    /**
     * Resolves the destination for a mandatory (WORK/EDUCATION) activity on a given day:
     * the job location, or the household location if this is one of the person's assigned
     * remote-work days, or the school location; falls back to destinationChoice when no
     * fixed location applies. Shared by the initial assignment and the reschedule loop so
     * the two can't drift out of sync with each other.
     */
    private void assignMandatoryActivityLocation(Person person, Purpose purpose, Activity activity, DayOfWeek day, DayOfWeek[] remoteWorkingDays) {
        if (purpose.equals(Purpose.WORK)) {
            if (person.getJob() != null) {
                boolean isRemoteWorkDay = Arrays.asList(remoteWorkingDays).contains(day);
                activity.setLocation(isRemoteWorkDay ? person.getHousehold().getLocation() : person.getJob().getLocation());
            } else {
                destinationChoice.selectMainActivityDestination(person, activity);
            }
        } else {
            if (person.getSchool() != null) {
                activity.setLocation(person.getSchool().getLocation());
            } else {
                destinationChoice.selectMainActivityDestination(person, activity);
            }
        }
    }

    /**
     * Schedules a discretionary activity as its own main tour: day-of-week, duration/start
     * time, and destination, with the placeholder reschedule retry loop kept as-is. If the
     * resulting timing overlaps an existing in-home WORK activity, that WORK activity is split
     * around this one instead of scheduling an independent tour - see
     * findOverlappingInHomeWorkActivity and PlanTools.splitInHomeWorkAroundActivity.
     */
    private void scheduleAsPrimaryTour(Plan plan, Person person, Activity activity) {
        dayOfWeekDiscretionaryAssignment.assignDayOfWeek(activity);
        timeAssignment.assignDurationAndThenStartTime(activity);
        destinationChoice.selectMainActivityDestination(person, activity);

        int maxTrial = 0;
        while (!plan.getBlockedTimeOfDay().isAvailable(activity.getStartTime_min(), activity.getEndTime_min()) && maxTrial <= TRIALS_RESCHEDULING) {
            timeAssignment.assignDurationAndThenStartTime(activity);
            destinationChoice.selectMainActivityDestination(person, activity);
            maxTrial += 1;
        }

        Activity overlappingWork = findOverlappingInHomeWorkActivity(plan, activity);
        if (overlappingWork != null) {
            planTools.splitInHomeWorkAroundActivity(plan, overlappingWork, activity);
        } else {
            planTools.addMainTour(plan, activity);
        }
    }

    /**
     * Finds a Purpose.WORK, isAtHome() activity overlapping the given activity's time - either an
     * untouched leg-less tour's main activity, or an already-split tour's bookend (a second
     * interruption the same day targets whichever WORK/isAtHome() activity it overlaps).
     */
    private Activity findOverlappingInHomeWorkActivity(Plan plan, Activity activity) {
        Stream<Activity> legLessTourMains = plan.getTours().values().stream()
                .filter(tour -> tour.getLegs().isEmpty())
                .map(Tour::getMainActivity);
        Stream<Activity> splitTourBookends = plan.getTours().values().stream()
                .filter(tour -> !tour.getLegs().isEmpty())
                .flatMap(tour -> tour.getLegs().values().stream())
                .flatMap(leg -> Stream.of(leg.getPreviousActivity(), leg.getNextActivity()))
                .distinct();

        return Stream.concat(legLessTourMains, splitTourBookends)
                .filter(a -> a.getPurpose() == Purpose.WORK && a.isAtHome())
                .filter(a -> a.getDayOfWeek() == activity.getDayOfWeek())
                .filter(a -> a.getStartTime_min() < activity.getEndTime_min()
                        && activity.getStartTime_min() < a.getEndTime_min())
                .findFirst()
                .orElse(null);
    }

    /**
     * Schedules a discretionary activity as a stop before/after the person's existing tour
     * for the given purpose. Logs and skips the activity if no such tour exists.
     */
    private void scheduleAsStopOnTour(Plan plan, Person person, Activity activity, Purpose tourPurposeToStackOn) {
        Tour selectedTour = planTools.findDiscretionaryTourByPurpose(plan, tourPurposeToStackOn);
        if (selectedTour == null) {
            logger.warn("No " + tourPurposeToStackOn + " tour found to attach a " + activity.getPurpose() + " stop for person " + person.getId() + " - activity dropped.");
            return;
        }

        activity.setDayOfWeek(selectedTour.getMainActivity().getDayOfWeek());
        //the order of time assignment and stopSplitByType is not yet decided
        timeAssignment.assignDurationToStop(activity);
        StopType stopType = stopSplitType.getStopType(person, activity, selectedTour);
        if (stopType != null) {
            destinationChoice.selectStopDestination(person, selectedTour, activity);
            if (stopType.equals(StopType.BEFORE)) {
                planTools.addStopBefore(plan, activity, selectedTour);
            } else {
                planTools.addStopAfter(plan, activity, selectedTour);
            }
        } else {
            logger.warn("Stops without a valid type: " + stopWithoutTypecounter.incrementAndGet());
        }
    }

    @Override
    public Object call() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);
        for (Household household : this.households) {
            createPlanForOneHousehold(household);
            counter.incrementAndGet();
            final int i = counter.get();
            if ((i % 1000) == 0) {
                logger.info("Completed " + i + " households by thread " + thread);
            }

        }
        return null;
    }
}
