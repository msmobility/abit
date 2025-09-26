package abm.io.input;

import abm.data.DataSet;

public class SubtourDestinationChoiceDataReaderManager implements DataReaderManager {

    @Override
    public DataSet readData() {
        DataSet dataSet = new DataSet();
        new ZoneReader(dataSet).read();
//       new MitoTravelTimeAndDistanceReader(dataSet).read();
//
        new HouseholdReader(dataSet).read();
//        new VehicleReader(dataSet).read();
//        new JobReader(dataSet).read();
//       new SchoolReader(dataSet).read();
        new PersonReader(dataSet).read();
//       populateZones(dataSet);
//        new EconomicStatusReader(dataSet).read();
//        new CalibrationZoneToRegionTypeReader(dataSet).read();

        return dataSet;
    }
}
