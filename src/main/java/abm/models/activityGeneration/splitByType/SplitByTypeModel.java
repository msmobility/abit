package abm.models.activityGeneration.splitByType;

import abm.data.plans.Activity;
import abm.data.plans.DiscretionaryActivityType;
import abm.data.plans.Purpose;
import abm.data.pop.Person;
import abm.utils.AbitUtils;

public class SplitByTypeModel implements SplitByType{


    @Override
    public DiscretionaryActivityType assignActivityType(Activity activity, Person person) {


        double utilityOfBeingOnMandatoryTour = calculateUtilityOfBeingOnMandatoryTour(activity, person);

        double probabilityOfBeingOnMandatoryTour = Math.exp(utilityOfBeingOnMandatoryTour);

        if (AbitUtils.getRandomObject().nextDouble() < probabilityOfBeingOnMandatoryTour){
            return DiscretionaryActivityType.ON_MANDATORY_TOUR;
        } else {
            if (person.getPlan().getTours().values().stream().filter(tour ->
                    Purpose.getDiscretionaryPurposes().contains(tour.getMainActivity().getPurpose())).count() == 0){
                return DiscretionaryActivityType.PRIMARY;
            } else {
                //do the choice based on the number of tours and the purpose order, plus some coefficients
                return null;
            }
        }
    }



    double calculateUtilityOfBeingOnMandatoryTour(Activity activity, Person person){
        return 0.;
    }

}
