package abm.models.activityGeneration.splitByType;

import abm.data.plans.Activity;
import abm.data.plans.DiscretionaryActivityType;
import abm.data.pop.Person;

public interface SplitByType {

    DiscretionaryActivityType assignActivityType(Activity activity, Person person);
}
