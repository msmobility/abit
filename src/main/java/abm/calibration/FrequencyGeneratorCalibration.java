package abm.calibration;

import abm.data.DataSet;
import abm.data.plans.*;
import abm.data.pop.Household;
import abm.data.pop.Person;


import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

import static abm.data.plans.Purpose.ACCOMPANY;


public class FrequencyGeneratorCalibration implements ModelComponent{

    //Todo define a few calibration parameters
    DataSet dataSet;
    Map<Purpose, Map<Integer, Double>> objectiveMandatoryFrequencyCount = new HashMap<>();
    Map<Purpose, Map<Integer, Double>> objectiveAccompanyFrequencyCount = new HashMap<>();
    Map<Purpose, Map<Integer, Double>> objectiveDiscretionaryFrequencyCount = new HashMap<>();

    Map<Purpose, Map<Integer, Integer>> simulatedFrequencyCount = new HashMap<>();

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
            if(!purpose.equals(Purpose.ACCOMPANY)) {
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

        //Todo: loop through the calibration process until criteria are met



        //Todo: obtain the updated coefficients + calibration factors


        //Todo: print the coefficients table to input folder

    }

    private void readObjectiveValues() {
        objectiveMandatoryFrequencyCount.get(Purpose.WORK).put(0, 0.0);
        objectiveMandatoryFrequencyCount.get(Purpose.WORK).put(1, 0.0);
        objectiveMandatoryFrequencyCount.get(Purpose.WORK).put(2, 0.0);
        objectiveMandatoryFrequencyCount.get(Purpose.WORK).put(3, 0.0);
        objectiveMandatoryFrequencyCount.get(Purpose.WORK).put(4, 0.0);
        objectiveMandatoryFrequencyCount.get(Purpose.WORK).put(5, 0.0);
        objectiveMandatoryFrequencyCount.get(Purpose.WORK).put(6, 0.0);
        objectiveMandatoryFrequencyCount.get(Purpose.WORK).put(7, 0.0);

        objectiveMandatoryFrequencyCount.get(Purpose.EDUCATION).put(0, 0.0);
        objectiveMandatoryFrequencyCount.get(Purpose.EDUCATION).put(1, 0.0);
        objectiveMandatoryFrequencyCount.get(Purpose.EDUCATION).put(2, 0.0);
        objectiveMandatoryFrequencyCount.get(Purpose.EDUCATION).put(3, 0.0);
        objectiveMandatoryFrequencyCount.get(Purpose.EDUCATION).put(4, 0.0);
        objectiveMandatoryFrequencyCount.get(Purpose.EDUCATION).put(5, 0.0);
        objectiveMandatoryFrequencyCount.get(Purpose.EDUCATION).put(6, 0.0);
        objectiveMandatoryFrequencyCount.get(Purpose.EDUCATION).put(7, 0.0);

        objectiveMandatoryFrequencyCount.get(ACCOMPANY).put(0, 0.0);
        objectiveMandatoryFrequencyCount.get(ACCOMPANY).put(1, 0.0);
        objectiveMandatoryFrequencyCount.get(ACCOMPANY).put(2, 0.0);
        objectiveMandatoryFrequencyCount.get(ACCOMPANY).put(3, 0.0);
        objectiveMandatoryFrequencyCount.get(ACCOMPANY).put(4, 0.0);
        objectiveMandatoryFrequencyCount.get(ACCOMPANY).put(5, 0.0);
        objectiveMandatoryFrequencyCount.get(ACCOMPANY).put(6, 0.0);
        objectiveMandatoryFrequencyCount.get(ACCOMPANY).put(7, 0.0);

        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(0, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(1, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(2, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(3, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(4, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(5, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(6, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(7, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(8, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(9, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(10, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(11, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(12, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(13, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(14, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.RECREATION).put(15, 0.0);

        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(0, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(1, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(2, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(3, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(4, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(5, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(6, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(7, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(8, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(9, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(10, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(11, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(12, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(13, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(14, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.SHOPPING).put(15, 0.0);

        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(0, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(1, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(2, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(3, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(4, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(5, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(6, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(7, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(8, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(9, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(10, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(11, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(12, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(13, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(14, 0.0);
        objectiveDiscretionaryFrequencyCount.get(Purpose.OTHER).put(15, 0.0);
    }

    private void summarizeSimulatedResult() {
        for (Household household : dataSet.getHouseholds().values()){
            for (Person person : household.getPersons()){
                for (Plan plan : person.getPlan()){
                    for (Tour tour : plan.getTours().values()){
                        for (Activity act: tour.getActivities().values()){

                        }
                    }
                }
            }
        }

    }

    private void printFinalCoefficientsTable(Map<Mode, Map<String, Double>> finalCoefficientsTable) throws FileNotFoundException {

    }
}
