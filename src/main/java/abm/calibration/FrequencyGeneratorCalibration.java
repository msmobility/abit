package abm.calibration;

import abm.data.DataSet;
import abm.data.plans.*;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.models.activityGeneration.frequency.FrequencyGeneratorModel;
import abm.models.modeChoice.NestedLogitHabitualModeChoiceModel;
import de.tum.bgu.msm.data.person.Occupation;
import org.apache.log4j.Logger;


import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

import static abm.data.plans.Purpose.ACCOMPANY;


public class FrequencyGeneratorCalibration implements ModelComponent {

    //Todo define a few calibration parameters
    static Logger logger = Logger.getLogger(HabitualModeChoiceCalibration.class);
    DataSet dataSet;
    private static final int MAX_ITERATION = 2_000_000;
    private static final double TERMINATION_THRESHOLD = 0.005;
    double stepSize = 10;
    Map<Purpose, Map<Integer, Double>> objectiveMandatoryFrequencyCount = new HashMap<>();
    Map<Purpose, Map<Integer, Double>> objectiveDiscretionaryFrequencyCount = new HashMap<>();
    Map<Purpose, Map<Integer, Integer>> simulatedMandatoryFrequencyCount = new HashMap<>();
    Map<Purpose, Map<Integer, Integer>> simulatedDiscretionaryFrequencyCount = new HashMap<>();
    Map<Purpose, Map<Integer, Double>> calibrationFactors = new HashMap<>();
    private FrequencyGeneratorModel frequencyGeneratorCalibration;

