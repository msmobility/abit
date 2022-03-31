package abm.models.activityGeneration.splitByType;

import abm.Utils;
import abm.data.plans.Activity;
import abm.data.plans.StopType;
import abm.data.pop.Person;

import java.util.Random;

public class SimpleSplitStopType implements SplitStopType {

    @Override
    public StopType getStopType(Person person, Activity activity){
        boolean isBefore = Utils.getRandomObject().nextBoolean();
        if (isBefore){
          return StopType.BEFORE;
        } else {
            return StopType.AFTER;
        }
    }

}
