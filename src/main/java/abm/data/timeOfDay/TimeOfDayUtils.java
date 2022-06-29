package abm.data.timeOfDay;


public class TimeOfDayUtils {

    private static final int SEARCH_INTERVAL_MIN = 15;
    private static final int MAX_VALUE = 7 * 24 * 60;


    /**
     * Converts the departure time distribution to a modified version of it where non-available time windows have a probability of zero
     * @param originalTOD
     * @param availableTOD
     * @return
     */
    public static TimeOfWeekDistribution updateTODWithAvailability(TimeOfWeekDistribution originalTOD,
                                                                  AvailableTimeOfWeek availableTOD) {

        TimeOfWeekDistribution newTOD = new TimeOfWeekDistribution();
        for (int minute : originalTOD.getMinutes()) {
            newTOD.setProbability(minute, originalTOD.probability(minute) * (double) availableTOD.isAvailable(minute));
        }
        return newTOD;
    }


    /**
     * Converts the available time object to a new one that can accomodate a trip of certain duration. It avoids starting a trip that will overlap
     * with an already existing one
     * @param baseAvailableTOD
     * @param tripDuration
     * @return
     */
    public static AvailableTimeOfWeek updateAvailableTimeForNextTrip(AvailableTimeOfWeek baseAvailableTOD,
                                                                     int tripDuration) {

        AvailableTimeOfWeek newAvailableTOD = new AvailableTimeOfWeek();

        for (int minute = SEARCH_INTERVAL_MIN; minute < MAX_VALUE; minute = minute + SEARCH_INTERVAL_MIN) {
            if (baseAvailableTOD.isAvailable(minute) == 0 && baseAvailableTOD.isAvailable(minute - SEARCH_INTERVAL_MIN) == 1){
                newAvailableTOD.blockTime(Math.max(0, minute - tripDuration), minute);
            } else if (baseAvailableTOD.isAvailable(minute) == 0) {
                newAvailableTOD.blockTime(minute - SEARCH_INTERVAL_MIN, minute);
            }
        }
        return newAvailableTOD;
    }


}
