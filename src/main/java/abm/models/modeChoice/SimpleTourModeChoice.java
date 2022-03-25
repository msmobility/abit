package abm.models.modeChoice;

import abm.data.plans.Mode;
import abm.data.plans.Tour;
import abm.data.pop.Person;

public class SimpleTourModeChoice implements TourModeChoice {
    @Override
    public void chooseMode(Person person, Tour tour) {
        tour.getLegs().values().forEach(leg -> {
            // Todo add logic for deciding the mode
            leg.setMode(Mode.WALK);
        });
    }
}
