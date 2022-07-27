package abm.models.activityGeneration.splitByType;

import abm.data.DataSet;
import abm.data.plans.Activity;
import abm.data.plans.DiscretionaryActivityType;
import abm.data.plans.Purpose;
import abm.data.plans.Tour;
import abm.data.pop.Person;
import abm.io.input.CoefficientsReader;
import abm.properties.AbitResources;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.data.person.Gender;
import de.tum.bgu.msm.data.person.Occupation;
import org.apache.log4j.Logger;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

public class SplitByTypeModel implements SplitByType{

    private static final Logger logger = Logger.getLogger(SplitByTypeModel.class);

    private final DataSet dataSet;
    private Map<String, Double> coefficients;

    public SplitByTypeModel(DataSet dataSet) {
        this.dataSet = dataSet;
        this.coefficients = new CoefficientsReader(dataSet, "discAllActSplit", Path.of(AbitResources.instance.getString("act.split.type"))).readCoefficients();
    }


    @Override
    public DiscretionaryActivityType assignActivityType(Activity activity, Person person) {


        double utilityOfBeingOnMandatoryTour = calculateUtilityOfBeingOnMandatoryTour(activity, person);
        double probabilityOfBeingOnMandatoryTour = Math.exp(utilityOfBeingOnMandatoryTour) / (1 + Math.exp(utilityOfBeingOnMandatoryTour));

        long mandatoryTours = person.getPlan().getTours().values().stream().filter(t -> Purpose.getMandatoryPurposes().contains(t.getMainActivity().getPurpose())).count();
        if (AbitUtils.getRandomObject().nextDouble() < probabilityOfBeingOnMandatoryTour && mandatoryTours > 0){
            return DiscretionaryActivityType.ON_MANDATORY_TOUR;
        } else {
            if (person.getPlan().getTours().values().stream().filter(tour ->
                    Purpose.getDiscretionaryPurposes().contains(tour.getMainActivity().getPurpose())).count() == 0){
                return DiscretionaryActivityType.PRIMARY;
            } else {
                //todo the choice will based on the number of tours and the purpose order, plus some parameters to be integrated here
                if (AbitUtils.getRandomObject().nextDouble() < 0.5){
                    return DiscretionaryActivityType.ON_DISCRETIONARY_TOUR;
                } else {
                    return DiscretionaryActivityType.PRIMARY;
                }

            }
        }
    }



