package abm.calibration;

import abm.data.DataSet;
import abm.data.geo.Location;
import abm.data.plans.*;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.data.vehicle.Car;
import abm.data.vehicle.Vehicle;
import abm.models.modeChoice.NestedLogitHabitualModeChoiceModel;
import abm.models.modeChoice.NestedLogitTourModeChoiceModel;
import abm.properties.AbitResources;
import de.tum.bgu.msm.data.person.Occupation;
import org.apache.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

public class TourModeChoiceCalibration implements ModelComponent {
    //Todo define a few calibration parameters
    static Logger logger = Logger.getLogger(TourModeChoiceCalibration.class);

    private static final int MAX_ITERATION = 2_000;
    private static final double TERMINATION_THRESHOLD = 0.05;

    double stepSize = 5;
    String inputFolder = AbitResources.instance.getString("tour.mode.coef.output");
    DataSet dataSet;
    Map<Purpose, Map<DayOfWeek, Map<Mode, Double>>> objectiveTourModeShare = new HashMap<>();
    Map<Purpose, Map<DayOfWeek, Map<Mode, Integer>>> simulatedTourModeCount = new HashMap<>();
    Map<Purpose, Map<DayOfWeek, Map<Mode, Double>>> simulatedTourModeShare = new HashMap<>();
    Map<Purpose, Map<DayOfWeek, Map<Mode, Double>>> calibrationFactors = new HashMap<>();
    private NestedLogitTourModeChoiceModel tourModeChoiceModelCalibration;

    public TourModeChoiceCalibration(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    @Override
    public void setup() {
        //Todo: read boolean input from the property file and create the model which needs to be calibrated
        boolean calibrateTourModeChoice = Boolean.parseBoolean(AbitResources.instance.getString("tour.mode.calibration"));
        tourModeChoiceModelCalibration = new NestedLogitTourModeChoiceModel(dataSet, calibrateTourModeChoice);

        //Todo: initialize all the data containers that might be needed for calibration
        //tourmodechoice
        for (Purpose purpose : Purpose.getSortedPurposes()) {
            objectiveTourModeShare.putIfAbsent(purpose, new HashMap<>());
            simulatedTourModeCount.putIfAbsent(purpose, new HashMap<>());
            simulatedTourModeShare.putIfAbsent(purpose, new HashMap<>());
            calibrationFactors.putIfAbsent(purpose, new HashMap<>());
            for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
                objectiveTourModeShare.get(purpose).putIfAbsent(dayOfWeek, new HashMap<>());
                simulatedTourModeCount.get(purpose).putIfAbsent(dayOfWeek, new HashMap<>());
                simulatedTourModeShare.get(purpose).putIfAbsent(dayOfWeek, new HashMap<>());
                calibrationFactors.get(purpose).putIfAbsent(dayOfWeek, new HashMap<>());
                for (Mode mode : Mode.values()) {
                    objectiveTourModeShare.get(purpose).get(dayOfWeek).putIfAbsent(mode, 0.0);
                    simulatedTourModeCount.get(purpose).get(dayOfWeek).putIfAbsent(mode, 0);
                    simulatedTourModeShare.get(purpose).get(dayOfWeek).putIfAbsent(mode, 0.0);
                    calibrationFactors.get(purpose).get(dayOfWeek).putIfAbsent(mode, 0.0);
                }
            }
        }
    }

    @Override
    public void load() {
        //Todo: read objective values
        readObjectiveValues();

        //Todo: consider having the result summarization in the statistics writer
        summarizeSimulatedResult();
    }

