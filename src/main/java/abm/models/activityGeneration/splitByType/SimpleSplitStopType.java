package abm.models.activityGeneration.splitByType;

import abm.data.plans.Activity;
import abm.data.plans.StopType;
import abm.data.pop.Person;

import java.util.Random;

public class SimpleSplitStopType implements SplitStopType {

    private Random random = new Random();

    @Override
    public StopType getStopType(Person person, Activity activity){
        boolean isBefore = random.nextBoolean();
        if (isBefore){
          return StopType.BEFORE;
        } else {
            return StopType.AFTER;
        }
    }

}
