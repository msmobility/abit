package abm.calibration;

import abm.data.DataSet;

import abm.properties.AbitResources;

import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;


public class CalibrationMuc {

    static Logger logger = Logger.getLogger(CalibrationMuc.class);
    DataSet dataSet;

    //Todo add booleans for all the models that need to be calibrated here:
    boolean calibrateHabitualModeChoice;
    //Todo add all the calibration class here
    private HabitualModeChoiceCalibration habitualModeChoiceCalibration;

    private final Map<String, Boolean> calibrationList = new HashMap<>();

    public CalibrationMuc(DataSet dataSet) {
        this.dataSet = dataSet;
        //Todo read all the boolean properties here and add them to the map
        calibrateHabitualModeChoice = Boolean.parseBoolean(AbitResources.instance.getString("habitual.mode.calibration"));
        calibrationList.put("HabitualModeChoice", calibrateHabitualModeChoice);
    }


    public void runCalibration() {

        checkCalibrationProcess();

        //Todo: extend the logic checking, supposedly one model in the upper stream is calibrated, others in the lower stream need to be calibrated as well.
        if (calibrateHabitualModeChoice) {
            habitualModeChoiceCalibration = new HabitualModeChoiceCalibration(dataSet);
            habitualModeChoiceCalibration.setup();
            habitualModeChoiceCalibration.load();
            habitualModeChoiceCalibration.run();
        }

    }

    private void checkCalibrationProcess() {

        String startPointOfCalibration = null;
        boolean calibrateStatus = false;

        for (Map.Entry<String, Boolean> entry : calibrationList.entrySet()) {
            if (entry.getValue()) {
                startPointOfCalibration = entry.getKey();
                logger.info("Calibration starts from the " + startPointOfCalibration);
                break;
            }
        }

        for (Map.Entry<String, Boolean> entry : calibrationList.entrySet()) {
            if (entry.getKey().equals(startPointOfCalibration)) {
                calibrateStatus = true;
            }
            if (calibrateStatus) {
                if (!entry.getValue()) {
                    System.err.println(entry.getKey() + " needs to be calibrated as well.");
                    calibrationList.replace(entry.getKey(), Boolean.TRUE);
                    logger.info("Calibration of " + entry.getKey() + " has been set to true");
                }
            }
        }
    }
}
