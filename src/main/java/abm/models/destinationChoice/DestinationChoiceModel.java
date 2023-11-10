package abm.models.destinationChoice;

import abm.data.DataSet;
import abm.data.geo.MicroscopicLocation;
import abm.data.geo.Zone;
import abm.data.plans.Activity;
import abm.data.plans.Mode;
import abm.data.plans.Purpose;
import abm.data.plans.Tour;
import abm.data.pop.Person;
import abm.io.input.CoefficientsReader;
import abm.properties.AbitResources;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix1D;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix2D;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;

import java.nio.file.Path;
import java.util.*;

public class DestinationChoiceModel implements DestinationChoice {

    private final DataSet dataSet;
    private final static Logger logger = Logger.getLogger(DestinationChoiceModel.class);
    private final Map<Purpose, Map<Zone, Double>> zoneAttractorsByPurpose;
    private Map<Purpose, IndexedDoubleMatrix2D> probabilityMatrixByPurpose = new HashMap<>();

    private Map<Purpose, Map<String, Double>> coefficientsMain;
    private Map<Purpose, Map<String, Double>> coefficientsStop;
    private boolean runCalibrationMain = false;
    private boolean runCalibrationStop = false;
    private Map<Purpose, Map<String, Double>> updatedCalibrationFactorsMain = new HashMap<>();
    private Map<Purpose, Map<String, Double>> updatedCalibrationFactorsStop = new HashMap<>();

    private static final double BETAFIXED = -0.01;
//    private static final double ALPHA = 20;
//    //TODO. Beta and alpha by purpose

    public DestinationChoiceModel(DataSet dataSet) {
        this.dataSet = dataSet;
        this.coefficientsMain = new HashMap<>();
        zoneAttractorsByPurpose = loadBasicAttraction();
        Path pathToCoefficientsMainFile = Path.of(AbitResources.instance.getString("destination.choice.main.act"));
        this.coefficientsStop = new HashMap<>();
        Path pathToCoefficientsStopFile = Path.of(AbitResources.instance.getString("destination.choice.stop.act"));
        for (Purpose purpose : Purpose.getAllPurposes()) {
            final Map<String, Double> purposeCoefficientsMain = new CoefficientsReader(dataSet, purpose.toString().toLowerCase(), pathToCoefficientsMainFile).readCoefficients();
            coefficientsMain.put(purpose, purposeCoefficientsMain);
            final Map<String, Double> purposeCoefficientsStop = new CoefficientsReader(dataSet, purpose.toString(), pathToCoefficientsStopFile).readCoefficients();
            coefficientsStop.put(purpose, purposeCoefficientsStop);
        }
        probabilityMatrixByPurpose = calculateProbability();
    }

