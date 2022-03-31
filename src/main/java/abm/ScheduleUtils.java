package abm;

import abm.data.plans.Activity;
import abm.data.plans.Plan;

public class ScheduleUtils {

    public static int endOfTheWeek() {
        return 7 * 24 * 3600;
    }

    public static int startOfTheWeek() {
        return 0;
    }

    boolean isAvailableForMainTour(Plan plan, Activity activity){

        return false;
    }

    boolean isAvailableForSubtour(Plan plan, Activity activity){

        return false;
    }

    boolean isAvailableForStopBefore(Plan plan, Activity activity){

        return false;
    }

    boolean isAvailableForStopAfter(Plan plan, Activity activity){

        return false;
    }


}