    public FrequencyGeneratorCalibration(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    @Override
    public void setup() {
        //Todo: read boolean input from the property file and create the model which needs to be calibrated

        //Todo: initialize all the data containers that might be needed for calibration
        for (Purpose purpose : Purpose.getMandatoryPurposes()) {
            objectiveMandatoryFrequencyCount.putIfAbsent(purpose, new HashMap<>());
            for (int freq = 0; freq < 7; freq++) {
                objectiveMandatoryFrequencyCount.get(purpose).putIfAbsent(freq, 0.0);
            }
        }
        objectiveMandatoryFrequencyCount.putIfAbsent(Purpose.ACCOMPANY, new HashMap<>());
        for (int freq = 0; freq < 7; freq++) {
            objectiveMandatoryFrequencyCount.get(Purpose.ACCOMPANY).putIfAbsent(freq, 0.0);
        }
        for (Purpose purpose : Purpose.getDiscretionaryPurposes()) {
            if (!purpose.equals(Purpose.ACCOMPANY)) {
                objectiveDiscretionaryFrequencyCount.putIfAbsent(purpose, new HashMap<>());
                for (int freq = 0; freq < 15; freq++) {
                    objectiveDiscretionaryFrequencyCount.get(purpose).putIfAbsent(freq, 0.0);
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
        logger.info("Start calibrating the frequency of trip model......");

        //Todo: loop through the calibration process until criteria are met
        for (int iteration = 0; iteration < MAX_ITERATION; iteration++) {

            double maxDifference = 0.0;

            for (Purpose purpose : Purpose.getMandatoryPurposes()) {
                for (int frequencies = 1; frequencies <= 7; frequencies++) {
                    double observedShare = objectiveMandatoryFrequencyCount.get(purpose).get(frequencies);
                    double simulatedShare = simulatedMandatoryFrequencyCount.get(purpose).get(frequencies);
                    double difference = observedShare - simulatedShare;
                    double factor = stepSize * (observedShare - simulatedShare);
                    calibrationFactors.get(purpose).replace(frequencies, factor);
                    logger.info("Frequency of mandatory trip model for " + purpose.toString() + "\t" + " and " + frequencies + "\t" + "difference: " + difference);
                    if (Math.abs(difference) > maxDifference) {
                        maxDifference = difference;
                    }
                }
            }

                for (int frequencies = 1; frequencies <= 7; frequencies++) {
                    double observedShare = objectiveMandatoryFrequencyCount.get(Purpose.ACCOMPANY).get(frequencies);
                    double simulatedShare = simulatedMandatoryFrequencyCount.get(Purpose.ACCOMPANY).get(frequencies);
                    double difference = observedShare - simulatedShare;
                    double factor = stepSize * (observedShare - simulatedShare);
                    calibrationFactors.get(Purpose.ACCOMPANY).replace(frequencies, factor);
                    logger.info("Frequency of accompany trip model for " + Purpose.ACCOMPANY.toString() + "\t" + " and " + frequencies + "\t" + "difference: " + difference);
                    if (Math.abs(difference) > maxDifference) {
                        maxDifference = difference;
                    }
                }

            for (Purpose purpose : Purpose.getDiscretionaryPurposes()) {
                if (!purpose.equals(Purpose.ACCOMPANY)) {
                    for (int frequencies = 1; frequencies <= 15; frequencies++) {
                        double observedShare = objectiveMandatoryFrequencyCount.get(purpose).get(frequencies);
                        double simulatedShare = simulatedMandatoryFrequencyCount.get(purpose).get(frequencies);
                        double difference = observedShare - simulatedShare;
                        double factor = stepSize * (observedShare - simulatedShare);
                        calibrationFactors.get(purpose).replace(frequencies, factor);
                        logger.info("Frequency of discretionary trip model for " + purpose.toString() + "\t" + " and " + frequencies + "\t" + "difference: " + difference);
                        if (Math.abs(difference) > maxDifference) {
                            maxDifference = difference;
                        }
                    }
                }
            }


            if (maxDifference <= TERMINATION_THRESHOLD) {
                break;
            }

//            frequencyGeneratorCalibration.updateCalibrationFactor(calibrationFactors);
//            dataSet.getPersons().values().parallelStream().forEach(p -> {
//                frequencyGeneratorCalibration.calculateNumberOfActivitiesPerWeek(p); //.chooseHabitualMode() should be replaced respectively by each model
//            });

            summarizeSimulatedResult();

        }

        logger.info("Finished the calibration of habitual mode choice model.");

        //Todo: obtain the updated coefficients + calibration factors
        //Map<Purpose, Map<Integer, Double>> finalCoefficientsTable = frequencyGeneratorCalibration.obtainCoefficientsTable();

        //Todo: print the coefficients table to input folder
//        try {
//            printFinalCoefficientsTable(finalCoefficientsTable);
//        } catch (FileNotFoundException e) {
//            System.err.println("Output path of the coefficient table is not correct.");
//        }

    }

    private void readObjectiveValues() {
        objectiveMandatoryFrequencyCount.get(Purpose.WORK).put(0, 0.7);
        objectiveMandatoryFrequencyCount.get(Purpose.WORK).put(1, 3.9);
        objectiveMandatoryFrequencyCount.get(Purpose.WORK).put(2, 5.7);
        objectiveMandatoryFrequencyCount.get(Purpose.WORK).put(3, 9.0);
        objectiveMandatoryFrequencyCount.get(Purpose.WORK).put(4, 19.6);
        objectiveMandatoryFrequencyCount.get(Purpose.WORK).put(5, 53.9);
        objectiveMandatoryFrequencyCount.get(Purpose.WORK).put(6, 6.0);
        objectiveMandatoryFrequencyCount.get(Purpose.WORK).put(7, 1.2);

        objectiveMandatoryFrequencyCount.get(Purpose.EDUCATION).put(0, 8.4);
        objectiveMandatoryFrequencyCount.get(Purpose.EDUCATION).put(1, 3.4);
        objectiveMandatoryFrequencyCount.get(Purpose.EDUCATION).put(2, 5.4);
        objectiveMandatoryFrequencyCount.get(Purpose.EDUCATION).put(3, 6.3);
        objectiveMandatoryFrequencyCount.get(Purpose.EDUCATION).put(4, 14.5);
        objectiveMandatoryFrequencyCount.get(Purpose.EDUCATION).put(5, 60.6);
        objectiveMandatoryFrequencyCount.get(Purpose.EDUCATION).put(6, 1.3);
        objectiveMandatoryFrequencyCount.get(Purpose.EDUCATION).put(7, 0.1);

        objectiveMandatoryFrequencyCount.get(ACCOMPANY).put(0, 64.1);
        objectiveMandatoryFrequencyCount.get(ACCOMPANY).put(1, 16.1);
        objectiveMandatoryFrequencyCount.get(ACCOMPANY).put(2, 7.3);
        objectiveMandatoryFrequencyCount.get(ACCOMPANY).put(3, 3.7);
        objectiveMandatoryFrequencyCount.get(ACCOMPANY).put(4, 2.7);
        objectiveMandatoryFrequencyCount.get(ACCOMPANY).put(5, 4.4);
        objectiveMandatoryFrequencyCount.get(ACCOMPANY).put(6, 1.4);
        objectiveMandatoryFrequencyCount.get(ACCOMPANY).put(7, 0.3);

        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(0, 15.5);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(1, 19.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(2, 17.1);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(3, 15.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(4, 11.1);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(5, 8.4);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(6, 5.4);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(7, 3.4);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(8, 1.7);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(9, 1.2);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(10, 1.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(11, 0.3);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(12, 0.4);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(13, 0.3);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(14, 0.1);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(15, 0.1);

        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(0, 20.6);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(1, 20.9);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(2, 20.1);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(3, 14.2);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(4, 9.2);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(5, 5.8);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(6, 3.9);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(7, 1.9);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(8, 1.5);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(9, 0.8);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(10, 0.8);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(11, 0.2);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(12, 0.1);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(13, 0.2);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(14, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(15, 0.0);

        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(0, 37.2);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(1, 26.8);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(2, 15.7);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(3, 9.3);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(4, 5.2);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(5, 2.6);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(6, 1.6);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(7, 0.7);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(8, 0.3);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(9, 0.2);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(10, 0.4);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(11, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(12, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(13, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(14, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(15, 0.0);
    }

    private void summarizeSimulatedResult() {
        for (Household household : dataSet.getHouseholds().values()) {
            if (household.getSimulated()) {
                int numberworkperweek = 0;
                int numbereducationperweek = 0;
                int numberaccompanyperweek = 0;
                int numberrecreationperweek = 0;
                int numberotherperweek = 0;
                int numbershoppingperweek = 0;
                for (Person person : household.getPersons()) {
                    for (Plan plan : person.getPlan()) {
                        for (Tour tour : plan.getTours().values()) {
                            for (Activity act : tour.getActivities().values()) {
                                if (act.getPurpose().equals(Purpose.WORK)) {
                                    numberworkperweek += 1;
                                }
                                if (act.getPurpose().equals(Purpose.EDUCATION)) {
                                    numbereducationperweek += 1;
                                }
                                if (act.getPurpose().equals(Purpose.ACCOMPANY)) {
                                    numberaccompanyperweek += 1;
                                }
                                if (act.getPurpose().equals(Purpose.RECREATION)) {
                                    numberrecreationperweek += 1;
                                }
                                if (act.getPurpose().equals(Purpose.OTHER)) {
                                    numberotherperweek += 1;
                                }
                                if (act.getPurpose().equals(Purpose.SHOPPING)) {
                                    numbershoppingperweek += 1;
                                }
                            }
                        }
                    }
                }
                int simluatedfrequencyMandatoryCount = simulatedMandatoryFrequencyCount.get(Purpose.WORK).get(numberworkperweek);
                simulatedMandatoryFrequencyCount.get(Purpose.WORK).replace(numberworkperweek, simulatedMandatoryFrequencyCount.get(Purpose.WORK).get(numberworkperweek) + 1);
                simluatedfrequencyMandatoryCount = simulatedMandatoryFrequencyCount.get(Purpose.EDUCATION).get(numbereducationperweek);
                simulatedMandatoryFrequencyCount.get(Purpose.EDUCATION).replace(numbereducationperweek, simulatedMandatoryFrequencyCount.get(Purpose.EDUCATION).get(numbereducationperweek) + 1);
                simluatedfrequencyMandatoryCount = simulatedMandatoryFrequencyCount.get(Purpose.ACCOMPANY).get(numberaccompanyperweek);
                simulatedMandatoryFrequencyCount.get(Purpose.ACCOMPANY).replace(numberaccompanyperweek, simulatedMandatoryFrequencyCount.get(Purpose.ACCOMPANY).get(numberaccompanyperweek) + 1);
                int simluatedfrequencyDiscretionaryCount = simulatedDiscretionaryFrequencyCount.get(Purpose.RECREATION).get(numberrecreationperweek);
                simulatedDiscretionaryFrequencyCount.get(Purpose.RECREATION).replace(numberrecreationperweek, simulatedDiscretionaryFrequencyCount.get(Purpose.RECREATION).get(numberrecreationperweek) + 1);
                simluatedfrequencyDiscretionaryCount = simulatedDiscretionaryFrequencyCount.get(Purpose.OTHER).get(numberotherperweek);
                simulatedDiscretionaryFrequencyCount.get(Purpose.OTHER).replace(numberotherperweek, simulatedDiscretionaryFrequencyCount.get(Purpose.OTHER).get(numberotherperweek) + 1);
                simluatedfrequencyDiscretionaryCount = simulatedDiscretionaryFrequencyCount.get(Purpose.SHOPPING).get(numbershoppingperweek);
                simulatedDiscretionaryFrequencyCount.get(Purpose.SHOPPING).replace(numbershoppingperweek, simulatedDiscretionaryFrequencyCount.get(Purpose.SHOPPING).get(numbershoppingperweek) + 1);
            }
        }
    }


    private void printFinalCoefficientsTable(Map<Mode, Map<String, Double>> finalCoefficientsTable) throws FileNotFoundException {

    }
}
