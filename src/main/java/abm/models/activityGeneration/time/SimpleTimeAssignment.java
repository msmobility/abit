package abm.models.activityGeneration.time;

import abm.Utils;
import abm.data.plans.Activity;

import java.util.Random;

public class SimpleTimeAssignment implements TimeAssignment {



    //TODO Split into assignStartTime and assignDuration (Stops do not need start time, since it is derived from the main tour)
    @Override
    public void assignTime(Activity activity) {

        int midnight = (activity.getDayOfWeek().getValue() - 1) * 24 * 3600;
        int startTime = (int) Math.max(0, 10 * 3600 + Utils.getRandomObject().nextGaussian() * 2 * 3600);
        int duration = (int) Math.max(60, 1 * 3600 + Utils.getRandomObject().nextGaussian() * 4 * 3600);

        //Todo add a method for scheduling
        activity.setStartTime_s(midnight + startTime);
        activity.setEndTime_s(midnight +  startTime + duration);

    }

    public void assignTimeToStop(Activity activity) {

        int midnight = (activity.getDayOfWeek().getValue() - 1) * 24 * 3600;
        int startTime = 0;
        int duration = (int) Math.max(60, 1 * 3600 + Utils.getRandomObject().nextGaussian() * 4 * 3600);

        //Todo add a method for scheduling
        activity.setStartTime_s(midnight + startTime);
        activity.setEndTime_s(midnight + startTime + duration);

    }
}
