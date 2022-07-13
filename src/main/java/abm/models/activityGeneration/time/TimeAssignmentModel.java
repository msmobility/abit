package abm.models.activityGeneration.time;

import abm.data.DataSet;
import abm.data.plans.Activity;
import abm.data.plans.Mode;
import abm.data.plans.Purpose;
import abm.data.timeOfDay.AvailableTimeOfWeek;
import abm.data.timeOfDay.DurationDistribution;
import abm.data.timeOfDay.TimeOfDayUtils;
import abm.data.timeOfDay.TimeOfWeekDistribution;
import abm.properties.AbitResources;
import abm.properties.InternalProperties;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.util.MitoUtil;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.Map;

public class TimeAssignmentModel implements TimeAssignment {


    private final Map<Purpose, TimeOfWeekDistribution> timeOfWeekDistributionMap;
    private final Map<Purpose, DurationDistribution> durationDistributionMap;
    private final DataSet dataSet;


    public TimeAssignmentModel(DataSet dataSet) {
        this.dataSet = dataSet;
        timeOfWeekDistributionMap = new HashMap<>();
        durationDistributionMap = new HashMap<>();

        //todo for now, the two distributions are independent, but one will depend on the other
        readTimeOfDayDistributions();
        readDurationDistributions();
    }

    private void readDurationDistributions() {
        int timeIndex;
        int purposeIndex;
        int probabilityIndex;

        try {
            BufferedReader br = new BufferedReader(new FileReader(AbitResources.instance.getString("duration.distributions")));
            String[] firstLine = br.readLine().split(",");

            timeIndex = MitoUtil.findPositionInArray("time", firstLine);
            purposeIndex = MitoUtil.findPositionInArray("purpose", firstLine);
            probabilityIndex = MitoUtil.findPositionInArray("probability", firstLine);

            String line;
            while((line = br.readLine())!= null){
                int time = Integer.parseInt(line.split(",")[timeIndex]);
                Purpose purpose = Purpose.valueOf(line.split(",")[purposeIndex]);
                double probability = Double.parseDouble(line.split(",")[probabilityIndex]);

                durationDistributionMap.putIfAbsent(purpose, new DurationDistribution());
                durationDistributionMap.get(purpose).setProbability(time, probability);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void readTimeOfDayDistributions() {

        int timeIndex;
        int purposeIndex;
        int probabilityIndex;

        try {
            BufferedReader br = new BufferedReader(new FileReader(AbitResources.instance.getString("start.time.distributions")));
            String[] firstLine = br.readLine().split(",");

            timeIndex = MitoUtil.findPositionInArray("time", firstLine);
            purposeIndex = MitoUtil.findPositionInArray("purpose", firstLine);
            probabilityIndex = MitoUtil.findPositionInArray("probability", firstLine);

            String line;
            while((line = br.readLine())!= null){
                int time = Integer.parseInt(line.split(",")[timeIndex]);
                Purpose purpose = Purpose.valueOf(line.split(",")[purposeIndex]);
                double probability = Double.parseDouble(line.split(",")[probabilityIndex]);

                timeOfWeekDistributionMap.putIfAbsent(purpose, new TimeOfWeekDistribution());
                timeOfWeekDistributionMap.get(purpose).setProbability(time, probability);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    @Override
    public void assignStartTimeAndDuration(Activity activity) {

        final DayOfWeek dayOfWeek = activity.getDayOfWeek();

        //define duration
        int duration = durationDistributionMap.get(activity.getPurpose()).selectTime();

        //add travel to duration
        duration += dataSet.getTravelTimes().getTravelTimeInMinutes(activity.getPerson().getHousehold().getLocation(), activity.getLocation(),
                Mode.UNKNOWN, InternalProperties.PEAK_HOUR_MIN) * 2; //the time is not yet known!

        //get the available time for new tours
        AvailableTimeOfWeek availableTimeOfWeek = activity.getPerson().getPlan().getAvailableTimeOfDay();
        availableTimeOfWeek = TimeOfDayUtils.updateAvailableTimeForNextTrip(availableTimeOfWeek, duration);
        availableTimeOfWeek = availableTimeOfWeek.getForThisDayOfWeek(dayOfWeek);


        //get the departure probabilities
        TimeOfWeekDistribution timeOfWeekDistribution = timeOfWeekDistributionMap.get(activity.getPurpose());

        //convert to the selected day
        timeOfWeekDistribution = timeOfWeekDistribution.getForThisDayOfWeek(dayOfWeek);

        //update the probabilities with the availability and expected tour duration
        timeOfWeekDistribution = TimeOfDayUtils.updateTODWithAvailability(timeOfWeekDistribution, availableTimeOfWeek);


        int startTime;
        startTime = timeOfWeekDistribution.selectTime();

        //int midnight = (activity.getDayOfWeek().getValue() - 1) * 24 * 3600/60;

        activity.setStartTime_min(startTime);
        activity.setEndTime_min(startTime + duration);

    }

    public void assignDurationToStop(Activity activity) {

        int midnight = (activity.getDayOfWeek().ordinal()) * 24*60 ;
        int startTime = 0;
        int duration = durationDistributionMap.get(activity.getPurpose()).selectTime();

        activity.setStartTime_min(midnight + startTime);
        activity.setEndTime_min(midnight + startTime + duration);

    }
}
