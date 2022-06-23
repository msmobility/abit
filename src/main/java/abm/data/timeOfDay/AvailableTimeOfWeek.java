package abm.data.timeOfDay;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;


/**
This class defines a map of availability, with a key start time in minutes of the time window and a value boolean that defines
if the window is available (true) or not (false)
 **/

public class AvailableTimeOfWeek {

    private SortedMap<Integer, Boolean> internalMap;
    private static final int INTERVAL_MIN = 15;
    private static final int MAP_SIZE = (int) (7 * 24 * 60 / INTERVAL_MIN);


    public AvailableTimeOfWeek() {
        internalMap = new TreeMap<>();
        for (int i = 0; i < MAP_SIZE; i = i + INTERVAL_MIN) {
            internalMap.put(i, true);
        }
    }

    public void blockTime(int from, int until) {
        for (int i = 0; i < MAP_SIZE; i = i + INTERVAL_MIN) {
            if (i >= from && i <= until) {
                internalMap.put(i, false);
            }
        }
    }

    public int isAvailable(int minute) {
        if (internalMap.containsKey(minute)) {
            return internalMap.get(minute) ? 1 : 0;
        } else {
            int newIndex = Math.round(minute/INTERVAL_MIN) * INTERVAL_MIN;
            return internalMap.get(newIndex) ? 1 : 0;
        }
    }

    public List<Integer> getMinutes() {
        return new ArrayList<>(internalMap.keySet());
    }


    public AvailableTimeOfWeek getForThisDayOfWeek(DayOfWeek dayOfWeek){
        int midnightBefore = (dayOfWeek.getValue() - 1) * 60;
        AvailableTimeOfWeek availableTimeOfDay = new AvailableTimeOfWeek();
        this.internalMap.keySet().forEach(m ->{
            if (m > midnightBefore && m < midnightBefore + 60 * 24){
                //do nothing
            } else {
                availableTimeOfDay.blockTime(m, m+INTERVAL_MIN);
            }
        });

        return availableTimeOfDay;
    }


    /*@Override
    public String toString() {
        StringBuilder value =  new StringBuilder();
        internalMap.values().forEach(v-> value.append(v?1:0));
        return value.toString();
    }*/
}
