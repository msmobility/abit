package abm.io.input;

import abm.data.DataSet;
import abm.data.geo.MicroscopicLocation;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.data.travelInformation.SimpleTravelTimes;

public class SimpleDataReaderManager implements DataReaderManager {


    @Override
    public DataSet readData() {
        DataSet dataSet = new DataSet();

        int numberOfHouseholds = 100;
        for (int i = 0; i < numberOfHouseholds; i++ ){
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
