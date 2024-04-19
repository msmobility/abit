package abm.models.destinationChoice;

import abm.data.DataSet;
import abm.data.geo.MicroscopicLocation;
import abm.data.geo.Zone;
import abm.data.plans.Activity;
import abm.data.plans.Mode;
import abm.data.plans.Purpose;
import abm.data.plans.Tour;
import abm.data.pop.Person;
import abm.data.vehicle.Car;
import abm.data.vehicle.CarType;
import abm.data.vehicle.Vehicle;
import abm.io.input.CoefficientsReader;
import abm.io.input.DefaultDataReaderManager;
import abm.io.input.LogsumReader;
import abm.properties.AbitResources;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix1D;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix2D;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class McLogsumBasedDestinationChoiceModel implements DestinationChoice {

    private static DataSet dataSet;
    private final static Logger logger = Logger.getLogger(McLogsumBasedDestinationChoiceModel.class);
    private final Map<Purpose, Map<Zone, Double>> zoneAttractorsByPurpose;
    private Map<Purpose, Map<String, IndexedDoubleMatrix2D>> expUtilityMatricesMainDestinationByPurposeByRole = new HashMap<>();
    private Map<Purpose, Map<String, IndexedDoubleMatrix2D>> expUtilityMatricesStopDestinationByPurposeByRole = new HashMap<>();

    private Map<Purpose, Map<String, Double>> coefficientsMain;
    private Map<Purpose, Map<String, Double>> coefficientsStop;
    private boolean runCalibrationMain = false;
    private boolean runCalibrationStop = false;

    private Map<Purpose, Map<String, Double>> updatedCalibrationFactorsMain = new HashMap<>();
    private Map<Purpose, Map<String, Double>> updatedCalibrationFactorsStop = new HashMap<>();
    LogsumReader logsumReader;
    private final String[] roles = {"evOwner", "nonEvOwner"};

    public static void main(String[] args) {
        AbitResources.initializeResources(args[0]);
        AbitUtils.loadHdf5Lib();
        MitoUtil.initializeRandomNumber(AbitUtils.getRandomObject());

        logger.info("Reading data");
        dataSet = new DefaultDataReaderManager().readData();

        McLogsumBasedDestinationChoiceModel mcLogsumBasedDestinationChoiceModel = new McLogsumBasedDestinationChoiceModel(dataSet);
    }

    public McLogsumBasedDestinationChoiceModel(DataSet dataSet) {
        this.dataSet = dataSet;
        new LogsumReader(this.dataSet).read();
        this.coefficientsMain = new HashMap<>();
        Path pathToCoefficientsMainFile = Path.of(AbitResources.instance.getString("destination.choice.main.act.logsum.input"));
        this.coefficientsStop = new HashMap<>();
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
    }

    public McLogsumBasedDestinationChoiceModel(DataSet dataSet, Boolean runCalibrationMain, Boolean runCalibrationStop) {
        this(dataSet);
        this.updatedCalibrationFactorsMain = new HashMap<>();
        this.updatedCalibrationFactorsStop = new HashMap<>();
        for (Purpose purpose : Purpose.getAllPurposes()) {
            if (runCalibrationMain) {
                this.updatedCalibrationFactorsMain.putIfAbsent(purpose, new HashMap<>());
                updatedCalibrationFactorsMain.get(purpose).put("ALPHA_calibration", 0.);
                updatedCalibrationFactorsMain.get(purpose).put("BETA_calibration", 0.);
            }
            if (runCalibrationStop) {
                this.updatedCalibrationFactorsStop.putIfAbsent(purpose, new HashMap<>());
                updatedCalibrationFactorsStop.get(purpose).put("ALPHA_calibration", 0.);
                updatedCalibrationFactorsStop.get(purpose).put("BETA_calibration", 0.);
            }
        }
        this.runCalibrationMain = runCalibrationMain;
        this.runCalibrationStop = runCalibrationStop;
    }

    private Map<Purpose, Map<String, IndexedDoubleMatrix2D>> calculateProbabilityMainDestination() {
        Map<Purpose, Map<String, IndexedDoubleMatrix2D>> expUtilityMatrixByPurposeByRole = new HashMap<>();
        for (Purpose purpose : Purpose.getAllPurposes()) {
            for (String role : roles){
                IndexedDoubleMatrix2D expUtilityMatrix = new IndexedDoubleMatrix2D(dataSet.getZones().values(), dataSet.getZones().values());
                expUtilityMatrixByPurposeByRole.get(purpose).putIfAbsent(role, expUtilityMatrix);
                for (Zone origin : dataSet.getZones().values()) {
                    for (Zone destination : dataSet.getZones().values()) {
                        final double logsum = dataSet.getLogsums().get(role).get(purpose).getIndexed(origin.getId(), destination.getId());
                        final double attractor = zoneAttractorsByPurpose.get(purpose).get(destination);
                        double utility = Math.pow(attractor, coefficientsMain.get(purpose).get("ALPHA")+ coefficientsMain.get(purpose).get("ALPHA_calibration")) * Math.exp(BETAFIXED * (coefficientsMain.get(purpose).get("BETA") + coefficientsMain.get(purpose).get("BETA_calibration")) * logsum);
                        double expUtility = Math.exp(utility);
                        expUtilityMatrix.setIndexed(origin.getId(), destination.getId(), expUtility);
                    }
                }
            }
        }
        return expUtilityMatrixByPurposeByRole;
    }

    private Map<Purpose, Map<String, IndexedDoubleMatrix2D>> calculateProbabilityStopDestination() {
        Map<Purpose, Map<String, IndexedDoubleMatrix2D>> expUtilityMatrixByPurposeByRole = new HashMap<>();
        for (Purpose purpose : Purpose.getAllPurposes()) {
            for (String role : roles){
                IndexedDoubleMatrix2D expUtilityMatrix = new IndexedDoubleMatrix2D(dataSet.getZones().values(), dataSet.getZones().values());
                expUtilityMatrixByPurposeByRole.get(purpose).putIfAbsent(role, expUtilityMatrix);
                for (Zone origin : dataSet.getZones().values()) {
                    for (Zone destination : dataSet.getZones().values()) {
                        final double logsum = dataSet.getLogsums().get(role).get(purpose).getIndexed(origin.getId(), destination.getId());
                        final double attractor = zoneAttractorsByPurpose.get(purpose).get(destination);
                        double utility = Math.pow(attractor, coefficientsMain.get(purpose).get("ALPHA")+ coefficientsMain.get(purpose).get("ALPHA_calibration")) * Math.exp(BETAFIXED * (coefficientsMain.get(purpose).get("BETA") + coefficientsMain.get(purpose).get("BETA_calibration")) * logsum);
                        double expUtility = Math.exp(utility);
                        expUtilityMatrix.setIndexed(origin.getId(), destination.getId(), expUtility);
                    }
                }
            }
        }
        return expUtilityMatrixByPurposeByRole;
    }

    private Map<Purpose, Map<Zone, Double>> loadBasicAttraction() {
        Path pathToFilePurpose = Path.of(AbitResources.instance.getString("destination.choice.attractors"));
        Map<Purpose, Map<Zone, Double>> zoneAttractorsByPurpose = new HashMap<>();
        for (Purpose purpose : Purpose.getAllPurposes()) {
            String columnName = purpose.toString().toLowerCase(); //todo review this
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

    public void updateMainDestinationProbability() {
        expUtilityMatricesMainDestinationByPurposeByRole = calculateProbabilityMainDestination();
    }

    public void updateStopDestinationProbability() {
        expUtilityMatricesStopDestinationByPurposeByRole = calculateProbabilityStopDestination();
    }

    @Override
    public void selectMainActivityDestination(Person person, Activity activity) {
        Zone origin = dataSet.getZones().get(person.getHousehold().getLocation().getZoneId());
        boolean hasEV = false;
        List<Vehicle> vehicles = person.getHousehold().getVehicles();
        for (Vehicle vehicle:vehicles){
            if (vehicle instanceof Car){
                ((Car)vehicle).getCarType().equals(CarType.ELECTRIC);
                hasEV = true;
                break;
            }
        }
        Zone destination;
        if (person.isHasLicense() && hasEV){
            final int selectedIndex = MitoUtil.select(expUtilityMatricesMainDestinationByPurposeByRole.get(activity.getPurpose()).get("evOwner").viewRow(origin.getId()).toNonIndexedArray());
            final int[] columnLookupArray = expUtilityMatricesMainDestinationByPurposeByRole.get(activity.getPurpose()).get("evOwner").getColumnLookupArray();
            destination = dataSet.getZones().get(columnLookupArray[selectedIndex]);

        } else{
            final int selectedIndex = MitoUtil.select(expUtilityMatricesMainDestinationByPurposeByRole.get(activity.getPurpose()).get("nonEvOwner").viewRow(origin.getId()).toNonIndexedArray());
            final int[] columnLookupArray = expUtilityMatricesMainDestinationByPurposeByRole.get(activity.getPurpose()).get("nonEvOwner").getColumnLookupArray();
            destination = dataSet.getZones().get(columnLookupArray[selectedIndex]);
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
        boolean hasEV = false;
        List<Vehicle> vehicles = person.getHousehold().getVehicles();
        for (Vehicle vehicle:vehicles){
            if (vehicle instanceof Car){
                ((Car)vehicle).getCarType().equals(CarType.ELECTRIC);
                hasEV = true;
                break;
            }
        }
        Zone destination;
        if(tour.getMainActivity().getPurpose().equals(Purpose.WORK)){
            if (person.isHasLicense() && hasEV){
                final int selectedIndex = MitoUtil.select(expUtilityMatricesMainDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("evOwner").viewRow(homeZone.getId()).toNonIndexedArray());
                final int[] columnLookupArray = expUtilityMatricesMainDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("evOwner").getColumnLookupArray();
                destination = dataSet.getZones().get(columnLookupArray[selectedIndex]);
            } else{
                final int selectedIndex = MitoUtil.select(expUtilityMatricesMainDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("nonEvOwner").viewRow(homeZone.getId()).toNonIndexedArray());
                final int[] columnLookupArray = expUtilityMatricesMainDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("nonEvOwner").getColumnLookupArray();
                destination = dataSet.getZones().get(columnLookupArray[selectedIndex]);
            }
        }else{

            if (person.isHasLicense() && hasEV){
                final int selectedIndex = MitoUtil.select(expUtilityMatricesMainDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("evOwner").viewRow(mainActZone.getId()).toNonIndexedArray());
                final int[] columnLookupArray = expUtilityMatricesMainDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("evOwner").getColumnLookupArray();
                destination = dataSet.getZones().get(columnLookupArray[selectedIndex]);
            } else{
                final int selectedIndex = MitoUtil.select(expUtilityMatricesMainDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("nonEvOwner").viewRow(mainActZone.getId()).toNonIndexedArray());
                final int[] columnLookupArray = expUtilityMatricesMainDestinationByPurposeByRole.get(stopActivity.getPurpose()).get("nonEvOwner").getColumnLookupArray();
                destination = dataSet.getZones().get(columnLookupArray[selectedIndex]);
                //Todo check if this is correct destination = dataSet.getZones().get(columnLookupArray[selectedIndex+1]);
            }
        }
        final Coordinate randomCoordinate = destination.getRandomCoordinate(AbitUtils.getRandomObject());
        MicroscopicLocation microDestination = new MicroscopicLocation(randomCoordinate.x, randomCoordinate.y);
        microDestination.setZone(destination);
        stopActivity.setLocation(microDestination);
    }

    public void updateCalibrationFactorsMain(Map<Purpose, Map<String, Double>> newCalibrationFactorsMain) {
        for (Purpose purpose : Purpose.getAllPurposes()) {
            double calibrationFactorsFromLastIteration = this.updatedCalibrationFactorsMain.get(purpose).get("BETA_calibration");
            double updatedCalibrationFactors = newCalibrationFactorsMain.get(purpose).get("BETA_calibration");
            this.updatedCalibrationFactorsMain.get(purpose).replace("BETA_calibration", calibrationFactorsFromLastIteration + updatedCalibrationFactors);
            logger.info("Calibration factor for " + purpose + "\t" + ": " + updatedCalibrationFactorsMain);
        }
    }

    public void updateCalibrationFactorsStop(Map<Purpose, Map<String, Double>> newCalibrationFactorsStop) {
        for (Purpose purpose : Purpose.getAllPurposes()) {
            double calibrationFactorsFromLastIteration = this.updatedCalibrationFactorsStop.get(purpose).get("BETA_calibration");
            double updatedCalibrationFactors = newCalibrationFactorsStop.get(purpose).get("BETA_calibration");
            this.updatedCalibrationFactorsStop.get(purpose).replace("BETA_calibration", calibrationFactorsFromLastIteration + updatedCalibrationFactors);
            logger.info("Calibration factor for " + purpose + "\t" + ": " + updatedCalibrationFactorsStop);
        }
    }

    public Map<Purpose, Map<String, Double>> obtainCoefficientsTableMain() {
        double originalCalibrationFactor = 0.0;
        double updatedCalibrationFactor = 0.0;
        double latestCalibrationFactor = 0.0;
        for (Purpose purpose : Purpose.values()) {
            if (!(purpose.equals(Purpose.HOME) || purpose.equals(Purpose.SUBTOUR))) {
                originalCalibrationFactor = coefficientsMain.get(purpose).get("BETA_calibration");
                updatedCalibrationFactor = updatedCalibrationFactorsMain.get(purpose).get("BETA_calibration");
                latestCalibrationFactor = originalCalibrationFactor + updatedCalibrationFactor;
                coefficientsMain.get(purpose).replace("BETA_calibration", latestCalibrationFactor);
            }
        }
        return this.coefficientsMain;
    }

    public Map<Purpose, Map<String, Double>> obtainCoefficientsTableStop() {
        double originalCalibrationFactor = 0.0;
        double updatedCalibrationFactor = 0.0;
        double latestCalibrationFactor = 0.0;
        for (Purpose purpose : Purpose.values()) {
            if (!(purpose.equals(Purpose.HOME) || purpose.equals(Purpose.SUBTOUR))) {
                originalCalibrationFactor = coefficientsStop.get(purpose).get("BETA_calibration");
                updatedCalibrationFactor = updatedCalibrationFactorsStop.get(purpose).get("BETA_calibration");
                latestCalibrationFactor = originalCalibrationFactor + updatedCalibrationFactor;
                coefficientsStop.get(purpose).replace("BETA_calibration", latestCalibrationFactor);
            }
        }
        return this.coefficientsStop;
    }
}