    public DestinationChoiceModel(DataSet dataSet, Boolean runCalibrationMain, Boolean runCalibrationStop) {
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

    private Map<Purpose, IndexedDoubleMatrix2D> calculateProbability() {


        Map<Purpose, IndexedDoubleMatrix2D> matrixByPurpose = new HashMap<>();
        for (Purpose purpose : Purpose.getAllPurposes()) {
            IndexedDoubleMatrix2D probabilityMatrix = new IndexedDoubleMatrix2D(dataSet.getZones().values(), dataSet.getZones().values());
            for (Zone origin : dataSet.getZones().values()) {
                for (Zone destination : dataSet.getZones().values()) {
                    final int travelDistanceInMeters = dataSet.getTravelDistances().getTravelDistanceInMeters(origin, destination, Mode.UNKNOWN, 0.);
                    final double travelDistanceInKm = travelDistanceInMeters / 1000;
                    final double attractor = zoneAttractorsByPurpose.get(purpose).get(destination);
                    double utility = (coefficientsMain.get(purpose).get("ALPHA") + coefficientsMain.get(purpose).get("ALPHA_calibration")) * Math.exp(BETAFIXED * (coefficientsMain.get(purpose).get("BETA") + coefficientsMain.get(purpose).get("BETA_calibration")) * travelDistanceInKm);

                    double probability = attractor * Math.exp(utility);
                    probabilityMatrix.setIndexed(origin.getId(), destination.getId(), probability);

                }
            }
            matrixByPurpose.put(purpose, probabilityMatrix);
        }

        return matrixByPurpose;
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
        probabilityMatrixByPurpose = calculateProbability();
    }

    @Override
    public void selectMainActivityDestination(Person person, Activity activity) {

        Zone origin = dataSet.getZones().get(person.getHousehold().getLocation().getZoneId());

        final int selectedIndex = MitoUtil.select(probabilityMatrixByPurpose.get(activity.getPurpose()).viewRow(origin.getId()).toNonIndexedArray());

        final int[] columnLookupArray = probabilityMatrixByPurpose.get(activity.getPurpose()).getColumnLookupArray();

        Zone destination = dataSet.getZones().get(columnLookupArray[selectedIndex]);

        final Coordinate randomCoordinate = destination.getRandomCoordinate(AbitUtils.getRandomObject());
        MicroscopicLocation microDestination = new MicroscopicLocation(randomCoordinate.x, randomCoordinate.y);
        microDestination.setZone(destination);
        activity.setLocation(microDestination);

    }


    @Override
    public void selectStopDestination(Person person, Tour tour, Activity stopActivity) {
        Zone homeZone = dataSet.getZones().get(person.getHousehold().getLocation().getZoneId());
        Zone mainActZone = dataSet.getZones().get(tour.getMainActivity().getLocation().getZoneId());
        Purpose mainActPurpose = tour.getMainActivity().getPurpose();

        if (mainActPurpose.equals(Purpose.WORK)) {
            final IndexedDoubleMatrix1D workStopUtilityList = new IndexedDoubleMatrix1D(dataSet.getZones().values());
            for (Zone z : dataSet.getZones().values()) {
                final int travelDistanceInMeters = dataSet.getTravelDistances().getTravelDistanceInMeters(homeZone, z, Mode.UNKNOWN, 0.);
                final double travelDistanceInKm = travelDistanceInMeters / 1000;
                final double attractor = zoneAttractorsByPurpose.get(stopActivity.getPurpose()).get(z);
                double workStopUtility = attractor * Math.exp((coefficientsStop.get(mainActPurpose).get("ALPHA") + coefficientsStop.get(mainActPurpose).get("ALPHA_calibration")) * Math.exp((coefficientsStop.get(mainActPurpose).get("BETA") + coefficientsStop.get(mainActPurpose).get("BETA_calibration")) * travelDistanceInKm));
                workStopUtilityList.setIndexed(z.getId(), workStopUtility);
            }
            final int selectedIndex = MitoUtil.select(workStopUtilityList.toNonIndexedArray());
            Zone destination = dataSet.getZones().get(selectedIndex+1);

            final Coordinate randomCoordinate = destination.getRandomCoordinate(AbitUtils.getRandomObject());
            MicroscopicLocation microDestination = new MicroscopicLocation(randomCoordinate.x, randomCoordinate.y);
            microDestination.setZone(destination);
            stopActivity.setLocation(microDestination);
        }

        if (mainActPurpose.equals(Purpose.EDUCATION) || mainActPurpose.equals(Purpose.ACCOMPANY) || mainActPurpose.equals(Purpose.SHOPPING) || mainActPurpose.equals(Purpose.OTHER) || mainActPurpose.equals(Purpose.RECREATION)) {
            final IndexedDoubleMatrix1D othersStopUtilityList = new IndexedDoubleMatrix1D(dataSet.getZones().values());
            for (Zone z : dataSet.getZones().values()) {
                final int travelDistanceInMeters = dataSet.getTravelDistances().getTravelDistanceInMeters(mainActZone, z, Mode.UNKNOWN, 0.);
                final double travelDistanceInKm = travelDistanceInMeters / 1000;
                final double attractor = zoneAttractorsByPurpose.get(stopActivity.getPurpose()).get(z);
                double otherStopUtility = attractor * Math.exp((coefficientsStop.get(mainActPurpose).get("ALPHA") + coefficientsStop.get(mainActPurpose).get("ALPHA_calibration")) * Math.exp((coefficientsStop.get(mainActPurpose).get("BETA") + coefficientsStop.get(mainActPurpose).get("BETA_calibration")) * travelDistanceInKm));
                othersStopUtilityList.setIndexed(z.getId(), otherStopUtility);
            }
            final int selectedIndex = MitoUtil.select(othersStopUtilityList.toNonIndexedArray());
            Zone destination = dataSet.getZones().get(selectedIndex+1);

            try {
                final Coordinate randomCoordinate = destination.getRandomCoordinate(AbitUtils.getRandomObject());
                MicroscopicLocation microDestination = new MicroscopicLocation(randomCoordinate.x, randomCoordinate.y);
                microDestination.setZone(destination);
                stopActivity.setLocation(microDestination);

            } catch (Exception e) {
                System.out.println("select index: " + selectedIndex);
                System.out.println("Error destination: " + destination.getId());
            }

        }

    }


    //Old codes
//    @Override
//    public void selectStopDestination(Person person, Activity previousActivity, Activity activity, Activity followingActivity) {
//
//        Zone previousActivityZone = dataSet.getZones().get(previousActivity.getLocation().getZoneId());
//        Zone followingActivityZone = dataSet.getZones().get(followingActivity.getLocation().getZoneId());
//
//        final IndexedDoubleMatrix1D utilityfromPrevious = utilityMatrixByPurpose.get(activity.getPurpose()).viewRow(previousActivityZone.getId());
//        final IndexedDoubleMatrix1D utiliyToFollowing = utilityMatrixByPurpose.get(activity.getPurpose()).viewRow(followingActivityZone.getId());
//
//        final IndexedDoubleMatrix1D jointUtility = new IndexedDoubleMatrix1D(dataSet.getZones().values());
//
//        for (Zone z : dataSet.getZones().values()) {
//            jointUtility.setIndexed(z.getId(), utilityfromPrevious.getIndexed(z.getId()) * utiliyToFollowing.getIndexed(z.getId()));
//        }
//
//        final int selectedIndex = MitoUtil.select(jointUtility.toNonIndexedArray());
//
//        final int[] columnLookupArray = utilityMatrixByPurpose.get(activity.getPurpose()).getColumnLookupArray();
//
//        Zone destination = dataSet.getZones().get(columnLookupArray[selectedIndex]);
//
//        final Coordinate randomCoordinate = destination.getRandomCoordinate(AbitUtils.getRandomObject());
//        MicroscopicLocation microDestination = new MicroscopicLocation(randomCoordinate.x, randomCoordinate.y);
//        microDestination.setZone(destination);
//        activity.setLocation(microDestination);
//
//
//    }

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
