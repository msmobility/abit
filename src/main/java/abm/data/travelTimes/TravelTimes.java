package abm.data.travelTimes;

import abm.data.geo.Location;
import abm.data.plans.Mode;

public interface TravelTimes {
    int getTravelTimeInMinutes(Location origin, Location destination, Mode mode, double time);
}
