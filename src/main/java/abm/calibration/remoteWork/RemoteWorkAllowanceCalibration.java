package abm.calibration.remoteWork;

import abm.calibration.ModelComponent;
import abm.data.DataSet;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.data.pop.Relationship;
import abm.models.remoteWorkArrangement.HouseholdType;
import abm.models.remoteWorkArrangement.RemoteWorkAllowanceMultinomialLogitModel;
import abm.models.remoteWorkArrangement.TeleworkAlternative;
import abm.properties.AbitResources;
import de.tum.bgu.msm.data.person.Gender;
import de.tum.bgu.msm.data.person.Occupation;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Calibrates {@link RemoteWorkAllowanceMultinomialLogitModel} - the household-level choice of
 * who (if anyone) is arranged to work remotely - against observed shares of each
 * {@link TeleworkAlternative} per {@link HouseholdType}. Segmented by household type only (not
 * further by occupation/remote-workable/disability, unlike the activity-frequency calibration
 * classes): each household type already has its own coefficient table and alternative set in the
 * underlying model, and that is the natural, and only, segmentation this choice is made at.
 */
public class RemoteWorkAllowanceCalibration implements ModelComponent {

    static Logger logger = Logger.getLogger(RemoteWorkAllowanceCalibration.class);
    DataSet dataSet;
    private static final int MAX_ITERATION = 2_000;
    private static final double TERMINATION_THRESHOLD = 0.02;
    double stepSize = 0.5;

    private static final List<HouseholdType> CALIBRATED_TYPES = List.of(
            HouseholdType.PARTNERED_DUAL_EARNER,
            HouseholdType.PARTNERED_SINGLE_EARNER_MALE,
            HouseholdType.PARTNERED_SINGLE_EARNER_FEMALE,
            HouseholdType.SINGLE_WORKER);

    Map<HouseholdType, Map<TeleworkAlternative, Double>> objectiveShare = new HashMap<>();
    Map<HouseholdType, Map<TeleworkAlternative, Integer>> simulatedCount = new HashMap<>();
    Map<HouseholdType, Map<TeleworkAlternative, Double>> simulatedShare = new HashMap<>();
    Map<HouseholdType, Map<TeleworkAlternative, Double>> calibrationFactors = new HashMap<>();

    private RemoteWorkAllowanceMultinomialLogitModel remoteWorkAllowanceModel;

    String objectivesPath = AbitResources.instance.getString("remoteWork.allowance.calibration.objectives");
    String outputPath = AbitResources.instance.getString("remoteWork.allowance.calibration.output");

