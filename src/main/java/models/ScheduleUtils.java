package models;

import data.plans.Activity;
import data.plans.Plan;

import java.util.Comparator;

public class ScheduleUtils {

    public static double endOfTheDay() {
        return 24 * 3600;
    }

    public static double startOfTheDay() {
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
