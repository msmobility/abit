package abm.io.input;

import abm.data.DataSet;

public class DefaultDataReaderManager implements DataReaderManager {
    @Override
    public DataSet readData() {
        DataSet dataSet = new DataSet();
        new ZoneReader(dataSet).read();
        new MitoTravelTimeAndDistanceReader(dataSet).read();

        new HouseholdReader(dataSet).read();
        new JobReader(dataSet).read();
        new SchoolReader(dataSet).read();
        new PersonReader(dataSet).read();
        new EconomicStatusReader(dataSet).read();


        return dataSet;
    }
}
