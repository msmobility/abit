package abm.io.input;


import abm.data.DataSet;
import abm.data.travelInformation.MitoBasedTravelDistances;
import abm.data.travelInformation.MitoBasedTravelTimes;
import abm.properties.Resources;
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

        String carFile = Resources.instance.getString("car.omx.file");
        String carMatrixName  = Resources.instance.getString("car.omx.matrix");
        travelTimes.readSkim("car", carFile, carMatrixName, 1/60.);

        String busFile  = Resources.instance.getString("car.omx.file");
        String busMatrixName  = Resources.instance.getString("car.omx.matrix");
        travelTimes.readSkim("bus", busFile, busMatrixName, 1/60. * busFactor);

        String tramMetroFile  = Resources.instance.getString("car.omx.file");
        String tramMetroMatrixName  = Resources.instance.getString("car.omx.matrix");
        travelTimes.readSkim("tramMetro", tramMetroFile, tramMetroMatrixName, 1/60. * tramMetroFactor);

        String trainFile  = Resources.instance.getString("car.omx.file");
        String trainMatrixName  = Resources.instance.getString("car.omx.matrix");
        travelTimes.readSkim("train", trainFile, trainMatrixName, 1/60.* trainFactor );

        dataSet.setTravelTimes(new MitoBasedTravelTimes(travelTimes));

        //it is using the traveltimes but this is distance!
        SkimTravelTimes travelDistances = new SkimTravelTimes();
        String nonMotorizedFile = Resources.instance.getString("car.omx.file");
        String nonMotorizedMatrixName  = Resources.instance.getString("car.distance.omx.matrix");
        travelDistances.readSkim("non_motorized_m", nonMotorizedFile, nonMotorizedMatrixName, 1.);

        dataSet.setTravelDistance(new MitoBasedTravelDistances(travelDistances));


    }
}
