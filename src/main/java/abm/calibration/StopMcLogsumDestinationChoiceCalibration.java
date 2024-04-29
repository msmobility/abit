package abm.calibration;

import abm.data.DataSet;
import abm.data.plans.Activity;
import abm.data.plans.Mode;
import abm.data.plans.Purpose;
import abm.data.plans.Tour;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.data.vehicle.Car;
import abm.data.vehicle.CarType;
import abm.data.vehicle.Vehicle;
import abm.properties.AbitResources;
import abm.scenarios.lowEmissionZones.model.McLogsumBasedDestinationChoiceModel;
import org.apache.log4j.Logger;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StopMcLogsumDestinationChoiceCalibration implements ModelComponent {
    //Todo define a few calibration parameters
    static Logger logger = Logger.getLogger(StopMcLogsumDestinationChoiceCalibration.class);
    private static final int MAX_ITERATION = 100; //2_000_000;
    private static final double TERMINATION_THRESHOLD_AVERAGE_DISTANCE = 1.00;
    String inputFolder = AbitResources.instance.getString("destination.choice.stop.act.logsum.output");
    DataSet dataSet;
    Map<Purpose, Map<String, Double>> objectiveStopDestinationAverageDistance_km = new HashMap<>();
    Map<Purpose, Map<String, Double>> simulatedStopDestinationAverageDistance_km = new HashMap<>();
    Map<Purpose, Map<String, Integer>> numberOfAct = new HashMap<>();
    Map<Purpose, Map<String, Double>> calibrationFactors = new HashMap<>();
    private McLogsumBasedDestinationChoiceModel destinationChoiceModel;
    private final String[] roles = {"evOwner", "nonEvOwner"};
    public StopMcLogsumDestinationChoiceCalibration(DataSet dataSet) {
        this.dataSet = dataSet;
    }
    @Override
    public void setup() {
        //Todo: read boolean input from the property file and create the model which needs to be calibrated
        boolean calibrateMainDestinationChoice = Boolean.parseBoolean(AbitResources.instance.getString("act.main.mcLogsum.destination.calibration"));
        boolean calibrateStopDestinationChoice = Boolean.parseBoolean(AbitResources.instance.getString("act.stop.mcLogsum.destination.calibration"));
        destinationChoiceModel = new McLogsumBasedDestinationChoiceModel(dataSet, calibrateMainDestinationChoice, calibrateStopDestinationChoice);
        //Todo: initialize all the data containers that might be needed for calibration
        for (Purpose purpose : Purpose.getAllPurposes()) {
            calibrationFactors.putIfAbsent(purpose, new HashMap<>());
            objectiveStopDestinationAverageDistance_km.put(purpose, new HashMap<>());
            simulatedStopDestinationAverageDistance_km.put(purpose,new HashMap<>());
            numberOfAct.put(purpose, new HashMap<>());
            for (String role : roles) {
                calibrationFactors.get(purpose).put("ALPHA_calibration_" + role, 0.);
                calibrationFactors.get(purpose).put("BETA_calibration_" + role, 0.);
                objectiveStopDestinationAverageDistance_km.get(purpose).put(role, 0.);
                simulatedStopDestinationAverageDistance_km.get(purpose).put(role, 0.);
                numberOfAct.get(purpose).put(role, 0);
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
        logger.info("Start calibrating the logsum stop destination choice model......");
        //Todo: loop through the calibration process until criteria are met
        for (int iteration = 0; iteration < MAX_ITERATION; iteration++) {
            double maxDifference = 0.0;
            for (Purpose purpose : Purpose.getAllPurposes()) {
                for (String role : roles) {
                    double differenceAverageDistance;
                    double factor = (objectiveStopDestinationAverageDistance_km.get(purpose).get(role) / simulatedStopDestinationAverageDistance_km.get(purpose).get(role));
                    factor = Math.max(factor, 0.5);
                    factor = Math.min(factor, 2);
                    differenceAverageDistance = Math.abs(objectiveStopDestinationAverageDistance_km.get(purpose).get(role) - simulatedStopDestinationAverageDistance_km.get(purpose).get(role));

                    calibrationFactors.get(purpose).replace("BETA_calibration_" + role, factor);
                    logger.info("Stop destination choice for" + purpose + "\t" + role + "\t" + "average distance: " + simulatedStopDestinationAverageDistance_km.get(purpose).get(role));
                    if (differenceAverageDistance > maxDifference) {
                        maxDifference = differenceAverageDistance;
                    }
                }
            }
            if (maxDifference <= TERMINATION_THRESHOLD_AVERAGE_DISTANCE) {
                break;
            }

            destinationChoiceModel.updateCalibrationFactorsStop(calibrationFactors);
            destinationChoiceModel.updateStopDestinationProbability();

            List<Household> simulatedHouseholds = dataSet.getHouseholds().values().parallelStream().filter(Household::getSimulated).collect(Collectors.toList());
            simulatedHouseholds.parallelStream().forEach(household -> household.getPersons().forEach(person -> {
                for (Tour tour : person.getPlan().getTours().values()) {
                    for (Activity activity : tour.getActivities().values()) {
                        if (!activity.equals(tour.getMainActivity())) {
                            destinationChoiceModel.selectStopDestination(person, tour, activity);
                            break;
                        }
                    }
                }
            }));
            summarizeSimulatedResult();
        }
        logger.info("Finished the calibration of stop destination choice.");
        //Todo: obtain the updated coefficients + calibration factors
        Map<Purpose, Map<String, Double>> finalCoefficientsTableStop = destinationChoiceModel.obtainCoefficientsTableStop();
        //Todo: print the coefficients table to input folder
        try {
            printFinalCoefficientsTable(finalCoefficientsTableStop);
        } catch (FileNotFoundException e) {
            System.err.println("Output path of the coefficient table is not correct.");
        }

    }

    private void readObjectiveValues() {
        //todo add objective average distance
        for (String role:roles){
            objectiveStopDestinationAverageDistance_km.get(Purpose.WORK).put(role ,4.0565034873952674);
            objectiveStopDestinationAverageDistance_km.get(Purpose.EDUCATION).put(role ,3.6220093126038324);
            objectiveStopDestinationAverageDistance_km.get(Purpose.ACCOMPANY).put(role ,3.6220093126038324);
            objectiveStopDestinationAverageDistance_km.get(Purpose.SHOPPING).put(role ,3.6220093126038324);
            objectiveStopDestinationAverageDistance_km.get(Purpose.OTHER).put(role ,3.6220093126038324);
            objectiveStopDestinationAverageDistance_km.get(Purpose.RECREATION).put(role ,3.6220093126038324);
        }
    }

    private void summarizeSimulatedResult() {

        for (Purpose purpose : Purpose.getAllPurposes()) {
            for (String role : roles){
                simulatedStopDestinationAverageDistance_km.get(purpose).put(role, 0.);
                numberOfAct.get(purpose).put(role, 0);
            }
        }

        for (Household household : dataSet.getHouseholds().values()) {
            if (household.getSimulated()) {

                boolean hasEV = false;
                List<Vehicle> vehicles = household.getVehicles();
                for (Vehicle vehicle:vehicles){
                    if (vehicle instanceof Car){
                        ((Car)vehicle).getCarType().equals(CarType.ELECTRIC);
                        hasEV = true;
                        break;
                    }
                }

                String role = "nonEvOwner";
                if (hasEV){
                    role = "evOwner";
                }

                for (Person person : household.getPersons()) {
                    for (Tour tour : person.getPlan().getTours().values()) {
                        Purpose tourPurpose = tour.getMainActivity().getPurpose();
                        for (Activity activity : tour.getActivities().values()) {
                            if (!activity.equals(tour.getMainActivity())) {
                                double distanceInMeters;
                                if (tourPurpose.equals(Purpose.WORK) ) {
                                    distanceInMeters = dataSet.getTravelDistances().getTravelDistanceInMeters(household.getLocation(), activity.getLocation(), Mode.UNKNOWN, 0.);
                                } else {
                                    distanceInMeters = dataSet.getTravelDistances().getTravelDistanceInMeters(tour.getMainActivity().getLocation(), activity.getLocation(), Mode.UNKNOWN, 0.);
                                }
                                double distanceInKm = distanceInMeters / 1000;
                                //here actually is the total distance for each purpose
                                simulatedStopDestinationAverageDistance_km.get(tourPurpose).put(role , simulatedStopDestinationAverageDistance_km.get(tourPurpose).get(role) + distanceInKm);
                                numberOfAct.get(tourPurpose).put(role, numberOfAct.get(tourPurpose).get(role) + 1);
                                break;
                            }
                        }
                    }
                }
            }
        }
        for (Purpose purpose : Purpose.getAllPurposes()) {
            for (String role : roles){
                if (purpose.equals(Purpose.WORK)) {
                    simulatedStopDestinationAverageDistance_km.get(purpose).put(role, simulatedStopDestinationAverageDistance_km.get(purpose).get(role) / numberOfAct.get(purpose).get(role));
                }else {
                    simulatedStopDestinationAverageDistance_km.get(purpose).put(role, simulatedStopDestinationAverageDistance_km.get(purpose).get(role) / numberOfAct.get(purpose).get(role));
                }
            }
        }
    }

    private void printFinalCoefficientsTable(Map<Purpose, Map<String, Double>> finalCoefficientsTable) throws FileNotFoundException {
        logger.info("Writing stop destination choice coefficient + calibration factors: " + inputFolder);
        PrintWriter pw = new PrintWriter(inputFolder);
        StringBuilder header = new StringBuilder("variable");
        for (Purpose purpose : Purpose.getAllPurposes()) {
                header.append(",");
                header.append(purpose);
        }
        pw.println(header);
        for (String variableNames : finalCoefficientsTable.get(Purpose.WORK).keySet()) {
            StringBuilder line = new StringBuilder(variableNames);
            for (Purpose purpose : Purpose.getAllPurposes()) {
                line.append(",");
                line.append(finalCoefficientsTable.get(purpose).get(variableNames));
            }
            pw.println(line);
        }
        pw.close();
    }
}

