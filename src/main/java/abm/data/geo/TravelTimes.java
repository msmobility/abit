package abm.data.geo;

import abm.data.plans.Mode;
import de.tum.bgu.msm.data.Location;

public interface TravelTimes {
    double getTravelTimeInSeconds(Location origin, Location destination, Mode mode, double time);
}
