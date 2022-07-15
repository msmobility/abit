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
import org.locationtech.jts.awt.PointShapeFactory;
import org.matsim.contrib.drt.optimizer.Waypoint;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.DayOfWeek;
import java.util.*;

public class TimeAssignmentModel implements TimeAssignment {


    private final Map<Purpose, TimeOfWeekDistribution> timeOfWeekDistributionMap;
    private final Map<Purpose, Map<StartTimeInterval, DurationDistribution>> durationDistributionMap;
    private final Map<Purpose, Integer> typicalDuration;
    private final DataSet dataSet;
    private SortedSet<StartTimeInterval> startTimeIntervals = new TreeSet<>();



    public TimeAssignmentModel(DataSet dataSet) {
        this.dataSet = dataSet;
        timeOfWeekDistributionMap = new HashMap<>();
        durationDistributionMap = new HashMap<>();

        readTimeOfDayDistributions();
        readDurationDistributions();
        typicalDuration = new HashMap<>();

        calculateTypicalDurations();
    }

    private void calculateTypicalDurations() {
        //do not calculate but provide an average?
        typicalDuration.put(Purpose.WORK, 8 * 60);
        typicalDuration.put(Purpose.EDUCATION, 8 * 60);
        typicalDuration.put(Purpose.SHOPPING, 25);
        typicalDuration.put(Purpose.RECREATION, 2 * 60);
        typicalDuration.put(Purpose.ACCOMPANY, 15);
        typicalDuration.put(Purpose.OTHER,  60);

    }

    StartTimeInterval getInterval(int time_h){
        for (StartTimeInterval timeInterval : startTimeIntervals){
            if (time_h < timeInterval.to){
                return timeInterval;
            }
        }
        throw new RuntimeException("The start time is not in the day");
    }

    private class StartTimeInterval implements Comparable<Integer>{

        int from;
        int to;


        StartTimeInterval(int from, int to){
            this.from = from;
            this.to = to;
        }




        @Override
        public int compareTo(Integer o) {
            return Integer.compare(from, o);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StartTimeInterval that = (StartTimeInterval) o;
            return from == that.from && to == that.to;
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, to);
        }
    }

    private void readDurationDistributions() {
        int timeIndex;
        int purposeIndex;
        int fromIndex;
        int toIndex;
        int probabilityIndex;

        try {
            BufferedReader br = new BufferedReader(new FileReader(AbitResources.instance.getString("duration.distributions")));
            String[] firstLine = br.readLine().split(",");

            timeIndex = MitoUtil.findPositionInArray("duration_min", firstLine);
            purposeIndex = MitoUtil.findPositionInArray("purpose", firstLine);
            fromIndex = MitoUtil.findPositionInArray("start_from_h", firstLine);
            toIndex = MitoUtil.findPositionInArray("strart_to_h", firstLine);
            probabilityIndex = MitoUtil.findPositionInArray("duration_prob", firstLine);

            String line;
            while((line = br.readLine())!= null){
                int time = Integer.parseInt(line.split(",")[timeIndex]);
                Purpose purpose = Purpose.valueOf(line.split(",")[purposeIndex]);
                int from_h = Integer.parseInt(line.split(",")[fromIndex]);
                int to_h = Integer.parseInt(line.split(",")[toIndex]);

                StartTimeInterval interval = new StartTimeInterval(from_h, to_h);
                startTimeIntervals.add(interval);

                double probability = Double.parseDouble(line.split(",")[probabilityIndex]);


                durationDistributionMap.putIfAbsent(purpose, new HashMap<>());
                durationDistributionMap.get(purpose).putIfAbsent(interval, new DurationDistribution());
                durationDistributionMap.get(purpose).get(interval).setProbability(time, probability);
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
        int startTime;
        int initialDuration = 2 * 60;
        TimeOfWeekDistribution timeOfWeekDistribution = timeOfWeekDistributionMap.get(activity.getPurpose());
        AvailableTimeOfWeek availableTimeOfWeek = activity.getPerson().getPlan().getAvailableTimeOfDay();
        availableTimeOfWeek = TimeOfDayUtils.updateAvailableTimeForNextTrip(availableTimeOfWeek, initialDuration);
        timeOfWeekDistribution = TimeOfDayUtils.updateTODWithAvailability(timeOfWeekDistribution, availableTimeOfWeek);
        timeOfWeekDistribution = timeOfWeekDistribution.getForThisDayOfWeek(dayOfWeek);
        startTime = timeOfWeekDistribution.selectTime();

        int newDuration = durationDistributionMap.get(activity.getPurpose()).get(getInterval(startTime/60)).selectTime();
        int travelTime = dataSet.getTravelTimes().getTravelTimeInMinutes(activity.getPerson().getHousehold().getLocation(), activity.getLocation(),
                Mode.UNKNOWN, InternalProperties.PEAK_HOUR_MIN) * 2; //the time is not yet known!

        if (newDuration + travelTime > initialDuration){
            //tour does not fit here! Make it shorter
            newDuration = initialDuration - travelTime;
        }

        if (newDuration < 0){
            int minActDuration = 5;
            startTime = startTime - newDuration - minActDuration;
            newDuration = minActDuration;
        }

        activity.setStartTime_min(startTime);
        activity.setEndTime_min(startTime + newDuration);

    }

    public void assignDurationToStop(Activity activity) {

        int midnight = (activity.getDayOfWeek().ordinal()) * 24*60 ;
        int startTime = 0;
        //assumes a duration if starting in the morning - this would only be relevant if the stop was a mandatory activity, for discretionary tehre is probably not a differentiation of durations by start time.
        int duration = durationDistributionMap.get(activity.getPurpose()).get(startTimeIntervals.first()).selectTime();

        activity.setStartTime_min(midnight + startTime);
        activity.setEndTime_min(midnight + startTime + duration);

    }
}