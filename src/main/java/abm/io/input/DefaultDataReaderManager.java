package abm.io.input;

import abm.data.DataSet;

public class DefaultDataReaderManager implements DataReaderManager {
    @Override
    public DataSet readData() {
        DataSet dataSet = new DataSet();

        new MitoTravelTimeAndDistanceReader(dataSet).read();

        return dataSet;
    }
}