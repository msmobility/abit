package abm.data.timeOfDay;

import abm.properties.InternalProperties;

import java.time.DayOfWeek;
import java.util.*;


/**
 * This class defines a map of availability, with a key start time in minutes of the time window and a value boolean that defines
 * if the window is available (true) or not (false)
 **/

public class AvailableTimeOfWeekLinkedList {


    private LinkedList<Integer> internalMap;
    private static final int MAX_VALUE = (int) (7 * 24 * 60);


    public AvailableTimeOfWeekLinkedList() {
        internalMap = new LinkedList<>();
//        for (int i = 0; i < MAX_VALUE; i = i + InternalProperties.SEARCH_INTERVAL_MIN) {
//            internalMap.add(i);
//        }
    }

    public void blockTime(int from, int until) {
        for (int i = 0; i < MAX_VALUE; i = i + InternalProperties.SEARCH_INTERVAL_MIN) {
            if (i >= from && i <= until) {
                if (!internalMap.contains(i)) {
                    internalMap.add(i);
                }
            }
        }
    }

    public int isAvailable(int minute) {
        if (internalMap.contains(minute)) {
            return 1;
        } else {
            int newIndex = Math.round(minute / InternalProperties.SEARCH_INTERVAL_MIN) * InternalProperties.SEARCH_INTERVAL_MIN;
            if (internalMap.contains(newIndex)) {
                return 1;
            }
        }
        return 0;
    }

    public boolean isAvailable(int startMinute, int endMinute) {


        for (int minute = startMinute; minute <= endMinute; minute++) {
            if (internalMap.contains(minute)) {

                return Boolean.FALSE;

            } else {
                int newIndex = Math.round(minute / InternalProperties.SEARCH_INTERVAL_MIN) * InternalProperties.SEARCH_INTERVAL_MIN;
                if (internalMap.contains(newIndex)) {
                    return Boolean.FALSE;
                }
            }
        }

        return Boolean.TRUE;
    }

    public List<Integer> getMinutes() {
        return internalMap;
    }


    public AvailableTimeOfWeekLinkedList getForThisDayOfWeek(DayOfWeek dayOfWeek) {
        int midnightBefore = (dayOfWeek.ordinal()) * 24 * 60;
        AvailableTimeOfWeekLinkedList availableTimeOfDay = new AvailableTimeOfWeekLinkedList();

        this.internalMap.forEach(m -> {
            if (m > midnightBefore && m < midnightBefore + 60 * 24) {
                if (this.internalMap.contains(m)) {
                    availableTimeOfDay.internalMap.add(m);
                }
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

    public LinkedList<Integer> getInternalMap() {
        return internalMap;
    }
}
