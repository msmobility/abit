package abm.scenarios.lowEmissionZones.models.destinationChoice;

import abm.data.DataSet;
import abm.data.geo.MicroscopicLocation;
import abm.data.geo.Zone;
import abm.data.plans.Activity;
import abm.data.plans.Mode;
import abm.data.plans.Purpose;
import abm.data.plans.Tour;
import abm.data.pop.Person;
import abm.data.vehicle.STCar;
import abm.data.vehicle.EmissionClass;
import abm.data.vehicle.Vehicle;
import abm.io.input.CoefficientsReader;
import abm.io.input.LogsumReader;
import abm.io.input.STLogsumReader;
import abm.models.destinationChoice.DestinationChoice;
import abm.properties.AbitResources;
import abm.scenarios.lowEmissionZones.io.LowEmissionZoneReader;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix2D;
import org.apache.log4j.Logger;
import org.eclipse.emf.ecore.util.Switch;
import org.locationtech.jts.geom.Coordinate;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class STMcLogsumBasedDestinationChoiceModel implements DestinationChoice {
    private final DataSet dataSet;
    private final static Logger logger = Logger.getLogger(STMcLogsumBasedDestinationChoiceModel.class);
    private final Map<Purpose, Map<Zone, Double>> zoneAttractorsByPurpose;
    private Map<Purpose, Map<String, IndexedDoubleMatrix2D>> expUtilityMatricesMainDestinationByPurposeByRole = new HashMap<>();
    private Map<Purpose, Map<String, IndexedDoubleMatrix2D>> expUtilityMatricesStopDestinationByPurposeByRole = new HashMap<>();
    private final Map<Purpose, Map<String, Double>> coefficientsMain;
    private final Map<Purpose, Map<String, Double>> coefficientsStop;
    private boolean runCalibrationMain = false;
    private boolean runCalibrationStop = false;
    private Map<Purpose, Map<String, Double>> updatedCalibrationFactorsMain = new HashMap<>();
    private Map<Purpose, Map<String, Double>> updatedCalibrationFactorsStop = new HashMap<>();
    private final String[] carEmissionClass = {"EURO4", "EURO5", "EURO6", "EUROx"};
    private static final boolean scenarioLowEmissionZone = Boolean.parseBoolean(AbitResources.instance.getString("scenario.lowEmissionZone"));
    private static Map<Integer, Boolean> lowEmissionZones = new HashMap<>();

    public STMcLogsumBasedDestinationChoiceModel(DataSet dataSet) throws FileNotFoundException {
        this.dataSet = dataSet;
        new STLogsumReader(this.dataSet).read();
        this.coefficientsMain = new HashMap<>();
        //for running LogsumAnalysis it was destination.choice.main.act
        Path pathToCoefficientsMainFile = Path.of(AbitResources.instance.getString("destination.choice.main.act.logsum.input"));
        this.coefficientsStop = new HashMap<>();
        //for running LogsumAnalysis it was destination.choice.stop.act
        Path pathToCoefficientsStopFile = Path.of(AbitResources.instance.getString("destination.choice.stop.act.logsum.input"));
        for (Purpose purpose : Purpose.getAllPurposes()) {
            final Map<String, Double> purposeCoefficientsMain = new CoefficientsReader(dataSet, purpose.toString().toLowerCase(), pathToCoefficientsMainFile).readCoefficients();
            coefficientsMain.put(purpose, purposeCoefficientsMain);
            final Map<String, Double> purposeCoefficientsStop = new CoefficientsReader(dataSet, purpose.toString(), pathToCoefficientsStopFile).readCoefficients();
            coefficientsStop.put(purpose, purposeCoefficientsStop);
        }
        zoneAttractorsByPurpose = loadBasicAttraction();
        expUtilityMatricesMainDestinationByPurposeByRole = calculateProbabilityMainDestination();
        expUtilityMatricesStopDestinationByPurposeByRole = calculateProbabilityStopDestination();

        if (scenarioLowEmissionZone) {
            lowEmissionZones = new LowEmissionZoneReader(dataSet).readLowEmissionZones();
        }
    }

    public STMcLogsumBasedDestinationChoiceModel(DataSet dataSet, Boolean runCalibrationMain, Boolean runCalibrationStop) throws FileNotFoundException {
        this(dataSet);

        this.updatedCalibrationFactorsMain = new HashMap<>();
        this.updatedCalibrationFactorsStop = new HashMap<>();
        for (Purpose purpose : Purpose.getAllPurposes()) {
            if (runCalibrationMain) {
                this.updatedCalibrationFactorsMain.putIfAbsent(purpose, new HashMap<>());
                updatedCalibrationFactorsMain.get(purpose).put("ALPHA_calibration", 0.);
                updatedCalibrationFactorsMain.get(purpose).put("BETA_calibration", 1.);
                updatedCalibrationFactorsMain.get(purpose).put("distanceUtility", 1.);
            }
            if (runCalibrationStop) {
                this.updatedCalibrationFactorsStop.putIfAbsent(purpose, new HashMap<>());
                updatedCalibrationFactorsStop.get(purpose).put("ALPHA_calibration", 0.);
                updatedCalibrationFactorsStop.get(purpose).put("BETA_calibration", 1.);
                updatedCalibrationFactorsStop.get(purpose).put("distanceUtility", 1.);
            }
        }
        this.runCalibrationMain = runCalibrationMain;
        this.runCalibrationStop = runCalibrationStop;
    }

    private Map<Purpose, Map<String, IndexedDoubleMatrix2D>> calculateProbabilityMainDestination() throws FileNotFoundException {
        Map<Purpose, Map<String, IndexedDoubleMatrix2D>> expUtilityMatrixByPurposeByRole = new HashMap<>();
        for (Purpose purpose : Purpose.getAllPurposes()) {
            expUtilityMatrixByPurposeByRole.put(purpose, new HashMap<>());
            for (String carEmissionClass : carEmissionClass) {
                IndexedDoubleMatrix2D expUtilityMatrix = new IndexedDoubleMatrix2D(dataSet.getZones().values(), dataSet.getZones().values());
                PrintWriter inputWriter = new PrintWriter("C:/Users/Sonja/Documents/data/abit_standalone/output/distributionDistanceLogsums_main_" + purpose.toString().toLowerCase() + "_" + carEmissionClass.toLowerCase()+ ".csv");
                inputWriter.println("origin, destination, distance, logsum, utility_distance, utility_logsum, attractor");
                for (Zone origin : dataSet.getZones().values()) {
                    for (Zone destination : dataSet.getZones().values()) {
                        double logsum = dataSet.getLogsums().get(carEmissionClass).get(purpose).getIndexed(origin.getId(), destination.getId());
                        double attractor = zoneAttractorsByPurpose.get(purpose).get(destination);
                        double utility_logsum;
                        if (runCalibrationMain) {
                            utility_logsum = coefficientsMain.get(purpose).get("BETA") * coefficientsMain.get(purpose).get("BETA_calibration") * updatedCalibrationFactorsMain.get(purpose).get("BETA_calibration") * logsum;
                        } else {
                            utility_logsum = coefficientsMain.get(purpose).get("BETA") * coefficientsMain.get(purpose).get("BETA_calibration") * logsum;
                        }
                        if (utility_logsum == Double.POSITIVE_INFINITY) {
                            logger.warn("Logsum utility is positive infinity for " + purpose + " from " + origin + " to " + destination);
                        }
                        double distance_km = dataSet.getTravelDistances().getTravelDistanceInMeters(origin, destination, Mode.UNKNOWN, 8 * 60) / 1000.0;
                        double utility_distance;
                        if (runCalibrationMain) {
                            utility_distance = (coefficientsMain.get(purpose).get("distanceUtility") * updatedCalibrationFactorsMain.get(purpose).get("distanceUtility")) * distance_km;
                        } else {
                            utility_distance = coefficientsMain.get(purpose).get("distanceUtility") * distance_km;
                        }
                        double utility = utility_logsum + utility_distance;
                        double expUtility = Math.exp(utility) * Math.pow(attractor, coefficientsMain.get(purpose).get("ALPHA"));
                        expUtilityMatrix.setIndexed(origin.getId(), destination.getId(), expUtility);
                        inputWriter.println(origin.getId() + "," + destination.getId() + "," + distance_km + "," + logsum + "," + utility_distance + "," + utility_logsum + "," + attractor);
                    }
                }
                inputWriter.close();
                expUtilityMatrixByPurposeByRole.get(purpose).putIfAbsent(carEmissionClass, expUtilityMatrix);
            }
        }
        return expUtilityMatrixByPurposeByRole;
    }

    private Map<Purpose, Map<String, IndexedDoubleMatrix2D>> calculateProbabilityStopDestination() throws FileNotFoundException {
        Map<Purpose, Map<String, IndexedDoubleMatrix2D>> expUtilityMatrixByPurposeByRole = new HashMap<>();
        for (Purpose purpose : Purpose.getAllPurposes()) {
            expUtilityMatrixByPurposeByRole.put(purpose, new HashMap<>());
            for (String carEmissionClass : carEmissionClass) {
                IndexedDoubleMatrix2D expUtilityMatrix = new IndexedDoubleMatrix2D(dataSet.getZones().values(), dataSet.getZones().values());
                PrintWriter inputWriter = new PrintWriter("C:/Users/Sonja/Documents/data/abit_standalone/output/distributionDistanceLogsums_stop_" + purpose.toString().toLowerCase() + "_" + carEmissionClass.toLowerCase()+ ".csv");
                inputWriter.println("origin, destination, distance, logsum, utility_distance, utility_logsum, attractor");
                for (Zone origin : dataSet.getZones().values()) {
                    for (Zone destination : dataSet.getZones().values()) {
                        double logsum = dataSet.getLogsums().get(carEmissionClass).get(purpose).getIndexed(origin.getId(), destination.getId());
                        double attractor = zoneAttractorsByPurpose.get(purpose).get(destination);
                        double utility_logsum;
                        if (runCalibrationStop) {
                            utility_logsum = (coefficientsStop.get(purpose).get("BETA") * coefficientsStop.get(purpose).get("BETA_calibration") * updatedCalibrationFactorsStop.get(purpose).get("BETA_calibration")) * logsum;
                        } else {
                            utility_logsum = (coefficientsStop.get(purpose).get("BETA") * coefficientsStop.get(purpose).get("BETA_calibration")) * logsum;
                        }
                        if (utility_logsum == Double.POSITIVE_INFINITY) {
                            logger.warn("Logsum utility is positive infinity for " + purpose + " from " + origin + " to " + destination);
                        }
                        double distance_km = dataSet.getTravelDistances().getTravelDistanceInMeters(origin, destination, Mode.UNKNOWN, 8 * 60) / 1000.0;
                        double utility_distance;
                        if (runCalibrationStop) {
                            utility_distance = (coefficientsStop.get(purpose).get("distanceUtility") * updatedCalibrationFactorsStop.get(purpose).get("distanceUtility")) * distance_km;
                        } else {
                            utility_distance = coefficientsStop.get(purpose).get("distanceUtility") * distance_km;
                        }
                        double utility = utility_logsum + utility_distance;
                        double expUtility = Math.exp(utility) * Math.pow(attractor, coefficientsStop.get(purpose).get("ALPHA"));
                        expUtilityMatrix.setIndexed(origin.getId(), destination.getId(), expUtility);
                        inputWriter.println(origin.getId() + "," + destination.getId() + "," + distance_km + "," + logsum + "," + utility_distance + "," + utility_logsum + "," + attractor);
                    }
                }
                inputWriter.close();
                expUtilityMatrixByPurposeByRole.get(purpose).putIfAbsent(carEmissionClass, expUtilityMatrix);
            }
        }
        return expUtilityMatrixByPurposeByRole;
    }

    private Map<Purpose, Map<Zone, Double>> loadBasicAttraction() {
        Path pathToFilePurpose = Path.of(AbitResources.instance.getString("destination.choice.attractors"));
        Map<Purpose, Map<Zone, Double>> zoneAttractorsByPurpose = new HashMap<>();
        for (Purpose purpose : Purpose.getAllPurposes()) {
            String columnName = purpose.toString().toLowerCase();
            Map<String, Double> coefficients = new CoefficientsReader(dataSet, columnName, pathToFilePurpose).readCoefficients();
            Map<Zone, Double> attractorByZone = new HashMap<>();
            for (Zone zone : dataSet.getZones().values()) {
                Double value = 0.;
                for (Map.Entry<String, Double> entry : coefficients.entrySet()) {
                    value += coefficients.get(entry.getKey()) * Integer.valueOf(String.valueOf(zone.getAttribute(entry.getKey()).get()));
                }
                attractorByZone.put(zone, value);
            }
            zoneAttractorsByPurpose.put(purpose, attractorByZone);
        }
        return zoneAttractorsByPurpose;
    }

    public void updateMainDestinationProbability() throws FileNotFoundException {
        expUtilityMatricesMainDestinationByPurposeByRole = calculateProbabilityMainDestination();
    }

    public void updateStopDestinationProbability() throws FileNotFoundException {
        expUtilityMatricesStopDestinationByPurposeByRole = calculateProbabilityStopDestination();
    }

    @Override
    public void selectMainActivityDestination(Person person, Activity activity) {
        Zone origin = dataSet.getZones().get(person.getHousehold().getLocation().getZoneId());

        List<Vehicle> vehicles = person.getHousehold().getVehicles();
        if (vehicles == null || vehicles.isEmpty()) {
            System.out.println("No vehicles found for household: " + person.getHousehold().getId());
            return;
        }

        EmissionClass highestEmissionClass = null;

        for (Vehicle vehicle : vehicles) {
            if (vehicle instanceof STCar) {
                EmissionClass currentClass = ((STCar) vehicle).getEmissionClass();
                if (currentClass == null) {
                    System.out.println("STCar with null emission class found.");
                    continue;
                }
                if (highestEmissionClass == null || currentClass.getWeight() > highestEmissionClass.getWeight()) {
                    highestEmissionClass = currentClass;
                }
            } else {
                System.out.println("Vehicle is not an instance of STCar: " + vehicle.getClass().getName());
            }
        }

        if (highestEmissionClass == null) {
            System.out.println("No valid STCar emission class found for household: " + person.getHousehold().getId());
            return;
        }

       // System.out.println("Highest emission class: " + highestEmissionClass.name());

        Zone destination;

//        if (highestEmissionClass == null) {
//            highestEmissionClass = EmissionClass.EUROx; // default/fallback emission class
//        }

        // alternative to block scope {}: define selectedIndex and columnLookUpArray before the switch and not as final
        switch (highestEmissionClass) {

            case EURO_4: {
                final int selectedIndex = MitoUtil.select(expUtilityMatricesMainDestinationByPurposeByRole.get(activity.getPurpose()).get("EURO4").viewRow(origin.getId()).toNonIndexedArray());
                final int[] columnLookupArray = expUtilityMatricesMainDestinationByPurposeByRole.get(activity.getPurpose()).get("EURO4").getColumnLookupArray();
                destination = dataSet.getZones().get(columnLookupArray[selectedIndex]);
                break;
            }

            case EURO_5: {
                final int selectedIndex = MitoUtil.select(expUtilityMatricesMainDestinationByPurposeByRole.get(activity.getPurpose()).get("EURO5").viewRow(origin.getId()).toNonIndexedArray());
                final int[] columnLookupArray = expUtilityMatricesMainDestinationByPurposeByRole.get(activity.getPurpose()).get("EURO5").getColumnLookupArray();
                destination = dataSet.getZones().get(columnLookupArray[selectedIndex]);
                break;
            }

            case EURO_6: {
                final int selectedIndex = MitoUtil.select(expUtilityMatricesMainDestinationByPurposeByRole.get(activity.getPurpose()).get("EURO6").viewRow(origin.getId()).toNonIndexedArray());
                final int[] columnLookupArray = expUtilityMatricesMainDestinationByPurposeByRole.get(activity.getPurpose()).get("EURO6").getColumnLookupArray();
                destination = dataSet.getZones().get(columnLookupArray[selectedIndex]);
                break;
            }

            case EUROx: {
                final int selectedIndex = MitoUtil.select(expUtilityMatricesMainDestinationByPurposeByRole.get(activity.getPurpose()).get("EUROx").viewRow(origin.getId()).toNonIndexedArray());
                final int[] columnLookupArray = expUtilityMatricesMainDestinationByPurposeByRole.get(activity.getPurpose()).get("EUROx").getColumnLookupArray();
                destination = dataSet.getZones().get(columnLookupArray[selectedIndex]);
                break;
            }
            default:
                throw new IllegalStateException("Unexpected value: " + highestEmissionClass);
        }
        final Coordinate randomCoordinate = destination.getRandomCoordinate(AbitUtils.getRandomObject());
        MicroscopicLocation microDestination = new MicroscopicLocation(randomCoordinate.x, randomCoordinate.y);
        microDestination.setZone(destination);
        activity.setLocation(microDestination);
    }

    @Override
    public void selectStopDestination(Person person, Tour tour, Activity stopActivity) {
        Zone homeZone = dataSet.getZones().get(person.getHousehold().getLocation().getZoneId());
        Zone mainActZone = dataSet.getZones().get(tour.getMainActivity().getLocation().getZoneId());

        List<Vehicle> vehicles = person.getHousehold().getVehicles();
        if (vehicles == null || vehicles.isEmpty()) {
            System.out.println("No vehicles found for household: " + person.getHousehold().getId());
            //return;
        }

        EmissionClass highestEmissionClass = null;

        for (Vehicle vehicle : vehicles) {
            if (vehicle instanceof STCar) {
                EmissionClass currentClass = ((STCar) vehicle).getEmissionClass();
                if (currentClass == null) {
                    System.out.println("STCar with null emission class found.");
                    continue;
                }
                if (highestEmissionClass == null || currentClass.getWeight() > highestEmissionClass.getWeight()) {
                    highestEmissionClass = currentClass;
                }
            } else {
                System.out.println("Vehicle is not an instance of STCar: " + vehicle.getClass().getName());
            }
        }

        if (highestEmissionClass == null) {
            System.out.println("No valid STCar emission class found for household: " + person.getHousehold().getId());
            //return; // or assign a default, throw exception, etc.
        }

        //System.out.println("Highest emission class: " + highestEmissionClass.name());

        Zone destination;

        // alternative to block scope {}: define selectedIndex and columnLookUpArray before the switch and not as final
        if (highestEmissionClass!=null) {
            if (tour.getMainActivity().getPurpose().equals(Purpose.WORK)) {
                switch (highestEmissionClass) {

                    case EURO_4: {
                        final int selectedIndex = MitoUtil.select(expUtilityMatricesStopDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("EURO4").viewRow(homeZone.getId()).toNonIndexedArray());
                        final int[] columnLookupArray = expUtilityMatricesStopDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("EURO4").getColumnLookupArray();
                        destination = dataSet.getZones().get(columnLookupArray[selectedIndex]);
                        break;
                    }

                    case EURO_5: {
                        final int selectedIndex = MitoUtil.select(expUtilityMatricesStopDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("EURO5").viewRow(homeZone.getId()).toNonIndexedArray());
                        final int[] columnLookupArray = expUtilityMatricesStopDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("EURO5").getColumnLookupArray();
                        destination = dataSet.getZones().get(columnLookupArray[selectedIndex]);
                        break;
                    }

                    case EURO_6: {
                        final int selectedIndex = MitoUtil.select(expUtilityMatricesStopDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("EURO6").viewRow(homeZone.getId()).toNonIndexedArray());
                        final int[] columnLookupArray = expUtilityMatricesStopDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("EURO6").getColumnLookupArray();
                        destination = dataSet.getZones().get(columnLookupArray[selectedIndex]);
                        break;
                    }

                    case EUROx: {
                        final int selectedIndex = MitoUtil.select(expUtilityMatricesStopDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("EUROx").viewRow(homeZone.getId()).toNonIndexedArray());
                        final int[] columnLookupArray = expUtilityMatricesStopDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("EUROx").getColumnLookupArray();
                        destination = dataSet.getZones().get(columnLookupArray[selectedIndex]);
                        break;
                    }
                    default:
                        throw new IllegalStateException("Unexpected value: " + highestEmissionClass);
                }
            } else {
                switch (highestEmissionClass) {

                    case EURO_4: {
                        final int selectedIndex = MitoUtil.select(expUtilityMatricesMainDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("EURO4").viewRow(mainActZone.getId()).toNonIndexedArray());
                        final int[] columnLookupArray = expUtilityMatricesMainDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("EURO4").getColumnLookupArray();
                        destination = dataSet.getZones().get(columnLookupArray[selectedIndex]);
                        break;
                    }

                    case EURO_5: {
                        final int selectedIndex = MitoUtil.select(expUtilityMatricesMainDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("EURO5").viewRow(mainActZone.getId()).toNonIndexedArray());
                        final int[] columnLookupArray = expUtilityMatricesMainDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("EURO5").getColumnLookupArray();
                        destination = dataSet.getZones().get(columnLookupArray[selectedIndex]);
                        break;
                    }

                    case EURO_6: {
                        final int selectedIndex = MitoUtil.select(expUtilityMatricesMainDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("EURO6").viewRow(mainActZone.getId()).toNonIndexedArray());
                        final int[] columnLookupArray = expUtilityMatricesMainDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("EURO6").getColumnLookupArray();
                        destination = dataSet.getZones().get(columnLookupArray[selectedIndex]);
                        break;
                    }

                    case EUROx: {
                        final int selectedIndex = MitoUtil.select(expUtilityMatricesMainDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("EUROx").viewRow(mainActZone.getId()).toNonIndexedArray());
                        final int[] columnLookupArray = expUtilityMatricesMainDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("EUROx").getColumnLookupArray();
                        destination = dataSet.getZones().get(columnLookupArray[selectedIndex]);
                        break;
                    }
                    default:
                        throw new IllegalStateException("Unexpected value: " + highestEmissionClass);
                }
            }
        }else{
            if (tour.getMainActivity().getPurpose().equals(Purpose.WORK)) {
//todo add logsum for nonßcar users
                final int selectedIndex = MitoUtil.select(expUtilityMatricesStopDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("EURO4").viewRow(homeZone.getId()).toNonIndexedArray());
                final int[] columnLookupArray = expUtilityMatricesStopDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("EURO4").getColumnLookupArray();
                destination = dataSet.getZones().get(columnLookupArray[selectedIndex]);


            } else {

                final int selectedIndex = MitoUtil.select(expUtilityMatricesMainDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("EURO4").viewRow(mainActZone.getId()).toNonIndexedArray());
                final int[] columnLookupArray = expUtilityMatricesMainDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("EURO4").getColumnLookupArray();
                destination = dataSet.getZones().get(columnLookupArray[selectedIndex]);

            }
        }





        final Coordinate randomCoordinate = destination.getRandomCoordinate(AbitUtils.getRandomObject());
        MicroscopicLocation microDestination = new MicroscopicLocation(randomCoordinate.x, randomCoordinate.y);
        microDestination.setZone(destination);
        stopActivity.setLocation(microDestination);
    }


    public void updateBetaCalibrationFactorsMain(Map<Purpose, Map<String, Double>> newCalibrationFactorsMain) {
        for (Purpose purpose : Purpose.getAllPurposes()) {
            double calibrationFactorsFromLastIteration = this.updatedCalibrationFactorsMain.get(purpose).get("BETA_calibration");
            double updatedCalibrationFactors = newCalibrationFactorsMain.get(purpose).get("BETA_calibration");
            double newCalibrationFactor = calibrationFactorsFromLastIteration * updatedCalibrationFactors;
            if (this.coefficientsMain.get(purpose).get("BETA") * this.coefficientsMain.get(purpose).get("BETA_calibration") * newCalibrationFactor > 1) {
                logger.warn("Calibration factor for " + purpose + "\t" + " is greater than 1.");
            }
            this.updatedCalibrationFactorsMain.get(purpose).replace("BETA_calibration", newCalibrationFactor);
            logger.info("Calibration factor for " + purpose + "\t" + ": " + this.coefficientsMain.get(purpose).get("BETA") * this.coefficientsMain.get(purpose).get("BETA_calibration") * newCalibrationFactor);
        }
    }

    public void updateShortDistanceDisUtilityCalibrationFactorsMain(Map<Purpose, Map<String, Double>> newCalibrationFactorsMain) {
        for (Purpose purpose : Purpose.getAllPurposes()) {
            double calibrationFactorsFromLastIteration = this.updatedCalibrationFactorsMain.get(purpose).get("distanceUtility");
            double updatedCalibrationFactors = newCalibrationFactorsMain.get(purpose).get("distanceUtility");
            double newCalibrationFactor = calibrationFactorsFromLastIteration * updatedCalibrationFactors;
            this.updatedCalibrationFactorsMain.get(purpose).replace("distanceUtility", newCalibrationFactor);
            logger.info("Calibration factor for " + purpose + "\t" + ": " + (this.coefficientsMain.get(purpose).get("distanceUtility") * newCalibrationFactor));
        }
    }

    public void updateBetaCalibrationFactorsStop(Map<Purpose, Map<String, Double>> newCalibrationFactorsStop) {
        for (Purpose purpose : Purpose.getAllPurposes()) {
            double calibrationFactorsFromLastIteration = this.updatedCalibrationFactorsStop.get(purpose).get("BETA_calibration");
            double updatedCalibrationFactors = newCalibrationFactorsStop.get(purpose).get("BETA_calibration");
            double newCalibrationFactor = calibrationFactorsFromLastIteration * updatedCalibrationFactors;
            if (this.coefficientsStop.get(purpose).get("BETA") * this.coefficientsStop.get(purpose).get("BETA_calibration") * newCalibrationFactor > 1) {
                logger.warn("Calibration factor for " + purpose + "\t" + " is greater than 1.");
            }
            this.updatedCalibrationFactorsStop.get(purpose).replace("BETA_calibration", newCalibrationFactor);
            logger.info("Calibration factor for " + purpose + "\t" + ": " + this.coefficientsStop.get(purpose).get("BETA") * this.coefficientsStop.get(purpose).get("BETA_calibration") * newCalibrationFactor);
        }
    }

    public void updateShortDistanceDisUtilityCalibrationFactorsStop(Map<Purpose, Map<String, Double>> newCalibrationFactorsStop) {
        for (Purpose purpose : Purpose.getAllPurposes()) {
            double calibrationFactorsFromLastIteration = this.updatedCalibrationFactorsStop.get(purpose).get("distanceUtility");
            double updatedCalibrationFactors = newCalibrationFactorsStop.get(purpose).get("distanceUtility");
            double newCalibrationFactor = calibrationFactorsFromLastIteration * updatedCalibrationFactors;
            this.updatedCalibrationFactorsStop.get(purpose).replace("distanceUtility", newCalibrationFactor);
            logger.info("Calibration factor for " + purpose + "\t" + ": " + (this.coefficientsStop.get(purpose).get("distanceUtility") * newCalibrationFactor));
        }
    }

    public Map<Purpose, Map<String, Double>> obtainCoefficientsTableMain() {
        double originalBetaCalibrationFactor;
        double updatedBetaCalibrationFactor;
        double latestBetaCalibrationFactor;
        for (Purpose purpose : Purpose.getAllPurposes()) {
            originalBetaCalibrationFactor = coefficientsMain.get(purpose).get("BETA_calibration");
            updatedBetaCalibrationFactor = updatedCalibrationFactorsMain.get(purpose).get("BETA_calibration");
            latestBetaCalibrationFactor = originalBetaCalibrationFactor * updatedBetaCalibrationFactor;
            coefficientsMain.get(purpose).replace("BETA_calibration", latestBetaCalibrationFactor);
        }

        double originalShortDistanceDisutilityCalibrationFactor;
        double updatedShortDistanceDisutilityCalibrationFactor;
        double latestShortDistanceDisutilityCalibrationFactor;
        for (Purpose purpose : Purpose.getAllPurposes()) {
            originalShortDistanceDisutilityCalibrationFactor = coefficientsMain.get(purpose).get("distanceUtility");
            updatedShortDistanceDisutilityCalibrationFactor = updatedCalibrationFactorsMain.get(purpose).get("distanceUtility");
            latestShortDistanceDisutilityCalibrationFactor = originalShortDistanceDisutilityCalibrationFactor * updatedShortDistanceDisutilityCalibrationFactor;
            coefficientsMain.get(purpose).replace("distanceUtility", latestShortDistanceDisutilityCalibrationFactor);
        }
        return this.coefficientsMain;
    }

    public Map<Purpose, Map<String, Double>> obtainCoefficientsTableStop() {
        double originalCalibrationFactor;
        double updatedCalibrationFactor;
        double latestCalibrationFactor;
        for (Purpose purpose : Purpose.getAllPurposes()) {
            originalCalibrationFactor = coefficientsStop.get(purpose).get("BETA_calibration");
            updatedCalibrationFactor = updatedCalibrationFactorsStop.get(purpose).get("BETA_calibration");
            latestCalibrationFactor = originalCalibrationFactor * updatedCalibrationFactor;
            coefficientsStop.get(purpose).replace("BETA_calibration", latestCalibrationFactor);
        }

        double originalShortDistanceDisutilityCalibrationFactor;
        double updatedShortDistanceDisutilityCalibrationFactor;
        double latestShortDistanceDisutilityCalibrationFactor;
        for (Purpose purpose : Purpose.getAllPurposes()) {
            originalShortDistanceDisutilityCalibrationFactor = coefficientsStop.get(purpose).get("distanceUtility");
            updatedShortDistanceDisutilityCalibrationFactor = updatedCalibrationFactorsStop.get(purpose).get("distanceUtility");
            latestShortDistanceDisutilityCalibrationFactor = originalShortDistanceDisutilityCalibrationFactor * updatedShortDistanceDisutilityCalibrationFactor;
            coefficientsStop.get(purpose).replace("distanceUtility", latestShortDistanceDisutilityCalibrationFactor);
        }

        return this.coefficientsStop;
    }
}



