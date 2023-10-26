package abm.models.modeChoice;

import abm.data.DataSet;
import abm.data.geo.RegioStaR2;
import abm.data.plans.Mode;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.data.pop.Relationship;
import abm.io.input.CoefficientsReader;
import abm.properties.AbitResources;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.data.person.Gender;
import de.tum.bgu.msm.data.person.Occupation;
import de.tum.bgu.msm.util.MitoUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class NestedLogitHabitualModeChoiceModel implements HabitualModeChoice {
    private final static Logger logger = LogManager.getLogger(NestedLogitHabitualModeChoiceModel.class);

    private final DataSet dataSet;
    private Map<Mode, Map<String, Double>> coefficients;

    private boolean runCalibration = false;
    private Map<Occupation, Map<Mode, Double>> updatedCalibrationFactors;

    public NestedLogitHabitualModeChoiceModel(DataSet dataSet) {
        this.dataSet = dataSet;
        this.coefficients = new HashMap<>();
        Path pathToCoefficientsFile = Path.of(AbitResources.instance.getString("habitual.mode.coef"));

        //the following loop will read the coefficient file Mode.getModes().size() times, which is acceptable?
        //Todo the modes in the habitual mode choice model is not consistent with the ABIT mode definition. Check with Carlos and Joanna
        for (Mode mode : Mode.getModes()) {
            final Map<String, Double> modeCoefficients = new CoefficientsReader(dataSet, mode.toString().toLowerCase(), pathToCoefficientsFile).readCoefficients();
            coefficients.put(mode, modeCoefficients);
        }
    }

    public NestedLogitHabitualModeChoiceModel(DataSet dataSet, Boolean runCalibration) {
        this(dataSet);
        this.updatedCalibrationFactors = new HashMap<>();
        for (Occupation occupation : Occupation.values()) {
            this.updatedCalibrationFactors.putIfAbsent(occupation, new HashMap<>());
            for (Mode mode : Mode.getHabitualModes()) {
                this.updatedCalibrationFactors.get(occupation).putIfAbsent(mode, 0.0);
            }
        }

        this.runCalibration = runCalibration;
    }

    @Override
    public void chooseHabitualMode(Person person) {

        Map<Mode, Double> utilities = new HashMap<>();
        for (Mode mode : Mode.getModes()) {
            utilities.put(mode, calculateUtilityForThisMode(mode, person));
        }

        final double utilityAutoD = utilities.get(Mode.CAR_DRIVER);
        final double utilityAutoP = utilities.get(Mode.CAR_PASSENGER);
        final double utilityPT = (utilities.get(Mode.BUS) + utilities.get(Mode.TRAM_METRO) + utilities.get(Mode.TRAIN)) / 3;
        final double utilityBicycle = utilities.get(Mode.BIKE);
        final double utilityWalk = utilities.get(Mode.WALK);

        final Double nestingCoefficientAutoModes = coefficients.get(Mode.CAR_DRIVER).get("nestingCoefficient");
        final Double nestingCoefficientActiveModes = coefficients.get(Mode.BUS).get("nestingCoefficient");

        double expsumNestAuto =
                Math.exp(utilityAutoD / nestingCoefficientAutoModes) +
                        Math.exp(utilityAutoP / nestingCoefficientAutoModes);

        double expsumNestActive =
                Math.exp(utilityBicycle / nestingCoefficientActiveModes) +
                        Math.exp(utilityWalk / nestingCoefficientActiveModes) +
                        Math.exp(utilityPT / nestingCoefficientActiveModes);

        double expsumTopLevel =
                Math.exp(nestingCoefficientAutoModes * Math.log(expsumNestAuto)) +
                        Math.exp(nestingCoefficientActiveModes * Math.log(expsumNestActive));

        double probabilityAutoD;
        double probabilityAutoP;
        if (expsumNestAuto > 0) {
            probabilityAutoD =
                    (Math.exp(utilityAutoD / nestingCoefficientAutoModes) / expsumNestAuto) * (Math.exp(nestingCoefficientAutoModes * Math.log(expsumNestAuto)) / expsumTopLevel);
            probabilityAutoP =
                    (Math.exp(utilityAutoP / nestingCoefficientAutoModes) / expsumNestAuto) * (Math.exp(nestingCoefficientAutoModes * Math.log(expsumNestAuto)) / expsumTopLevel);
        } else {
            probabilityAutoD = 0.0;
            probabilityAutoP = 0.0;
        }

        //double probabilityPT = Math.exp(utilityPT) / expsumTopLevel;

        double probabilityBike;
        double probabilityWalk;
        double probabilityPT;
        if (expsumNestActive > 0) {
            probabilityBike =
                    (Math.exp(utilityBicycle / nestingCoefficientActiveModes) / expsumNestActive) * (Math.exp(nestingCoefficientActiveModes * Math.log(expsumNestActive)) / expsumTopLevel);
            probabilityWalk =
                    (Math.exp(utilityWalk / nestingCoefficientActiveModes) / expsumNestActive) * (Math.exp(nestingCoefficientActiveModes * Math.log(expsumNestActive)) / expsumTopLevel);
            probabilityPT =
                    (Math.exp(utilityPT / nestingCoefficientActiveModes) / expsumNestActive) * (Math.exp(nestingCoefficientActiveModes * Math.log(expsumNestActive)) / expsumTopLevel);

        } else {
            probabilityBike = 0.0;
            probabilityWalk = 0.0;
            probabilityPT = 0.0;
        }

        EnumMap<Mode, Double> probabilities = new EnumMap<>(Mode.class);
        probabilities.put(Mode.CAR_DRIVER, probabilityAutoD);
        probabilities.put(Mode.CAR_PASSENGER, probabilityAutoP);
        probabilities.put(Mode.BIKE, probabilityBike);
        probabilities.put(Mode.WALK, probabilityWalk);
        probabilities.put(Mode.BUS, probabilityPT); //Todo temporary assign pt to bus mode


        //found Nan when there is no transit!!
        probabilities.replaceAll((mode, probability) ->
                probability.isNaN() ? 0 : probability);

        double sum = 0;
        for (double probability : probabilities.values()) {
            sum += probability;
        }

        if (sum > 0) {
            final Mode select = MitoUtil.select(probabilities, AbitUtils.getRandomObject());
            person.setHabitualMode(select);
        } else {
            logger.error("Negative probabilities for person " + person.getId() + "'s habitual mode");
            person.setHabitualMode(Mode.UNKNOWN);
        }
    }

    private double calculateUtilityForThisMode(Mode mode, Person person) {
        final double SPEED_WALK_KMH = 4;
        final double SPEED_BICYCLE_KMH = 10;
        Household household = person.getHousehold();
        double utility = 0.;

        utility += coefficients.get(mode).get("(Intercept)");

        utility += household.getPersons().size() * coefficients.get(mode).get("hh.size");

        int numAdults = (int) household.getPersons().stream().filter(p -> p.getRelationship() != Relationship.child || p.getAge() >= 18).count();
        double numAutos = household.getNumberOfCars();
        double autosPerAdult = numAutos / numAdults;
        if (autosPerAdult > 1) {
            autosPerAdult = 1.0;
        }

        utility += autosPerAdult * coefficients.get(mode).get("hh.autosPerAdult");

        RegioStaR2 regioStaR2 = dataSet.getZones().get(household.getLocation().getZoneId()).getRegioStaR2Type();
        if (regioStaR2.equals(RegioStaR2.URBAN)) {
            utility += coefficients.get(mode).get("hh.urban");
        }

        if (person.getGender().equals(Gender.FEMALE)) {
            utility += coefficients.get(mode).get("p.female");
        }

        if (person.isHasLicense()) {
            utility += coefficients.get(mode).get("p.driversLicense");
        }

        if (person.hasBicycle()) {
            utility += coefficients.get(mode).get("p.ownBicycle");
        }

        if (person.getOccupation().equals(Occupation.STUDENT)) {
            utility += coefficients.get(mode).get("p.occupationStatus_Student");
        }


        if (person.getOccupation().equals(Occupation.EMPLOYED)) {
            double travelTime;
            double travelDistanceAuto;
            if (mode == Mode.CAR_DRIVER || mode == Mode.CAR_PASSENGER) {
                travelTime = dataSet.getTravelTimes().getTravelTimeInMinutes(person.getHousehold().getLocation(), person.getJob().getLocation(), Mode.CAR_DRIVER, person.getJob().getStartTime_min());
            } else if (mode == Mode.BUS || mode == Mode.TRAM_METRO || mode == Mode.TRAIN) {
                travelTime = dataSet.getTravelTimes().getTravelTimeInMinutes(person.getHousehold().getLocation(), person.getJob().getLocation(), mode, person.getJob().getStartTime_min());
            } else if (mode == Mode.BIKE) {
                travelDistanceAuto = dataSet.getTravelDistances().getTravelDistanceInMeters(person.getHousehold().getLocation(), person.getJob().getLocation(), Mode.UNKNOWN, person.getJob().getStartTime_min());
                travelTime = (travelDistanceAuto / 1000. / SPEED_BICYCLE_KMH) * 60;
            } else {
                travelDistanceAuto = dataSet.getTravelDistances().getTravelDistanceInMeters(person.getHousehold().getLocation(), person.getJob().getLocation(), Mode.UNKNOWN, person.getJob().getStartTime_min());
                travelTime = (travelDistanceAuto / 1000. / SPEED_WALK_KMH) * 60;
            }

            utility += travelTime * coefficients.get(mode).get("travelTime");
        }

        if (person.getOccupation().equals(Occupation.STUDENT)) {

            double travelTime;
            double travelDistanceAuto;
            if (mode == Mode.CAR_DRIVER || mode == Mode.CAR_PASSENGER) {
                travelTime = dataSet.getTravelTimes().getTravelTimeInMinutes(person.getHousehold().getLocation(), person.getSchool().getLocation(), Mode.CAR_DRIVER, person.getSchool().getStartTime_min());
            } else if (mode == Mode.BUS || mode == Mode.TRAM_METRO || mode == Mode.TRAIN) {
                travelTime = dataSet.getTravelTimes().getTravelTimeInMinutes(person.getHousehold().getLocation(), person.getSchool().getLocation(), mode, person.getSchool().getStartTime_min());
            } else if (mode == Mode.BIKE) {
                travelDistanceAuto = dataSet.getTravelDistances().getTravelDistanceInMeters(person.getHousehold().getLocation(), person.getSchool().getLocation(), Mode.UNKNOWN, person.getSchool().getStartTime_min());
                travelTime = (travelDistanceAuto / 1000. / SPEED_BICYCLE_KMH) * 60;
            } else {
                travelDistanceAuto = dataSet.getTravelDistances().getTravelDistanceInMeters(person.getHousehold().getLocation(), person.getSchool().getLocation(), Mode.UNKNOWN, person.getSchool().getStartTime_min());
                travelTime = (travelDistanceAuto / 1000. / SPEED_WALK_KMH) * 60;
            }
            utility += travelTime * coefficients.get(mode).get("travelTime");
        }

        //Todo add the latest calibration factors to the utility calculation
        switch (person.getOccupation()) {
            case EMPLOYED:
                if (mode.equals(Mode.TRAIN) || mode.equals(Mode.TRAM_METRO)) {
                    utility += utility + coefficients.get(Mode.BUS).get("calibration_employed");
                } else {
                    utility += utility + coefficients.get(mode).get("calibration_employed");
                }
            case STUDENT:
                if (mode.equals(Mode.TRAIN) || mode.equals(Mode.TRAM_METRO)) {
                    utility += utility + coefficients.get(Mode.BUS).get("calibration_student");
                } else {
                    utility += utility + coefficients.get(mode).get("calibration_student");
                }
            case TODDLER:
                if (mode.equals(Mode.TRAIN) || mode.equals(Mode.TRAM_METRO)) {
                    utility += utility + coefficients.get(Mode.BUS).get("calibration_toddler");
                } else {
                    utility += utility + coefficients.get(mode).get("calibration_toddler");
                }
            case RETIREE:
                if (mode.equals(Mode.TRAIN) || mode.equals(Mode.TRAM_METRO)) {
                    utility += utility + coefficients.get(Mode.BUS).get("calibration_retiree");
                } else {
                    utility += utility + coefficients.get(mode).get("calibration_retiree");
                }
            case UNEMPLOYED:
                if (mode.equals(Mode.TRAIN) || mode.equals(Mode.TRAM_METRO)) {
                    utility += utility + coefficients.get(Mode.BUS).get("calibration_unemployed");
                } else {
                    utility += utility + coefficients.get(mode).get("calibration_unemployed");
                }
        }

        //Todo add updated calibration factor to the utility calculation, starting from 0
        if (runCalibration) {
            if (mode.equals(Mode.TRAIN) || mode.equals(Mode.TRAM_METRO)) {
                utility = utility + updatedCalibrationFactors.get(person.getOccupation()).get(Mode.BUS);
            } else {
                utility = utility + updatedCalibrationFactors.get(person.getOccupation()).get(mode);
            }
        }
        return utility;
    }

    public void updateCalibrationFactor(Map<Occupation, Map<Mode, Double>> newCalibrationFactors) {
        for (Occupation occupation : Occupation.values()) {
            for (Mode mode : Mode.getHabitualModes()) {
                double updatedCalibrationFactor = newCalibrationFactors.get(occupation).get(mode);
                this.updatedCalibrationFactors.get(occupation).replace(mode, updatedCalibrationFactor);
                logger.info("Calibration factor for " + mode + "\t" + "and" + mode + "\t" + ": " + updatedCalibrationFactor);
            }
        }
    }

    public Map<Mode, Map<String, Double>> obtainCoefficientsTable() {

        double originalCalibrationFactor = 0.0;
        double updatedCalibrationFactor = 0.0;
        double latestValibrationFactor = 0.0;

        for (Mode mode : Mode.getModes()) {
            for (Occupation occupation : Occupation.values()) {

                if (mode.equals(Mode.TRAIN) || mode.equals(Mode.TRAM_METRO)) {
                    switch (occupation) {
                        case EMPLOYED:
                            originalCalibrationFactor = this.coefficients.get(mode).get("calibration_employed");
                            updatedCalibrationFactor = updatedCalibrationFactors.get(occupation).get(Mode.BUS);
                            latestValibrationFactor = originalCalibrationFactor + updatedCalibrationFactor;
                            coefficients.get(mode).replace("calibration_employed", latestValibrationFactor);
                            break;
                        case STUDENT:
                            originalCalibrationFactor = this.coefficients.get(mode).get("calibration_student");
                            updatedCalibrationFactor = updatedCalibrationFactors.get(occupation).get(Mode.BUS);
                            latestValibrationFactor = originalCalibrationFactor + updatedCalibrationFactor;
                            this.coefficients.get(mode).replace("calibration_employed", latestValibrationFactor);
                        case TODDLER:
                            originalCalibrationFactor = this.coefficients.get(mode).get("calibration_toddler");
                            updatedCalibrationFactor = updatedCalibrationFactors.get(occupation).get(Mode.BUS);
                            latestValibrationFactor = originalCalibrationFactor + updatedCalibrationFactor;
                            this.coefficients.get(mode).replace("calibration_employed", latestValibrationFactor);
                        case RETIREE:
                            originalCalibrationFactor = this.coefficients.get(mode).get("calibration_retiree");
                            updatedCalibrationFactor = updatedCalibrationFactors.get(occupation).get(Mode.BUS);
                            latestValibrationFactor = originalCalibrationFactor + updatedCalibrationFactor;
                            this.coefficients.get(mode).replace("calibration_employed", latestValibrationFactor);
                        case UNEMPLOYED:
                            originalCalibrationFactor = this.coefficients.get(mode).get("calibration_unemployed");
                            updatedCalibrationFactor = updatedCalibrationFactors.get(occupation).get(Mode.BUS);
                            latestValibrationFactor = originalCalibrationFactor + updatedCalibrationFactor;
                            this.coefficients.get(mode).replace("calibration_employed", latestValibrationFactor);
                    }
                } else {
                    switch (occupation) {
                        case EMPLOYED:
                            originalCalibrationFactor = this.coefficients.get(mode).get("calibration_employed");
                            updatedCalibrationFactor = updatedCalibrationFactors.get(occupation).get(mode);
                            latestValibrationFactor = originalCalibrationFactor + updatedCalibrationFactor;
                            coefficients.get(mode).replace("calibration_employed", latestValibrationFactor);
                            break;
                        case STUDENT:
                            originalCalibrationFactor = this.coefficients.get(mode).get("calibration_student");
                            updatedCalibrationFactor = updatedCalibrationFactors.get(occupation).get(mode);
                            latestValibrationFactor = originalCalibrationFactor + updatedCalibrationFactor;
                            this.coefficients.get(mode).replace("calibration_employed", latestValibrationFactor);
                        case TODDLER:
                            originalCalibrationFactor = this.coefficients.get(mode).get("calibration_toddler");
                            updatedCalibrationFactor = updatedCalibrationFactors.get(occupation).get(mode);
                            latestValibrationFactor = originalCalibrationFactor + updatedCalibrationFactor;
                            this.coefficients.get(mode).replace("calibration_employed", latestValibrationFactor);
                        case RETIREE:
                            originalCalibrationFactor = this.coefficients.get(mode).get("calibration_retiree");
                            updatedCalibrationFactor = updatedCalibrationFactors.get(occupation).get(mode);
                            latestValibrationFactor = originalCalibrationFactor + updatedCalibrationFactor;
                            this.coefficients.get(mode).replace("calibration_employed", latestValibrationFactor);
                        case UNEMPLOYED:
                            originalCalibrationFactor = this.coefficients.get(mode).get("calibration_unemployed");
                            updatedCalibrationFactor = updatedCalibrationFactors.get(occupation).get(mode);
                            latestValibrationFactor = originalCalibrationFactor + updatedCalibrationFactor;
                            this.coefficients.get(mode).replace("calibration_employed", latestValibrationFactor);
                    }
                }

            }
        }
        return this.coefficients;
    }

}
