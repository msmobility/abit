package abm.models.modeChoice;

import abm.data.plans.Mode;
import abm.data.plans.Tour;
import abm.data.pop.Person;

public class SimpleTourModeChoice implements TourModeChoice {
    @Override
    public Mode chooseMode(Person person, Tour tour) {
        return Mode.WALK;
    }
}
