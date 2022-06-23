package abm.data.timeOfDay;

import de.tum.bgu.msm.util.MitoUtil;

import java.time.DayOfWeek;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;


/**
 * Defines the probability distribution of starting a trip or of the duration of a trip.
 * Defined by a map with key the starting time of the time window and valua a double indicating probability
 */
public class TimeOfWeekDistribution {

    private SortedMap<Integer, Double> internalMap;
    private static final int INTERVAL_MIN = 15;
    private static final int MAP_SIZE = (int) (7 * 24 * 60 / INTERVAL_MIN);


    public TimeOfWeekDistribution() {
        internalMap = new TreeMap<>();
        for (int i = 0; i < MAP_SIZE; i = i+ INTERVAL_MIN) {
            internalMap.put(i, 0.);
        }
    }

    public void setProbability(int minute, double probability) {
        if (internalMap.containsKey(minute)) {
            internalMap.put(minute, probability);
        } else {

        }
    }


    public double probability(int minute) {
        if (internalMap.containsKey(minute)) {
            return internalMap.get(minute);
        } else {
            int newIndex = Math.round(minute/INTERVAL_MIN) * INTERVAL_MIN;
            return internalMap.get(newIndex);
        }


    }

    public int selectTime() {
        if (MitoUtil.getSum(internalMap.values()) > 0){
            return MitoUtil.select(internalMap);
        } else {
            return -1;
        }
    }

    public List<Integer> getMinutes() {
        return internalMap.keySet().stream().collect(Collectors.toList());
    }

    public TimeOfWeekDistribution getForThisDayOfWeek(DayOfWeek dayOfWeek){
        int midnightBefore = (dayOfWeek.getValue() - 1) * 60;
        TimeOfWeekDistribution timeOfDayDistribution = new TimeOfWeekDistribution();
        this.internalMap.keySet().forEach(m ->{
            if (m > midnightBefore && m < midnightBefore + 60 * 24){
                timeOfDayDistribution.setProbability(m, this.internalMap.get(m));
            } else {
                timeOfDayDistribution.setProbability(m, 0.);
            }
        });

        return timeOfDayDistribution;
    }

}
