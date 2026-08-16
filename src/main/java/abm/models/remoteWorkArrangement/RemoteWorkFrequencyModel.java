package abm.models.remoteWorkArrangement;

import abm.data.DataSet;
import abm.data.geo.RegioStaR2;
import abm.data.geo.RegioStaR7;
import abm.data.geo.RegioStaRGem5;
import abm.data.geo.Zone;
import abm.data.plans.Purpose;
import abm.data.plans.Tour;
import abm.data.pop.*;
import abm.io.input.CoefficientsReader;
import abm.models.activityGeneration.frequency.FrequencyGenerator;
import abm.properties.AbitResources;
import de.tum.bgu.msm.data.person.Gender;
import de.tum.bgu.msm.data.person.Occupation;
import org.apache.log4j.Logger;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RemoteWorkFrequencyModel implements FrequencyGenerator {

    private static final Logger logger = Logger.getLogger(RemoteWorkFrequencyModel.class);

    private final DataSet dataSet;
    private final Purpose purpose;

    private Map<String, Double> zeroCoef;
    private final Map<String, Double> countCoef;

    private boolean runCalibration;

    Map<Integer, Double> updatedCalibrationFactors = new HashMap<>();

    public RemoteWorkFrequencyModel(DataSet dataSet, Purpose purpose) {
        this.dataSet = dataSet;
        this.purpose = purpose;

        this.zeroCoef =
                new CoefficientsReader(dataSet, purpose.toString().toLowerCase(),
                        Path.of(AbitResources.instance.getString("remoteWork.gen.zero"))).readCoefficients();

        this.countCoef =
                new CoefficientsReader(dataSet, purpose.toString().toLowerCase(),
                        Path.of(AbitResources.instance.getString("remoteWork.gen.count"))).readCoefficients();

    }

    public RemoteWorkFrequencyModel(DataSet dataSet, Purpose purpose, boolean runCalibration) {
        this(dataSet, purpose);
        this.runCalibration = runCalibration;
        for (int frequency = 0; frequency <= 15; frequency++) {
            updatedCalibrationFactors.putIfAbsent(frequency, 0.0);
        }
    }

    @Override
    public int calculateNumberOfActivitiesPerWeek(Person person, Purpose purpose) {
        int numOfRemoteWorkingDays = 0;

        if (person.canRemoteWork()) {
            //Todo update the model later after it is updated
            //numOfRemoteWorkingDays = polrEstimateTrips(person);
            numOfRemoteWorkingDays = 0;
        }

        return numOfRemoteWorkingDays;
    }

    /**
     * Calculate 0-inflated binary + ordered logit
     *
     * @param pp
     * @return
     */
    private int polrEstimateTrips(Person pp) {
        //double randomNumber = AbitUtils.getRandomObject().nextDouble();
        double randomNumber = pp.getRandom().nextDouble();
        double binaryUtility = getPredictor(pp, zeroCoef) + zeroCoef.get("calibration");
        if (runCalibration) {
            binaryUtility += updatedCalibrationFactors.get(0);
        }
        double phi = Math.exp(binaryUtility) / (1 + Math.exp(binaryUtility));
        double mu = getPredictor(pp, countCoef);

        double[] intercepts = new double[6];
        intercepts[0] = countCoef.get("1|2") + countCoef.get("calibration_1|2");
        intercepts[1] = countCoef.get("2|3") + countCoef.get("calibration_2|3");
        intercepts[2] = countCoef.get("3|4") + countCoef.get("calibration_3|4");
        intercepts[3] = countCoef.get("4|5") + countCoef.get("calibration_4|5");
        intercepts[4] = countCoef.get("5|6") + countCoef.get("calibration_5|6");
        intercepts[5] = countCoef.get("6|7") + countCoef.get("calibration_6|7");

        if (runCalibration){
            intercepts[0] += updatedCalibrationFactors.get(1);
            intercepts[1] += updatedCalibrationFactors.get(2);
            intercepts[2] += updatedCalibrationFactors.get(3);
            intercepts[3] += updatedCalibrationFactors.get(4);
            intercepts[4] += updatedCalibrationFactors.get(5);
            intercepts[5] += updatedCalibrationFactors.get(6);
        }

        int i = 0;
        double cumProb = 0;
        double prob = 1 - phi;
        cumProb += prob;

        while (cumProb < randomNumber) {
            i++;
            if (i < 7) {
                prob = 1 / (1 + Math.exp(mu - intercepts[i - 1]));
            } else {
                prob = 1;
            }
            if (i > 1) {
                prob -= 1 / (1 + Math.exp(mu - intercepts[i - 2]));
            }
            cumProb += phi * prob;
        }
        return i;
    }

    /**
     * Calculate the linear predictor for the model ()
     *
     * @param pp
     * @param coefficients
     * @return
     */
    public double getPredictor(Person pp, Map<String, Double> coefficients) {
        Household hh = pp.getHousehold();
        double predictor = 0.;

        // Intercept
        predictor += coefficients.get("(Intercept)");

        Zone zone = dataSet.getZones().get(hh.getLocation().getZoneId());

        //Todo It seems like MOP doesn't have BBSR type, but the regioStaRGem5. Ask Joanna for double checking
//        BBSRType bbsr = zone.getBBSRType(); //hh.municipalityType_51-54
//        switch (bbsr) {
//            case CORE_CITY:
//                predictor += coefficients.get("hh.municipalityType_51");
//                break;
//            case MEDIUM_SIZED_CITY:
//                predictor += coefficients.get("hh.municipalityType_52");
//                break;
//            case TOWN:
//                predictor += coefficients.get("hh.municipalityType_53");
//                break;
//            case RURAL:
//                predictor += coefficients.get("hh.municipalityType_54");
//                break;
//        }

        RegioStaR2 regioStrR2 = zone.getRegioStaR2Type();
        switch (regioStrR2) {
            case URBAN:
                predictor += coefficients.get("hh.urban");
                break;
        }

        RegioStaR7 regioStaR7 = zone.getRegioStaR7Type();
        switch (regioStaR7) {
            case URBAN_METROPOLIS:
                predictor += coefficients.get("hh.regionType_71");
                break;
            case URBAN_REGIOPOLIS:
                predictor += coefficients.get("hh.regionType_72");
                break;
            case URBAN_MEDIUM_SIZED_CITY:
                predictor += coefficients.get("hh.regionType_73");
                break;
            case URBAN_PROVINCIAL:
                predictor += coefficients.get("hh.regionType_74");
                break;
            case RURAL_CENTRAL_CITY:
                predictor += coefficients.get("hh.regionType_75");
                break;
            case RURAL_URBAN_AREA:
                predictor += coefficients.get("hh.regionType_76");
                break;
            case RURAL_PROVICIAL:
                predictor += coefficients.get("hh.regionType_77");
                break;
        }

        RegioStaRGem5 regioStaRGem5 = zone.getRegioStaRGem5Type();
        switch (regioStaRGem5) {
            case METROPOLIS:
                predictor += coefficients.get("hh.municipalityType_51");
                break;
            case REGIOPOLIS_LARGE_CITY:
                predictor += coefficients.get("hh.municipalityType_52");
                break;
            case CENTRAL_CITY:
                predictor += coefficients.get("hh.municipalityType_53");
                break;
            case URBAN_AREA:
                predictor += coefficients.get("hh.municipalityType_54");
                break;
            case PROVINCIAL_RURAL:
                predictor += coefficients.get("hh.municipalityType_55");
                break;
        }

        // Refer to the EconomicStatus class for more information
        EconomicStatus economicStatus = pp.getHousehold().getEconomicStatus();
        switch (economicStatus) {
            case from0to800:
                predictor += coefficients.get("hh.econStatus_1");
                break;
            case from801to1600:
                predictor += coefficients.get("hh.econStatus_2");
                break;
            case from1601to2400:
                predictor += coefficients.get("hh.econStatus_3");
                break;
            case from2401:
                //predictor += coefficients.get("hh.econStatus_4");
                break;
        }

        int numUnemployedInHh = 0;
        for (Person person : pp.getHousehold().getPersons()) {
            if (!person.getOccupation().equals(Occupation.EMPLOYED)) {
                numUnemployedInHh += 1;
            }
        }
        predictor += numUnemployedInHh * coefficients.get("hh.notEmployed");


        int householdSize = hh.getPersons().size();
        if (householdSize == 2) {
            predictor += coefficients.get("hh.size_2");
        } else if (householdSize == 3) {
            predictor += coefficients.get("hh.size_3");
        } else if (householdSize == 4) {
            predictor += coefficients.get("hh.size_4");
        } else if (householdSize >= 5) {
            //assert (householdSize >= 5); what is assert?
            predictor += coefficients.get("hh.size_5");
        }

        // Number of children in household
        int householdChildren = (int) hh.getPersons().stream().filter(person -> person.getAge() < 18).count();
        if (householdChildren == 1) {
            predictor += coefficients.get("hh.children_1");
        } else if (householdChildren == 2) {
            predictor += coefficients.get("hh.children_2");
        } else if (householdChildren >= 3) {
            predictor += coefficients.get("hh.children_3");
        }

        int householdAdult = householdSize - householdChildren;
        if (householdAdult == 1) {
            predictor += coefficients.get("hh.adults_1");
        } else if (householdAdult == 2) {
            predictor += coefficients.get("hh.adults_2");
        } else if (householdAdult >= 3) {
            predictor += coefficients.get("hh.adults_3");
        } else if (householdAdult >= 4) {
            predictor += coefficients.get("hh.adults_4");
        }

        if (householdChildren != 0) {
            double adultsPerChild = householdAdult / householdChildren;
            if (adultsPerChild < 1) {
                predictor += coefficients.get("hh.adults_per_child_0");
            } else if (adultsPerChild == 1) {
                predictor += coefficients.get("hh.adults_per_child_1");
            } else {
                predictor += coefficients.get("hh.adults_per_child_2");
            }
        }

        int age = pp.getAge();
        predictor += age * coefficients.get("p.age");

        AgeGroup ageGroup = AgeGroup.assignAgeGroup(age);
        switch (ageGroup) {
            case from0to18:
                predictor += coefficients.get("p.age_gr_1");
                break;
            case from19to29:
                predictor += coefficients.get("p.age_gr_2");
                break;
            case from30to49:
                predictor += coefficients.get("p.age_gr_3");
                break;
            case from50to59:
                predictor += coefficients.get("p.age_gr_4");
                break;
            case from60to69:
                predictor += coefficients.get("p.age_gr_5");
                break;
            case from70:
                predictor += coefficients.get("p.age_gr_6");
                break;
        }

        AgeGroupFine ageGroupFine = AgeGroupFine.assignAgeGroupFine(age);
        switch (ageGroupFine) {
            case from0to18:
                predictor += coefficients.get("p.age_gr_fine_1");
                break;
            case from19to24:
                predictor += coefficients.get("p.age_gr_fine_2");
                break;
            case from25to29:
                predictor += coefficients.get("p.age_gr_fine_3");
                break;
            case from30to49:
                //predictor += coefficients.get("p.age_gr_fine_4");
                //is the reference and it is not added to the tables (perhaps it should)
                break;
            case from50to59:
                predictor += coefficients.get("p.age_gr_fine_5");
                break;
            case from60to69:
                predictor += coefficients.get("p.age_gr_fine_6");
                break;
            case from70:
                predictor += coefficients.get("p.age_gr_fine_7");
                break;
        }

        switch (pp.getOccupation()) {
            case STUDENT:
                predictor += coefficients.get("p.occupationStatus_Student");
                break;
            case EMPLOYED:
                //todo move this into the person reader and then define a partTime variable status?
                if (pp.getEmploymentStatus().equals(EmploymentStatus.HALFTIME_EMPLOYED)){
                    predictor += coefficients.get("p.occupationStatus_Halftime");
                    break;
                }else{
                    predictor += coefficients.get("p.occupationStatus_Employed");
                    break;
                }
            case UNEMPLOYED:
                predictor += coefficients.get("p.occupationStatus_Unemployed");
                break;
            case RETIREE:
                //todo is this like unemployed?
                predictor += coefficients.get("p.occupationStatus_Unemployed");
                break;
            case TODDLER:
                //todo is this like unemployed? Joanna: MOP has data from age 11+
                predictor += coefficients.get("p.occupationStatus_Unemployed");
                break;
        }
        //carlos added this for testing - needs check


        if (pp.getGender().equals(Gender.FEMALE)) {
            predictor += coefficients.get("p.female");
        }

        if (pp.isHasLicense()) {
            predictor += coefficients.get("p.driversLicense");
        }

        if (pp.hasBicycle()) {
            predictor += coefficients.get("p.ownBicycle");
        }

        int householdAutos = hh.getNumberOfCars();
        if (householdAutos == 1) {
            predictor += coefficients.get("hh.cars_1");
        } else if (householdAutos == 2) {
            predictor += coefficients.get("hh.cars_2");
        } else if (householdAutos >= 3) {
            predictor += coefficients.get("hh.cars_3");
        }

        switch (pp.getHabitualMode()) {
            case CAR_DRIVER:
                predictor += coefficients.get("p.t_mand_habmode_car");
                break;
            case BIKE:
                predictor += coefficients.get("p.t_mand_habmode_cycle");
                break;
            case PT:
                predictor += coefficients.get("p.t_mand_habmode_PT");
                break;
            case WALK:
                predictor += coefficients.get("p.t_mand_habmode_walk");
                break;
        }

        /*switch (purpose) {
            case WORK:
                predictor += coefficients.get("act.purpose_work");
                break;
            case EDUCATION:
                predictor += coefficients.get("act.purpose_education");
                break;
            case ACCOMPANY:
                predictor += coefficients.get("act.purpose_accompany");
                break;
            case SHOPPING:
                predictor += coefficients.get("act.purpose_shop");
                break;
            case RECREATION:
                predictor += coefficients.get("act.purpose_recreation");
                break;
            case OTHER:
                predictor += coefficients.get("act.purpose_other");
                break;
            case HOME:
                predictor += coefficients.get("act.purpose_home");
                break;
        }*/
        //these coefficients do not exist in the act generation models

        int numDaysWork = 0;
        int numDaysEducation = 0;

        if (Purpose.getDiscretionaryPurposes().contains(purpose)) {

            final List<Tour> tourList = pp.getPlan().getTours().values().stream().filter(tour -> Purpose.getMandatoryPurposes().contains(tour.getMainActivity().getPurpose())).collect(Collectors.toList());

            int[] daysOfWork = new int[]{0, 0, 0, 0, 0, 0, 0};
            int[] daysOfEducation = new int[]{0, 0, 0, 0, 0, 0, 0};

            for (Tour tour : tourList) {
                if (tour.getMainActivity().getPurpose().equals(Purpose.WORK)) {
                    int dayOfWeek = tour.getMainActivity().getDayOfWeek().getValue();
                    if (daysOfWork[dayOfWeek - 1] == 0) {
                        daysOfWork[dayOfWeek - 1] = 1;
                    }
                } else {
                    int dayOfWeek = tour.getMainActivity().getDayOfWeek().getValue();
                    if (daysOfEducation[dayOfWeek - 1] == 0) {
                        daysOfEducation[dayOfWeek - 1] = 1;
                    }
                }
            }

            numDaysWork = Arrays.stream(daysOfWork).sum();
            numDaysEducation = Arrays.stream(daysOfEducation).sum();
        }

        predictor += numDaysWork * coefficients.get("num_days_edu");
        predictor += numDaysEducation * coefficients.get("num_days_work");
        //predictor += coefficients.get("calibration");

        return predictor;
    }

    public void updateCalibrationFactor(Map<Integer, Double> newCalibrationFactors) {
        for (int i = 0; i < newCalibrationFactors.size(); i++) {
            double calibrationFactorFromLastIteration = this.updatedCalibrationFactors.get(i);
            double updatedCalibrationFactor = newCalibrationFactors.get(i) + calibrationFactorFromLastIteration;
            this.updatedCalibrationFactors.replace(i, updatedCalibrationFactor);
            logger.info("Calibration factor for " + purpose + "\t" + "and " + i + "\t" + ": " + updatedCalibrationFactor);
        }
    }

}
