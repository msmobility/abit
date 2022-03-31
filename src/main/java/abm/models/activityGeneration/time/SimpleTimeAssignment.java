package abm.models.activityGeneration.time;

import abm.Utils;
import abm.data.plans.Activity;

import java.util.Random;

public class SimpleTimeAssignment implements TimeAssignment {

    static Random random = Utils.random;


    //TODO Split into assignStartTime and assignDuration (Stops do not need start time, since it is derived from the main tour)
    @Override
    public void assignTime(Activity activity) {

        double midnight = (activity.getDayOfWeek().getValue() - 1) * 24 * 3600;
        double startTime = Math.max(0, 10 * 3600 + random.nextGaussian() * 2 * 3600);
        double duration = Math.max(60, 1 * 3600 + random.nextGaussian() * 4 * 3600);

        //Todo add a method for scheduling

        activity.setStartTime_s(midnight + startTime);
        activity.setEndTime_s(midnight + startTime + duration);

    }

    public void assignTimeToStop(Activity activity) {

        double midnight = (activity.getDayOfWeek().getValue() - 1) * 24 * 3600;
        double startTime = 0;
        double duration = Math.max(60, 1 * 3600 + random.nextGaussian() * 4 * 3600);


        //Todo add a method for scheduling

        activity.setStartTime_s(midnight + startTime);
        activity.setEndTime_s(midnight + startTime + duration);

    }
}
