package abm.data.timeOfDay;

import abm.properties.InternalProperties;
import cern.colt.map.tint.OpenIntIntHashMap;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;


/**
 * This class defines a map of availability, with a key start time in minutes of the time window and a value boolean that defines
 * if the window is available (true) or not (false)
 **/

public class CarAvailableTimeOfWeekLinkedList {


    private LinkedList<Integer> internalList;
    private static final int MAX_VALUE = (int) (7 * 24 * 60);


    public CarAvailableTimeOfWeekLinkedList() {
        internalList = new LinkedList<Integer>();

        //Todo this for loop might not be needed because we want to just add the blocked time into the arraylist
//        for (int i = 0; i < MAX_VALUE; i = i + InternalProperties.SEARCH_INTERVAL_MIN) {
//            internalList.add(i);
//        }

    }

    public void blockTime(int from, int until) {
        for (int i = 0; i < MAX_VALUE; i = i + InternalProperties.SEARCH_INTERVAL_MIN) {
            if (i >= from && i <= until) {
                internalList.add(i);
            }
        }
    }

    public int isAvailable(int minute) {
        if (internalList.contains(minute)) {
            return internalList.get(minute);
        } else {
            int newIndex = Math.round(minute / InternalProperties.SEARCH_INTERVAL_MIN) * InternalProperties.SEARCH_INTERVAL_MIN;
            return internalList.get(newIndex);
        }
    }

    public boolean isAvailable(int startMinute, int endMinute) {

        for (int minute = startMinute; minute <= endMinute; minute++) {
            if (internalList.contains(minute)) {
                return Boolean.FALSE;
            } else {
                int newIndex = Math.round(minute / InternalProperties.SEARCH_INTERVAL_MIN) * InternalProperties.SEARCH_INTERVAL_MIN;
                if (internalList.contains(newIndex)) {
                    return Boolean.FALSE;
                }
            }
        }

        return Boolean.TRUE;
    }

    public List<Integer> getMinutes() {
        return new ArrayList<>(internalList);
    }


    public CarAvailableTimeOfWeekLinkedList getForThisDayOfWeek(DayOfWeek dayOfWeek) {
        int midnightBefore = (dayOfWeek.ordinal()) * 24 * 60;
        CarAvailableTimeOfWeekLinkedList availableTimeOfDay = new CarAvailableTimeOfWeekLinkedList();

        //Todo not so sure what the method is for, check with Qin
//        this.internalList.keys().forEach(m -> {
//            if (m > midnightBefore && m < midnightBefore + 60 * 24) {
//                if (this.internalList.get(m) == 1) {
//                    availableTimeOfDay.internalList.put(m, 1);
//                } else {
//                    availableTimeOfDay.internalList.put(m, 0);
//                }
//            } else {
//                availableTimeOfDay.internalList.put(m, 0);
//            }
//            return true;
//        });

        return availableTimeOfDay;
    }


    /*@Override
    public String toString() {
        StringBuilder value =  new StringBuilder();
        internalMap.values().forEach(v-> value.append(v?1:0));
        return value.toString();
    }*/

    public LinkedList<Integer> getInternalList() {
        return internalList;
    }
}
