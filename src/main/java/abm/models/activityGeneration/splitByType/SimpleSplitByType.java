package abm.models.activityGeneration.splitByType;

import abm.data.plans.Activity;
import abm.data.plans.DiscretionaryActivityType;
import abm.data.pop.Person;

import java.util.Random;

public class SimpleSplitByType implements SplitByType {

    static Random random = new Random(2);

    @Override
    public DiscretionaryActivityType assignActivityType(Activity activity, Person person) {
        DiscretionaryActivityType[] values = DiscretionaryActivityType.values();
        return values[random.nextInt(values.length - 1)];
    }
}
