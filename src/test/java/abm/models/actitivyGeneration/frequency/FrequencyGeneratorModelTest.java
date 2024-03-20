package abm.models.actitivyGeneration.frequency;

import abm.data.DataSet;
import abm.data.geo.RegioStaR2;
import abm.data.geo.RegioStaR7;
import abm.data.geo.RegioStaRGem5;
import abm.data.geo.Zone;
import abm.data.plans.HabitualMode;
import abm.data.plans.Mode;
import abm.data.plans.Plan;
import abm.data.plans.Purpose;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.data.pop.Relationship;
import abm.models.activityGeneration.frequency.FrequencyGeneratorModel;

import abm.properties.AbitResources;
import de.tum.bgu.msm.data.person.Disability;
import de.tum.bgu.msm.data.person.Gender;
import de.tum.bgu.msm.data.person.Occupation;
import junitx.framework.Assert;
import org.junit.Test;
import org.apache.log4j.Logger;

import java.util.Map;

public class FrequencyGeneratorModelTest {

    private Logger logger = Logger.getLogger(FrequencyGeneratorModelTest.class);

    @Test
    public void test() {

        //Initialize the properties file
        AbitResources.initializeResources("C:/models/abit_standalone/abit.properties");

        //Create dummy person, household, and zone
        DataSet dataSet = new DataSet();
        final Zone dummyZone = new Zone(1);
        dummyZone.setRegioStaR2Type(RegioStaR2.URBAN);
        dummyZone.setRegioStaRGem5Type(RegioStaRGem5.METROPOLIS);
        dummyZone.setRegioStaR7Type(RegioStaR7.URBAN_METROPOLIS);
        dataSet.getZones().put(1, dummyZone);

        Household household = new Household(1, dummyZone, 1);

        Person person = new Person(1, household, 36, Gender.FEMALE,
                Relationship.married, Occupation.EMPLOYED, true, null, 10000,
                480, 480, 2000, null, Disability.WITHOUT);
        household.getPersons().add(person);

        Plan.initializePlan(person);

        person.setHabitualMode(HabitualMode.PT);

        double probOfActivity;

        Map<Purpose, Double> reference = Map.of(
                Purpose.WORK, 5.5,
                Purpose.EDUCATION, 0.0,
                Purpose.OTHER, 0.0,
                Purpose.ACCOMPANY, 5.0,
                Purpose.RECREATION, 1.0,
                Purpose.SHOPPING, 2.0
        );

        //Run the model
        for (Purpose purpose : Purpose.getAllPurposes()) {
            final FrequencyGeneratorModel frequencyGeneratorModel = new FrequencyGeneratorModel(dataSet, purpose);
            probOfActivity = frequencyGeneratorModel.calculateProbabilityOfNumberOfActivitiesPerWeek(person, purpose);
            logger.info(probOfActivity + "% probability of purpose " + purpose);
            //Compare probabilities of the reference with the model output
            Assert.assertEquals((double) reference.get(purpose), probOfActivity);
        }
    }
}
