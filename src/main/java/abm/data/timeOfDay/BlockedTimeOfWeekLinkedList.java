package abm.data.timeOfDay;

import abm.properties.InternalProperties;

import java.time.DayOfWeek;
import java.util.*;


/**
 * This class defines a map of availability, with a key start time in minutes of the time window and a value boolean that defines
 * if the window is available (true) or not (false)
 **/

public class BlockedTimeOfWeekLinkedList {


    private LinkedList<Integer> internalMap;
    private static final int MAX_VALUE = (int) (7 * 24 * 60);


    public BlockedTimeOfWeekLinkedList() {
        internalMap = new LinkedList<>();
//        for (int i = 0; i < MAX_VALUE; i = i + InternalProperties.SEARCH_INTERVAL_MIN) {
//            internalMap.add(i);
//        }
    }

    public void blockTime(int from, int until) {

        int startIndex = (int) (Math.floor((double) from / InternalProperties.SEARCH_INTERVAL_MIN) * InternalProperties.SEARCH_INTERVAL_MIN);
        int toIndex = (int) (Math.ceil((double) until / InternalProperties.SEARCH_INTERVAL_MIN) * InternalProperties.SEARCH_INTERVAL_MIN);

        for (int i = startIndex; i <= toIndex; i = i + InternalProperties.SEARCH_INTERVAL_MIN) {

            if (!internalMap.contains(i)) {
                internalMap.add(i);
            }

        }
    }

    public void setAvailable(int from, int to) {

        for (int minute = from; minute <= to; minute++) {
            if (internalMap.contains(minute)) {

                internalMap.remove(minute);

            }
        }


    }

    public int isAvailable(int minute) {


        if (internalMap.contains(minute)) {
            return 0;
        } else {
            int newIndex = Math.round(minute / InternalProperties.SEARCH_INTERVAL_MIN) * InternalProperties.SEARCH_INTERVAL_MIN;
            if (internalMap.contains(newIndex)) {
                return 0;
            }
        }
        return 1;
    }

    public boolean isAvailable(int startMinute, int endMinute) {

        int startIndex = (int) (Math.floor((double) startMinute / InternalProperties.SEARCH_INTERVAL_MIN) * InternalProperties.SEARCH_INTERVAL_MIN);
        int toIndex = (int) (Math.ceil((double) endMinute / InternalProperties.SEARCH_INTERVAL_MIN) * InternalProperties.SEARCH_INTERVAL_MIN);

        for (int minute = startIndex; minute <= toIndex; minute++) {
            if (internalMap.contains(minute)) {
                return Boolean.FALSE;
            }
        }
        return Boolean.TRUE;
    }

    public List<Integer> getMinutes() {
        return internalMap;
    }


    public BlockedTimeOfWeekLinkedList getForThisDayOfWeek(DayOfWeek dayOfWeek) {
        int midnightBefore = (dayOfWeek.ordinal()) * 24 * 60;
        BlockedTimeOfWeekLinkedList availableTimeOfDay = new BlockedTimeOfWeekLinkedList();

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
