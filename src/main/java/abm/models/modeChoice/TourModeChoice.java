package abm.models.modeChoice;

import abm.data.plans.Mode;
import abm.data.plans.Purpose;
import abm.data.plans.Tour;
import abm.data.pop.Household;
import abm.data.pop.Person;

import java.time.DayOfWeek;
import java.util.Map;

public interface TourModeChoice {

    void chooseMode(Person person, Tour tour);

    void chooseMode(Person person, Tour tour, Purpose purpose);

    Mode chooseMode(Person person, Tour tour, Purpose purpose, Boolean carAvailable);

    void checkCarAvailabilityAndChooseMode(Household household, Person person, Tour tour, Purpose purpose);

    void updateCalibrationFactor(Map<String, Map<Purpose, Map<DayOfWeek, Map<Mode, Double>>>> calibrationFactors);

    Map<Purpose, Map<Mode, Map<String, Double>>> obtainCoefficientsTable();
}
