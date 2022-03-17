package abm.data.geo;

import abm.data.plans.Mode;
import de.tum.bgu.msm.data.Location;

public class SimpleTravelTimes implements TravelTimes{

    @Override
    public double getTravelTimeInSeconds(Location origin, Location destination, Mode mode, double time){
        double distance_m = Math.abs(((MicroscopicLocation) origin).x - ((MicroscopicLocation)destination).x) +
                Math.abs(((MicroscopicLocation) origin).y - ((MicroscopicLocation)destination).y);

        double speed_ms = 15. / 3.6;

        return distance_m / speed_ms;
    }
}
