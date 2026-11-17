package abm.matsim;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.*;

public class AddHbefaDescriptionsToVehicles {

    // must match the "VehCat" / "Technology" values in your HBEFA tables exactly
    private static final String PC  = "PASSENGER_CAR";
    private static final String HGV = "HEAVY_GOODS_VEHICLE";

    public static void main(String[] args) {

        String inputVehicles =
                "output/uncalibrated_no_telework_25_pct_population/2011_scenario/matsim_monday1/output_allVehicles.xml.gz";
        String outputVehicles =
                "output/uncalibrated_no_telework_25_pct_population/2011_scenario/matsim_monday1/hbefa_vehicles.xml.gz";

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimVehicleReader(scenario.getVehicles()).readFile(inputVehicles);
        Vehicles vehicles = scenario.getVehicles();

        // Gasoline car -> detailed lookup, technology "petrol (4S)"
        VehicleType gasoline = getOrCreateType(vehicles, "Car_GASOLINE", TransportMode.car, 7.5, 1.0);
        setHbefa(gasoline, PC, "petrol (4S)", "average", "average");

        // Diesel car -> detailed lookup, technology "diesel"
        VehicleType diesel = getOrCreateType(vehicles, "Car_DIESEL", TransportMode.car, 7.5, 1.0);
        setHbefa(diesel, PC, "diesel", "average", "average");

        // Truck -> average table only
        VehicleType truck = getOrCreateType(vehicles, "truck", TransportMode.truck, 15.0, 3.5);
        setHbefa(truck, HGV, "average", "average", "average");

        // Warn about any other type without HBEFA info (would break the emission run)
        for (VehicleType type : vehicles.getVehicleTypes().values()) {
            String id = type.getId().toString();
            if (!id.equals("Car_GASOLINE") && !id.equals("Car_DIESEL") && !id.equals("truck")) {
                System.out.println("WARNING: vehicle type without HBEFA info: " + id);
            }
        }

        new MatsimVehicleWriter(vehicles).writeFile(outputVehicles);
        System.out.println("Vehicle types written with HBEFA information: " + outputVehicles);
    }

    /** Returns the existing type or creates it with basic physical properties. */
    private static VehicleType getOrCreateType(Vehicles vehicles, String id, String networkMode,
                                               double length, double pce) {
        Id<VehicleType> typeId = Id.create(id, VehicleType.class);
        VehicleType type = vehicles.getVehicleTypes().get(typeId);
        if (type == null) {
            type = VehicleUtils.createVehicleType(typeId);
            type.setNetworkMode(networkMode);
            type.setLength(length);
            type.setPcuEquivalents(pce);
            vehicles.addVehicleType(type);
            System.out.println("Created missing vehicle type: " + id);
        }
        return type;
    }

    /** Writes HBEFA info as engine attributes and as legacy description string. */
    private static void setHbefa(VehicleType type, String category, String technology,
                                 String sizeClass, String concept) {
        EngineInformation ei = type.getEngineInformation();
        VehicleUtils.setHbefaVehicleCategory(ei, category);
        VehicleUtils.setHbefaTechnology(ei, technology);
        VehicleUtils.setHbefaSizeClass(ei, sizeClass);
        VehicleUtils.setHbefaEmissionsConcept(ei, concept);

        type.setDescription("BEGIN_EMISSIONS" + category + ";" + technology + ";"
                + sizeClass + ";" + concept + "END_EMISSIONS");
    }
}