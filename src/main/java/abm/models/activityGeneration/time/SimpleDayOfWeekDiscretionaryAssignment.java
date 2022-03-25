package abm.models.activityGeneration.time;

import abm.data.plans.Activity;

import java.time.DayOfWeek;
import java.util.Random;

public class SimpleDayOfWeekDiscretionaryAssignment implements DayOfWeekDiscretionaryAssignment {

    static Random random = new Random(1);

    @Override
    public void assignDayOfWeek(Activity activity) {
        activity.setDayOfWeek(DayOfWeek.of(random.nextInt(6) + 1));
    }
}