    public double calculateUtilityOfBeingOnMandatoryTour(Activity activity, Person person){


        double utility = 0.;

        utility += coefficients.get("(Intercept)");

        //missing area type variables here (not significant!)

        switch (person.getHousehold().getEconomicStatus()){
            case VERY_LOW:
                utility += coefficients.get("hh.econStatus_1");
                break;
            case LOW:
                utility += coefficients.get("hh.econStatus_2");
                break;
            case HIGH:
                utility += coefficients.get("hh.econStatus_3");
                break;
            case VERY_HIGH:
                utility += coefficients.get("hh.econStatus_4");
                break;
        }

        if (person.getHousehold().getPersons().stream().filter(p -> p.getOccupation().equals(Occupation.EMPLOYED)).count() == 0){
            utility += coefficients.get("hh.notEmployed");
        }

        int householdSize = person.getHousehold().getPersons().size();
        if (householdSize == 2) {
            utility += coefficients.get("hh.size_2");
        } else if (householdSize == 3) {
            utility += coefficients.get("hh.size_3");
        } else if (householdSize == 4) {
            utility += coefficients.get("hh.size_4");
        } else if ((householdSize >= 5) ) {
            utility += coefficients.get("hh.size_5");
        }

        // Number of children in household
        int householdChildren = (int) person.getHousehold().getPersons().stream().filter(p -> p.getAge() < 18).count();
        if (householdChildren == 1) {
            utility += coefficients.get("hh.children_1");
        } else if (householdChildren == 2) {
            utility += coefficients.get("hh.children_2");
        } else if (householdChildren >= 3) {
            utility += coefficients.get("hh.children_3");
        }

        int householdAdult = householdSize - householdChildren;
        if (householdAdult == 1) {
            utility += coefficients.get("hh.adults_1");
        } else if (householdAdult == 2) {
            utility += coefficients.get("hh.adults_2");
        } else if (householdAdult >= 3) {
            utility += coefficients.get("hh.adults_3");
        } else if (householdAdult >= 4) {
            utility += coefficients.get("hh.adults_4");
        }

        if (householdChildren != 0) {
            double adultsPerChild = householdAdult / householdChildren;
            if (adultsPerChild < 1) {
                utility += coefficients.get("hh.adults_per_child_0");
            } else if (adultsPerChild == 1) {
                utility += coefficients.get("hh.adults_per_child_1");
            } else {
                utility += coefficients.get("hh.adults_per_child_2");
            }
        }


        int age = person.getAge();

        if (age < 15){

        } else if (age < 25) {
            utility += coefficients.get("p.age_gr_1");
        } else if (age < 35) {
            utility += coefficients.get("p.age_gr_2");
        } else if (age < 45) {
            utility += coefficients.get("p.age_gr_3");
        } else if (age < 55) {
            utility += coefficients.get("p.age_gr_4");
        } else if (age < 65) {
            utility += coefficients.get("p.age_gr_5");
        } else {
            utility += coefficients.get("p.age_gr_6");
        }

        switch (person.getOccupation()){


            case STUDENT:
                utility += coefficients.get("p.occupationStatus_Student");
                break;
            case EMPLOYED:
                //todo move this into the person reader and then define a partTime variable status?
                final Tour workTour = person.getPlan().getTours().values().stream().
                        filter(t -> t.getMainActivity().getPurpose().equals(Purpose.WORK)).findAny().orElse(null);
                if (workTour != null){
                    if (workTour.getMainActivity().getDuration() > 6 * 60) {
                        utility += coefficients.get("p.occupationStatus_Employed");
                    } else {
                        utility += coefficients.get("p.occupationStatus_Halftime");
                    }
                }
                break;
            case UNEMPLOYED:
                utility += coefficients.get("p.occupationStatus_Unemployed");
                break;
            case RETIREE:
                //todo is this like unemployed?
                utility += coefficients.get("p.occupationStatus_Unemployed");
                break;
            case TODDLER:
                //todo is this like unemployed?
                utility += coefficients.get("p.occupationStatus_Unemployed");
                break;
        }

        if (person.getGender().equals(Gender.FEMALE)){
            utility += coefficients.get("p.female");
        }

        if (person.isHasLicense()){
            utility += coefficients.get("p.driversLicense");
        }

        if (person.hasBicycle()){
            utility += coefficients.get("p.ownBicycle");
        }

        int cars = person.getHousehold().getNumberOfCars();

        if (cars == 1){
            utility += coefficients.get("hh.cars_1");
        } else if (cars == 2){
            utility += coefficients.get("hh.cars_2");
        } else if (cars >= 2){
            utility += coefficients.get("hh.cars_3");
        }

        switch (person.getHabitualMode()){
            //todo this should be the time on the habitual mode instead, not a dummy variable!
            case BUS:
            case TRAIN:
            case TRAM_METRO:
                utility += coefficients.get("p.t_mand_habmode_PT");
                break;
            case CAR:
                utility += coefficients.get("p.t_mand_habmode_car");
                break;
            case BIKE:
                utility += coefficients.get("p.t_mand_habmode_cycle");
                break;
            case WALK:
                utility += coefficients.get("p.t_mand_habmode_walk");
                break;
            case UNKNOWN:
                throw new RuntimeException("The habitual mode cannot be unknown");
        }

        switch (activity.getPurpose()){

            case HOME:
            case WORK:
            case EDUCATION:
                throw new RuntimeException("Trying to define the type of an home or mandatory activity - this model is" +
                        " only intended for discretionary purposes");
            case ACCOMPANY:
                utility += coefficients.get("act.purpose_accompany");
                break;
            case SHOPPING:
                utility += coefficients.get("act.purpose_shop");
                break;
            case OTHER:
                utility += coefficients.get("act.purpose_other");
                break;
            case RECREATION:
                utility += coefficients.get("act.purpose_recreation");
                break;
        }

        //here there are other variables in the table, but they are not significant

        return utility;
    }

}
