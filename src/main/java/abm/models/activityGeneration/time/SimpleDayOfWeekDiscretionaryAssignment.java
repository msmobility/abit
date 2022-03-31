package abm.models.activityGeneration.time;

import abm.Utils;
import abm.data.plans.Activity;

import java.time.DayOfWeek;
import java.util.Random;

public class SimpleDayOfWeekDiscretionaryAssignment implements DayOfWeekDiscretionaryAssignment {

    @Override
    public void assignDayOfWeek(Activity activity) {
        activity.setDayOfWeek(DayOfWeek.of(Utils.getRandomObject().nextInt(6) + 1));
    }
}
