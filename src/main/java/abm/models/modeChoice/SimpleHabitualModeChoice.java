package abm.models.modeChoice;

import abm.data.plans.Mode;
import abm.data.pop.Person;

public class SimpleHabitualModeChoice implements HabitualModeChoice{
    @Override
    public void chooseHabitualMode(Person person) {
        person.setHabitualMode(Mode.WALK);
    }
}
