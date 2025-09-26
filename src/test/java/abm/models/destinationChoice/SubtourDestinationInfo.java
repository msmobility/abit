package abm.models.destinationChoice;

import abm.data.DataSet;
import abm.data.geo.Zone;
import abm.data.plans.Mode;
import abm.io.input.HabitualModeChoiceDataReaderManager;
import abm.io.input.SubtourDestinationChoiceDataReaderManager;
import abm.properties.AbitResources;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix2D;

import java.util.Map;

public class SubtourDestinationInfo {


    public static void main(String[] args) {

        AbitResources.initializeResources("abit_wei.properties");
        AbitUtils.loadHdf5Lib();
        MitoUtil.initializeRandomNumber(AbitUtils.getRandomObject());

        DataSet dataset = new SubtourDestinationChoiceDataReaderManager().readData();
        SubtourDestinationChoiceModel subtourDestinationChoiceModel = new SubtourDestinationChoiceModel(dataset);
        Map<Zone, Double> zoneAttractors = subtourDestinationChoiceModel.loadBasicAttraction();

        final double attractor = zoneAttractors.get(dataset.getZones().get(2));
        final int travelDistanceInMeters = dataset.getTravelDistances().getTravelDistanceInMeters(dataset.getZones().get(1), dataset.getZones().get(2),
                Mode.UNKNOWN, 0.);

        System.out.println("attractor: " +attractor);
        System.out.println("travelDistanceInMeters:" + travelDistanceInMeters );
    }

}
