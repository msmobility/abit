package abm.calibration;

import abm.data.DataSet;
import abm.data.plans.DisabilityMuc;
import abm.data.plans.HabitualMode;
import abm.data.plans.Mode;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.data.pop.RemoteWorkable;
import abm.models.modeChoice.NestedLogitHabitualModeChoiceModel;
import abm.properties.AbitResources;
import de.tum.bgu.msm.data.person.Disability;
import de.tum.bgu.msm.data.person.Occupation;
import org.apache.hadoop.hdfs.inotify.Event;
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
import java.util.stream.Collectors;

public class HabitualModeChoiceCalibration implements ModelComponent {

    static Logger logger = Logger.getLogger(HabitualModeChoiceCalibration.class);
    private static final int MAX_ITERATION = 2_000_000;
    private static final double TERMINATION_THRESHOLD = 0.02;
    double stepSize = 0.5;
    String inputFolder = AbitResources.instance.getString("habitual.mode.calibration.output");
    String habitualModeObjectivesPath = AbitResources.instance.getString("habitual.mode.calibration.objectives");
    String habitualModeAggregateObjectivesPath = AbitResources.instance.getString("habitual.mode.calibration.aggregate.objectives");
    DataSet dataSet;

    Map<Occupation, Map<RemoteWorkable, Map<DisabilityMuc, Map<HabitualMode, Double>>>> objectiveHabitualModeShare = new HashMap<>();
    Map<Occupation, Map<RemoteWorkable, Map<DisabilityMuc, Map<HabitualMode, Integer>>>> simulatedHabitualModeCount = new HashMap<>();
    Map<Occupation, Map<RemoteWorkable, Map<DisabilityMuc, Integer>>> simulatedPopCount = new HashMap<>();
    Map<Occupation, Map<RemoteWorkable, Map<DisabilityMuc, Map<HabitualMode, Double>>>> simulatedHabitualModeShare = new HashMap<>();
    Map<Occupation, Map<RemoteWorkable, Map<DisabilityMuc, Map<HabitualMode, Double>>>> calibrationFactors = new HashMap<>();

    Map<HabitualMode, Double> objectiveHabitualAggreModeShare = new HashMap<>();
    Map<HabitualMode, Integer> simulatedHabitualAggreModeCount = new HashMap<>();
    Map<HabitualMode, Double> simulatedHabitualAggreModeShare = new HashMap<>();
    private NestedLogitHabitualModeChoiceModel habitualModeChoiceCalibration;

