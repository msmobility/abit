package abm.calibration;

import abm.data.DataSet;
import abm.data.plans.Mode;
import abm.data.plans.Plan;
import abm.data.plans.Purpose;
import abm.data.plans.Tour;
import abm.data.pop.Household;
import abm.data.pop.Person;
import de.tum.bgu.msm.data.person.Occupation;

import java.io.FileNotFoundException;
import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.Map;

public class TourModeChoiceCalibration implements ModelComponent {
    //Todo define a few calibration parameters
    Map<Purpose, Map<DayOfWeek, Map<Mode, Double>>> objectiveTourModeShare = new HashMap<>();
    Map<Purpose, Map<DayOfWeek, Map<Mode, Integer>>> simulatedTourModeCount = new HashMap<>();
    Map<Purpose, Map<DayOfWeek, Map<Mode, Double>>> simulatedTourModeShare = new HashMap<>();
    DataSet dataSet;
    public TourModeChoiceCalibration(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    @Override
    public void setup() {
        //Todo: read boolean input from the property file and create the model which needs to be calibrated

        //Todo: initialize all the data containers that might be needed for calibration
        //tourmodechoice
        for (Purpose purpose : Purpose.getSortedPurposes()) {
            objectiveTourModeShare.putIfAbsent(purpose, new HashMap<>());
            simulatedTourModeCount.putIfAbsent(purpose, new HashMap<>());
            simulatedTourModeShare.putIfAbsent(purpose, new HashMap<>());
            for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
                objectiveTourModeShare.get(purpose).putIfAbsent(dayOfWeek, new HashMap<>());
                simulatedTourModeCount.get(purpose).putIfAbsent(dayOfWeek, new HashMap<>());
                simulatedTourModeShare.get(purpose).putIfAbsent(dayOfWeek, new HashMap<>());
                for (Mode mode : Mode.values()) {
                    objectiveTourModeShare.get(purpose).get(dayOfWeek).putIfAbsent(mode, 0.0);
                    simulatedTourModeCount.get(purpose).get(dayOfWeek).putIfAbsent(mode, 0);
                    simulatedTourModeShare.get(purpose).get(dayOfWeek).putIfAbsent(mode, 0.0);
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

    //WORK; EDUCATION; ACCOMPANY; OTHER; SHOP; RECR
    private void readObjectiveValues() {
        //WORK
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.MONDAY).put(Mode.CAR_DRIVER, 0.63);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.MONDAY).put(Mode.CAR_PASSENGER, 0.03);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.MONDAY).put(Mode.BUS, 0.03);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.MONDAY).put(Mode.TRAIN, 0.09);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.MONDAY).put(Mode.TRAM_METRO, 0.05);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.MONDAY).put(Mode.BIKE, 0.12);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.MONDAY).put(Mode.WALK, 0.05);


        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.TUESDAY).put(Mode.CAR_DRIVER, 0.63);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.TUESDAY).put(Mode.CAR_PASSENGER, 0.03);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.TUESDAY).put(Mode.BUS, 0.03);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.TUESDAY).put(Mode.TRAIN, 0.09);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.TUESDAY).put(Mode.TRAM_METRO, 0.05);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.TUESDAY).put(Mode.BIKE, 0.12);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.TUESDAY).put(Mode.WALK, 0.05);

        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.WEDNESDAY).put(Mode.CAR_DRIVER, 0.63);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.WEDNESDAY).put(Mode.CAR_PASSENGER, 0.03);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.WEDNESDAY).put(Mode.BUS, 0.03);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.WEDNESDAY).put(Mode.TRAIN, 0.09);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.WEDNESDAY).put(Mode.TRAM_METRO, 0.05);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.WEDNESDAY).put(Mode.BIKE, 0.12);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.WEDNESDAY).put(Mode.WALK, 0.05);

        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.THURSDAY).put(Mode.CAR_DRIVER, 0.63);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.THURSDAY).put(Mode.CAR_PASSENGER, 0.03);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.THURSDAY).put(Mode.BUS, 0.03);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.THURSDAY).put(Mode.TRAIN, 0.09);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.THURSDAY).put(Mode.TRAM_METRO, 0.05);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.THURSDAY).put(Mode.BIKE, 0.12);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.THURSDAY).put(Mode.WALK, 0.05);

        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.FRIDAY).put(Mode.CAR_DRIVER, 0.63);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.FRIDAY).put(Mode.CAR_PASSENGER, 0.03);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.FRIDAY).put(Mode.BUS, 0.03);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.FRIDAY).put(Mode.TRAIN, 0.09);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.FRIDAY).put(Mode.TRAM_METRO, 0.05);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.FRIDAY).put(Mode.BIKE, 0.12);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.FRIDAY).put(Mode.WALK, 0.05);


        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SATURDAY).put(Mode.CAR_DRIVER, 0.70);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SATURDAY).put(Mode.CAR_PASSENGER, 0.05);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SATURDAY).put(Mode.BUS, 0.02);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SATURDAY).put(Mode.TRAIN, 0.04);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SATURDAY).put(Mode.TRAM_METRO, 0.04);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SATURDAY).put(Mode.BIKE, 0.10);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SATURDAY).put(Mode.WALK, 0.05);

        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SUNDAY).put(Mode.CAR_DRIVER, 0.72);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SUNDAY).put(Mode.CAR_PASSENGER, 0.05);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SUNDAY).put(Mode.BUS, 0.01);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SUNDAY).put(Mode.TRAIN, 0.05);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SUNDAY).put(Mode.TRAM_METRO, 0.05);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SUNDAY).put(Mode.BIKE, 0.07);
        objectiveTourModeShare.get(Purpose.WORK).get(DayOfWeek.SUNDAY).put(Mode.WALK, 0.05);

        //EDUCATION
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.MONDAY).put(Mode.CAR_DRIVER, 0.12);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.MONDAY).put(Mode.CAR_PASSENGER, 0.09);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.MONDAY).put(Mode.BUS, 0.31);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.MONDAY).put(Mode.TRAIN, 0.12);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.MONDAY).put(Mode.TRAM_METRO, 0.07);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.MONDAY).put(Mode.BIKE, 0.19);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.MONDAY).put(Mode.WALK, 0.10);

        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.TUESDAY).put(Mode.CAR_DRIVER, 0.12);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.TUESDAY).put(Mode.CAR_PASSENGER, 0.09);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.TUESDAY).put(Mode.BUS, 0.31);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.TUESDAY).put(Mode.TRAIN, 0.12);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.TUESDAY).put(Mode.TRAM_METRO, 0.07);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.TUESDAY).put(Mode.BIKE, 0.19);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.TUESDAY).put(Mode.WALK, 0.10);

        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.WEDNESDAY).put(Mode.CAR_DRIVER, 0.12);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.WEDNESDAY).put(Mode.CAR_PASSENGER, 0.09);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.WEDNESDAY).put(Mode.BUS, 0.31);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.WEDNESDAY).put(Mode.TRAIN, 0.12);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.WEDNESDAY).put(Mode.TRAM_METRO, 0.07);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.WEDNESDAY).put(Mode.BIKE, 0.19);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.WEDNESDAY).put(Mode.WALK, 0.10);

        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.THURSDAY).put(Mode.CAR_DRIVER, 0.12);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.THURSDAY).put(Mode.CAR_PASSENGER, 0.09);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.THURSDAY).put(Mode.BUS, 0.31);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.THURSDAY).put(Mode.TRAIN, 0.12);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.THURSDAY).put(Mode.TRAM_METRO, 0.07);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.THURSDAY).put(Mode.BIKE, 0.19);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.THURSDAY).put(Mode.WALK, 0.10);

        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.FRIDAY).put(Mode.CAR_DRIVER, 0.12);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.FRIDAY).put(Mode.CAR_PASSENGER, 0.09);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.FRIDAY).put(Mode.BUS, 0.31);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.FRIDAY).put(Mode.TRAIN, 0.12);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.FRIDAY).put(Mode.TRAM_METRO, 0.07);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.FRIDAY).put(Mode.BIKE, 0.19);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.FRIDAY).put(Mode.WALK, 0.10);


        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SATURDAY).put(Mode.CAR_DRIVER, 0.47);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SATURDAY).put(Mode.CAR_PASSENGER, 0.23);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SATURDAY).put(Mode.BUS, 0.05);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SATURDAY).put(Mode.TRAIN, 0.06);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SATURDAY).put(Mode.TRAM_METRO, 0.02);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SATURDAY).put(Mode.BIKE, 0.10);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SATURDAY).put(Mode.WALK, 0.08);

        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SUNDAY).put(Mode.CAR_DRIVER, 0.46);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SUNDAY).put(Mode.CAR_PASSENGER, 0.19);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SUNDAY).put(Mode.BUS, 0.04);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SUNDAY).put(Mode.TRAIN, 0.23);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SUNDAY).put(Mode.TRAM_METRO, 0.04);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SUNDAY).put(Mode.BIKE, 0.04);
        objectiveTourModeShare.get(Purpose.EDUCATION).get(DayOfWeek.SUNDAY).put(Mode.WALK, 0.00);

        //ACCOMPANY
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.MONDAY).put(Mode.CAR_DRIVER, 0.74);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.MONDAY).put(Mode.CAR_PASSENGER, 0.05);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.MONDAY).put(Mode.BUS, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.MONDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.MONDAY).put(Mode.TRAM_METRO, 0.02);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.MONDAY).put(Mode.BIKE, 0.05);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.MONDAY).put(Mode.WALK, 0.13);

        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.TUESDAY).put(Mode.CAR_DRIVER, 0.74);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.TUESDAY).put(Mode.CAR_PASSENGER, 0.05);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.TUESDAY).put(Mode.BUS, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.TUESDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.TUESDAY).put(Mode.TRAM_METRO, 0.02);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.TUESDAY).put(Mode.BIKE, 0.05);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.TUESDAY).put(Mode.WALK, 0.13);

        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.WEDNESDAY).put(Mode.CAR_DRIVER, 0.74);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.WEDNESDAY).put(Mode.CAR_PASSENGER, 0.05);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.WEDNESDAY).put(Mode.BUS, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.WEDNESDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.WEDNESDAY).put(Mode.TRAM_METRO, 0.02);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.WEDNESDAY).put(Mode.BIKE, 0.05);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.WEDNESDAY).put(Mode.WALK, 0.13);

        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.THURSDAY).put(Mode.CAR_DRIVER, 0.74);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.THURSDAY).put(Mode.CAR_PASSENGER, 0.05);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.THURSDAY).put(Mode.BUS, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.THURSDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.THURSDAY).put(Mode.TRAM_METRO, 0.02);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.THURSDAY).put(Mode.BIKE, 0.05);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.THURSDAY).put(Mode.WALK, 0.13);

        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.FRIDAY).put(Mode.CAR_DRIVER, 0.74);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.FRIDAY).put(Mode.CAR_PASSENGER, 0.05);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.FRIDAY).put(Mode.BUS, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.FRIDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.FRIDAY).put(Mode.TRAM_METRO, 0.02);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.FRIDAY).put(Mode.BIKE, 0.05);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.FRIDAY).put(Mode.WALK, 0.13);


        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SATURDAY).put(Mode.CAR_DRIVER, 0.81);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SATURDAY).put(Mode.CAR_PASSENGER, 0.12);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SATURDAY).put(Mode.BUS, 0.00);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SATURDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SATURDAY).put(Mode.TRAM_METRO, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SATURDAY).put(Mode.BIKE, 0.02);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SATURDAY).put(Mode.WALK, 0.04);

        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SUNDAY).put(Mode.CAR_DRIVER, 0.75);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SUNDAY).put(Mode.CAR_PASSENGER, 0.16);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SUNDAY).put(Mode.BUS, 0.00);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SUNDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SUNDAY).put(Mode.TRAM_METRO, 0.01);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SUNDAY).put(Mode.BIKE, 0.02);
        objectiveTourModeShare.get(Purpose.ACCOMPANY).get(DayOfWeek.SUNDAY).put(Mode.WALK, 0.05);

        //OTHER
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.MONDAY).put(Mode.CAR_DRIVER, 0.48);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.MONDAY).put(Mode.CAR_PASSENGER, 0.13);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.MONDAY).put(Mode.BUS, 0.04);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.MONDAY).put(Mode.TRAIN, 0.02);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.MONDAY).put(Mode.TRAM_METRO, 0.04);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.MONDAY).put(Mode.BIKE, 0.11);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.MONDAY).put(Mode.WALK, 0.18);

        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.TUESDAY).put(Mode.CAR_DRIVER, 0.48);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.TUESDAY).put(Mode.CAR_PASSENGER, 0.13);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.TUESDAY).put(Mode.BUS, 0.04);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.TUESDAY).put(Mode.TRAIN, 0.02);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.TUESDAY).put(Mode.TRAM_METRO, 0.04);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.TUESDAY).put(Mode.BIKE, 0.11);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.TUESDAY).put(Mode.WALK, 0.18);

        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.WEDNESDAY).put(Mode.CAR_DRIVER, 0.48);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.WEDNESDAY).put(Mode.CAR_PASSENGER, 0.13);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.WEDNESDAY).put(Mode.BUS, 0.04);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.WEDNESDAY).put(Mode.TRAIN, 0.02);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.WEDNESDAY).put(Mode.TRAM_METRO, 0.04);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.WEDNESDAY).put(Mode.BIKE, 0.11);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.WEDNESDAY).put(Mode.WALK, 0.18);

        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.THURSDAY).put(Mode.CAR_DRIVER, 0.48);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.THURSDAY).put(Mode.CAR_PASSENGER, 0.13);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.THURSDAY).put(Mode.BUS, 0.04);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.THURSDAY).put(Mode.TRAIN, 0.02);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.THURSDAY).put(Mode.TRAM_METRO, 0.04);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.THURSDAY).put(Mode.BIKE, 0.11);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.THURSDAY).put(Mode.WALK, 0.18);

        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.FRIDAY).put(Mode.CAR_DRIVER, 0.48);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.FRIDAY).put(Mode.CAR_PASSENGER, 0.13);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.FRIDAY).put(Mode.BUS, 0.04);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.FRIDAY).put(Mode.TRAIN, 0.02);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.FRIDAY).put(Mode.TRAM_METRO, 0.04);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.FRIDAY).put(Mode.BIKE, 0.11);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.FRIDAY).put(Mode.WALK, 0.18);


        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SATURDAY).put(Mode.CAR_DRIVER, 0.50);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SATURDAY).put(Mode.CAR_PASSENGER, 0.21);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SATURDAY).put(Mode.BUS, 0.02);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SATURDAY).put(Mode.TRAIN, 0.02);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SATURDAY).put(Mode.TRAM_METRO, 0.03);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SATURDAY).put(Mode.BIKE, 0.08);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SATURDAY).put(Mode.WALK, 0.14);

        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SUNDAY).put(Mode.CAR_DRIVER, 0.45);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SUNDAY).put(Mode.CAR_PASSENGER, 0.22);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SUNDAY).put(Mode.BUS, 0.01);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SUNDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SUNDAY).put(Mode.TRAM_METRO, 0.02);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SUNDAY).put(Mode.BIKE, 0.07);
        objectiveTourModeShare.get(Purpose.OTHER).get(DayOfWeek.SUNDAY).put(Mode.WALK, 0.21);

        //SHOPPING
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.MONDAY).put(Mode.CAR_DRIVER, 0.46);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.MONDAY).put(Mode.CAR_PASSENGER, 0.11);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.MONDAY).put(Mode.BUS, 0.03);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.MONDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.MONDAY).put(Mode.TRAM_METRO, 0.03);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.MONDAY).put(Mode.BIKE, 0.13);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.MONDAY).put(Mode.WALK, 0.23);

        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.TUESDAY).put(Mode.CAR_DRIVER, 0.46);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.TUESDAY).put(Mode.CAR_PASSENGER, 0.11);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.TUESDAY).put(Mode.BUS, 0.03);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.TUESDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.TUESDAY).put(Mode.TRAM_METRO, 0.03);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.TUESDAY).put(Mode.BIKE, 0.13);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.TUESDAY).put(Mode.WALK, 0.23);

        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.WEDNESDAY).put(Mode.CAR_DRIVER, 0.46);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.WEDNESDAY).put(Mode.CAR_PASSENGER, 0.11);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.WEDNESDAY).put(Mode.BUS, 0.03);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.WEDNESDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.WEDNESDAY).put(Mode.TRAM_METRO, 0.03);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.WEDNESDAY).put(Mode.BIKE, 0.13);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.WEDNESDAY).put(Mode.WALK, 0.23);

        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.THURSDAY).put(Mode.CAR_DRIVER, 0.46);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.THURSDAY).put(Mode.CAR_PASSENGER, 0.11);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.THURSDAY).put(Mode.BUS, 0.03);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.THURSDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.THURSDAY).put(Mode.TRAM_METRO, 0.03);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.THURSDAY).put(Mode.BIKE, 0.13);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.THURSDAY).put(Mode.WALK, 0.23);

        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.FRIDAY).put(Mode.CAR_DRIVER, 0.46);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.FRIDAY).put(Mode.CAR_PASSENGER, 0.11);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.FRIDAY).put(Mode.BUS, 0.03);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.FRIDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.FRIDAY).put(Mode.TRAM_METRO, 0.03);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.FRIDAY).put(Mode.BIKE, 0.13);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.FRIDAY).put(Mode.WALK, 0.23);


        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SATURDAY).put(Mode.CAR_DRIVER, 0.47);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SATURDAY).put(Mode.CAR_PASSENGER, 0.14);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SATURDAY).put(Mode.BUS, 0.03);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SATURDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SATURDAY).put(Mode.TRAM_METRO, 0.02);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SATURDAY).put(Mode.BIKE, 0.13);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SATURDAY).put(Mode.WALK, 0.21);

        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SUNDAY).put(Mode.CAR_DRIVER, 0.44);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SUNDAY).put(Mode.CAR_PASSENGER, 0.10);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SUNDAY).put(Mode.BUS, 0.01);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SUNDAY).put(Mode.TRAIN, 0.01);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SUNDAY).put(Mode.TRAM_METRO, 0.01);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SUNDAY).put(Mode.BIKE, 0.17);
        objectiveTourModeShare.get(Purpose.SHOPPING).get(DayOfWeek.SUNDAY).put(Mode.WALK, 0.27);

        //RECREATION
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.MONDAY).put(Mode.CAR_DRIVER, 0.39);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.MONDAY).put(Mode.CAR_PASSENGER, 0.17);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.MONDAY).put(Mode.BUS, 0.04);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.MONDAY).put(Mode.TRAIN, 0.02);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.MONDAY).put(Mode.TRAM_METRO, 0.03);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.MONDAY).put(Mode.BIKE, 0.15);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.MONDAY).put(Mode.WALK, 0.20);

        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.TUESDAY).put(Mode.CAR_DRIVER, 0.39);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.TUESDAY).put(Mode.CAR_PASSENGER, 0.17);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.TUESDAY).put(Mode.BUS, 0.04);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.TUESDAY).put(Mode.TRAIN, 0.02);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.TUESDAY).put(Mode.TRAM_METRO, 0.03);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.TUESDAY).put(Mode.BIKE, 0.15);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.TUESDAY).put(Mode.WALK, 0.20);

        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.WEDNESDAY).put(Mode.CAR_DRIVER, 0.39);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.WEDNESDAY).put(Mode.CAR_PASSENGER, 0.17);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.WEDNESDAY).put(Mode.BUS, 0.04);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.WEDNESDAY).put(Mode.TRAIN, 0.02);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.WEDNESDAY).put(Mode.TRAM_METRO, 0.03);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.WEDNESDAY).put(Mode.BIKE, 0.15);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.WEDNESDAY).put(Mode.WALK, 0.20);

        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.THURSDAY).put(Mode.CAR_DRIVER, 0.39);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.THURSDAY).put(Mode.CAR_PASSENGER, 0.17);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.THURSDAY).put(Mode.BUS, 0.04);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.THURSDAY).put(Mode.TRAIN, 0.02);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.THURSDAY).put(Mode.TRAM_METRO, 0.03);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.THURSDAY).put(Mode.BIKE, 0.15);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.THURSDAY).put(Mode.WALK, 0.20);

        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.FRIDAY).put(Mode.CAR_DRIVER, 0.39);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.FRIDAY).put(Mode.CAR_PASSENGER, 0.17);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.FRIDAY).put(Mode.BUS, 0.04);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.FRIDAY).put(Mode.TRAIN, 0.02);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.FRIDAY).put(Mode.TRAM_METRO, 0.03);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.FRIDAY).put(Mode.BIKE, 0.15);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.FRIDAY).put(Mode.WALK, 0.20);


        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SATURDAY).put(Mode.CAR_DRIVER, 0.35);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SATURDAY).put(Mode.CAR_PASSENGER, 0.25);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SATURDAY).put(Mode.BUS, 0.03);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SATURDAY).put(Mode.TRAIN, 0.05);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SATURDAY).put(Mode.TRAM_METRO, 0.04);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SATURDAY).put(Mode.BIKE, 0.11);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SATURDAY).put(Mode.WALK, 0.17);

        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SUNDAY).put(Mode.CAR_DRIVER, 0.37);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SUNDAY).put(Mode.CAR_PASSENGER, 0.24);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SUNDAY).put(Mode.BUS, 0.02);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SUNDAY).put(Mode.TRAIN, 0.03);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SUNDAY).put(Mode.TRAM_METRO, 0.04);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SUNDAY).put(Mode.BIKE, 0.11);
        objectiveTourModeShare.get(Purpose.RECREATION).get(DayOfWeek.SUNDAY).put(Mode.WALK, 0.20);

    }

    private void summarizeSimulatedResult() {
        for (Household household : dataSet.getHouseholds().values()) {
            if (household.getSimulated()) {
                for (Person person : household.getPersons()) {
                    Plan plan = person.getPlan();
                    for (Tour tour : plan.getTours().values()) {
                        Purpose purpose = tour.getMainActivity().getPurpose();
                        DayOfWeek dayOfWeek = tour.getMainActivity().getDayOfWeek();
                        Mode mode = tour.getTourMode();
                        int count = simulatedTourModeCount.get(purpose).get(dayOfWeek).get(mode);
                        count = count + 1;
                        simulatedTourModeCount.get(purpose).get(dayOfWeek).replace(mode, count);

                    }

                }
            }
        }
        for (Purpose purpose : Purpose.getSortedPurposes()) {
            for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
                int cumulativeSum = 0;
                for (Mode mode : Mode.getModes()) {
                    cumulativeSum = cumulativeSum + simulatedTourModeCount.get(purpose).get(dayOfWeek).get(mode);
                }
                for (Mode mode : Mode.getModes()) {
                    double share = simulatedTourModeCount.get(purpose).get(dayOfWeek).get(mode) / cumulativeSum;
                    simulatedTourModeShare.get(purpose).get(dayOfWeek).replace(mode, share);
                }
            }
        }
    }

    private void printFinalCoefficientsTable
            (Map<Mode, Map<String, Double>> finalCoefficientsTable) throws FileNotFoundException {

    }
}