    public RemoteWorkAllowanceCalibration(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    @Override
    public void setup() {
        boolean calibrateRemoteWorkAllowance = Boolean.parseBoolean(AbitResources.instance.getString("remoteWork.allowance.calibration"));
        remoteWorkAllowanceModel = new RemoteWorkAllowanceMultinomialLogitModel(dataSet, calibrateRemoteWorkAllowance);

        for (HouseholdType type : CALIBRATED_TYPES) {
            objectiveShare.putIfAbsent(type, new HashMap<>());
            simulatedCount.putIfAbsent(type, new HashMap<>());
            simulatedShare.putIfAbsent(type, new HashMap<>());
            calibrationFactors.putIfAbsent(type, new HashMap<>());
            for (TeleworkAlternative alt : TeleworkAlternative.values()) {
                objectiveShare.get(type).putIfAbsent(alt, 0.0);
                simulatedCount.get(type).putIfAbsent(alt, 0);
                simulatedShare.get(type).putIfAbsent(alt, 0.0);
                calibrationFactors.get(type).putIfAbsent(alt, 0.0);
            }
        }
    }

    @Override
    public void load() {
        readObjectiveValues();
        summarizeSimulatedResult();
    }

    @Override
    public void run() {
        logger.info("Start calibrating the remote work allowance model......");

        for (int iteration = 0; iteration < MAX_ITERATION; iteration++) {
            logger.info("Iteration......" + iteration);
            double maxDifference = 0.0;

            for (HouseholdType type : CALIBRATED_TYPES) {
                for (TeleworkAlternative alt : TeleworkAlternative.values()) {
                    double observed = objectiveShare.get(type).get(alt);
                    double simulated = simulatedShare.get(type).get(alt);
                    double difference = observed - simulated;
                    // NO_ONE is the reference alternative - held fixed at 0 so the utility
                    // differences (the only thing a logit model's probabilities depend on) are
                    // well-determined; only the other alternatives' ASC-style factors move.
                    double factor = alt == TeleworkAlternative.NO_ONE ? 0.0 : stepSize * difference;
                    calibrationFactors.get(type).put(alt, factor);
                    logger.info(type + " | " + alt + " diff = " + difference);
                    maxDifference = Math.max(maxDifference, Math.abs(difference));
                }
            }
            remoteWorkAllowanceModel.updateCalibrationFactor(calibrationFactors);

            if (maxDifference <= TERMINATION_THRESHOLD) {
                break;
            } else {
                logger.info("MAX Diff: " + maxDifference);
            }

            dataSet.getHouseholds().values().stream()
                    .filter(Household::getSimulated)
                    .filter(hh -> CALIBRATED_TYPES.contains(hh.getHouseholdType()))
                    .forEach(remoteWorkAllowanceModel::assignRemoteWorkAllowance);

            summarizeSimulatedResult();
        }

        logger.info("Finished the calibration of the remote work allowance model.");

        // make absolutely sure final shares are up-to-date
        summarizeSimulatedResult();

        try {
            writeSimulatedValues();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        Map<HouseholdType, Map<TeleworkAlternative, Double>> finalCoefficientsTable = remoteWorkAllowanceModel.obtainCoefficientsTable();

        try {
            printFinalCoefficientsTable(finalCoefficientsTable);
        } catch (FileNotFoundException e) {
            System.err.println("Output path of the coefficient table is not correct.");
        }
    }

    private void readObjectiveValues() {
        Path path = Path.of(objectivesPath);
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            reader.readLine(); // header
            String line;
            while ((line = reader.readLine()) != null) {
                String[] record = line.split(",");
                HouseholdType type = HouseholdType.valueOf(record[0].trim().toUpperCase());
                TeleworkAlternative alt = TeleworkAlternative.valueOf(record[1].trim().toUpperCase());
                double share = Double.parseDouble(record[2].trim());
                objectiveShare.get(type).put(alt, share);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not read remote work allowance objective file: " + path, e);
        }
    }

    private void summarizeSimulatedResult() {
        for (HouseholdType type : CALIBRATED_TYPES) {
            for (TeleworkAlternative alt : TeleworkAlternative.values()) {
                simulatedCount.get(type).put(alt, 0);
            }
        }

        for (Household household : dataSet.getHouseholds().values()) {
            if (!household.getSimulated()) {
                continue;
            }
            HouseholdType type = household.getHouseholdType();
            if (!CALIBRATED_TYPES.contains(type)) {
                continue;
            }
            TeleworkAlternative chosen = inferChosenAlternative(household, type);
            if (chosen == null) {
                continue;
            }
            simulatedCount.get(type).merge(chosen, 1, Integer::sum);
        }

        for (HouseholdType type : CALIBRATED_TYPES) {
            int total = 0;
            for (TeleworkAlternative alt : TeleworkAlternative.values()) {
                total += simulatedCount.get(type).get(alt);
            }
            if (total == 0) {
                continue;
            }
            for (TeleworkAlternative alt : TeleworkAlternative.values()) {
                double share = simulatedCount.get(type).get(alt) / (double) total;
                simulatedShare.get(type).put(alt, share);
            }
        }
    }

    /**
     * Reconstructs which TeleworkAlternative a household's assignRemoteWorkAllowance() call
     * effectively chose, by reading back the resulting canRemoteWork() flags on the relevant
     * household members - the model itself only records the boolean outcome per person, not the
     * discrete alternative that produced it. Returns null if this household doesn't actually
     * match the profile its HouseholdType implies (mirrors the model's own defensive checks).
     */
    private TeleworkAlternative inferChosenAlternative(Household household, HouseholdType type) {
        switch (type) {
            case PARTNERED_DUAL_EARNER -> {
                Optional<Person> male = household.getPersons().stream().filter(p -> p.getRelationship() == Relationship.married && p.getGender() == Gender.MALE && p.getOccupation() == Occupation.EMPLOYED).findFirst();
                Optional<Person> female = household.getPersons().stream().filter(p -> p.getRelationship() == Relationship.married && p.getGender() == Gender.FEMALE && p.getOccupation() == Occupation.EMPLOYED).findFirst();
                if (male.isEmpty() || female.isEmpty()) {
                    return null;
                }
                boolean m = male.get().canRemoteWork();
                boolean f = female.get().canRemoteWork();
                if (m && f) {
                    return TeleworkAlternative.BOTH;
                }
                if (m) {
                    return TeleworkAlternative.ONLY_MALE;
                }
                if (f) {
                    return TeleworkAlternative.ONLY_FEMALE;
                }
                return TeleworkAlternative.NO_ONE;
            }
            case PARTNERED_SINGLE_EARNER_MALE -> {
                Optional<Person> male = household.getPersons().stream().filter(p -> p.getRelationship() == Relationship.married && p.getGender() == Gender.MALE && p.getOccupation() == Occupation.EMPLOYED).findFirst();
                if (male.isEmpty()) {
                    return null;
                }
                return male.get().canRemoteWork() ? TeleworkAlternative.ONLY_MALE : TeleworkAlternative.NO_ONE;
            }
            case PARTNERED_SINGLE_EARNER_FEMALE -> {
                Optional<Person> female = household.getPersons().stream().filter(p -> p.getRelationship() == Relationship.married && p.getGender() == Gender.FEMALE && p.getOccupation() == Occupation.EMPLOYED).findFirst();
                if (female.isEmpty()) {
                    return null;
                }
                return female.get().canRemoteWork() ? TeleworkAlternative.ONLY_FEMALE : TeleworkAlternative.NO_ONE;
            }
            case SINGLE_WORKER -> {
                if (household.getPersons().isEmpty()) {
                    return null;
                }
                Person p = household.getPersons().get(0);
                if (p.getOccupation() != Occupation.EMPLOYED) {
                    return null;
                }
                if (!p.canRemoteWork()) {
                    return TeleworkAlternative.NO_ONE;
                }
                return p.getGender() == Gender.MALE ? TeleworkAlternative.ONLY_MALE : TeleworkAlternative.ONLY_FEMALE;
            }
            default -> {
                return null;
            }
        }
    }

    private void writeSimulatedValues() throws FileNotFoundException {
        Path outPath = Path.of(objectivesPath).getParent().resolve("remote_work_allowance_simulated.csv");
        PrintWriter pw = new PrintWriter(outPath.toString());
        pw.println("household_type,alternative,share");
        for (HouseholdType type : CALIBRATED_TYPES) {
            for (TeleworkAlternative alt : TeleworkAlternative.values()) {
                pw.println(type + "," + alt + "," + simulatedShare.get(type).get(alt));
            }
        }
        pw.close();
    }

    private void printFinalCoefficientsTable(Map<HouseholdType, Map<TeleworkAlternative, Double>> finalCoefficientsTable) throws FileNotFoundException {
        logger.info("Writing remote work allowance calibration factors: " + outputPath);
        PrintWriter pw = new PrintWriter(outputPath);
        pw.println("household_type,alternative,calibration_factor");
        for (HouseholdType type : CALIBRATED_TYPES) {
            for (TeleworkAlternative alt : TeleworkAlternative.values()) {
                pw.println(type + "," + alt + "," + finalCoefficientsTable.get(type).get(alt));
            }
        }
        pw.close();
    }
}
