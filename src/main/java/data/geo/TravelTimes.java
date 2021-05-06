package data.geo;

import data.plans.Mode;

public class TravelTimes {

    public static double getTravelTimeInSeconds(Location origin, Location destination, Mode mode, double time){
        double distance_m = Math.abs(origin.getCoordinates().getX() - destination.getCoordinates().getX()) +
                Math.abs(origin.getCoordinates().getY() - destination.getCoordinates().getY());

        double speed_ms = 15. / 3.6;

        return distance_m / speed_ms;
    }
}
