package abm.models.modeChoice;

import abm.data.plans.Mode;
import abm.data.plans.Tour;
import abm.data.pop.Person;

public interface TourModeChoice {

    Mode chooseMode(Person person, Tour tour);
}
