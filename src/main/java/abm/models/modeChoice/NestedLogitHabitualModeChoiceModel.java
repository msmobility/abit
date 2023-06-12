package abm.models.modeChoice;

import abm.data.DataSet;
import abm.data.geo.RegioStaR2;
import abm.data.plans.Leg;
import abm.data.plans.Mode;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.data.pop.Relationship;
import abm.io.input.CoefficientsReader;
import abm.properties.AbitResources;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.data.person.Gender;
import de.tum.bgu.msm.data.person.Occupation;
import de.tum.bgu.msm.modules.modeChoice.ModeChoice;
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


    public NestedLogitHabitualModeChoiceModel(DataSet dataSet) {
        this.dataSet = dataSet;
        this.coefficients = new HashMap<>();
        Path pathToFile = Path.of(AbitResources.instance.getString("habitual.mode.coef"));

        //the following loop will read the coefficient file Mode.getModes().size() times, which is acceptable?
        //Todo the modes in the habitual mode choice model is not consistent with the ABIT mode definition. Check with Carlos and Joanna
        for (Mode mode : Mode.getModes()) {
            final Map<String, Double> modeCoefficients = new CoefficientsReader(dataSet, mode.toString().toLowerCase(), pathToFile).readCoefficients();
            coefficients.put(mode, modeCoefficients);
        }

    }

    @Override
    public void chooseHabitualMode(Person person) {

        Map<Mode, Double> utilities = new HashMap<>();
        for (Mode mode : Mode.getModes()) {
            utilities.put(mode, calculateUtilityForThisMode(mode, person));
        }

        final double utilityAutoD = utilities.get(Mode.CAR_DRIVER);
        final double utilityAutoP = utilities.get(Mode.CAR_PASSENGER);
        final double utilityPT = (utilities.get(Mode.BUS) + utilities.get(Mode.TRAM_METRO) + utilities.get(Mode.TRAIN))/3;
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
                        Math.exp(utilityPT / nestingCoefficientActiveModes) ;

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

        int numAdults = (int) household.getPersons().stream().filter(p -> p.getRelationship()!= Relationship.child||p.getAge()>=18).count();
        double numAutos = household.getNumberOfCars();
        double autosPerAdult = numAutos / numAdults;
        if (autosPerAdult > 1) {
            autosPerAdult = 1.0;
        }

        utility += autosPerAdult * coefficients.get(mode).get("hh.autosPerAdult");

        RegioStaR2 regioStaR2 = dataSet.getZones().get(household.getLocation().getZoneId()).getRegioStaR2Type();
        if (regioStaR2.equals(RegioStaR2.URBAN)){
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
            if(mode == Mode.CAR_DRIVER || mode == Mode.CAR_PASSENGER) {
                travelTime = dataSet.getTravelTimes().getTravelTimeInMinutes(person.getHousehold().getLocation(), person.getJob().getLocation(), Mode.CAR_DRIVER, person.getJob().getStartTime_min());
            } else if (mode == Mode.BUS || mode == Mode.TRAM_METRO || mode == Mode.TRAIN){
                travelTime = dataSet.getTravelTimes().getTravelTimeInMinutes(person.getHousehold().getLocation(), person.getJob().getLocation(), mode, person.getJob().getStartTime_min());
            } else if (mode == Mode.BIKE){
                travelDistanceAuto = dataSet.getTravelDistances().getTravelDistanceInMeters(person.getHousehold().getLocation(), person.getJob().getLocation(), Mode.UNKNOWN, person.getJob().getStartTime_min());
                travelTime = (travelDistanceAuto/ SPEED_BICYCLE_KMH) *60;
            } else {
                travelDistanceAuto = dataSet.getTravelDistances().getTravelDistanceInMeters(person.getHousehold().getLocation(), person.getJob().getLocation(), Mode.UNKNOWN, person.getJob().getStartTime_min());
                travelTime = (travelDistanceAuto / SPEED_WALK_KMH) * 60;
            }

            utility += travelTime * coefficients.get(mode).get("travelTime");
        }

        if (person.getOccupation().equals(Occupation.STUDENT)) {
            double travelTime;
            double travelDistanceAuto;
            if(mode == Mode.CAR_DRIVER || mode == Mode.CAR_PASSENGER) {
                travelTime = dataSet.getTravelTimes().getTravelTimeInMinutes(person.getHousehold().getLocation(), person.getSchool().getLocation(), Mode.CAR_DRIVER, person.getSchool().getStartTime_min());
            } else if (mode == Mode.BUS || mode == Mode.TRAM_METRO || mode == Mode.TRAIN){
                travelTime = dataSet.getTravelTimes().getTravelTimeInMinutes(person.getHousehold().getLocation(), person.getSchool().getLocation(), mode, person.getSchool().getStartTime_min());
            } else if (mode == Mode.BIKE){
                travelDistanceAuto = dataSet.getTravelDistances().getTravelDistanceInMeters(person.getHousehold().getLocation(), person.getSchool().getLocation(), Mode.UNKNOWN, person.getSchool().getStartTime_min());
                travelTime = (travelDistanceAuto/ SPEED_BICYCLE_KMH) *60;
            } else {
                travelDistanceAuto = dataSet.getTravelDistances().getTravelDistanceInMeters(person.getHousehold().getLocation(), person.getSchool().getLocation(), Mode.UNKNOWN, person.getSchool().getStartTime_min());
                travelTime = (travelDistanceAuto / SPEED_WALK_KMH) * 60;
            }
            utility += travelTime * coefficients.get(mode).get("travelTime");
        }

        return utility;
    }


}
