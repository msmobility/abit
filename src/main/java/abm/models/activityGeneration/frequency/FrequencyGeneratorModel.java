package abm.models.activityGeneration.frequency;

import abm.data.DataSet;
import abm.data.geo.BBSRType;
import abm.data.geo.RegionalType;
import abm.data.geo.UrbanRuralType;
import abm.data.geo.Zone;
import abm.data.plans.Activity;
import abm.data.plans.Purpose;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.io.input.CoefficientsReader;
import abm.properties.AbitResources;
import abm.utils.AbitUtils;
import org.apache.log4j.Logger;
import umontreal.ssj.probdist.NegativeBinomialDist;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class FrequencyGeneratorModel implements FrequencyGenerator {
    //extends RandomizableConcurrentFunction<Tuple<Purpose, Map<Person, List<Activity>>>>

    private static final Logger logger = Logger.getLogger(FrequencyGeneratorModel.class);
    private Map<Person, List<Activity>> ActivityByPP = new HashMap<>();

    private final DataSet dataSet;
    private final Purpose purpose;

    private Map<String, Double> zeroCoef;
    private final Map<String, Double> countCoef;


    public FrequencyGeneratorModel(DataSet dataSet, Purpose purpose) {
        //super(AbitUtils.getRandomObject().nextLong());
        this.dataSet = dataSet;
        this.purpose = purpose;
        if (purpose.equals(Purpose.WORK) || purpose.equals(Purpose.EDUCATION)) {
            this.zeroCoef =
                    new CoefficientsReader(dataSet, purpose.toString().toLowerCase(),
                            Path.of(AbitResources.instance.getString("actgen.mand.zero"))).readCoefficients();

            this.countCoef =
                    new CoefficientsReader(dataSet, purpose.toString().toLowerCase(),
                            Path.of(AbitResources.instance.getString("actgen.mand.count"))).readCoefficients();
        } else {
            this.countCoef =
                    new CoefficientsReader(dataSet, purpose.toString().toLowerCase(),
                            Path.of(AbitResources.instance.getString("actgen.disc.count"))).readCoefficients();
        }
    }

    @Override
    public int calculateNumberOfActivitiesPerWeek(Person person, Purpose purpose) {
        int numOfActivity;

        if (purpose.equals(Purpose.WORK) || purpose.equals(Purpose.EDUCATION)) {
            numOfActivity = polrEstimateTrips(person);
        } else {
            numOfActivity = hurdleEstimateTrips(person);
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

        //Todo Corin is figuring out how to implement the hurdle model without the p0_zero part
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
//        while(randomNumber > cumProb) {
//            i++;
//            prob = Math.exp(logphi + Math.log(nb.prob(i)));
//            cumProb += prob;
//        }
//        return(i);
        return 0;
    }

    /**
     * Negative binomial
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

        while(randomNumber > cumProb) {
            i++;
            cumProb += nb.prob(i);
        }
        return(i);
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

        //Area type
        Zone zone = dataSet.getZones().get(hh.getLocation().getZoneId());

        BBSRType bbsr = zone.getBBSRType(); //hh.municipalityType_51-54
        switch (bbsr) {
            case CORE_CITY:
                predictor += coefficients.get("hh.municipalityType_51");
                break;
            case MEDIUM_SIZED_CITY:
                predictor += coefficients.get("hh.municipalityType_52");
                break;
            case TOWN:
                predictor += coefficients.get("hh.municipalityType_53");
                break;
            case RURAL:
                predictor += coefficients.get("hh.municipalityType_54");
                break;
        }

        RegionalType regioType = zone.getRegionalType();
        //todo add switch after the definition is added

        UrbanRuralType urbanRuralType = zone.getUrbanRuralType();
        //todo add switch after the definition is added

        //Economic status
        //todo still missing oecstatus object


        // Household size
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




//        // Household in urban region
//        if(!(hh.getHomeZone().getAreaTypeR().equals(AreaTypes.RType.RURAL))) {
//            predictor += coefficients.get("hh.urban");
//        }
//
//        // Household autos
//        int householdAutos = hh.getAutos();
//        if(householdAutos == 1) {
//            predictor += coefficients.get("hh.cars_1");
//        }
//        else if(householdAutos == 2) {
//            predictor += coefficients.get("hh.cars_2");
//        }
//        else if(householdAutos >= 3) {
//            predictor += coefficients.get("hh.cars_3");
//        }
//
//        // Autos per adult
//        int householdAdults = householdSize - householdChildren;
//        double autosPerAdult = Math.min((double) hh.getAutos() / (double) householdAdults , 1.0);
//        predictor += autosPerAdult * coefficients.get("hh.autosPerAdult");
//
//        // Age
//        int age = pp.getAge();
//        if (age <= 18) {
//            predictor += coefficients.get("p.age_gr_1");
//        }
//        else if (age <= 29) {
//            predictor += coefficients.get("p.age_gr_2");
//        }
//        else if (age <= 49) {
//            predictor += coefficients.get("p.age_gr_3");
//        }
//        else if (age <= 59) {
//            predictor += coefficients.get("p.age_gr_4");
//        }
//        else if (age <= 69) {
//            predictor += coefficients.get("p.age_gr_5");
//        }
//        else {
//            predictor += coefficients.get("p.age_gr_6");
//        }
//
//        // Female
//        if (pp.getMitoGender().equals(MitoGender.FEMALE)) {
//            predictor += coefficients.get("p.female");
//        }
//
//        // Has drivers Licence
//        if (pp.hasDriversLicense()) {
//            predictor += coefficients.get("p.driversLicense");
//        }
//
//        // Has bicycle
//        if (pp.hasBicycle()) {
//            predictor += coefficients.get("p.ownBicycle");
//        }
//
//        // Mito occupation Status
//        MitoOccupationStatus occupationStatus = pp.getMitoOccupationStatus();
//        if (occupationStatus.equals(MitoOccupationStatus.STUDENT)) {
//            predictor += coefficients.get("p.occupationStatus_Student");
//        } else if (occupationStatus.equals(MitoOccupationStatus.UNEMPLOYED)) {
//            predictor += coefficients.get("p.occupationStatus_Unemployed");
//        }
//
//        // Work trips & mean distance
//        List<MitoTrip> workTrips = pp.getTripsForPurpose(Purpose.HBW);
//        int workTripCount = workTrips.size();
//        if(workTripCount > 0) {
//            if (workTripCount == 1) {
//                predictor += coefficients.get("p.workTrips_1");
//            } else if (workTripCount == 2) {
//                predictor += coefficients.get("p.workTrips_2");
//            } else if (workTripCount == 3) {
//                predictor += coefficients.get("p.workTrips_3");
//            } else if (workTripCount == 4) {
//                predictor += coefficients.get("p.workTrips_4");
//            } else {
//                predictor += coefficients.get("p.workTrips_5");
//            }
//            int homeZoneId = pp.getHousehold().getZoneId();
//            double meanWorkKm = workTrips.stream().
//                    mapToDouble(t -> dataSet.getTravelDistancesNMT().
//                            getTravelDistance(homeZoneId, t.getTripDestination().getZoneId())).average().getAsDouble();
//            predictor += Math.log(meanWorkKm) * coefficients.get("p.log_km_mean_HBW");
//        }
//
//        // Education trips & mean distance
//        List<MitoTrip> eduTrips = pp.getTripsForPurpose(Purpose.HBE);
//        int eduTripCount = eduTrips.size();
//        if(eduTripCount > 0) {
//            if (eduTripCount == 1) {
//                predictor += coefficients.get("p.eduTrips_1");
//            } else if (eduTripCount == 2) {
//                predictor += coefficients.get("p.eduTrips_2");
//            } else if (eduTripCount == 3) {
//                predictor += coefficients.get("p.eduTrips_3");
//            } else if (eduTripCount == 4) {
//                predictor += coefficients.get("p.eduTrips_4");
//            } else {
//                predictor += coefficients.get("p.eduTrips_5");
//            }
//            int homeZoneId = pp.getHousehold().getZoneId();
//            double meanWorkKm = eduTrips.stream().
//                    mapToDouble(t -> dataSet.getTravelDistancesNMT().
//                            getTravelDistance(homeZoneId, t.getTripDestination().getZoneId())).average().getAsDouble();
//            predictor += Math.log(meanWorkKm) * coefficients.get("p.log_km_mean_HBW");
//        }

        return predictor;
    }

//    @Override
//    public Object call() throws Exception {
//        return null;
//    }
}