    public HabitualModeChoiceCalibration(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    @Override
    public void setup() {
        //Todo: read boolean input from the property file and create the model which needs to be calibrated
        boolean calibrateHabitualModeChoice = Boolean.parseBoolean(AbitResources.instance.getString("habitual.mode.calibration"));
        habitualModeChoiceCalibration = new NestedLogitHabitualModeChoiceModel(dataSet, calibrateHabitualModeChoice);

        //Todo: initialize all the data containers that might be needed for calibration

        for (Occupation occupation : List.of(
                Occupation.EMPLOYED,
                Occupation.STUDENT)) {

            objectiveHabitualModeShare.putIfAbsent(occupation, new HashMap<>());
            simulatedHabitualModeCount.putIfAbsent(occupation, new HashMap<>());
            simulatedPopCount.putIfAbsent(occupation, new HashMap<>());
            simulatedHabitualModeShare.putIfAbsent(occupation, new HashMap<>());
            calibrationFactors.putIfAbsent(occupation, new HashMap<>());

            for (RemoteWorkable rw : RemoteWorkable.values()) {

                objectiveHabitualModeShare.get(occupation).putIfAbsent(rw, new HashMap<>());
                simulatedHabitualModeCount.get(occupation).putIfAbsent(rw, new HashMap<>());
                simulatedPopCount.get(occupation).putIfAbsent(rw, new HashMap<>());
                simulatedHabitualModeShare.get(occupation).putIfAbsent(rw, new HashMap<>());
                calibrationFactors.get(occupation).putIfAbsent(rw, new HashMap<>());

                for (DisabilityMuc disability : DisabilityMuc.values()) {
                    objectiveHabitualModeShare.get(occupation).get(rw).putIfAbsent(disability, new HashMap<>());
                    simulatedHabitualModeCount.get(occupation).get(rw).putIfAbsent(disability, new HashMap<>());
                    simulatedPopCount.get(occupation).get(rw).putIfAbsent(disability, 0);
                    simulatedHabitualModeShare.get(occupation).get(rw).putIfAbsent(disability, new HashMap<>());
                    calibrationFactors.get(occupation).get(rw).putIfAbsent(disability, new HashMap<>());

                    for (HabitualMode mode : HabitualMode.getHabitualModesWithoutUnknown()) {
                        objectiveHabitualModeShare.get(occupation).get(rw).get(disability).put(mode, 0.0);
                        simulatedHabitualModeCount.get(occupation).get(rw).get(disability).put(mode, 0);
                        simulatedHabitualModeShare.get(occupation).get(rw).get(disability).put(mode, 0.0);
                        calibrationFactors.get(occupation).get(rw).get(disability).put(mode, 0.0);
                    }
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

        //Todo: loop through the calibration process until criteria are met
        for (int iteration = 0; iteration < MAX_ITERATION; iteration++) {
            logger.info("Iteration......" + iteration);
            double maxDifference = 0.0;

            for (HabitualMode habitualMode : HabitualMode.getHabitualModesWithoutUnknown()) {
                double observedAggreShare = objectiveHabitualAggreModeShare.get(habitualMode);
                double simulatedAggreShare = simulatedHabitualAggreModeShare.get(habitualMode);
                double differenceAggre = observedAggreShare - simulatedAggreShare;
                logger.info("Habitual mode choice model for " + habitualMode.toString() + "\t" + "difference: " + differenceAggre);
            }

//            for (Occupation occupation : Occupation.values()) {
//                for (HabitualMode habitualMode : HabitualMode.getHabitualModes()) {
//                    double observedShare = objectiveHabitualModeShare.get(occupation).get(habitualMode);
//                    double simulatedShare = simulatedHabitualModeShare.get(occupation).get(habitualMode);
//                    double difference = observedShare - simulatedShare;
//                    double factor = stepSize * (observedShare - simulatedShare);
//                    if (habitualMode.equals(HabitualMode.CAR_DRIVER)) {
//                        factor = 0.00;
//                    }
//                    calibrationFactors.get(occupation).replace(habitualMode, factor);
//                    logger.info("Habitual mode choice model for " + occupation.toString() + "\t" + " and " + habitualMode.toString() + "\t" + "difference: " + difference);
//                    if (Math.abs(difference) > maxDifference) {
//                        maxDifference = Math.abs(difference);
//                    }
//                }
//            }

// New implementation -------------------------------------------------------------------- start

            for (Occupation occupation : List.of(Occupation.EMPLOYED, Occupation.STUDENT)) {
                for (RemoteWorkable rw : RemoteWorkable.values()) {
                    for (DisabilityMuc disability  : DisabilityMuc.values()) {
                        for (HabitualMode habitualMode : HabitualMode.getHabitualModesWithoutUnknown()) {
                            double observedShare =
                                    objectiveHabitualModeShare
                                            .get(occupation)
                                            .get(rw)
                                            .get(disability)
                                            .get(habitualMode);

                            double simulatedShare =
                                    simulatedHabitualModeShare
                                            .get(occupation)
                                            .get(rw)
                                            .get(disability)
                                            .get(habitualMode);

                            double difference = observedShare - simulatedShare;

                            double factor = stepSize * difference;

                            if (habitualMode == HabitualMode.CAR_DRIVER) {
                                factor = 0;
                            }

                            calibrationFactors
                                    .get(occupation)
                                    .get(rw)
                                    .get(disability)
                                    .put(habitualMode, factor);

                            logger.info(occupation + " " + rw + " " + disability + " " + habitualMode + " difference = " + difference);
                            maxDifference = Math.max(maxDifference, Math.abs(difference));
                        }
                    }
                }
            }
// New implementation -------------------------------------------------------------------- end

            habitualModeChoiceCalibration.updateCalibrationFactor(calibrationFactors);

            if (maxDifference <= TERMINATION_THRESHOLD) {
                break;
            }else {
                logger.info("MAX Diff: " + maxDifference);
            }

            //List<Household> simulatedHousehold = dataSet.getHouseholds().values().parallelStream().filter(Household::getSimulated).collect(Collectors.toList());
            //simulatedHousehold.forEach(household -> household.getPersons());

            dataSet.getHouseholds().values().parallelStream().filter(Household::getSimulated)
                    .flatMap(household -> household.getPersons().stream())
                    .forEach(p -> habitualModeChoiceCalibration.chooseHabitualMode(p));

            summarizeSimulatedResult();

        }

        logger.info("Finished the calibration of habitual mode choice model.");


        // make absolutely sure final shares are up-to-date
        summarizeSimulatedResult();

        // write final simulated values to csv
        try {
            Path outputPath = Path.of(habitualModeObjectivesPath)
                    .getParent()
                    .resolve("habitual_mode_simulated_newIMP.csv");

            writeSimulatedValues(outputPath.toString());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        //Todo: obtain the updated coefficients + calibration factors
        Map<HabitualMode, Map<String, Double>> finalCoefficientsTable = habitualModeChoiceCalibration.obtainCoefficientsTable();

        //Todo: print the coefficients table to input folder
        try {
            printFinalCoefficientsTable(finalCoefficientsTable);
        } catch (FileNotFoundException e) {
            System.err.println("Output path of the coefficient table is not correct.");
        }

    }

//    private void readObjectiveValues() {
//
//        objectiveHabitualAggreModeShare.put(HabitualMode.CAR_DRIVER, 0.5424);
//        objectiveHabitualAggreModeShare.put(HabitualMode.CAR_PASSENGER, 0.0341);
//        objectiveHabitualAggreModeShare.put(HabitualMode.PT, 0.2471);
//        objectiveHabitualAggreModeShare.put(HabitualMode.BIKE, 0.1270);
//        objectiveHabitualAggreModeShare.put(HabitualMode.WALK, 0.0494);
//
//        objectiveHabitualModeShare.get(Occupation.EMPLOYED).put(HabitualMode.CAR_DRIVER, 0.6476);
//        objectiveHabitualModeShare.get(Occupation.EMPLOYED).put(HabitualMode.CAR_PASSENGER, 0.0257);
//        objectiveHabitualModeShare.get(Occupation.EMPLOYED).put(HabitualMode.PT, 0.1725);
//        objectiveHabitualModeShare.get(Occupation.EMPLOYED).put(HabitualMode.BIKE, 0.1135);
//        objectiveHabitualModeShare.get(Occupation.EMPLOYED).put(HabitualMode.WALK, 0.0407);
//
//        objectiveHabitualModeShare.get(Occupation.STUDENT).put(HabitualMode.CAR_DRIVER, 0.1414);
//        objectiveHabitualModeShare.get(Occupation.STUDENT).put(HabitualMode.CAR_PASSENGER, 0.0662);
//        objectiveHabitualModeShare.get(Occupation.STUDENT).put(HabitualMode.PT, 0.5313);
//        objectiveHabitualModeShare.get(Occupation.STUDENT).put(HabitualMode.BIKE, 0.1786);
//        objectiveHabitualModeShare.get(Occupation.STUDENT).put(HabitualMode.WALK, 0.0825);
//
////        objectiveHabitualModeShare.get(Occupation.TODDLER).putIfAbsent(Mode.CAR_DRIVER, 0.00);
////        objectiveHabitualModeShare.get(Occupation.TODDLER).putIfAbsent(Mode.CAR_PASSENGER, 0.00);
////        objectiveHabitualModeShare.get(Occupation.TODDLER).putIfAbsent(Mode.BUS, 0.00);
////        objectiveHabitualModeShare.get(Occupation.TODDLER).putIfAbsent(Mode.BIKE, 0.00);
////        objectiveHabitualModeShare.get(Occupation.TODDLER).putIfAbsent(Mode.WALK, 0.00);
////
////        objectiveHabitualModeShare.get(Occupation.RETIREE).putIfAbsent(Mode.CAR_DRIVER, 0.00);
////        objectiveHabitualModeShare.get(Occupation.RETIREE).putIfAbsent(Mode.CAR_PASSENGER, 0.00);
////        objectiveHabitualModeShare.get(Occupation.RETIREE).putIfAbsent(Mode.BUS, 0.00);
////        objectiveHabitualModeShare.get(Occupation.RETIREE).putIfAbsent(Mode.BIKE, 0.00);
////        objectiveHabitualModeShare.get(Occupation.RETIREE).putIfAbsent(Mode.WALK, 0.00);
////
////        objectiveHabitualModeShare.get(Occupation.UNEMPLOYED).putIfAbsent(Mode.CAR_DRIVER, 0.00);
////        objectiveHabitualModeShare.get(Occupation.UNEMPLOYED).putIfAbsent(Mode.CAR_PASSENGER, 0.00);
////        objectiveHabitualModeShare.get(Occupation.UNEMPLOYED).putIfAbsent(Mode.BUS, 0.00);
////        objectiveHabitualModeShare.get(Occupation.UNEMPLOYED).putIfAbsent(Mode.BIKE, 0.00);
////        objectiveHabitualModeShare.get(Occupation.UNEMPLOYED).putIfAbsent(Mode.WALK, 0.00);
//    }

    private void readObjectiveValues() {

        Path path = Path.of(habitualModeObjectivesPath);
        Path aggregatePath = Path.of(habitualModeAggregateObjectivesPath);

        try (BufferedReader reader = Files.newBufferedReader(path);
        BufferedReader aggregateReader = Files.newBufferedReader(aggregatePath)) {

            reader.readLine();
            aggregateReader.readLine();

            String line;

            while ((line = reader.readLine()) != null) {

                String[] record = line.split(",");

                Occupation occupation = Occupation.valueOf(record[0].trim().toUpperCase());

                RemoteWorkable rw = RemoteWorkable.valueOf(record[1].trim().toUpperCase());

                String value = record[2].trim();

                DisabilityMuc disability = value.equals("TRUE")
                        ? DisabilityMuc.WITH
                        : DisabilityMuc.WITHOUT;

                HabitualMode mode = HabitualMode.valueOf(record[3].trim().toUpperCase());

                double share = Double.parseDouble(record[4].trim());

                objectiveHabitualModeShare
                        .get(occupation)
                        .get(rw)
                        .get(disability)
                        .put(mode, share);

                System.out.println(
                        occupation + " " + mode + " -> " + share
                );

                }
            while ((line = aggregateReader.readLine()) != null) {
                String[] record = line.split(",");

                HabitualMode mode = HabitualMode.valueOf(record[0].trim().toUpperCase());

                double share = Double.parseDouble(record[1].trim());

                objectiveHabitualAggreModeShare.put(mode, share);

                System.out.println(
                        "Aggregate: " + mode + " -> " + share
                );

            }

        } catch (IOException e) {
            throw new RuntimeException(
                    "Could not read habitual mode objective file: " + path, e);
        }
    }

    private void summarizeSimulatedResult() {

//        for (Occupation occupation : Occupation.values()) {
//            simulatedPopCount.put(occupation, 0);
//            for (HabitualMode habitualMode : HabitualMode.getHabitualModes()) {
//                simulatedHabitualModeCount.get(occupation).put(habitualMode, 0);
//                simulatedHabitualModeShare.get(occupation).put(habitualMode, 0.0);
//                simulatedHabitualAggreModeCount.put(habitualMode, 0);
//                simulatedHabitualAggreModeShare.put(habitualMode, 0.0);
//            }
//        }

// New implementation -------------------------------------------------------------------- start
        for (Occupation occupation : List.of(Occupation.EMPLOYED, Occupation.STUDENT)) {
            for (RemoteWorkable rw : RemoteWorkable.values()) {
                for (DisabilityMuc disability : DisabilityMuc.values()) {
                    simulatedPopCount.get(occupation).get(rw).put(disability, 0);
                    for (HabitualMode mode : HabitualMode.getHabitualModesWithoutUnknown()) {
                        simulatedHabitualModeCount.get(occupation).get(rw).get(disability).put(mode, 0);
                        simulatedHabitualModeShare.get(occupation).get(rw).get(disability).put(mode, 0.0);
                        simulatedHabitualAggreModeCount.put(mode, 0);
                        simulatedHabitualAggreModeShare.put(mode, 0.0);

                    }
                }
            }
        }
// New implementation -------------------------------------------------------------------- end

//        for (Household household : dataSet.getHouseholds().values()) {
//            if (household.getSimulated()) {
//                for (Person person : household.getPersons()) {
//                    if (person.getAge() >= 10) {
//                        if (person.getOccupation().equals(Occupation.EMPLOYED) || person.getOccupation().equals(Occupation.STUDENT)) {
//                            int modeCount = simulatedHabitualModeCount.get(person.getOccupation()).get(person.getHabitualMode());
//                            simulatedHabitualModeCount.get(person.getOccupation()).replace(person.getHabitualMode(), modeCount + 1);
//                            int popCount = simulatedPopCount.get(person.getOccupation());
//                            simulatedPopCount.replace(person.getOccupation(), popCount + 1);
//                            int modeAggreCount = simulatedHabitualAggreModeCount.get(person.getHabitualMode());
//                            simulatedHabitualAggreModeCount.replace(person.getHabitualMode(), modeAggreCount + 1);
//                        }
//                    }
//                }
//            }
//        }


        // New implementation -------------------------------------------------------------------- start
        for (Household household : dataSet.getHouseholds().values()) {
            if (household.getSimulated()) {
                for (Person person : household.getPersons()) {
                    if (person.getAge() >= 10) {
                        if (person.getOccupation().equals(Occupation.EMPLOYED) || person.getOccupation().equals(Occupation.STUDENT)) {
                            Occupation occupation = person.getOccupation();
                            RemoteWorkable rw = getRemoteWorkable(person);
                            DisabilityMuc disability = hasDisability(person);
                            int modeCount = simulatedHabitualModeCount.get(occupation).get(rw).get(disability).get(person.getHabitualMode());
                            simulatedHabitualModeCount.get(occupation).get(rw).get(disability).replace(person.getHabitualMode(), modeCount + 1);
                            int popCount = simulatedPopCount.get(occupation).get(rw).get(disability);
                            simulatedPopCount.get(occupation).get(rw).replace(disability, popCount + 1);
                            int modeAggreCount = simulatedHabitualAggreModeCount.get(person.getHabitualMode());
                            simulatedHabitualAggreModeCount.replace(person.getHabitualMode(), modeAggreCount + 1);
                        }
                    }
                }
            }
        }
        // New implementation -------------------------------------------------------------------- end


//        for (Occupation occupation : Occupation.values()) {
//            if (occupation.equals(Occupation.EMPLOYED) || occupation.equals(Occupation.STUDENT)) {
//                int popCount = simulatedPopCount.get(occupation);
//                int modeCount;
//                for (HabitualMode habitualMode : HabitualMode.getHabitualModes()) {
//                    modeCount = simulatedHabitualModeCount.get(occupation).get(habitualMode);
//                    double modeShare = (double) modeCount / popCount;
//                    simulatedHabitualModeShare.get(occupation).replace(habitualMode, modeShare);
//                }
//            }
//        }

        // New implementation -------------------------------------------------------------------- start
        for (Occupation occupation : List.of(Occupation.EMPLOYED, Occupation.STUDENT)) {
            for (RemoteWorkable rw : RemoteWorkable.values()) {
                for (DisabilityMuc disability : DisabilityMuc.values()) {
                    int popCount = simulatedPopCount.get(occupation).get(rw).get(disability);
                    if (popCount == 0) {continue;
                    }
                    for (HabitualMode mode : HabitualMode.getHabitualModesWithoutUnknown()) {
                        int modeCount = simulatedHabitualModeCount.get(occupation).get(rw).get(disability).get(mode);
                        double modeShare = (double) modeCount / popCount;
                        simulatedHabitualModeShare.get(occupation).get(rw).get(disability).replace(mode, modeShare);
                    }
                }
            }
        }
        // New implementation -------------------------------------------------------------------- end


        for (HabitualMode habitualMode : HabitualMode.getHabitualModesWithoutUnknown()) {
//            int popCount = simulatedPopCount.get(Occupation.EMPLOYED) + simulatedPopCount.get(Occupation.STUDENT);
            int popCount = 0;
            for (Occupation occupation : List.of(Occupation.EMPLOYED, Occupation.STUDENT)) {
                for (RemoteWorkable rw : RemoteWorkable.values()) {
                    for (DisabilityMuc disability : DisabilityMuc.values()) {
                        popCount += simulatedPopCount.get(occupation).get(rw).get(disability);
                    }
                }
            }
            int modeCount = simulatedHabitualAggreModeCount.get(habitualMode);
            double modeShare = (double) modeCount / popCount;
            simulatedHabitualAggreModeShare.replace(habitualMode, modeShare);
        }




    }





     // temporary method--
//    private void writeSimulatedValues(String fileName)
//            throws FileNotFoundException {
//
//        PrintWriter pw = new PrintWriter(fileName);
//
//        pw.println("occupation,mode,simulatedShare");
//
//        for (Occupation occupation : Occupation.values()) {
//
//            if (!(occupation == Occupation.EMPLOYED ||
//                    occupation == Occupation.STUDENT))
//                continue;
//
//            for (HabitualMode mode : HabitualMode.getHabitualModes()) {
//
//                pw.println(
//                        occupation + "," +
//                                mode + "," +
//                                simulatedHabitualModeShare
//                                        .get(occupation)
//                                        .get(mode)
//                );
//            }
//        }
//
//        pw.close();
//    }

    // New implementation -------------------------------------------------------------------- start
    private void writeSimulatedValues(String fileName) throws FileNotFoundException {
        PrintWriter pw = new PrintWriter(fileName);
        pw.println("occupation,remote_work_allowance," + "disability,mode,simulatedShare");
        for (Occupation occupation : List.of(Occupation.EMPLOYED, Occupation.STUDENT)) {
            for (RemoteWorkable rw : RemoteWorkable.values()) {
                for (DisabilityMuc disability : DisabilityMuc.values()) {
                    for (HabitualMode mode : HabitualMode.getHabitualModesWithoutUnknown()) {
                        boolean disabilityBoolean = disability == DisabilityMuc.WITH;
                        pw.println(occupation + "," + rw + "," + (disabilityBoolean ? "TRUE" : "FALSE")  + "," + mode + "," + simulatedHabitualModeShare.get(occupation).get(rw).get(disability).get(mode));
                    }
                }
            }
        }
        pw.close();
    }
    // New implementation -------------------------------------------------------------------- end
    // -- temporary method

    private void printFinalCoefficientsTable(Map<HabitualMode, Map<String, Double>> finalCoefficientsTable) throws FileNotFoundException {
        logger.info("Writing habitual mode choice coefficient + calibration factors: " + inputFolder);
        PrintWriter pw = new PrintWriter(inputFolder);

        StringBuilder header = new StringBuilder("variable");
        for (HabitualMode habitualMode : HabitualMode.getHabitualModesWithoutUnknown()) {
            header.append(",");
            header.append(habitualMode);
        }
        pw.println(header);

        for (String variableNames : finalCoefficientsTable.get(HabitualMode.PT).keySet()) {
            StringBuilder line = new StringBuilder(variableNames);
            for (HabitualMode habitualMode : HabitualMode.getHabitualModesWithoutUnknown()) {
                if (variableNames.equals("calibration_retiree") || variableNames.equals("calibration_toddler") || variableNames.equals("calibration_unemployed")) {
                    line.append(",");
                    line.append(0);
                } else {
                    line.append(",");
                    line.append(finalCoefficientsTable.get(habitualMode).get(variableNames));
                }
            }
            pw.println(line);
        }
        pw.close();
    }

    private DisabilityMuc hasDisability(Person person) {
        return person.getDisability() == Disability.WITHOUT
                ? DisabilityMuc.WITHOUT
                : DisabilityMuc.WITH;
    }

    private RemoteWorkable getRemoteWorkable(Person person) {

        return person.canRemoteWork()
                ? RemoteWorkable.TRUE
                : RemoteWorkable.FALSE;
    }
}
