package abm.scenarios.parkingGarage.io;

import abm.data.DataSet;
import abm.data.geo.Zone;
import abm.properties.AbitResources;
import de.tum.bgu.msm.util.MitoUtil;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ParkingRestrictionZoneReader {

    private final DataSet dataSet;
    private final String PARKING_RESTRICTION_ZONES_PATH = AbitResources.instance.getString("parkingRestrictionZones.input");
    public ParkingRestrictionZoneReader(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    public Map<Integer, Boolean> readParkingRestrictionZones() {
        Map<Integer, Boolean> parkingRestrictionZones = new HashMap<>();
        for (Zone zoneId : dataSet.getZones().values()) {
            parkingRestrictionZones.put(zoneId.getId(), false);
        }

        try {
            final Map<String, Integer> indexes = new HashMap<>();
            BufferedReader br = new BufferedReader(new FileReader(PARKING_RESTRICTION_ZONES_PATH));
            processHeader(br, indexes);
            processRecords(br, indexes, parkingRestrictionZones);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return parkingRestrictionZones;
    }

    private void processHeader(BufferedReader br, Map<String, Integer> indexes) throws IOException {
        String[] header = br.readLine().split(",");
        indexes.put("id", MitoUtil.findPositionInArray("id", header));
    }

    private void processRecords(BufferedReader br, Map<String, Integer> indexes, Map<Integer, Boolean> parkingRestrictionZones) throws IOException {
        String line;
        while ((line = br.readLine()) != null) {
            String[] splitLine = line.split(",");
            int id = Integer.parseInt(splitLine[indexes.get("id")]);
            parkingRestrictionZones.put(id, true);

        }
    }
}