    @Override
    public void run() {

        logger.info("Start calibrating the habitual mode choice model......");
        List<Household> simulatedHouseholds = dataSet.getHouseholds().values().parallelStream().filter(Household::getSimulated).collect(Collectors.toList());

        //Todo: loop through the calibration process until criteria are met
        for (int iteration = 0; iteration < MAX_ITERATION; iteration++) {
            logger.info("Iteration......" + iteration);
            double maxDifference = 0.0;

            //Todo summarize share difference and share
            for (Purpose purpose : Purpose.getAllPurposes()) {
                for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
                    for (Mode mode : Mode.getModes()) {
                        double observedShare = objectiveTourModeShare.get(purpose).get(dayOfWeek).get(mode);
                        double simulatedShare = simulatedTourModeShare.get(purpose).get(dayOfWeek).get(mode);
                        double difference = observedShare - simulatedShare;
                        double factor = stepSize * (observedShare - simulatedShare);
                        if (mode.equals(Mode.CAR_DRIVER)) {
                            factor = 0.00;
                        }
                        if (dayOfWeek.equals(DayOfWeek.SUNDAY) && purpose.equals(Purpose.SHOPPING)) {
                            factor = 0.00;
                        }
                        calibrationFactors.get(purpose).get(dayOfWeek).replace(mode, factor);
                        logger.info("Tour mode choice model for " + purpose.toString() + "\t" + dayOfWeek.toString() + "\t" + " and " + mode.toString() + "\t" + "difference: " + difference);
                        if (Math.abs(difference) > maxDifference) {
                            maxDifference = Math.abs(difference);
                        }
                    }
                }
            }

            tourModeChoiceModelCalibration.updateCalibrationFactor(calibrationFactors);

            if (maxDifference <= TERMINATION_THRESHOLD) {
                break;
            } else {
                logger.info("MAX Diff: " + maxDifference);
            }

            simulatedHouseholds.parallelStream().forEach(household -> {
                household.getVehicles().parallelStream().forEach(vehicle -> {
                    ((Car) vehicle).getBlockedTimeOfWeek().resetCarBlockedTimeOfWeekLinkedList();
                });
            });

            for (Household household : simulatedHouseholds){
                if (household.getNumberOfCars() > 0){
                    for (Purpose purpose : Purpose.getSortedPurposes()) {
                        if (purpose.equals(Purpose.WORK)) {
                            //Step 1: loop over all workers in the household, check car and transit travel time ratio
                            // car/pt ratio the smaller (more poor pt accessibility compared to car), then higher preference to use car
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
                                    Activity workActivity = person.getPlan().getTours().values().stream().filter(tour -> tour.getMainActivity().getPurpose().equals(Purpose.WORK)).collect(Collectors.toList()).get(0).getMainActivity();
                                    jobLocation = workActivity.getLocation();
                                    startTime = workActivity.getStartTime_min();
                                }

                                int carTravelTime = dataSet.getTravelTimes().getTravelTimeInMinutes(person.getHousehold().getLocation(), jobLocation, Mode.CAR_DRIVER, startTime);
                                int transitTravelTime = dataSet.getTravelTimes().getTravelTimeInMinutes(person.getHousehold().getLocation(), jobLocation, Mode.TRAIN, startTime);
                                double carPtRatio = carTravelTime / (double) transitTravelTime;
                                carUsePreference.put(person, carPtRatio);
                            }

                            List<Map.Entry<Person, Double>> sortedPreference = new ArrayList<>(carUsePreference.entrySet());
                            Collections.sort(sortedPreference, Map.Entry.comparingByValue());

                            //Step 2: check availability and choose mode for Work tours by the order of preference
                            for (Map.Entry<Person, Double> entry : sortedPreference) {
                                entry.getKey().getPlan().getTours().values().forEach(tour -> {
                                    if (tour.getMainActivity().getPurpose().equals(Purpose.WORK)) {
                                        tourModeChoiceModelCalibration.checkCarAvailabilityAndChooseMode(household, entry.getKey(), tour, Purpose.WORK);
                                    }
                                });
                            }
                        } else {
                            //check availability and choose mode for other tours by the order of (education > accompany > other > shopping > recreational)
                            for (Person person : household.getPersons()) {
                                person.getPlan().getTours().values().forEach(tour -> {
                                    if (tour.getMainActivity().getPurpose().equals(purpose)) {
                                        tourModeChoiceModelCalibration.checkCarAvailabilityAndChooseMode(household, person, tour, purpose);
                                    }
                                });
                            }
                        }
                    }
                }else{
                    for (Person person : household.getPersons()) {
                        person.getPlan().getTours().values().forEach(tour -> {
                            tourModeChoiceModelCalibration.chooseMode(person, tour, tour.getMainActivity().getPurpose(), Boolean.FALSE);
                        });
                    }
                }
            }

            summarizeSimulatedResult();

        }

        logger.info("Finished the calibration of habitual mode choice model.");

        //Todo: obtain the updated coefficients + calibration factors
        Map<Purpose, Map<Mode, Map<String, Double>>> finalCoefficientsTable = tourModeChoiceModelCalibration.obtainCoefficientsTable();

        //Todo: print the coefficients table to input folder
        try {
            printFinalCoefficientsTable(finalCoefficientsTable);
        } catch (FileNotFoundException e) {
            System.err.println("Output path of the coefficient table is not correct.");
        }

    }

    //WORK; EDUCATION; ACCOMPANY; OTHER; SHOP; RECR
    private void readObjectiveValues() {
        //WORK
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.MONDAY).put(Mode.CAR_DRIVER, 0.63);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.MONDAY).put(Mode.CAR_PASSENGER, 0.03);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.MONDAY).put(Mode.BUS, 0.03);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.MONDAY).put(Mode.TRAIN, 0.09);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.MONDAY).put(Mode.TRAM_METRO, 0.05);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.MONDAY).put(Mode.BIKE, 0.12);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.MONDAY).put(Mode.WALK, 0.05);


        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.TUESDAY).put(Mode.CAR_DRIVER, 0.63);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.TUESDAY).put(Mode.CAR_PASSENGER, 0.03);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.TUESDAY).put(Mode.BUS, 0.03);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.TUESDAY).put(Mode.TRAIN, 0.09);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.TUESDAY).put(Mode.TRAM_METRO, 0.05);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.TUESDAY).put(Mode.BIKE, 0.12);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.TUESDAY).put(Mode.WALK, 0.05);

        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.WEDNESDAY).put(Mode.CAR_DRIVER, 0.63);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.WEDNESDAY).put(Mode.CAR_PASSENGER, 0.03);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.WEDNESDAY).put(Mode.BUS, 0.03);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.WEDNESDAY).put(Mode.TRAIN, 0.09);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.WEDNESDAY).put(Mode.TRAM_METRO, 0.05);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.WEDNESDAY).put(Mode.BIKE, 0.12);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.WEDNESDAY).put(Mode.WALK, 0.05);

        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.THURSDAY).put(Mode.CAR_DRIVER, 0.63);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.THURSDAY).put(Mode.CAR_PASSENGER, 0.03);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.THURSDAY).put(Mode.BUS, 0.03);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.THURSDAY).put(Mode.TRAIN, 0.09);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.THURSDAY).put(Mode.TRAM_METRO, 0.05);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.THURSDAY).put(Mode.BIKE, 0.12);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.THURSDAY).put(Mode.WALK, 0.05);

        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.FRIDAY).put(Mode.CAR_DRIVER, 0.63);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.FRIDAY).put(Mode.CAR_PASSENGER, 0.03);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.FRIDAY).put(Mode.BUS, 0.03);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.FRIDAY).put(Mode.TRAIN, 0.09);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.FRIDAY).put(Mode.TRAM_METRO, 0.05);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.FRIDAY).put(Mode.BIKE, 0.12);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.FRIDAY).put(Mode.WALK, 0.05);


        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SATURDAY).put(Mode.CAR_DRIVER, 0.70);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SATURDAY).put(Mode.CAR_PASSENGER, 0.05);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SATURDAY).put(Mode.BUS, 0.02);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SATURDAY).put(Mode.TRAIN, 0.04);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SATURDAY).put(Mode.TRAM_METRO, 0.04);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SATURDAY).put(Mode.BIKE, 0.10);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SATURDAY).put(Mode.WALK, 0.05);

        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SUNDAY).put(Mode.CAR_DRIVER, 0.72);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SUNDAY).put(Mode.CAR_PASSENGER, 0.05);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SUNDAY).put(Mode.BUS, 0.01);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SUNDAY).put(Mode.TRAIN, 0.05);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SUNDAY).put(Mode.TRAM_METRO, 0.05);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SUNDAY).put(Mode.BIKE, 0.07);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SUNDAY).put(Mode.WALK, 0.05);

        //EDUCATION
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.MONDAY).put(Mode.CAR_DRIVER, 0.12);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.MONDAY).put(Mode.CAR_PASSENGER, 0.09);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.MONDAY).put(Mode.BUS, 0.31);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.MONDAY).put(Mode.TRAIN, 0.12);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.MONDAY).put(Mode.TRAM_METRO, 0.07);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.MONDAY).put(Mode.BIKE, 0.19);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.MONDAY).put(Mode.WALK, 0.10);

        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.TUESDAY).put(Mode.CAR_DRIVER, 0.12);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.TUESDAY).put(Mode.CAR_PASSENGER, 0.09);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.TUESDAY).put(Mode.BUS, 0.31);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.TUESDAY).put(Mode.TRAIN, 0.12);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.TUESDAY).put(Mode.TRAM_METRO, 0.07);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.TUESDAY).put(Mode.BIKE, 0.19);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.TUESDAY).put(Mode.WALK, 0.10);

        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.WEDNESDAY).put(Mode.CAR_DRIVER, 0.12);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.WEDNESDAY).put(Mode.CAR_PASSENGER, 0.09);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.WEDNESDAY).put(Mode.BUS, 0.31);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.WEDNESDAY).put(Mode.TRAIN, 0.12);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.WEDNESDAY).put(Mode.TRAM_METRO, 0.07);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.WEDNESDAY).put(Mode.BIKE, 0.19);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.WEDNESDAY).put(Mode.WALK, 0.10);

        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.THURSDAY).put(Mode.CAR_DRIVER, 0.12);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.THURSDAY).put(Mode.CAR_PASSENGER, 0.09);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.THURSDAY).put(Mode.BUS, 0.31);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.THURSDAY).put(Mode.TRAIN, 0.12);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.THURSDAY).put(Mode.TRAM_METRO, 0.07);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.THURSDAY).put(Mode.BIKE, 0.19);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.THURSDAY).put(Mode.WALK, 0.10);

        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.FRIDAY).put(Mode.CAR_DRIVER, 0.12);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.FRIDAY).put(Mode.CAR_PASSENGER, 0.09);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.FRIDAY).put(Mode.BUS, 0.31);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.FRIDAY).put(Mode.TRAIN, 0.12);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.FRIDAY).put(Mode.TRAM_METRO, 0.07);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.FRIDAY).put(Mode.BIKE, 0.19);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.FRIDAY).put(Mode.WALK, 0.10);


        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SATURDAY).put(Mode.CAR_DRIVER, 0.47);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SATURDAY).put(Mode.CAR_PASSENGER, 0.23);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SATURDAY).put(Mode.BUS, 0.05);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SATURDAY).put(Mode.TRAIN, 0.06);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SATURDAY).put(Mode.TRAM_METRO, 0.02);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SATURDAY).put(Mode.BIKE, 0.10);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SATURDAY).put(Mode.WALK, 0.08);

        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SUNDAY).put(Mode.CAR_DRIVER, 0.46);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SUNDAY).put(Mode.CAR_PASSENGER, 0.19);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SUNDAY).put(Mode.BUS, 0.04);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SUNDAY).put(Mode.TRAIN, 0.23);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SUNDAY).put(Mode.TRAM_METRO, 0.04);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SUNDAY).put(Mode.BIKE, 0.04);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SUNDAY).put(Mode.WALK, 0.00);

        //ACCOMPANY
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.MONDAY).put(Mode.CAR_DRIVER, 0.74);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.MONDAY).put(Mode.CAR_PASSENGER, 0.05);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.MONDAY).put(Mode.BUS, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.MONDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.MONDAY).put(Mode.TRAM_METRO, 0.02);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.MONDAY).put(Mode.BIKE, 0.05);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.MONDAY).put(Mode.WALK, 0.13);

        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.TUESDAY).put(Mode.CAR_DRIVER, 0.74);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.TUESDAY).put(Mode.CAR_PASSENGER, 0.05);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.TUESDAY).put(Mode.BUS, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.TUESDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.TUESDAY).put(Mode.TRAM_METRO, 0.02);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.TUESDAY).put(Mode.BIKE, 0.05);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.TUESDAY).put(Mode.WALK, 0.13);

        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.WEDNESDAY).put(Mode.CAR_DRIVER, 0.74);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.WEDNESDAY).put(Mode.CAR_PASSENGER, 0.05);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.WEDNESDAY).put(Mode.BUS, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.WEDNESDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.WEDNESDAY).put(Mode.TRAM_METRO, 0.02);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.WEDNESDAY).put(Mode.BIKE, 0.05);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.WEDNESDAY).put(Mode.WALK, 0.13);

        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.THURSDAY).put(Mode.CAR_DRIVER, 0.74);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.THURSDAY).put(Mode.CAR_PASSENGER, 0.05);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.THURSDAY).put(Mode.BUS, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.THURSDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.THURSDAY).put(Mode.TRAM_METRO, 0.02);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.THURSDAY).put(Mode.BIKE, 0.05);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.THURSDAY).put(Mode.WALK, 0.13);

        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.FRIDAY).put(Mode.CAR_DRIVER, 0.74);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.FRIDAY).put(Mode.CAR_PASSENGER, 0.05);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.FRIDAY).put(Mode.BUS, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.FRIDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.FRIDAY).put(Mode.TRAM_METRO, 0.02);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.FRIDAY).put(Mode.BIKE, 0.05);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.FRIDAY).put(Mode.WALK, 0.13);


        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SATURDAY).put(Mode.CAR_DRIVER, 0.81);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SATURDAY).put(Mode.CAR_PASSENGER, 0.12);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SATURDAY).put(Mode.BUS, 0.00);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SATURDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SATURDAY).put(Mode.TRAM_METRO, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SATURDAY).put(Mode.BIKE, 0.02);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SATURDAY).put(Mode.WALK, 0.04);

        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SUNDAY).put(Mode.CAR_DRIVER, 0.75);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SUNDAY).put(Mode.CAR_PASSENGER, 0.16);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SUNDAY).put(Mode.BUS, 0.00);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SUNDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SUNDAY).put(Mode.TRAM_METRO, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SUNDAY).put(Mode.BIKE, 0.02);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SUNDAY).put(Mode.WALK, 0.05);

        //OTHER
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.MONDAY).put(Mode.CAR_DRIVER, 0.48);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.MONDAY).put(Mode.CAR_PASSENGER, 0.13);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.MONDAY).put(Mode.BUS, 0.04);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.MONDAY).put(Mode.TRAIN, 0.02);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.MONDAY).put(Mode.TRAM_METRO, 0.04);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.MONDAY).put(Mode.BIKE, 0.11);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.MONDAY).put(Mode.WALK, 0.18);

        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.TUESDAY).put(Mode.CAR_DRIVER, 0.48);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.TUESDAY).put(Mode.CAR_PASSENGER, 0.13);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.TUESDAY).put(Mode.BUS, 0.04);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.TUESDAY).put(Mode.TRAIN, 0.02);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.TUESDAY).put(Mode.TRAM_METRO, 0.04);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.TUESDAY).put(Mode.BIKE, 0.11);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.TUESDAY).put(Mode.WALK, 0.18);

        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.WEDNESDAY).put(Mode.CAR_DRIVER, 0.48);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.WEDNESDAY).put(Mode.CAR_PASSENGER, 0.13);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.WEDNESDAY).put(Mode.BUS, 0.04);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.WEDNESDAY).put(Mode.TRAIN, 0.02);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.WEDNESDAY).put(Mode.TRAM_METRO, 0.04);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.WEDNESDAY).put(Mode.BIKE, 0.11);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.WEDNESDAY).put(Mode.WALK, 0.18);

        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.THURSDAY).put(Mode.CAR_DRIVER, 0.48);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.THURSDAY).put(Mode.CAR_PASSENGER, 0.13);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.THURSDAY).put(Mode.BUS, 0.04);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.THURSDAY).put(Mode.TRAIN, 0.02);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.THURSDAY).put(Mode.TRAM_METRO, 0.04);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.THURSDAY).put(Mode.BIKE, 0.11);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.THURSDAY).put(Mode.WALK, 0.18);

        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.FRIDAY).put(Mode.CAR_DRIVER, 0.48);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.FRIDAY).put(Mode.CAR_PASSENGER, 0.13);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.FRIDAY).put(Mode.BUS, 0.04);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.FRIDAY).put(Mode.TRAIN, 0.02);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.FRIDAY).put(Mode.TRAM_METRO, 0.04);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.FRIDAY).put(Mode.BIKE, 0.11);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.FRIDAY).put(Mode.WALK, 0.18);


        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SATURDAY).put(Mode.CAR_DRIVER, 0.50);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SATURDAY).put(Mode.CAR_PASSENGER, 0.21);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SATURDAY).put(Mode.BUS, 0.02);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SATURDAY).put(Mode.TRAIN, 0.02);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SATURDAY).put(Mode.TRAM_METRO, 0.03);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SATURDAY).put(Mode.BIKE, 0.08);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SATURDAY).put(Mode.WALK, 0.14);

        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SUNDAY).put(Mode.CAR_DRIVER, 0.45);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SUNDAY).put(Mode.CAR_PASSENGER, 0.22);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SUNDAY).put(Mode.BUS, 0.01);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SUNDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SUNDAY).put(Mode.TRAM_METRO, 0.02);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SUNDAY).put(Mode.BIKE, 0.07);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SUNDAY).put(Mode.WALK, 0.21);

        //SHOPPING
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.MONDAY).put(Mode.CAR_DRIVER, 0.46);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.MONDAY).put(Mode.CAR_PASSENGER, 0.11);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.MONDAY).put(Mode.BUS, 0.03);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.MONDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.MONDAY).put(Mode.TRAM_METRO, 0.03);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.MONDAY).put(Mode.BIKE, 0.13);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.MONDAY).put(Mode.WALK, 0.23);

        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.TUESDAY).put(Mode.CAR_DRIVER, 0.46);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.TUESDAY).put(Mode.CAR_PASSENGER, 0.11);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.TUESDAY).put(Mode.BUS, 0.03);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.TUESDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.TUESDAY).put(Mode.TRAM_METRO, 0.03);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.TUESDAY).put(Mode.BIKE, 0.13);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.TUESDAY).put(Mode.WALK, 0.23);

        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.WEDNESDAY).put(Mode.CAR_DRIVER, 0.46);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.WEDNESDAY).put(Mode.CAR_PASSENGER, 0.11);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.WEDNESDAY).put(Mode.BUS, 0.03);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.WEDNESDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.WEDNESDAY).put(Mode.TRAM_METRO, 0.03);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.WEDNESDAY).put(Mode.BIKE, 0.13);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.WEDNESDAY).put(Mode.WALK, 0.23);

        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.THURSDAY).put(Mode.CAR_DRIVER, 0.46);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.THURSDAY).put(Mode.CAR_PASSENGER, 0.11);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.THURSDAY).put(Mode.BUS, 0.03);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.THURSDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.THURSDAY).put(Mode.TRAM_METRO, 0.03);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.THURSDAY).put(Mode.BIKE, 0.13);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.THURSDAY).put(Mode.WALK, 0.23);

        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.FRIDAY).put(Mode.CAR_DRIVER, 0.46);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.FRIDAY).put(Mode.CAR_PASSENGER, 0.11);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.FRIDAY).put(Mode.BUS, 0.03);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.FRIDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.FRIDAY).put(Mode.TRAM_METRO, 0.03);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.FRIDAY).put(Mode.BIKE, 0.13);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.FRIDAY).put(Mode.WALK, 0.23);


        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SATURDAY).put(Mode.CAR_DRIVER, 0.47);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SATURDAY).put(Mode.CAR_PASSENGER, 0.14);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SATURDAY).put(Mode.BUS, 0.03);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SATURDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SATURDAY).put(Mode.TRAM_METRO, 0.02);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SATURDAY).put(Mode.BIKE, 0.13);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SATURDAY).put(Mode.WALK, 0.21);

        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SUNDAY).put(Mode.CAR_DRIVER, 0.44);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SUNDAY).put(Mode.CAR_PASSENGER, 0.10);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SUNDAY).put(Mode.BUS, 0.01);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SUNDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SUNDAY).put(Mode.TRAM_METRO, 0.01);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SUNDAY).put(Mode.BIKE, 0.17);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SUNDAY).put(Mode.WALK, 0.27);

        //RECREATION
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.MONDAY).put(Mode.CAR_DRIVER, 0.39);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.MONDAY).put(Mode.CAR_PASSENGER, 0.17);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.MONDAY).put(Mode.BUS, 0.04);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.MONDAY).put(Mode.TRAIN, 0.02);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.MONDAY).put(Mode.TRAM_METRO, 0.03);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.MONDAY).put(Mode.BIKE, 0.15);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.MONDAY).put(Mode.WALK, 0.20);

        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.TUESDAY).put(Mode.CAR_DRIVER, 0.39);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.TUESDAY).put(Mode.CAR_PASSENGER, 0.17);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.TUESDAY).put(Mode.BUS, 0.04);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.TUESDAY).put(Mode.TRAIN, 0.02);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.TUESDAY).put(Mode.TRAM_METRO, 0.03);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.TUESDAY).put(Mode.BIKE, 0.15);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.TUESDAY).put(Mode.WALK, 0.20);

        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.WEDNESDAY).put(Mode.CAR_DRIVER, 0.39);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.WEDNESDAY).put(Mode.CAR_PASSENGER, 0.17);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.WEDNESDAY).put(Mode.BUS, 0.04);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.WEDNESDAY).put(Mode.TRAIN, 0.02);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.WEDNESDAY).put(Mode.TRAM_METRO, 0.03);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.WEDNESDAY).put(Mode.BIKE, 0.15);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.WEDNESDAY).put(Mode.WALK, 0.20);

        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.THURSDAY).put(Mode.CAR_DRIVER, 0.39);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.THURSDAY).put(Mode.CAR_PASSENGER, 0.17);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.THURSDAY).put(Mode.BUS, 0.04);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.THURSDAY).put(Mode.TRAIN, 0.02);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.THURSDAY).put(Mode.TRAM_METRO, 0.03);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.THURSDAY).put(Mode.BIKE, 0.15);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.THURSDAY).put(Mode.WALK, 0.20);

        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.FRIDAY).put(Mode.CAR_DRIVER, 0.39);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.FRIDAY).put(Mode.CAR_PASSENGER, 0.17);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.FRIDAY).put(Mode.BUS, 0.04);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.FRIDAY).put(Mode.TRAIN, 0.02);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.FRIDAY).put(Mode.TRAM_METRO, 0.03);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.FRIDAY).put(Mode.BIKE, 0.15);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.FRIDAY).put(Mode.WALK, 0.20);


        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SATURDAY).put(Mode.CAR_DRIVER, 0.35);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SATURDAY).put(Mode.CAR_PASSENGER, 0.25);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SATURDAY).put(Mode.BUS, 0.03);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SATURDAY).put(Mode.TRAIN, 0.05);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SATURDAY).put(Mode.TRAM_METRO, 0.04);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SATURDAY).put(Mode.BIKE, 0.11);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SATURDAY).put(Mode.WALK, 0.17);

        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SUNDAY).put(Mode.CAR_DRIVER, 0.37);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SUNDAY).put(Mode.CAR_PASSENGER, 0.24);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SUNDAY).put(Mode.BUS, 0.02);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SUNDAY).put(Mode.TRAIN, 0.03);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SUNDAY).put(Mode.TRAM_METRO, 0.04);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SUNDAY).put(Mode.BIKE, 0.11);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SUNDAY).put(Mode.WALK, 0.20);

    }

    private void summarizeSimulatedResult() {

        for (Purpose purpose : Purpose.getSortedPurposes()) {
            for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
                for (Mode mode : Mode.values()) {
                    simulatedTourModeCount.get(purpose).get(dayOfWeek).putIfAbsent(mode, 0);
                    simulatedTourModeShare.get(purpose).get(dayOfWeek).putIfAbsent(mode, 0.0);
                }
            }
        }

        for (Household household : dataSet.getHouseholds().values()) {
            if (household.getSimulated()) {
                for (Person person : household.getPersons()) {
                    Plan plan = person.getPlan();
                    for (Tour tour : plan.getTours().values()) {
                        Purpose purpose = tour.getMainActivity().getPurpose();
                        DayOfWeek dayOfWeek = tour.getMainActivity().getDayOfWeek();
                        Mode mode = tour.getTourMode();
                        int count = simulatedTourModeCount.get(purpose).get(dayOfWeek).get(mode);
                        count = count + 1;
                        simulatedTourModeCount.get(purpose).get(dayOfWeek).replace(mode, count);

                    }

                }
            }
        }
        for (Purpose purpose : Purpose.getSortedPurposes()) {
            for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
                int cumulativeSum = 0;
                for (Mode mode : Mode.getModes()) {
                    cumulativeSum = cumulativeSum + simulatedTourModeCount.get(purpose).get(dayOfWeek).get(mode);
                }
                for (Mode mode : Mode.getModes()) {
                    double share = (double) simulatedTourModeCount.get(purpose).get(dayOfWeek).get(mode) / cumulativeSum;
                    simulatedTourModeShare.get(purpose).get(dayOfWeek).replace(mode, share);
                }
            }
        }
    }

    private void printFinalCoefficientsTable(Map<Purpose, Map<Mode, Map<String, Double>>> finalCoefficientsTable) throws FileNotFoundException {

//        accompanyMode_nestedLogit.csv
//        shoppingMode_nestedLogit.csv
//        recreationMode_nestedLogit.csv
//        otherMode_nestedLogit.csv

        logger.info("Writing tour mode choice coefficient + calibration factors: " + inputFolder + "mandatoryMode_nestedLogit_calibrated.csv");
        PrintWriter pw = new PrintWriter(inputFolder + "mandatoryMode_nestedLogit_calibrated.csv");

        StringBuilder header = new StringBuilder("variable");
        for (Mode mode : Mode.getModes()) {
            header.append(",");
            header.append(mode);
        }
        pw.println(header);

        for (String variableNames : finalCoefficientsTable.get(Purpose.WORK).get(Mode.BUS).keySet()) {
            StringBuilder line = new StringBuilder(variableNames);

            if (variableNames.equals("calibration_education_monday") || variableNames.equals("calibration_education_tuesday") ||
                    variableNames.equals("calibration_education_wednesday") || variableNames.equals("calibration_education_thursday") ||
                    variableNames.equals("calibration_education_friday") || variableNames.equals("calibration_education_satday") ||
                    variableNames.equals("calibration_education_sunday")) {


                for (Mode mode : Mode.getModes()) {
                    line.append(",");
                    line.append(finalCoefficientsTable.get(Purpose.EDUCATION).get(mode).get(variableNames));
                }

            } else {
                for (Mode mode : Mode.getModes()) {
                    line.append(",");
                    line.append(finalCoefficientsTable.get(Purpose.WORK).get(mode).get(variableNames));
                }
            }
            pw.println(line);
        }
        pw.close();

        //one for discretionary


        for (Purpose purpose : Purpose.getDiscretionaryPurposes()) {

            logger.info("Writing tour mode choice coefficient + calibration factors: " + inputFolder + purpose + "Mode_nestedLogit_calibrated.csv");
            PrintWriter pww = new PrintWriter(inputFolder + purpose.toString().toLowerCase() + "Mode_nestedLogit_calibrated.csv");

            StringBuilder headerr = new StringBuilder("variable");
            for (Mode mode : Mode.getModes()) {
                headerr.append(",");
                headerr.append(mode);
            }
            pww.println(headerr);


            for (String variableNames : finalCoefficientsTable.get(Purpose.SHOPPING).get(Mode.BUS).keySet()) {
                StringBuilder line = new StringBuilder(variableNames);

                for (Mode mode : Mode.getModes()) {
                    line.append(",");
                    line.append(finalCoefficientsTable.get(purpose).get(mode).get(variableNames));
                }

                pww.println(line);
            }
            pww.close();

        }

    }
}
