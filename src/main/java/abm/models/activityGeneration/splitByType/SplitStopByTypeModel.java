package abm.models.activityGeneration.splitByType;

import abm.data.plans.*;
import abm.data.timeOfDay.TimeOfWeekDistribution;
import abm.properties.AbitResources;
import abm.properties.InternalProperties;
import abm.utils.AbitUtils;
import abm.data.pop.Person;
import abm.data.timeOfDay.AvailableTimeOfWeek;
import de.tum.bgu.msm.util.MitoUtil;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.Map;

public class SplitStopByTypeModel implements SplitStopType {

    private int counterErrors = 0;
    private final Map<Purpose, TimeOfWeekDistribution> timeOfWeekDistributionMap;

    public SplitStopByTypeModel() {
        timeOfWeekDistributionMap = new HashMap<>();
        readTimeOfDayDistributions();
    }

    private void readTimeOfDayDistributions() {

        int timeIndex;
        int purposeIndex;
        int probabilityIndex;

        try {
            BufferedReader br = new BufferedReader(new FileReader(AbitResources.instance.getString("start.time.distributions")));
            String[] firstLine = br.readLine().split(",");

            timeIndex = MitoUtil.findPositionInArray("time_week_min", firstLine);
            purposeIndex = MitoUtil.findPositionInArray("purpose", firstLine);
            probabilityIndex = MitoUtil.findPositionInArray("start_time_prob", firstLine);

            String line;
            while ((line = br.readLine()) != null) {
                int time = Integer.parseInt(line.split(",")[timeIndex]);
                Purpose purpose = Purpose.valueOf(line.split(",")[purposeIndex].toUpperCase());
                double probability = Double.parseDouble(line.split(",")[probabilityIndex]);

                timeOfWeekDistributionMap.putIfAbsent(purpose, new TimeOfWeekDistribution());
                timeOfWeekDistributionMap.get(purpose).setProbability(time, probability);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public StopType getStopType(Person person, Activity activity, Tour tour) {

        AvailableTimeOfWeek availableTimeOfDay = person.getPlan().getAvailableTimeOfDay();

        final DayOfWeek dayOfWeek = tour.getMainActivity().getDayOfWeek();
        int midnight = dayOfWeek.ordinal() * 24 * 60;

        availableTimeOfDay = availableTimeOfDay.getForThisDayOfWeek(dayOfWeek);

        int tourStart_min = tour.getLegs().firstKey();
        Leg lastLeg = tour.getLegs().get(tour.getLegs().lastKey());
        int tourEnd_min = tour.getLegs().lastKey() + lastLeg.getTravelTime_min();


        //count periods before
        int nBefore = 0;
        double pBefore = 0;
        for (int t = tourStart_min - InternalProperties.SEARCH_INTERVAL_MIN; t > midnight; t -= InternalProperties.SEARCH_INTERVAL_MIN) {
            if (availableTimeOfDay.isAvailable(t) == 1) {
                nBefore++;
                pBefore += timeOfWeekDistributionMap.get(activity.getPurpose()).probability(t);
            } else {
                //until it finds the previous blocked time
                break;
            }
        }

        //count periods after
        int nAfter = 0;
        double pAfter = 0;
        for (int t = tourEnd_min + InternalProperties.SEARCH_INTERVAL_MIN; t < midnight + 60 * 24; t += InternalProperties.SEARCH_INTERVAL_MIN) {
            if (availableTimeOfDay.isAvailable(t) == 1) {
                nAfter++;
                pAfter += timeOfWeekDistributionMap.get(activity.getPurpose()).probability(t);
            } else {
                //until it finds the next blocked time
                break;
            }
        }


        if (nAfter * 15 > activity.getDuration() && nBefore * 15 > activity.getDuration()) {
            double probabilityBefore = pBefore / (pBefore + pAfter);
            if (AbitUtils.randomObject.nextDouble() < probabilityBefore) {
                return StopType.BEFORE;
            } else {
                return StopType.AFTER;
            }

        } else if (nAfter * 15 > activity.getDuration()) {
            return StopType.AFTER;
        } else if (nBefore * 15 > activity.getDuration()) {
            return StopType.BEFORE;
        } else {
            counterErrors++;
            //System.out.println("Cannot allocate this stop: n = " + counterErrors);
            return null;
        }


    }

}
