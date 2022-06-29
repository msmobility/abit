package abm.models.activityGeneration.splitByType;

import abm.Utils;
import abm.data.plans.Activity;
import abm.data.plans.Leg;
import abm.data.plans.StopType;
import abm.data.plans.Tour;
import abm.data.pop.Person;
import abm.data.timeOfDay.AvailableTimeOfWeek;

import java.time.DayOfWeek;

public class SimpleSplitStopTypeWithTimeAvailability implements SplitStopType {

    private static final int SEARCH_INTERVAL = 15;

    @Override
    public StopType getStopType(Person person, Activity activity, Tour tour){

        AvailableTimeOfWeek availableTimeOfDay = person.getPlan().getAvailableTimeOfDay();

        final DayOfWeek dayOfWeek = tour.getMainActivity().getDayOfWeek();
        int midnight = dayOfWeek.ordinal() * 24*60;

        availableTimeOfDay = availableTimeOfDay.getForThisDayOfWeek(dayOfWeek);

        int tourStart_min = tour.getLegs().firstKey();
        Leg lastLeg = tour.getLegs().get(tour.getLegs().lastKey());
        int tourEnd_min = tour.getLegs().lastKey() + lastLeg.getTravelTime_min();


        //count periods before
        int nBefore = 0;
        for (int t = midnight + SEARCH_INTERVAL ; t < tourStart_min; t +=SEARCH_INTERVAL){
            if (availableTimeOfDay.isAvailable(t) == 1) {
                nBefore++;
            } else {
                break;
            }
        }

        //count periods after
        int nAfter = 0;
        for (int t = tourEnd_min + SEARCH_INTERVAL ; t < midnight + 60*24; t +=SEARCH_INTERVAL){
            if (availableTimeOfDay.isAvailable(t) == 1) {
                nAfter++;
            } else {
            break;
        }
        }


        if (nAfter + nBefore == 0){
            //System.out.println("There is no time to allocate this stop!");
            //todo for some stops there is no time available!
            return null;
        } else {
            double probabilityBefore = nBefore / (nBefore + nAfter);
            if (Utils.randomObject.nextDouble() < probabilityBefore){
                return StopType.BEFORE;
            } else {
                return StopType.AFTER;
            }

        }






    }

}
