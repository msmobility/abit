package abm.models.activityGeneration.frequency;

import abm.data.plans.Purpose;
import abm.data.pop.Person;

import java.util.Random;

public class SimpleFrequencyGenerator implements FrequencyGenerator {
    @Override
    public int calculateNumberOfActivitiesPerWeek(Person person, Purpose purpose) {

        Random random = new Random(0);
        return random.nextInt(7);
    }
}
