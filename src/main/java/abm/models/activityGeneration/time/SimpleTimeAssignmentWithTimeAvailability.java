package abm.models.activityGeneration.time;

import abm.Utils;
import abm.data.plans.Activity;
import abm.data.timeOfDay.AvailableTimeOfWeek;
import abm.data.timeOfDay.TimeOfDayUtils;
import abm.data.timeOfDay.TimeOfWeekDistribution;

import java.time.DayOfWeek;

public class SimpleTimeAssignmentWithTimeAvailability implements TimeAssignment {



    //TODO Split into assignStartTime and assignDuration (Stops do not need start time, since it is derived from the main tour)
    @Override
    public void assignStartTimeAndDuration(Activity activity) {

        final DayOfWeek dayOfWeek = activity.getDayOfWeek();

        //define duration
        int duration = 100;

        //add travel to duration
        duration += 2 * 30;

        //get the available time for new tours
        AvailableTimeOfWeek availableTimeOfWeek = activity.getPerson().getPlan().getAvailableTimeOfDay();
        availableTimeOfWeek = availableTimeOfWeek.getForThisDayOfWeek(dayOfWeek);


        //get the departure probabilities
        TimeOfWeekDistribution timeOfWeekDistribution = new TimeOfWeekDistribution();
        timeOfWeekDistribution = timeOfWeekDistribution.getForThisDayOfWeek(dayOfWeek);
        //convert to the selected day

        //update the probabilities with the availability and expected tour duration
        availableTimeOfWeek = TimeOfDayUtils.updateAvailableTimeForNextTrip(availableTimeOfWeek, duration);
        timeOfWeekDistribution = TimeOfDayUtils.updateTODWithAvailability(timeOfWeekDistribution, availableTimeOfWeek);

        int startTime = timeOfWeekDistribution.selectTime();
        int midnight = (activity.getDayOfWeek().getValue() - 1) * 24 * 3600/60;


        activity.setStartTime_min(midnight + startTime);
        activity.setEndTime_min(midnight +  startTime + duration);

    }

    public void assignDurationToStop(Activity activity) {

        int midnight = (activity.getDayOfWeek().getValue() - 1) * 24 * 3600;
        int startTime = 0;
        int duration = (int) Math.max(60, 1 * 3600 + Utils.getRandomObject().nextGaussian() * 4 * 3600);

        //Todo add a method for scheduling
        activity.setStartTime_min(midnight + startTime);
        activity.setEndTime_min(midnight + startTime + duration);

    }
}
