package abm.data.geo;

import abm.data.plans.Mode;
import de.tum.bgu.msm.data.Location;

public class MitoBasedTravelTimes implements TravelTimes {

    private final de.tum.bgu.msm.data.travelTimes.TravelTimes mitoTravelTimes;

    public MitoBasedTravelTimes(de.tum.bgu.msm.data.travelTimes.TravelTimes mitoTravelTimes) {
        this.mitoTravelTimes = mitoTravelTimes;
    }


    @Override
    public double getTravelTimeInSeconds(Location origin, Location destination, Mode mode, double time){
       return mitoTravelTimes.getTravelTime(origin, destination, time, mode.toString())*60;
    }







}
