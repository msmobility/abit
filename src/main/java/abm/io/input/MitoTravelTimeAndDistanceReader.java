package abm.io.input;


import abm.data.DataSet;
import abm.data.geo.Zone;
import abm.data.plans.Mode;
import abm.data.travelInformation.MitoBasedTravelDistances;
import abm.data.travelInformation.MitoBasedTravelTimes;
import abm.properties.AbitResources;
import de.tum.bgu.msm.data.MitoZone;
import de.tum.bgu.msm.data.travelDistances.MatrixTravelDistances;
import de.tum.bgu.msm.data.travelTimes.SkimTravelTimes;
import de.tum.bgu.msm.io.input.AbstractParquetReader;
import de.tum.bgu.msm.io.input.readers.OmxSkimsReader;
import de.tum.bgu.msm.io.input.readers.ParquetSkimsReader;
import de.tum.bgu.msm.resources.Properties;
import de.tum.bgu.msm.resources.Resources;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix2D;
import org.apache.hadoop.yarn.nodelabels.StringAttributeValue;

import java.util.Collection;

public class MitoTravelTimeAndDistanceReader implements Reader {


    private final DataSet dataSet;
    Collection<Zone> lookup;

    public MitoTravelTimeAndDistanceReader(DataSet dataSet) {
        this.dataSet = dataSet;
        this.lookup = dataSet.getZones().values();
    }


    @Override
    public void read() {

        String carFile = AbitResources.instance.getString("car.omx.file");
        String carMatrixName  = AbitResources.instance.getString("car.omx.matrix");

        String busFile  = AbitResources.instance.getString("bus.omx.file");
        String busMatrixName  = AbitResources.instance.getString("bus.omx.matrix");

        String tramMetroFile  = AbitResources.instance.getString("tramMetro.omx.file");
        String tramMetroMatrixName  = AbitResources.instance.getString("tramMetro.omx.matrix");

        String trainFile  = AbitResources.instance.getString("train.omx.file");
        String trainMatrixName  = AbitResources.instance.getString("train.omx.matrix");

        String nonMotorizedFile = AbitResources.instance.getString("car.omx.file");
        String nonMotorizedMatrixName  = AbitResources.instance.getString("car.distance.omx.matrix");

        SkimTravelTimes travelTimes = new SkimTravelTimes();
        SkimTravelTimes travelDistances = new SkimTravelTimes();

        if (Resources.instance.getRelativePath(Properties.AUTO_PEAK_SKIM).toString().contains(".omx"))  {

            travelTimes.readSkim(Mode.CAR_DRIVER.toString(), carFile, carMatrixName, 1/60.);
            travelTimes.readSkim(Mode.BUS.toString(), busFile, busMatrixName, 1/60.);
            travelTimes.readSkim(Mode.TRAM_METRO.toString(), tramMetroFile, tramMetroMatrixName, 1/60.);
            travelTimes.readSkim(Mode.TRAIN.toString(), trainFile, trainMatrixName, 1/60.);
            dataSet.setTravelTimes(new MitoBasedTravelTimes(travelTimes));

            travelDistances.readSkim(Mode.UNKNOWN.toString(), nonMotorizedFile, nonMotorizedMatrixName, 1.);
            dataSet.setTravelDistances(new MitoBasedTravelDistances(travelDistances));

        } else if (Resources.instance.getRelativePath(Properties.AUTO_PEAK_SKIM).toString().contains(".parquet")) {

            travelTimes.readSkimFromParquet(Mode.CAR_DRIVER.toString(),Resources.instance.getRelativePath(Properties.AUTO_PEAK_SKIM).toString(), "FROM", "TO", "inVehTime_sec",1/60., lookup);
            travelTimes.readSkimFromParquet(Mode.BUS.toString(), Resources.instance.getRelativePath(Properties.BUS_TRAVEL_TIME_SKIM).toString(), "FROM", "TO", "totalTravelTime_sec",1/60., lookup);
            travelTimes.readSkimFromParquet(Mode.TRAM_METRO.toString(), Resources.instance.getRelativePath(Properties.TRAM_METRO_TRAVEL_TIME_SKIM).toString(), "FROM", "TO", "totalTravelTime_sec",1/60., lookup);
            travelTimes.readSkimFromParquet(Mode.TRAIN.toString(), Resources.instance.getRelativePath(Properties.TRAIN_TRAVEL_TIME_SKIM).toString(), "FROM", "TO", "totalTravelTime_sec",1/60., lookup);
            dataSet.setTravelTimes(new MitoBasedTravelTimes(travelTimes));

            travelDistances.readSkimFromParquet(Mode.UNKNOWN.toString(), Resources.instance.getRelativePath(Properties.AUTO_PEAK_SKIM).toString(),
                    "FROM","TO", "inVehDistance_m",1. / 1000., lookup);
            dataSet.setTravelDistances(new MitoBasedTravelDistances(travelDistances));

        }
    }
}
