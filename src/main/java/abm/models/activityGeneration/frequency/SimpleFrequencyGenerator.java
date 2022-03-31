package abm.models.activityGeneration.frequency;

import abm.Utils;
import abm.data.plans.Purpose;
import abm.data.pop.Person;

import java.util.Random;

public class SimpleFrequencyGenerator implements FrequencyGenerator {
    @Override
    public int calculateNumberOfActivitiesPerWeek(Person person, Purpose purpose) {

        Random random = Utils.getRandomObject();
        return random.nextInt(7);
    }
}
