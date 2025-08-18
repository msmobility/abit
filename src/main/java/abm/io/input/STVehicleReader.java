package abm.io.input;

import abm.data.DataSet;
import abm.data.pop.Household;
import abm.data.vehicle.STCar;
import abm.data.vehicle.CarType;
import abm.data.vehicle.EmissionClass;
import abm.data.vehicle.VehicleUtil;
import abm.properties.AbitResources;
import de.tum.bgu.msm.util.MitoUtil;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class STVehicleReader implements Reader {

    private final DataSet dataSet;
    private final Map<String, Integer> indexes;
    final String path;
    private BufferedReader br;

    final static String REGEX = ",";
    private Logger logger = Logger.getLogger(STVehicleReader.class);

    public STVehicleReader(DataSet dataSet) {
        this.dataSet = dataSet;
        indexes = new HashMap<>();
        path = AbitResources.instance.getString("vehicles.file");
    }

    // for testing the Vehicle Reader: Core constructor (used for testability and flexible file paths)
//    public VehicleReader(DataSet dataSet, String path) {
//        this.dataSet = dataSet;
//        this.indexes = new HashMap<>();
//        this.path = path;
//    }

    // for testing the Vehicle Reader: Original constructor (used in production, delegates to core)
//    public VehicleReader(DataSet dataSet) {
//        this(dataSet, AbitResources.instance.getString("vehicles.file"));
//    }

    @Override
    public void read() {
        try {
            br = new BufferedReader(new FileReader(path));
            logger.info("Reading from " + path);
            processHeader(br, indexes);
            processRecords(br, indexes, dataSet);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void processHeader(BufferedReader br, Map<String, Integer> indexes) throws IOException {
        String[] header = br.readLine().split(REGEX);

        indexes.put("hhId", MitoUtil.findPositionInArray("id", header));
        //indexes.put("hhId", MitoUtil.findPositionInArray("hh", header));
        indexes.put("numAutos", MitoUtil.findPositionInArray("autos", header));
        indexes.put("index", MitoUtil.findPositionInArray("index", header));
        indexes.put("vehId", MitoUtil.findPositionInArray("vehId", header));
        indexes.put("type", MitoUtil.findPositionInArray("type", header));
        indexes.put("emissionclass", MitoUtil.findPositionInArray("emissionClass", header));
        indexes.put("age", MitoUtil.findPositionInArray("age", header));
    }


    private void processRecords(BufferedReader br, Map<String, Integer> indexes, DataSet dataSet) throws IOException {

        String line;
        while ((line = br.readLine()) != null) {

            String[] splitLine = line.split(REGEX);

            int id = Integer.parseInt(splitLine[indexes.get("index")]);
            int hhid = Integer.parseInt(splitLine[indexes.get("hhId")]); //originally "Id"


            Household hh = dataSet.getHouseholds().getOrDefault(hhid, null);

            if (hh == null) {
                throw new RuntimeException("The household does not exist");
            }

            int numAutos = Integer.parseInt(splitLine[indexes.get("numAutos")]);
            String vehType = splitLine[indexes.get("type")]; //.toUpperCase();
            String emissionClass = splitLine[indexes.get("emissionclass")];

            if (vehType.equals("DIESEL") && emissionClass.equals ("EURO_4")){
                hh.getVehicles().add(new STCar(id, CarType.DIESEL, EmissionClass.EURO_4, VehicleUtil.getVehicleAgeInBaseYear()));
            } else if (vehType.equals("DIESEL") && emissionClass.equals ("EURO_5")) {
                hh.getVehicles().add(new STCar(id, CarType.DIESEL, EmissionClass.EURO_5, VehicleUtil.getVehicleAgeInBaseYear()));
            } else if (vehType.equals("DIESEL") && emissionClass.equals ("EURO_6")) {
                hh.getVehicles().add(new STCar(id, CarType.DIESEL, EmissionClass.EURO_6, VehicleUtil.getVehicleAgeInBaseYear()));
            } else if (vehType.equals("nonDIESEL") && emissionClass.equals ("EUROx")) {
                hh.getVehicles().add(new STCar(id, CarType.nonDIESEL, EmissionClass.EUROx,VehicleUtil.getVehicleAgeInBaseYear()));
                }


//            if (numAutos != hh.getNumberOfCars()) {
//                throw new RuntimeException("Number of cars in hh and vv doesn't match, hh id: " + hh.getId());
//            } else {
//                if (vehType.equals("CONVENTIONAL")) {
//                    hh.getVehicles().add(new Car(id, CarType.CONVENTIONAL, VehicleUtil.getVehicleAgeInBaseYear()));
//                } else if (vehType.equals("ELECTRIC")) {
//                    hh.getVehicles().add(new Car(id, CarType.ELECTRIC, VehicleUtil.getVehicleAgeInBaseYear()));
//                }
//            }

//            if (numAutos != hh.getNumberOfCars()) {
//                throw new RuntimeException("Number of cars in hh and vv doesn't match, hh id: " + hh.getId());
//            } else {

//            }
        }
    }
}
