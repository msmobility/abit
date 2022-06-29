package abm.io.input;

import abm.data.DataSet;
import abm.data.geo.MicroscopicLocation;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.data.travelInformation.SimpleTravelTimes;
import abm.utils.AbitUtils;

public class SimpleDataReaderManager implements DataReaderManager {


    private int numberOfHouseholds;

    public SimpleDataReaderManager(int numberOfHouseholds) {
        this.numberOfHouseholds = numberOfHouseholds;
    }

    @Override
    public DataSet readData() {
        DataSet dataSet = new DataSet();


        for (int i = 0; i < numberOfHouseholds; i++ ){
            MicroscopicLocation homeLocation = new MicroscopicLocation((AbitUtils.randomObject.nextDouble() - 0.5) * 1000,
                    (AbitUtils.randomObject.nextDouble() - 0.5));
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
