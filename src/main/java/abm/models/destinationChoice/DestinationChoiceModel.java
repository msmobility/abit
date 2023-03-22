package abm.models.destinationChoice;

import abm.data.DataSet;
import abm.data.geo.MicroscopicLocation;
import abm.data.geo.Zone;
import abm.data.plans.Activity;
import abm.data.plans.Mode;
import abm.data.plans.Purpose;
import abm.data.pop.Household;
import abm.data.pop.Job;
import abm.data.pop.Person;
import abm.io.input.CoefficientsReader;
import abm.properties.AbitResources;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix1D;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix2D;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.matsim.core.utils.collections.Tuple;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public class DestinationChoiceModel implements DestinationChoice {

    private final DataSet dataSet;
    private final static Logger logger = Logger.getLogger(DestinationChoiceModel.class);
    private final Map<Purpose, Map<Zone, Double>> zoneAttractorsByPurpose;
    private Map<Purpose, IndexedDoubleMatrix2D> utilityMatrixByPurpose = new HashMap<>();

    private static final double BETA = -0.0005;
    private static final double ALPHA = 2;
    //TODO. Beta and alpha by purpose

    public DestinationChoiceModel(DataSet dataSet) {
        this.dataSet = dataSet;
        zoneAttractorsByPurpose = loadBasicAttraction();
        utilityMatrixByPurpose = loadUtilities();
    }

    private Map<Purpose, IndexedDoubleMatrix2D> loadUtilities() {

        Map<Purpose, IndexedDoubleMatrix2D> matrixByPurpose = new HashMap<>();
        for (Purpose purpose : Purpose.getAllPurposes()) {
            IndexedDoubleMatrix2D utilityMatrix = new IndexedDoubleMatrix2D(dataSet.getZones().values(), dataSet.getZones().values());
            for (Zone origin : dataSet.getZones().values()) {
                for (Zone destination : dataSet.getZones().values()) {
                    final int travelDistanceInMeters = dataSet.getTravelDistances().getTravelDistanceInMeters(origin, destination, Mode.UNKNOWN, 0.);
                    final double attractor = zoneAttractorsByPurpose.get(purpose).get(destination);
                    double utility = attractor * Math.exp(ALPHA * Math.exp(BETA * travelDistanceInMeters));
                    utilityMatrix.setIndexed(origin.getId(), destination.getId(), utility);
                }
            }
            matrixByPurpose.put(purpose, utilityMatrix);
        }


        return matrixByPurpose;
    }

    private Map<Purpose, Map<Zone, Double>> loadBasicAttraction() {

        Path pathToFilePurpose = Path.of(AbitResources.instance.getString("destinationChoice.coef"));
        Map<Purpose, Map<Zone, Double>> zoneAttractorsByPurpose = new HashMap<>();
        for (Purpose purpose : Purpose.getAllPurposes()) {
            String columnName = purpose.toString().toLowerCase(); //todo review this
            Map<String, Double> coefficients = new CoefficientsReader(dataSet, columnName, pathToFilePurpose).readCoefficients();
            Map<Zone, Double> attractorByZone = new HashMap<>();
            for (Zone zone: dataSet.getZones().values()) {
                Double value = 0.;
                for (Map.Entry<String, Double> entry : coefficients.entrySet()) {
                    value += coefficients.get(entry.getKey()) * Integer.valueOf(String.valueOf(zone.getAttribute(entry.getKey()).get()));
                }
                attractorByZone.put(zone, value);
            }
            zoneAttractorsByPurpose.put(purpose, attractorByZone);
        }
        return zoneAttractorsByPurpose;
    }


    @Override
    public void selectMainActivityDestination(Person person, Activity activity) {

        Zone origin = dataSet.getZones().get(person.getHousehold().getLocation().getZoneId());

        final int selectedIndex = MitoUtil.select(utilityMatrixByPurpose.get(activity.getPurpose()).viewRow(origin.getId()).toNonIndexedArray());

        final int[] columnLookupArray = utilityMatrixByPurpose.get(activity.getPurpose()).getColumnLookupArray();

        Zone destination = dataSet.getZones().get(columnLookupArray[selectedIndex]);

        final Coordinate randomCoordinate = destination.getRandomCoordinate(AbitUtils.getRandomObject());
        MicroscopicLocation microDestination = new MicroscopicLocation(randomCoordinate.x, randomCoordinate.y);
        microDestination.setZone(destination);
        activity.setLocation(microDestination);

    }

    @Override
    public void selectStopDestination(Person person, Activity previousActivity, Activity activity, Activity followingActivity) {

        Zone previousActivityZone = dataSet.getZones().get(previousActivity.getLocation().getZoneId());
        Zone followingActivityZone = dataSet.getZones().get(followingActivity.getLocation().getZoneId());

        final IndexedDoubleMatrix1D utilityfromPrevious = utilityMatrixByPurpose.get(activity.getPurpose()).viewRow(previousActivityZone.getId());
        final IndexedDoubleMatrix1D utiliyToFollowing = utilityMatrixByPurpose.get(activity.getPurpose()).viewRow(followingActivityZone.getId());

        final IndexedDoubleMatrix1D jointUtility = new IndexedDoubleMatrix1D(dataSet.getZones().values());

        for (Zone z : dataSet.getZones().values()) {
            jointUtility.setIndexed(z.getId(), utilityfromPrevious.getIndexed(z.getId()) * utiliyToFollowing.getIndexed(z.getId()));
        }

        final int selectedIndex = MitoUtil.select(jointUtility.toNonIndexedArray());

        final int[] columnLookupArray = utilityMatrixByPurpose.get(activity.getPurpose()).getColumnLookupArray();

        Zone destination = dataSet.getZones().get(columnLookupArray[selectedIndex]);

        final Coordinate randomCoordinate = destination.getRandomCoordinate(AbitUtils.getRandomObject());
        MicroscopicLocation microDestination = new MicroscopicLocation(randomCoordinate.x, randomCoordinate.y);
        microDestination.setZone(destination);
        activity.setLocation(microDestination);


    }
}
