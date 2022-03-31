package abm.models.activityGeneration.splitByType;

import abm.Utils;
import abm.data.plans.Activity;
import abm.data.plans.DiscretionaryActivityType;
import abm.data.plans.PlanTools;
import abm.data.pop.Person;

import java.util.*;

public class SimpleSplitByType implements SplitByType {

    @Override
    public DiscretionaryActivityType assignActivityType(Activity activity, Person person) {

        List<DiscretionaryActivityType> discretionaryActivityTypeSet = new ArrayList<>();
        discretionaryActivityTypeSet.add(DiscretionaryActivityType.PRIMARY);

        if (PlanTools.findMandatoryTour(person.getPlan()) != null ){
            discretionaryActivityTypeSet.add(DiscretionaryActivityType.ON_MANDATORY_TOUR);
        }
        if (PlanTools.findDiscretionaryTour(person.getPlan()) != null ){
            discretionaryActivityTypeSet.add(DiscretionaryActivityType.ON_DISCRETIONARY_TOUR);
        }

        Collections.shuffle(discretionaryActivityTypeSet, Utils.getRandomObject());

        return discretionaryActivityTypeSet.stream().findFirst().get();
    }
}
