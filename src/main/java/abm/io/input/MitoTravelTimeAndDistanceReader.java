package abm.io.input;


import abm.data.DataSet;
import abm.data.travelInformation.MitoBasedTravelDistances;
import abm.data.travelInformation.MitoBasedTravelTimes;
import abm.properties.AbitResources;
import de.tum.bgu.msm.data.travelTimes.SkimTravelTimes;

public class MitoTravelTimeAndDistanceReader implements Reader {


    private final DataSet dataSet;

    public MitoTravelTimeAndDistanceReader(DataSet dataSet) {
        this.dataSet = dataSet;
    }


    @Override
    public void read() {

        //todo temporary factors for testing
        double busFactor = 0.35;
        double tramMetroFactor = 0.6;
        double trainFactor = 0.75;


        SkimTravelTimes travelTimes = new SkimTravelTimes();

        String carFile = AbitResources.instance.getString("car.omx.file");
        String carMatrixName  = AbitResources.instance.getString("car.omx.matrix");
        travelTimes.readSkim("car", carFile, carMatrixName, 1/60.);

        String busFile  = AbitResources.instance.getString("car.omx.file");
        String busMatrixName  = AbitResources.instance.getString("car.omx.matrix");
        travelTimes.readSkim("bus", busFile, busMatrixName, 1/60. * busFactor);

        String tramMetroFile  = AbitResources.instance.getString("car.omx.file");
        String tramMetroMatrixName  = AbitResources.instance.getString("car.omx.matrix");
        travelTimes.readSkim("tramMetro", tramMetroFile, tramMetroMatrixName, 1/60. * tramMetroFactor);

        String trainFile  = AbitResources.instance.getString("car.omx.file");
        String trainMatrixName  = AbitResources.instance.getString("car.omx.matrix");
        travelTimes.readSkim("train", trainFile, trainMatrixName, 1/60.* trainFactor );

        dataSet.setTravelTimes(new MitoBasedTravelTimes(travelTimes));

        //it is using the traveltimes but this is distance!
        SkimTravelTimes travelDistances = new SkimTravelTimes();
        String nonMotorizedFile = AbitResources.instance.getString("car.omx.file");
        String nonMotorizedMatrixName  = AbitResources.instance.getString("car.distance.omx.matrix");
        travelDistances.readSkim("non_motorized_m", nonMotorizedFile, nonMotorizedMatrixName, 1.);

        dataSet.setTravelDistances(new MitoBasedTravelDistances(travelDistances));


    }
}
