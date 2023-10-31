package abm.calibration;

import abm.data.DataSet;
import abm.data.plans.Mode;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.models.modeChoice.NestedLogitHabitualModeChoiceModel;
import abm.properties.AbitResources;
import de.tum.bgu.msm.data.person.Occupation;
import org.apache.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

public class HabitualModeChoiceCalibration implements ModelComponent {

    //Todo define a few calibration parameters
    static Logger logger = Logger.getLogger(HabitualModeChoiceCalibration.class);
    private static final int MAX_ITERATION = 2_000_000;
    private static final double TERMINATION_THRESHOLD = 0.005;
    double stepSize = 10;
    String inputFolder = AbitResources.instance.getString("habitual.mode.calibration.output");
    DataSet dataSet;
    Map<Occupation, Map<Mode, Double>> objectiveHabitualModeShare = new HashMap<>();
    Map<Occupation, Map<Mode, Integer>> simulatedHabitualModeCount = new HashMap<>();
    Map<Occupation, Integer> simulatedPopCount = new HashMap<>();
    Map<Occupation, Map<Mode, Double>> simulatedHabitualModeShare = new HashMap<>();
    Map<Occupation, Map<Mode, Double>> calibrationFactors = new HashMap<>();
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
        for (Occupation occupation : Occupation.values()) {
            objectiveHabitualModeShare.putIfAbsent(occupation, new HashMap<>());
            simulatedHabitualModeCount.putIfAbsent(occupation, new HashMap<>());
            simulatedPopCount.putIfAbsent(occupation, 0);
            simulatedHabitualModeShare.putIfAbsent(occupation, new HashMap<>());
            calibrationFactors.putIfAbsent(occupation, new HashMap<>());

            for (Mode mode : Mode.getHabitualModes()) {
                objectiveHabitualModeShare.get(occupation).putIfAbsent(mode, 0.0);
                simulatedHabitualModeCount.get(occupation).putIfAbsent(mode, 0);
                simulatedHabitualModeShare.get(occupation).putIfAbsent(mode, 0.0);
                calibrationFactors.get(occupation).putIfAbsent(mode, 0.0);
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

            double maxDifference = 0.0;

            for (Occupation occupation : Occupation.values()) {
                for (Mode mode : Mode.getHabitualModes()) {
                    double observedShare = objectiveHabitualModeShare.get(occupation).get(mode);
                    double simulatedShare = simulatedHabitualModeShare.get(occupation).get(mode);
                    double difference = observedShare - simulatedShare;
                    double factor = stepSize * (observedShare - simulatedShare);
                    if (mode.equals(Mode.CAR_DRIVER)){
                        factor = 0.00;
                    }
                    calibrationFactors.get(occupation).replace(mode, factor);
                    logger.info("Habitual mode choice model for " + occupation.toString() + "\t" + " and " + mode.toString() + "\t" + "difference: " + difference);
                    if (Math.abs(difference) > maxDifference) {
                        maxDifference = difference;
                    }
                }
            }

            if (maxDifference <= TERMINATION_THRESHOLD) {
                break;
            }

            habitualModeChoiceCalibration.updateCalibrationFactor(calibrationFactors);
            dataSet.getPersons().values().parallelStream().forEach(p -> {
                habitualModeChoiceCalibration.chooseHabitualMode(p); //.chooseHabitualMode() should be replaced respectively by each model
            });

            summarizeSimulatedResult();

        }

        logger.info("Finished the calibration of habitual mode choice model.");

        //Todo: obtain the updated coefficients + calibration factors
        Map<Mode, Map<String, Double>> finalCoefficientsTable = habitualModeChoiceCalibration.obtainCoefficientsTable();

        //Todo: print the coefficients table to input folder
        try{
            printFinalCoefficientsTable(finalCoefficientsTable);
        }catch(FileNotFoundException e){
            System.err.println("Output path of the coefficient table is not correct.");
        }

    }

