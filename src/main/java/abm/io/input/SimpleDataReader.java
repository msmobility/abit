package abm.io.input;

import abm.data.DataSet;
import abm.data.geo.MicroscopicLocation;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.data.travelTimes.SimpleTravelTimes;

public class SimpleDataReader {


    public DataSet readData() {
        DataSet dataSet = new DataSet();

        for (int i = 0; i < 20; i++ ){
            MicroscopicLocation homeLocation = new MicroscopicLocation(100. * i, 100. * i);
            Household household = new Household(i, homeLocation);
            Person person = new Person(i, household);
            household.getPersons().add(person);
            dataSet.getHouseholds().put(household.getId(), household);
            dataSet.getPersons().put(person.getId(), person);

        }


        dataSet.setTravelTimes(new SimpleTravelTimes());

        return dataSet;

    }

}
