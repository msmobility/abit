package abm.models.activityGeneration.frequency;

import abm.data.DataSet;
import abm.data.geo.RegioStaR2;
import abm.data.geo.RegioStaR7;
import abm.data.geo.RegioStaRGem5;
import abm.data.geo.Zone;
import abm.data.plans.Purpose;
import abm.data.plans.Tour;
import abm.data.pop.*;
import abm.io.input.CoefficientsReader;
import abm.properties.AbitResources;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.data.AreaTypes;
import de.tum.bgu.msm.data.person.Gender;
import de.tum.bgu.msm.data.person.Occupation;
import org.apache.log4j.Logger;
import umontreal.ssj.probdist.NegativeBinomialDist;

import java.nio.file.Path;
import java.util.Arrays;
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

    @Override
    public int calculateNumberOfActivitiesPerWeek(Person person, Purpose purpose) {
        int numOfActivity;

        if (purpose.equals(Purpose.WORK) || purpose.equals(Purpose.EDUCATION)) {
            numOfActivity = polrEstimateTrips(person);
        } else if (purpose.equals(Purpose.ACCOMPANY)) {
            numOfActivity = hurdleEstimateTrips(person);
        } else {
            numOfActivity = nbEstimateTrips(person);
        }

        return numOfActivity;
    }

    /**
     * Calculate 0-inflated binary + ordered logit
     *
     * @param pp
     * @return
     */
    private int polrEstimateTrips(Person pp) {
        double randomNumber = AbitUtils.getRandomObject().nextDouble();
        double binaryUtility = getPredictor(pp, zeroCoef);
        double phi = Math.exp(binaryUtility) / (1 + Math.exp(binaryUtility));
        double mu = getPredictor(pp, countCoef);

        double[] intercepts = new double[6];
        intercepts[0] = countCoef.get("1|2");
        intercepts[1] = countCoef.get("2|3");
        intercepts[2] = countCoef.get("3|4");
        intercepts[3] = countCoef.get("4|5");
        intercepts[4] = countCoef.get("5|6");
        intercepts[5] = countCoef.get("6|7");

        int i = 0;
        double cumProb = 0;
        double prob = 1 - phi;
        cumProb += prob;

        while (randomNumber > cumProb) {
            i++;
            if (i < 7) {
                prob = Math.exp(intercepts[i - 1] - mu) / (1 + Math.exp(intercepts[i - 1] - mu));
            } else {
                prob = 1;
            }
            if (i > 1) {
                prob -= Math.exp(intercepts[i - 2] - mu) / (1 + Math.exp(intercepts[i - 2] - mu));
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
    private int hurdleEstimateTrips(Person pp) {
        double randomNumber = AbitUtils.getRandomObject().nextDouble();
        double binaryUtility = getPredictor(pp, zeroCoef);
        double phi = Math.exp(binaryUtility) / (1 + Math.exp(binaryUtility));
        double mu = Math.exp(getPredictor(pp, countCoef));
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
        return (i);
    }

    /**
     * Negative binomial
     *
     * @param pp
     * @return
     */
    private int nbEstimateTrips(Person pp) {
        double randomNumber = AbitUtils.getRandomObject().nextDouble();
        double mu = Math.exp(getPredictor(pp, countCoef));
        double theta = countCoef.get("theta");

        NegativeBinomialDist nb = new NegativeBinomialDist(theta, theta / (theta + mu));

        int i = 0;
        double cumProb = nb.prob(0);

        while (randomNumber > cumProb) {
            i++;
            cumProb += nb.prob(i);
        }
        return (i);
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
        switch (regioStrR2){
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
        switch (economicStatus){
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
                predictor += coefficients.get("hh.econStatus_4");
                break;
        }

        //Todo check with Joanna, what is hh.notEmployed
        int numUnemployedInHh = 0;
        for (Person person: pp.getHousehold().getPersons()){
            if (!person.getOccupation().equals(Occupation.EMPLOYED)){
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
        } else {
            assert (householdSize >= 5);
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
                predictor += coefficients.get("p.age_gr_fine_4");
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
                final Tour workTour = pp.getPlan().getTours().values().stream().
                        filter(t -> t.getMainActivity().getPurpose().equals(Purpose.WORK)).findAny().orElse(null);
                if (workTour != null) {
                    if (workTour.getMainActivity().getDuration() > 6 * 60) {
                        predictor += coefficients.get("p.occupationStatus_Employed");
                    } else {
                        predictor += coefficients.get("p.occupationStatus_Halftime");
                    }
                }
                break;
            case UNEMPLOYED:
                predictor += coefficients.get("p.occupationStatus_Unemployed");
                break;
            case RETIREE:
                //todo is this like unemployed?
                predictor += coefficients.get("p.occupationStatus_Unemployed");
                break;
            case TODDLER:
                //todo is this like unemployed?
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

        switch (pp.getHabitualMode()){
            case CAR:
                predictor += coefficients.get("p.t_mand_habmode_car");
                break;
            case BIKE:
                predictor += coefficients.get("p.t_mand_habmode_cycle");
                break;
            case BUS:
            case TRAM_METRO:
            case TRAIN:
                predictor += coefficients.get("p.t_mand_habmode_PT");
                break;
            case WALK:
                predictor += coefficients.get("p.t_mand_habmode_walk");
                break;
        }

        switch (purpose) {
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
        }

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

        return predictor;
    }

//    @Override
//    public Object call() throws Exception {
//        return null;
//    }
}