    private void readObjectiveValues() {
        //Todo: Objective values of habitual mode share, which is going to be calibrated by mode and by employment status; should read from input and need to be in a loop
        objectiveHabitualModeShare.get(Occupation.EMPLOYED).putIfAbsent(Mode.CAR_DRIVER, 0.00);
        objectiveHabitualModeShare.get(Occupation.EMPLOYED).putIfAbsent(Mode.CAR_PASSENGER, 0.00);
        objectiveHabitualModeShare.get(Occupation.EMPLOYED).putIfAbsent(Mode.BUS, 0.00);
        objectiveHabitualModeShare.get(Occupation.EMPLOYED).putIfAbsent(Mode.BIKE, 0.00);
        objectiveHabitualModeShare.get(Occupation.EMPLOYED).putIfAbsent(Mode.WALK, 0.00);

        objectiveHabitualModeShare.get(Occupation.STUDENT).putIfAbsent(Mode.CAR_DRIVER, 0.00);
        objectiveHabitualModeShare.get(Occupation.STUDENT).putIfAbsent(Mode.CAR_PASSENGER, 0.00);
        objectiveHabitualModeShare.get(Occupation.STUDENT).putIfAbsent(Mode.BUS, 0.00);
        objectiveHabitualModeShare.get(Occupation.STUDENT).putIfAbsent(Mode.BIKE, 0.00);
        objectiveHabitualModeShare.get(Occupation.STUDENT).putIfAbsent(Mode.WALK, 0.00);

        objectiveHabitualModeShare.get(Occupation.TODDLER).putIfAbsent(Mode.CAR_DRIVER, 0.00);
        objectiveHabitualModeShare.get(Occupation.TODDLER).putIfAbsent(Mode.CAR_PASSENGER, 0.00);
        objectiveHabitualModeShare.get(Occupation.TODDLER).putIfAbsent(Mode.BUS, 0.00);
        objectiveHabitualModeShare.get(Occupation.TODDLER).putIfAbsent(Mode.BIKE, 0.00);
        objectiveHabitualModeShare.get(Occupation.TODDLER).putIfAbsent(Mode.WALK, 0.00);

        objectiveHabitualModeShare.get(Occupation.RETIREE).putIfAbsent(Mode.CAR_DRIVER, 0.00);
        objectiveHabitualModeShare.get(Occupation.RETIREE).putIfAbsent(Mode.CAR_PASSENGER, 0.00);
        objectiveHabitualModeShare.get(Occupation.RETIREE).putIfAbsent(Mode.BUS, 0.00);
        objectiveHabitualModeShare.get(Occupation.RETIREE).putIfAbsent(Mode.BIKE, 0.00);
        objectiveHabitualModeShare.get(Occupation.RETIREE).putIfAbsent(Mode.WALK, 0.00);

        objectiveHabitualModeShare.get(Occupation.UNEMPLOYED).putIfAbsent(Mode.CAR_DRIVER, 0.00);
        objectiveHabitualModeShare.get(Occupation.UNEMPLOYED).putIfAbsent(Mode.CAR_PASSENGER, 0.00);
        objectiveHabitualModeShare.get(Occupation.UNEMPLOYED).putIfAbsent(Mode.BUS, 0.00);
        objectiveHabitualModeShare.get(Occupation.UNEMPLOYED).putIfAbsent(Mode.BIKE, 0.00);
        objectiveHabitualModeShare.get(Occupation.UNEMPLOYED).putIfAbsent(Mode.WALK, 0.00);
    }

    private void summarizeSimulatedResult() {

        for (Household household : dataSet.getHouseholds().values()) {
            if (household.getSimulated()) {
                for (Person person : household.getPersons()) {
                    int modeCount = simulatedHabitualModeCount.get(person.getOccupation()).get(person.getHabitualMode());
                    simulatedHabitualModeCount.get(person.getOccupation()).replace(person.getHabitualMode(), modeCount + 1);
                    int popCount = simulatedPopCount.get(person.getOccupation());
                    simulatedPopCount.replace(person.getOccupation(), popCount + 1);
                }
            }
        }

        for (Occupation occupation : Occupation.values()) {
            int popCount = simulatedPopCount.get(occupation);
            int modeCount;

            for (Mode mode : Mode.getHabitualModes()) {
                modeCount = simulatedHabitualModeCount.get(occupation).get(mode);
                double modeShare = (double) modeCount / popCount;
                simulatedHabitualModeShare.get(occupation).replace(mode, modeShare);
            }
        }
    }

    private void printFinalCoefficientsTable(Map<Mode, Map<String, Double>> finalCoefficientsTable) throws FileNotFoundException {
        logger.info("Writing habitual mode choice coefficient + calibration factors: " + inputFolder);
        PrintWriter pw = new PrintWriter(inputFolder);

        StringBuilder header = new StringBuilder("variable");
        for (Mode mode : Mode.getModes()){
            header.append(",");
            header.append(mode);
        }
        pw.println(header);

        for (String variableNames : finalCoefficientsTable.get(Mode.BUS).keySet()){
            StringBuilder line = new StringBuilder(variableNames);
            for (Mode mode : Mode.getModes()){
                if (mode.equals(Mode.TRAIN) || mode.equals(Mode.TRAM_METRO)){
                    line.append(",");
                    line.append(finalCoefficientsTable.get(Mode.BUS).get(variableNames));
                }else {
                    line.append(",");
                    line.append(finalCoefficientsTable.get(mode).get(variableNames));
                }
            }
            pw.println(line);
        }
        pw.close();
    }
}
