package abm.analysis.spaceTimePrism.data;

import abm.data.geo.Zone;

import java.util.Arrays;
import java.util.List;

public class SpaceTimePrism {

    private int id;
    private int personId;

    private double startTime_min;

    private double endTime_min;

    private double timeBudget_min;

    private List<Zone> accessibleZones;


    public SpaceTimePrism(int id, int personId, double startTime_min, double endTime_min, double timeBudget_min, List<Zone> accessibleZones) {
        this.id = id;
        this.personId = personId;
        this.startTime_min = startTime_min;
        this.endTime_min = endTime_min;
        this.timeBudget_min = timeBudget_min;
        this.accessibleZones = accessibleZones;
    }

    public int getId() {
        return id;
    }

    public int getPersonId() {
        return personId;
    }

    public double getStartTime_min() {
        return startTime_min;
    }

    public double getEndTime_min() {
        return endTime_min;
    }

    public double getTimeBudget_min() {
        return timeBudget_min;
    }

    public List<Zone> getAccessibleZones() {
        return accessibleZones;
    }

    @Override
    public String toString() {
        return id + "," + personId + "," + startTime_min + "," + endTime_min + "," + timeBudget_min + "," + Arrays.toString(accessibleZones.toArray());
    }

    public static String getHeader() {
        return "id,personId,startTime_min,endTime_min,timeBudget_min,accessibleZones";
    }


}
