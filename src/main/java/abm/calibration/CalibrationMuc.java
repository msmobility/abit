package abm.calibration;

import abm.data.DataSet;

import abm.properties.AbitResources;

import org.apache.log4j.Logger;


public class CalibrationMuc {

    static Logger logger = Logger.getLogger(CalibrationMuc.class);
    DataSet dataSet;

    //Todo add booleans for all the models that need to be calibrated here:
    private HabitualModeChoiceCalibration habitualModeChoiceCalibration;

    boolean calibrateHabitualModeChoice;

    public CalibrationMuc(DataSet dataSet) {
        this.dataSet = dataSet;
        calibrateHabitualModeChoice = Boolean.parseBoolean(AbitResources.instance.getString("habitual.mode.calibration"));
    }

    public void runCalibration() {
        //Todo: extend the logic checking, supposedly one model in the upper stream is calibrated, others in the lower stream need to be calibrated as well.
        if (calibrateHabitualModeChoice) {
            habitualModeChoiceCalibration = new HabitualModeChoiceCalibration(dataSet);
            habitualModeChoiceCalibration.setup();
            habitualModeChoiceCalibration.load();
            habitualModeChoiceCalibration.run();
        }
    }

}
