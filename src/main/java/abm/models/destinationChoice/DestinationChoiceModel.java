package abm.models.destinationChoice;

import abm.data.DataSet;
import abm.data.geo.MicroscopicLocation;
import abm.data.geo.Zone;
import abm.data.plans.Activity;
import abm.data.plans.Mode;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix1D;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix2D;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;

import java.util.HashMap;
import java.util.Map;

public class DestinationChoiceModel implements DestinationChoice {

    private final DataSet dataSet;
    private final static Logger logger = Logger.getLogger(DestinationChoiceModel.class);
    private final Map<Zone, Double> zoneAttractors;
    private final IndexedDoubleMatrix2D utilityMatrix;

    private static final double BETA = -0.0005;

    public DestinationChoiceModel(DataSet dataSet) {
        this.dataSet = dataSet;
        zoneAttractors = loadBasicAttraction();
        utilityMatrix = loadUtilities();
    }

    private IndexedDoubleMatrix2D loadUtilities() {

        IndexedDoubleMatrix2D utilityMatrix = new IndexedDoubleMatrix2D(dataSet.getZones().values(), dataSet.getZones().values());
        for (Zone origin : dataSet.getZones().values()) {
            for (Zone destination : dataSet.getZones().values()) {
                final int travelDistanceInMeters = dataSet.getTravelDistances().getTravelDistanceInMeters(origin, destination, Mode.UNKNOWN, 0.);
                final double attractor = zoneAttractors.get(destination);
                double utility = attractor * Math.exp(BETA * travelDistanceInMeters);
                utilityMatrix.setIndexed(origin.getId(), destination.getId(), utility);
            }
        }

        return utilityMatrix;
    }

    private Map<Zone, Double> loadBasicAttraction() {

        Map<Zone, Double> zoneAttractors = new HashMap<>();
        for (Zone z : dataSet.getZones().values()) {
            zoneAttractors.put(z, 0.);
        }
        for (Household household : dataSet.getHouseholds().values()) {
            Zone zone = dataSet.getZones().get(household.getLocation().getZoneId());
            zoneAttractors.put(zone, zoneAttractors.get(zone) + household.getPersons().size());
        }
        return zoneAttractors;
    }


    @Override
    public void selectMainActivityDestination(Person person, Activity activity) {
        Zone origin = dataSet.getZones().get(person.getHousehold().getLocation().getZoneId());

        final int selectedIndex = MitoUtil.select(utilityMatrix.viewRow(origin.getId()).toNonIndexedArray());

        final int[] columnLookupArray = utilityMatrix.getColumnLookupArray();

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

        final IndexedDoubleMatrix1D utilityfromPrevious = utilityMatrix.viewRow(previousActivityZone.getId());
        final IndexedDoubleMatrix1D utiliyToFollowing = utilityMatrix.viewRow(followingActivityZone.getId());

        final IndexedDoubleMatrix1D jointUtility = new IndexedDoubleMatrix1D(dataSet.getZones().values());

        for (Zone z : dataSet.getZones().values()) {
            jointUtility.setIndexed(z.getId(), utilityfromPrevious.getIndexed(z.getId()) * utiliyToFollowing.getIndexed(z.getId()));
        }

        final int selectedIndex = MitoUtil.select(jointUtility.toNonIndexedArray());

        final int[] columnLookupArray = utilityMatrix.getColumnLookupArray();

        Zone destination = dataSet.getZones().get(columnLookupArray[selectedIndex]);

        final Coordinate randomCoordinate = destination.getRandomCoordinate(AbitUtils.getRandomObject());
        MicroscopicLocation microDestination = new MicroscopicLocation(randomCoordinate.x, randomCoordinate.y);
        microDestination.setZone(destination);
        activity.setLocation(microDestination);


    }
}
