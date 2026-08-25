package abm.models.activityGeneration.frequency;

import abm.calibration.CalibrationOccupation;
import abm.data.DataSet;
import abm.data.geo.RegioStaR2;
import abm.data.geo.RegioStaR7;
import abm.data.geo.RegioStaRGem5;
import abm.data.geo.Zone;
import abm.data.plans.DisabilityMuc;
import abm.data.plans.Mode;
import abm.data.plans.Purpose;
import abm.data.plans.Tour;
import abm.data.pop.*;
import abm.io.input.CoefficientsReader;
import abm.properties.AbitResources;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.data.person.Disability;
import de.tum.bgu.msm.data.person.Gender;
import de.tum.bgu.msm.data.person.Occupation;
import org.apache.log4j.Logger;
import umontreal.ssj.probdist.NegativeBinomialDist;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class FrequencyGeneratorModel implements FrequencyGenerator {
    //extends RandomizableConcurrentFunction<Tuple<Purpose, Map<Person, List<Activity>>>>

    private static final Logger logger = Logger.getLogger(FrequencyGeneratorModel.class);

    private final DataSet dataSet;
    private final Purpose purpose;

    private Map<String, Double> zeroCoef;
    private final Map<String, Double> countCoef;

    private boolean runCalibration;

    Map<Integer, Double> updatedCalibrationFactors = new HashMap<>();
    Map<CalibrationOccupation, Map<EmploymentStatus, Map<Integer, Double>>> updatedWorkCalibrationFactors;
    Map<Integer, Map<Integer, Double>> updatedRemoteWorkCalibrationFactors;
    Map<Integer, Double> updatedEducationCalibrationFactors = new HashMap<>();
    Map<CalibrationOccupation, Map<RemoteWorkable, Map<DisabilityMuc, Map<Integer, Double>>>> updatedDiscretionaryCalibrationFactors;

    public FrequencyGeneratorModel(DataSet dataSet, Purpose purpose) {
        this.dataSet = dataSet;
        this.purpose = purpose;
        if (purpose.equals(Purpose.WORK) || purpose.equals(Purpose.EDUCATION)) {
            this.zeroCoef =
                    new CoefficientsReader(dataSet, purpose.toString().toLowerCase(),
                            Path.of(AbitResources.instance.getString("actgen.mand.zero"))).readCoefficients();

            this.countCoef =
                    new CoefficientsReader(dataSet, purpose.toString().toLowerCase(),
                            Path.of(AbitResources.instance.getString("actgen.mand.count"))).readCoefficients();
        } else if (purpose.equals(Purpose.ACCOMPANY)) {
            this.zeroCoef =
                    new CoefficientsReader(dataSet, purpose.toString().toLowerCase(),
                            Path.of(AbitResources.instance.getString("actgen.ac-rr.zero"))).readCoefficients();

            this.countCoef =
                    new CoefficientsReader(dataSet, purpose.toString().toLowerCase(),
                            Path.of(AbitResources.instance.getString("actgen.ac-rr.count"))).readCoefficients();
        } else {
            this.countCoef =
                    new CoefficientsReader(dataSet, purpose.toString().toLowerCase(),
                            Path.of(AbitResources.instance.getString("actgen.sh-re-ot.count"))).readCoefficients();
        }
    }

    // new calibration constructor------------------------
    public FrequencyGeneratorModel(DataSet dataSet, Purpose purpose, boolean runCalibration) {
        this(dataSet, purpose);
        this.runCalibration = runCalibration;

        if (purpose.equals(Purpose.WORK)) {

            updatedWorkCalibrationFactors = new HashMap<>();

            for (CalibrationOccupation occupation : CalibrationOccupation.values()) {
                updatedWorkCalibrationFactors.putIfAbsent(occupation, new HashMap<>());

                for (EmploymentStatus employmentStatus : EmploymentStatus.values()) {
                    updatedWorkCalibrationFactors.get(occupation).putIfAbsent(employmentStatus, new HashMap<>());

                    for (int freq = 0; freq <= 7; freq++) {
                        updatedWorkCalibrationFactors.get(occupation)
                                .get(employmentStatus)
                                .put(freq, 0.0);
                    }
                }
            }

        } else if (purpose.equals(Purpose.EDUCATION)) {

            for (int freq = 0; freq <= 7; freq++) {
                updatedEducationCalibrationFactors.put(freq, 0.0);
            }

        } else if (Purpose.getDiscretionaryPurposes().contains(purpose)) {

            updatedDiscretionaryCalibrationFactors = new HashMap<>();

            int maxFrequency =
                    purpose.equals(Purpose.ACCOMPANY) ? 7 : 15;

            for (CalibrationOccupation occupation : CalibrationOccupation.values()) {
                updatedDiscretionaryCalibrationFactors.putIfAbsent(occupation, new HashMap<>());

                for (RemoteWorkable rw : RemoteWorkable.values()) {
                    updatedDiscretionaryCalibrationFactors.get(occupation).putIfAbsent(rw, new HashMap<>());

                    for (DisabilityMuc disability : DisabilityMuc.values()) {
                        updatedDiscretionaryCalibrationFactors.get(occupation)
                                .get(rw)
                                .putIfAbsent(disability, new HashMap<>());

                        for (int freq = 0; freq <= maxFrequency; freq++) {
                            updatedDiscretionaryCalibrationFactors.get(occupation)
                                    .get(rw)
                                    .get(disability)
                                    .put(freq, 0.0);
                        }
                    }
                }
            }
        }
    }
    // ------------end

    @Override
    public int calculateNumberOfActivitiesPerWeek(Person person, Purpose purpose) {
        int numOfActivity;

        if (purpose.equals(Purpose.WORK)) {

            if (person.getAge() < 15 && person.getAge() > 70) {
                numOfActivity = 0;
            } else {
                numOfActivity = polrEstimateTrips(person);
            }

            if (numOfActivity > 7) {
                numOfActivity = 7;
            }


        } else if (purpose.equals(Purpose.EDUCATION)) {

            if (! person.getOccupation().equals(Occupation.STUDENT)) {
                numOfActivity = 0;
            } else {
                numOfActivity = polrEstimateTrips(person);
            }

            if (numOfActivity > 7) {
                numOfActivity = 7;
            }

        } else if (purpose.equals(Purpose.ACCOMPANY)) {
            numOfActivity = hurdleEstimateTrips(person);
            if (numOfActivity > 7) {
                numOfActivity = 7;
            }
        } else {
            numOfActivity = nbEstimateTrips(person);
            if (numOfActivity > 15){
                numOfActivity = 15;
            }
        }
        return numOfActivity;
    }

    /**
     * Calculate 0-inflated binary + ordered logit
     *
     * @param pp
     * @return
     */
//    private int polrEstimateTrips(Person pp) {
//        //double randomNumber = AbitUtils.getRandomObject().nextDouble();
//        double randomNumber = pp.getRandom().nextDouble();
//        double binaryUtility = getPredictor(pp, zeroCoef) + zeroCoef.get("calibration");
//        if (runCalibration) {
//            binaryUtility += updatedCalibrationFactors.get(0);
//        }
//        double phi = Math.exp(binaryUtility) / (1 + Math.exp(binaryUtility));
//        double mu = getPredictor(pp, countCoef);
//
//        double[] intercepts = new double[6];
//        intercepts[0] = countCoef.get("1|2") + countCoef.get("calibration_1|2");
//        intercepts[1] = countCoef.get("2|3") + countCoef.get("calibration_2|3");
//        intercepts[2] = countCoef.get("3|4") + countCoef.get("calibration_3|4");
//        intercepts[3] = countCoef.get("4|5") + countCoef.get("calibration_4|5");
//        intercepts[4] = countCoef.get("5|6") + countCoef.get("calibration_5|6");
//        intercepts[5] = countCoef.get("6|7") + countCoef.get("calibration_6|7");
//
//        if (runCalibration){
//            intercepts[0] += updatedCalibrationFactors.get(1);
//            intercepts[1] += updatedCalibrationFactors.get(2);
//            intercepts[2] += updatedCalibrationFactors.get(3);
//            intercepts[3] += updatedCalibrationFactors.get(4);
//            intercepts[4] += updatedCalibrationFactors.get(5);
//            intercepts[5] += updatedCalibrationFactors.get(6);
//        }
//
//        int i = 0;
//        double cumProb = 0;
//        double prob = 1 - phi;
//        cumProb += prob;
//
//        while (cumProb < randomNumber) {
//            i++;
//            if (i < 7) {
//                prob = 1 / (1 + Math.exp(mu - intercepts[i - 1]));
//            } else {
//                prob = 1;
//            }
//            if (i > 1) {
//                prob -= 1 / (1 + Math.exp(mu - intercepts[i - 2]));
//            }
//            cumProb += phi * prob;
//        }
//        return i;
//    }

    // new polr model
    private int polrEstimateTrips(Person pp) {
        double randomNumber = pp.getRandom().nextDouble();
        double binaryUtility = getPredictor(pp, zeroCoef) + zeroCoef.get("calibration");
        if (runCalibration) {
            if (purpose.equals(Purpose.WORK)) {
                CalibrationOccupation occupation = getCalibrationOccupation(pp);
                EmploymentStatus status = pp.getEmploymentStatus();
                binaryUtility += updatedWorkCalibrationFactors.get(occupation).get(status).get(0);
            } else if (purpose.equals(Purpose.EDUCATION)) {
                binaryUtility += updatedEducationCalibrationFactors.get(0);
            }
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
        if (runCalibration) {
            if (purpose.equals(Purpose.WORK)) {
                CalibrationOccupation occupation = getCalibrationOccupation(pp);
                EmploymentStatus status = pp.getEmploymentStatus();
                intercepts[0] += updatedWorkCalibrationFactors.get(occupation).get(status).get(1);
                intercepts[1] += updatedWorkCalibrationFactors.get(occupation).get(status).get(2);
                intercepts[2] += updatedWorkCalibrationFactors.get(occupation).get(status).get(3);
                intercepts[3] += updatedWorkCalibrationFactors.get(occupation).get(status).get(4);
                intercepts[4] += updatedWorkCalibrationFactors.get(occupation).get(status).get(5);
                intercepts[5] += updatedWorkCalibrationFactors.get(occupation).get(status).get(6);
            } else if (purpose.equals(Purpose.EDUCATION)) {
                intercepts[0] += updatedEducationCalibrationFactors.get(1);
                intercepts[1] += updatedEducationCalibrationFactors.get(2);
                intercepts[2] += updatedEducationCalibrationFactors.get(3);
                intercepts[3] += updatedEducationCalibrationFactors.get(4);
                intercepts[4] += updatedEducationCalibrationFactors.get(5);
                intercepts[5] += updatedEducationCalibrationFactors.get(6);
            }
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
     * Binary + negative binomial
     *
     * @param pp
     * @return
     */
//    private int hurdleEstimateTrips(Person pp) {
//        double randomNumber = AbitUtils.getRandomObject().nextDouble();
//        double binaryUtility = getPredictor(pp, zeroCoef) + zeroCoef.get("calibration");
//        if (runCalibration) {
//            binaryUtility += updatedCalibrationFactors.get(0);
//        }
//        double phi = Math.exp(binaryUtility) / (1 + Math.exp(binaryUtility));
//
//        double mu;
//        if (runCalibration){
//            mu = Math.exp(getPredictor(pp, countCoef) + countCoef.get("calibration") + updatedCalibrationFactors.get(1));
//        } else{
//            mu = Math.exp(getPredictor(pp, countCoef) + countCoef.get("calibration"));
//        }
//
//        double theta = countCoef.get("theta") ;
//
//        NegativeBinomialDist nb = new NegativeBinomialDist(theta, theta / (theta + mu));
//
//        double p0_zero = Math.log(phi);
//        double p0_count = Math.log(1 - nb.cdf(0));
//        double logphi = p0_zero - p0_count;
//
//        int i = 0;
//        double cumProb = 0;
//        double prob = 1 - Math.exp(p0_zero);
//        cumProb += prob;
//
//        while (randomNumber > cumProb) {
//            i++;
//            prob = Math.exp(logphi + Math.log(nb.prob(i)));
//            cumProb += prob;
//        }
//        return (i);
//    }

    // new hurdle
    private int hurdleEstimateTrips(Person pp) {

        double randomNumber = AbitUtils.getRandomObject().nextDouble();

        double binaryUtility = getPredictor(pp, zeroCoef) + zeroCoef.get("calibration");

        if (runCalibration) {
            CalibrationOccupation occupation = getCalibrationOccupation(pp);
            RemoteWorkable remoteWorkable = getRemoteWorkable(pp);
            DisabilityMuc disability = hasDisability(pp);

            binaryUtility += updatedDiscretionaryCalibrationFactors.get(occupation).get(remoteWorkable).get(disability).get(0);
        }

        double phi = Math.exp(binaryUtility) / (1 + Math.exp(binaryUtility));

        double mu;
        if (runCalibration) {
            CalibrationOccupation occupation = getCalibrationOccupation(pp);
            RemoteWorkable remoteWorkable = getRemoteWorkable(pp);
            DisabilityMuc disability = hasDisability(pp);

            mu = Math.exp(getPredictor(pp, countCoef) + countCoef.get("calibration") + updatedDiscretionaryCalibrationFactors.get(occupation).get(remoteWorkable).get(disability).get(1));
        } else {
            mu = Math.exp(getPredictor(pp, countCoef) + countCoef.get("calibration"));
        }
        double theta = countCoef.get("theta");

        NegativeBinomialDist nb = new NegativeBinomialDist(theta, theta / (theta + mu));

        double p0_zero = Math.log(phi);
        double p0_count = Math.log(1 - nb.cdf(0));
        double logphi = p0_zero - p0_count;

        int i = 0;
        double cumProb = 0;
        double prob = 1 - Math.exp(p0_zero);

        cumProb += prob;

        while (randomNumber > cumProb) {
            i++;
            prob = Math.exp(logphi + Math.log(nb.prob(i)));
            cumProb += prob;
        }

        return i;
    }


    /**
     * Negative binomial
     *
     * @param pp
     * @return
     */
//    private int nbEstimateTrips(Person pp) {
//        double randomNumber = AbitUtils.getRandomObject().nextDouble();
//        double mu;
//        if (runCalibration){
//            mu = Math.exp(getPredictor(pp, countCoef) + countCoef.get("calibration") + updatedCalibrationFactors.get(0));
//        } else{
//            mu = Math.exp(getPredictor(pp, countCoef) + countCoef.get("calibration"));
//        }
//        double theta = countCoef.get("theta") + countCoef.get("calibration");
//
//        NegativeBinomialDist nb = new NegativeBinomialDist(theta, theta / (theta + mu));
//
//        int i = 0;
//        double cumProb = nb.prob(0);
//
//        while (randomNumber > cumProb) {
//            i++;
//            cumProb += nb.prob(i);
//        }
//        return (i);
//    }

    // new nbEstimateTrip
    private int nbEstimateTrips(Person pp) {

        double randomNumber = AbitUtils.getRandomObject().nextDouble();
        double mu;

        if (runCalibration) {

            CalibrationOccupation occupation = getCalibrationOccupation(pp);
            RemoteWorkable remoteWorkable = getRemoteWorkable(pp);
            DisabilityMuc disability = hasDisability(pp);

            mu = Math.exp(getPredictor(pp, countCoef) + countCoef.get("calibration") + updatedDiscretionaryCalibrationFactors
                            .get(occupation)
                            .get(remoteWorkable)
                            .get(disability)
                            .get(0));
        } else {
            mu = Math.exp(getPredictor(pp, countCoef) + countCoef.get("calibration"));
        }

        double theta = countCoef.get("theta") + countCoef.get("calibration");

        NegativeBinomialDist nb = new NegativeBinomialDist(theta, theta / (theta + mu));

        int i = 0;
        double cumProb = nb.prob(0);

        while (randomNumber > cumProb) {
            i++;
            cumProb += nb.prob(i);
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

            // read directly from Plan rather than deriving from Tour structure - immune to
            // whatever shape a WFH day ends up taking (leg-less tour, split-bookend tour)
            numDaysWork = pp.getPlan().getWorkDays().size();

            final List<Tour> tourList = pp.getPlan().getTours().values().stream().filter(tour -> Purpose.getMandatoryPurposes().contains(tour.getMainActivity().getPurpose())).collect(Collectors.toList());

            int[] daysOfEducation = new int[]{0, 0, 0, 0, 0, 0, 0};

            for (Tour tour : tourList) {
                if (!tour.getMainActivity().getPurpose().equals(Purpose.WORK)) {
                    int dayOfWeek = tour.getMainActivity().getDayOfWeek().getValue();
                    if (daysOfEducation[dayOfWeek - 1] == 0) {
                        daysOfEducation[dayOfWeek - 1] = 1;
                    }
                }
            }

            numDaysEducation = Arrays.stream(daysOfEducation).sum();
        }

        predictor += numDaysWork * coefficients.get("num_days_work");
        predictor += numDaysEducation * coefficients.get("num_days_edu");
        //predictor += coefficients.get("calibration");

        return predictor;
    }

    private CalibrationOccupation getCalibrationOccupation(Person person) {
        if (person.getOccupation() == Occupation.EMPLOYED) {
            return CalibrationOccupation.EMPLOYED;
        }
        return CalibrationOccupation.OTHER;
    }


//    public void updateCalibrationFactor(Map<Integer, Double> newCalibrationFactors) {
//        for (int i = 0; i < newCalibrationFactors.size(); i++) {
//            double calibrationFactorFromLastIteration = this.updatedCalibrationFactors.get(i);
//            double updatedCalibrationFactor = newCalibrationFactors.get(i) + calibrationFactorFromLastIteration;
//            this.updatedCalibrationFactors.replace(i, updatedCalibrationFactor);
//            logger.info("Calibration factor for " + purpose + "\t" + "and " + i + "\t" + ": " + updatedCalibrationFactor);
//        }
//    }

    // new segmented update calibration factors
    public void updateWorkCalibrationFactor(Map<CalibrationOccupation, Map<EmploymentStatus, Map<Integer, Double>>> newCalibrationFactors) {
        for (CalibrationOccupation occupation : CalibrationOccupation.values()) {
            for (EmploymentStatus employmentStatus : EmploymentStatus.values()) {

                for (int freq = 0; freq <= 7; freq++) {

                    double calibrationFactorFromLastIteration = updatedWorkCalibrationFactors.get(occupation).get(employmentStatus).get(freq);
                    double updatedCalibrationFactor = calibrationFactorFromLastIteration + newCalibrationFactors.get(occupation).get(employmentStatus).get(freq);
                    updatedWorkCalibrationFactors.get(occupation).get(employmentStatus).put(freq, updatedCalibrationFactor);
                    logger.info("Calibration factor for " + purpose + " | " + occupation + " | " + employmentStatus + " | " + freq + " : " + updatedCalibrationFactor);
                }
            }
        }
    }

    public void updateEducationCalibrationFactor(Map<Integer, Double> newCalibrationFactors) {

        for (int freq = 0; freq <= 7; freq++) {
            double calibrationFactorFromLastIteration = updatedEducationCalibrationFactors.get(freq);
            double updatedCalibrationFactor = calibrationFactorFromLastIteration + newCalibrationFactors.get(freq);
            updatedEducationCalibrationFactors.put(freq, updatedCalibrationFactor);
            logger.info("Calibration factor for " + purpose + " | " + freq + " : " + updatedCalibrationFactor);
        }
    }

    public void updateDiscretionaryCalibrationFactor(Map<CalibrationOccupation, Map<RemoteWorkable, Map<DisabilityMuc, Map<Integer, Double>>>> newCalibrationFactors) {

        int maxFrequency = purpose == Purpose.ACCOMPANY ? 7 : 15;

        for (CalibrationOccupation occupation : CalibrationOccupation.values()) {
            for (RemoteWorkable remoteWorkable : RemoteWorkable.values()) {
                for (DisabilityMuc disability : DisabilityMuc.values()) {
                    for (int freq = 0; freq <= maxFrequency; freq++) {
                        double calibrationFactorFromLastIteration = updatedDiscretionaryCalibrationFactors.get(occupation).get(remoteWorkable).get(disability).get(freq);
                        double updatedCalibrationFactor = calibrationFactorFromLastIteration + newCalibrationFactors.get(occupation).get(remoteWorkable).get(disability).get(freq);
                        updatedDiscretionaryCalibrationFactors.get(occupation).get(remoteWorkable).get(disability).put(freq, updatedCalibrationFactor);
                        logger.info("Calibration factor for " + purpose + " | " + occupation + " | " + remoteWorkable + " | " + disability + " | " + freq + " : " + updatedCalibrationFactor);
                    }
                }
            }
        }
    }

//    public Map<String, Double> obtainZeroCoefficients() {
//        double originalCalibrationFactor = zeroCoef.get("calibration");
//        double updatedCalibrationFactor = updatedCalibrationFactors.get(0);
//        double latestCalibrationFactor = originalCalibrationFactor + updatedCalibrationFactor;
//        this.zeroCoef.replace("calibration", latestCalibrationFactor);
//        return zeroCoef;
//    }

    // new ------------------------------------
//    public Map<String, Double> obtainZeroCoefficients(
//            CalibrationOccupation occupation,
//            EmploymentStatus employmentStatus,
//            RemoteWorkable remoteWorkable,
//            DisabilityMuc disability) {
//
//        Map<String, Double> coefficients = new HashMap<>(zeroCoef);
//
//        double originalCalibration = coefficients.get("calibration");
//        double updatedCalibration;
//        if (purpose == Purpose.WORK) {
//            updatedCalibration = updatedWorkCalibrationFactors.get(occupation).get(employmentStatus).get(0);
//        } else if (purpose == Purpose.EDUCATION) {
//            updatedCalibration = updatedEducationCalibrationFactors.get(0);
//        } else { // ACCOMPANY
//            updatedCalibration = updatedDiscretionaryCalibrationFactors.get(occupation).get(remoteWorkable).get(disability).get(0);
//        }
//        coefficients.replace("calibration", originalCalibration + updatedCalibration);
//        return coefficients;
//    }

    public Map<String, Double> obtainZeroCoefficients() {
        double originalCalibrationFactor = zeroCoef.get("calibration");
        double updatedCalibrationFactor = updatedCalibrationFactors.get(0);
        double latestCalibrationFactor = originalCalibrationFactor + updatedCalibrationFactor;
        this.zeroCoef.replace("calibration", latestCalibrationFactor);
        return zeroCoef;
    }

    public Map<String, Double> obtainWorkZeroCoefficients(
            CalibrationOccupation occupation,
            EmploymentStatus employmentStatus) {

        Map<String, Double> coefficients = new HashMap<>(zeroCoef);

        double originalCalibrationFactor = coefficients.get("calibration");
        double updatedCalibrationFactor = updatedWorkCalibrationFactors.get(occupation).get(employmentStatus).get(0);
        double latestCalibrationFactor = originalCalibrationFactor + updatedCalibrationFactor;
        coefficients.put("calibration", latestCalibrationFactor);
        return coefficients;
    }

    public Map<String, Double> obtainEducationZeroCoefficients() {

        Map<String, Double> coefficients = new HashMap<>(zeroCoef);

        double originalCalibrationFactor = coefficients.get("calibration");
        double updatedCalibrationFactor = updatedEducationCalibrationFactors.get(0);
        double latestCalibrationFactor = originalCalibrationFactor + updatedCalibrationFactor;
        coefficients.put("calibration", latestCalibrationFactor);

        return coefficients;
    }

    public Map<String, Double> obtainAccompanyZeroCoefficients(
            CalibrationOccupation occupation,
            RemoteWorkable remoteWorkable,
            DisabilityMuc disability) {

        Map<String, Double> coefficients = new HashMap<>(zeroCoef);

        double originalCalibrationFactor = coefficients.get("calibration");
        double updatedCalibrationFactor = updatedDiscretionaryCalibrationFactors.get(occupation).get(remoteWorkable).get(disability).get(0);
        double latestCalibrationFactor = originalCalibrationFactor + updatedCalibrationFactor;
        coefficients.put("calibration", latestCalibrationFactor);

        return coefficients;
    }

    //-----------


    // new obtain method---------------------------------------------------------
    public Map<String, Double> obtainCountWorkCoefficients(
            CalibrationOccupation occupation,
            EmploymentStatus employmentStatus) {

        Map<String, Double> coefficients = new HashMap<>(countCoef);

        double originalCalibrationFactor_1_2 = coefficients.get("calibration_1|2");
        double originalCalibrationFactor_2_3 = coefficients.get("calibration_2|3");
        double originalCalibrationFactor_3_4 = coefficients.get("calibration_3|4");
        double originalCalibrationFactor_4_5 = coefficients.get("calibration_4|5");
        double originalCalibrationFactor_5_6 = coefficients.get("calibration_5|6");
        double originalCalibrationFactor_6_7 = coefficients.get("calibration_6|7");

        double updatedCalibrationFactor_1_2 = updatedWorkCalibrationFactors.get(occupation).get(employmentStatus).get(1);
        double updatedCalibrationFactor_2_3 = updatedWorkCalibrationFactors.get(occupation).get(employmentStatus).get(2);
        double updatedCalibrationFactor_3_4 = updatedWorkCalibrationFactors.get(occupation).get(employmentStatus).get(3);
        double updatedCalibrationFactor_4_5 = updatedWorkCalibrationFactors.get(occupation).get(employmentStatus).get(4);
        double updatedCalibrationFactor_5_6 = updatedWorkCalibrationFactors.get(occupation).get(employmentStatus).get(5);
        double updatedCalibrationFactor_6_7 = updatedWorkCalibrationFactors.get(occupation).get(employmentStatus).get(6);

        double latestCalibrationFactor_1_2 = originalCalibrationFactor_1_2 + updatedCalibrationFactor_1_2;
        double latestCalibrationFactor_2_3 = originalCalibrationFactor_2_3 + updatedCalibrationFactor_2_3;
        double latestCalibrationFactor_3_4 = originalCalibrationFactor_3_4 + updatedCalibrationFactor_3_4;
        double latestCalibrationFactor_4_5 = originalCalibrationFactor_4_5 + updatedCalibrationFactor_4_5;
        double latestCalibrationFactor_5_6 = originalCalibrationFactor_5_6 + updatedCalibrationFactor_5_6;
        double latestCalibrationFactor_6_7 = originalCalibrationFactor_6_7 + updatedCalibrationFactor_6_7;

        coefficients.replace("calibration_1|2", latestCalibrationFactor_1_2);
        coefficients.replace("calibration_2|3", latestCalibrationFactor_2_3);
        coefficients.replace("calibration_3|4", latestCalibrationFactor_3_4);
        coefficients.replace("calibration_4|5", latestCalibrationFactor_4_5);
        coefficients.replace("calibration_5|6", latestCalibrationFactor_5_6);
        coefficients.replace("calibration_6|7", latestCalibrationFactor_6_7);

        return coefficients;
    }

    public Map<String, Double> obtainCountEducationCoefficients() {

        Map<String, Double> coefficients = new HashMap<>(countCoef);

        double originalCalibrationFactor_1_2 = coefficients.get("calibration_1|2");
        double originalCalibrationFactor_2_3 = coefficients.get("calibration_2|3");
        double originalCalibrationFactor_3_4 = coefficients.get("calibration_3|4");
        double originalCalibrationFactor_4_5 = coefficients.get("calibration_4|5");
        double originalCalibrationFactor_5_6 = coefficients.get("calibration_5|6");
        double originalCalibrationFactor_6_7 = coefficients.get("calibration_6|7");

        double updatedCalibrationFactor_1_2 = updatedEducationCalibrationFactors.get(1);
        double updatedCalibrationFactor_2_3 = updatedEducationCalibrationFactors.get(2);
        double updatedCalibrationFactor_3_4 = updatedEducationCalibrationFactors.get(3);
        double updatedCalibrationFactor_4_5 = updatedEducationCalibrationFactors.get(4);
        double updatedCalibrationFactor_5_6 = updatedEducationCalibrationFactors.get(5);
        double updatedCalibrationFactor_6_7 = updatedEducationCalibrationFactors.get(6);

        double latestCalibrationFactor_1_2 = originalCalibrationFactor_1_2 + updatedCalibrationFactor_1_2;
        double latestCalibrationFactor_2_3 = originalCalibrationFactor_2_3 + updatedCalibrationFactor_2_3;
        double latestCalibrationFactor_3_4 = originalCalibrationFactor_3_4 + updatedCalibrationFactor_3_4;
        double latestCalibrationFactor_4_5 = originalCalibrationFactor_4_5 + updatedCalibrationFactor_4_5;
        double latestCalibrationFactor_5_6 = originalCalibrationFactor_5_6 + updatedCalibrationFactor_5_6;
        double latestCalibrationFactor_6_7 = originalCalibrationFactor_6_7 + updatedCalibrationFactor_6_7;

        coefficients.replace("calibration_1|2", latestCalibrationFactor_1_2);
        coefficients.replace("calibration_2|3", latestCalibrationFactor_2_3);
        coefficients.replace("calibration_3|4", latestCalibrationFactor_3_4);
        coefficients.replace("calibration_4|5", latestCalibrationFactor_4_5);
        coefficients.replace("calibration_5|6", latestCalibrationFactor_5_6);
        coefficients.replace("calibration_6|7", latestCalibrationFactor_6_7);

        return coefficients;
    }

    public Map<String, Double> obtainAccompanyCountCoefficients(
            CalibrationOccupation occupation,
            RemoteWorkable remoteWorkable,
            DisabilityMuc disability) {

        Map<String, Double> coefficients = new HashMap<>(countCoef);

        double originalCalibrationFactor = coefficients.get("calibration");
        double updatedCalibrationFactor = updatedDiscretionaryCalibrationFactors.get(occupation).get(remoteWorkable).get(disability).get(1);
        double latestCalibrationFactor = originalCalibrationFactor + updatedCalibrationFactor;
        coefficients.replace("calibration", latestCalibrationFactor);

        return coefficients;
    }

    public Map<String, Double> obtainDiscretionaryCountCoefficients(
            CalibrationOccupation occupation,
            RemoteWorkable remoteWorkable,
            DisabilityMuc disability) {

        Map<String, Double> coefficients = new HashMap<>(countCoef);

        double originalCalibrationFactor = coefficients.get("calibration");
        double updatedCalibrationFactor = updatedDiscretionaryCalibrationFactors.get(occupation).get(remoteWorkable).get(disability).get(0);
        double latestCalibrationFactor = originalCalibrationFactor + updatedCalibrationFactor;
        coefficients.replace("calibration", latestCalibrationFactor);

        return coefficients;
    }

    // -----------------------------------------------------------


    private RemoteWorkable getRemoteWorkable(Person person) {

        return person.canRemoteWork()
                ? RemoteWorkable.TRUE
                : RemoteWorkable.FALSE;
    }

    private DisabilityMuc hasDisability(Person person) {

        return person.getDisability() == Disability.WITHOUT
                ? DisabilityMuc.WITHOUT
                : DisabilityMuc.WITH;
    }

//    @Override
//    public Object call() throws Exception {
//        return null;
//    }
}