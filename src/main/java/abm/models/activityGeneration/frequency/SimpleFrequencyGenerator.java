package abm.models.activityGeneration.frequency;

import abm.Utils;
import abm.data.plans.Purpose;
import abm.data.pop.Person;

import java.util.Random;

public class SimpleFrequencyGenerator implements FrequencyGenerator {
    @Override
    public int calculateNumberOfActivitiesPerWeek(Person person, Purpose purpose) {

        switch (purpose){
            case WORK:
                return 3;
            case EDUCATION:
                return 1;
            case RECREATION:
                return 5;
            case OTHER:
                return 3;
            case SHOPPING:
                return 4;
            case ACCOMPANY:
                return 6;
            default:
                throw new RuntimeException("Not possible");
        }
    }
}
