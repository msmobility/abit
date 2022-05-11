package abm.models.activityGeneration.time;

import abm.data.plans.Purpose;

import java.time.DayOfWeek;

public class SimpleDayOfWeekMandatoryAssignment implements DayOfWeekMandatoryAssignment {
    @Override
    public DayOfWeek[] assignDaysOfWeek(int numberOfActivities, Purpose purpose) {
        //Todo read Corin's table and return the DaysOfWeek

        switch (purpose){
            case WORK:
                return new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY};
            case EDUCATION:
                return new DayOfWeek[]{DayOfWeek.FRIDAY};
            default:
                throw new RuntimeException("Not possible");
        }



    }
}
