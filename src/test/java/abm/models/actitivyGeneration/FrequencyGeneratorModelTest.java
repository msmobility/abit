package abm.models.actitivyGeneration;

import abm.data.DataSet;
import abm.data.geo.Zone;
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

public class FrequencyGeneratorModelTest {


    @Test
    public void test(){

        AbitResources.initializeResources("PATH_TO_PROPERTIES");

        DataSet dataSet = new DataSet();
        final Zone dummyZone = new Zone(1);
        dataSet.getZones().put(1, dummyZone);


        Household household = new Household(1, dummyZone, 1);

        Person person = new Person(1, household, 36, Gender.FEMALE,
                Relationship.married, Occupation.EMPLOYED, true, null, 10000, null);

        int trips;

        trips = new FrequencyGeneratorModel(dataSet, Purpose.WORK).calculateNumberOfActivitiesPerWeek(person, Purpose.WORK);

        Assert.assertEquals(1, trips);

    }

}
