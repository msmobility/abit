package abm.models.actitivyGeneration.frequency;

import abm.data.DataSet;
import abm.data.geo.RegioStaR2;
import abm.data.geo.RegioStaR7;
import abm.data.geo.RegioStaRGem5;
import abm.data.geo.Zone;
import abm.data.plans.Mode;
import abm.data.plans.Plan;
import abm.data.plans.Purpose;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.data.pop.Relationship;
import abm.models.activityGeneration.frequency.FrequencyGeneratorModel;

import abm.properties.AbitResources;
import de.tum.bgu.msm.data.person.Gender;
import de.tum.bgu.msm.data.person.Occupation;
import junitx.framework.Assert;
import org.junit.Test;

import java.util.Map;

public class FrequencyGeneratorModelTest {


    @Test
    public void test(){

        AbitResources.initializeResources("C:/models/abit/abit.properties");

        DataSet dataSet = new DataSet();
        final Zone dummyZone = new Zone(1);
        dummyZone.setRegioStaR2Type(RegioStaR2.URBAN);
        dummyZone.setRegioStaRGem5Type(RegioStaRGem5.METROPOLIS);
        dummyZone.setRegioStaR7Type(RegioStaR7.URBAN_METROPOLIS);
        dataSet.getZones().put(1, dummyZone);


        Household household = new Household(1, dummyZone, 1);

        Person person = new Person(1, household, 36, Gender.FEMALE,
                Relationship.married, Occupation.EMPLOYED, true, null, 10000, null,null);
        household.getPersons().add(person);

        Plan.initializePlan(person);

        person.setHabitualMode(Mode.TRAIN);

        int trips;

        Map<Purpose, Integer> reference = Map.of(Purpose.WORK, 5,
                Purpose.EDUCATION, 0,
                Purpose.OTHER, 0,
                Purpose.ACCOMPANY, 5,
                Purpose.RECREATION, 1,
                Purpose.SHOPPING, 2);

        for (Purpose purpose : Purpose.getAllPurposes()){
            final FrequencyGeneratorModel frequencyGeneratorModel = new FrequencyGeneratorModel(dataSet, purpose);
            trips = frequencyGeneratorModel.calculateNumberOfActivitiesPerWeek(person, purpose);
            System.out.println(trips + " of purpose " + purpose);
            Assert.assertEquals((int) reference.get(purpose), trips);
        }



    }

}
