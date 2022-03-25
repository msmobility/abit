package abm.models.destinationChoice;

import abm.Utils;
import abm.data.geo.Location;
import abm.data.geo.MicroscopicLocation;
import abm.data.plans.Activity;
import abm.data.pop.Person;

import java.util.Random;

public class SimpleDestinationChoice implements DestinationChoice{

    Random rmd = Utils.random;

    @Override
    public void selectMainActivityDestination(Person person, Activity activity) {
        MicroscopicLocation tempLocation = new MicroscopicLocation(rmd.nextDouble() * 10_000, rmd.nextDouble() * 10_000);
        activity.setLocation(tempLocation);
    }

    @Override
    public void selectStopDestination(Person person, Activity previousActivity, Activity activity, Activity followingActivity) {
        MicroscopicLocation tempLocation = new MicroscopicLocation(rmd.nextDouble() * 10_000, rmd.nextDouble() * 10_000);
        activity.setLocation(tempLocation);
    }
}
