package abm.calibration;

import abm.data.DataSet;
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
import de.tum.bgu.msm.data.person.Occupation;
import org.apache.log4j.Logger;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MainMcLogsumDestinationChoiceCalibration implements ModelComponent {
    //Todo define a few calibration parameters
    static Logger logger = Logger.getLogger(MainMcLogsumDestinationChoiceCalibration.class);
    private static final int MAX_ITERATION = 100;//2_000_000;
    private static final double TERMINATION_THRESHOLD = 1.0;
    String inputFolder = AbitResources.instance.getString("destination.choice.main.act.logsum.output");
    DataSet dataSet;
    Map<Purpose, Map<String, Double>> objectiveMainDestinationAverageDistance_km = new HashMap<>();
    Map<Purpose, Map<String, Double>> simulatedMainDestinationAverageDistance_km = new HashMap<>();
    Map<Purpose, Map<String, Integer>> numberOfAct = new HashMap<>();
    Map<Purpose, Map<String, Double>> calibrationFactors = new HashMap<>();
    private McLogsumBasedDestinationChoiceModel destinationChoiceModel;
    private final String[] roles = {"evOwner", "nonEvOwner"};
    public MainMcLogsumDestinationChoiceCalibration(DataSet dataSet) {
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
            objectiveMainDestinationAverageDistance_km.put(purpose, new HashMap<>());
            simulatedMainDestinationAverageDistance_km.put(purpose,new HashMap<>());
            numberOfAct.put(purpose, new HashMap<>());
            for (String role : roles){
                calibrationFactors.get(purpose).put("ALPHA_calibration_" + role, 0.0);
                calibrationFactors.get(purpose).put("BETA_calibration_" + role, 0.0);
                objectiveMainDestinationAverageDistance_km.get(purpose).put(role, 0.);
                simulatedMainDestinationAverageDistance_km.get(purpose).put(role, 0.);
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
        logger.info("Start calibrating the main logsum destination choice model......");
        //Todo: loop through the calibration process until criteria are met
        for (int iteration = 0; iteration < MAX_ITERATION; iteration++) {
            double maxDifference = 0.0;
            for (Purpose purpose : Purpose.getAllPurposes()) {
                for (String role:roles){
                    double differenceAverageDistance;
                    double factor = (objectiveMainDestinationAverageDistance_km.get(purpose).get(role) / simulatedMainDestinationAverageDistance_km.get(purpose).get(role));
                    factor = Math.max(factor, 0.5);
                    factor = Math.min(factor, 2);
                    differenceAverageDistance = Math.abs(objectiveMainDestinationAverageDistance_km.get(purpose).get(role) - simulatedMainDestinationAverageDistance_km.get(purpose).get(role));

                    //todo calculate the difference between observed and simulated average distance
                    if (purpose.equals(Purpose.EDUCATION)){
                        calibrationFactors.get(purpose).replace("BETA_calibration_" + role, 1.0);
                    }else{
                        calibrationFactors.get(purpose).replace("BETA_calibration_" + role , factor);
                    }

                    logger.info("Main destination choice for" + purpose + "\t" + role  + "\t" + "average distance: " + simulatedMainDestinationAverageDistance_km.get(purpose).get(role));
                    if (!purpose.equals(Purpose.WORK) && !purpose.equals(Purpose.EDUCATION)) {
                        if (Math.abs(differenceAverageDistance) > maxDifference) {
                            maxDifference = differenceAverageDistance;
                        }
                    }
                }
            }

            if (maxDifference <= TERMINATION_THRESHOLD) {
                break;
            }

            destinationChoiceModel.updateCalibrationFactorsMain(calibrationFactors);
            destinationChoiceModel.updateMainDestinationProbability();

            List<Household> simulatedHouseholds = dataSet.getHouseholds().values().parallelStream().filter(Household::getSimulated).collect(Collectors.toList());
            simulatedHouseholds.parallelStream().forEach(household -> household.getPersons().forEach(person -> person.getPlan().getTours().forEach((tourIndex, tour) -> destinationChoiceModel.selectMainActivityDestination(person, tour.getMainActivity()))));

            summarizeSimulatedResult();

        }
        logger.info("Finished the calibration of main destination choice.");
        //Todo: obtain the updated coefficients + calibration factors
        Map<Purpose, Map<String, Double>> finalCoefficientsTableMain = destinationChoiceModel.obtainCoefficientsTableMain();

        //Todo: print the coefficients table to input folder
        try {
            printFinalCoefficientsTable(finalCoefficientsTableMain);
        } catch (FileNotFoundException e) {
            System.err.println("Output path of the coefficient table is not correct.");
        }

    }

    private void readObjectiveValues() {
        //todo add objective average distance
        for (String role : roles){
            objectiveMainDestinationAverageDistance_km.get(Purpose.WORK).put(role ,17.0369);
            objectiveMainDestinationAverageDistance_km.get(Purpose.EDUCATION).put(role ,11.1591);
            objectiveMainDestinationAverageDistance_km.get(Purpose.ACCOMPANY).put(role ,7.9266);
            objectiveMainDestinationAverageDistance_km.get(Purpose.SHOPPING).put(role ,5.4520);
            objectiveMainDestinationAverageDistance_km.get(Purpose.OTHER).put(role ,9.4005);
            objectiveMainDestinationAverageDistance_km.get(Purpose.RECREATION).put(role ,10.5703);
        }
    }

    private void summarizeSimulatedResult() {

        for (Purpose purpose : Purpose.getAllPurposes()) {
            for (String role : roles) {
                simulatedMainDestinationAverageDistance_km.get(purpose).put(role, 0.);
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
                    for (Tour tour : person.getPlan().getTours().values()){
                            Purpose mainPurpose = tour.getMainActivity().getPurpose();
                            if (person.getOccupation().equals(Occupation.EMPLOYED) && tour.getMainActivity().getPurpose().equals(Purpose.WORK)){
                                break;
                            }
                            if (person.getOccupation().equals(Occupation.STUDENT) && tour.getMainActivity().getPurpose().equals(Purpose.EDUCATION)){
                                break;
                            }
                            double distanceInMeters = dataSet.getTravelDistances().getTravelDistanceInMeters(household.getLocation(), tour.getMainActivity().getLocation(), Mode.UNKNOWN, 0.);
                            double distanceInKm = distanceInMeters / 1000;
                            //here actually is the total distance for each purpose
                            simulatedMainDestinationAverageDistance_km.get(mainPurpose).put(role, simulatedMainDestinationAverageDistance_km.get(mainPurpose).get(role) + distanceInKm);
                            numberOfAct.get(mainPurpose).put(role , numberOfAct.get(mainPurpose).get(role) + 1);
                    }
                }
            }
        }

        for (Purpose purpose : Purpose.getAllPurposes()) {
            for (String role : roles){
                simulatedMainDestinationAverageDistance_km.get(purpose).put(role, simulatedMainDestinationAverageDistance_km.get(purpose).get(role) / (double)numberOfAct.get(purpose).get(role));
            }
        }
    }

    private void printFinalCoefficientsTable(Map<Purpose, Map<String, Double>> finalCoefficientsTable) throws FileNotFoundException {
        logger.info("Writing main destination choice coefficient + calibration factors: " + inputFolder);
        PrintWriter pw = new PrintWriter(inputFolder);
        StringBuilder header = new StringBuilder("variable");
        for (Purpose purpose : Purpose.getAllPurposes()) {
            header.append(",");
            header.append(purpose);
        }
        pw.println(header);
        for (String variableNames : finalCoefficientsTable.get(Purpose.WORK).keySet()) {
            StringBuilder line = new StringBuilder(variableNames);
            for (Purpose purpose : Purpose.getAllPurposes()){
                line.append(",");
                line.append(finalCoefficientsTable.get(purpose).get(variableNames));

            }
            pw.println(line);
        }
        pw.close();
    }
}
