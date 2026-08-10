package abm.matsim;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.MatsimVehicleReader;
import org.matsim.vehicles.MatsimVehicleWriter;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;

public class AddHbefaDescriptionsToVehicles {

    public static void main(String[] args) {

        String inputVehicles =
                "output/10pct-sample-size/80pct_10Iter_matsim_output/output_allVehicles.xml.gz";

        String outputVehicles =
                "output/10pct-sample-size/vehicles_80pct_hbefa.xml";

        // Load vehicles
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimVehicleReader(scenario.getVehicles()).readFile(inputVehicles);
        Vehicles vehicles = scenario.getVehicles();

        // Update only the descriptions
        for (VehicleType type : vehicles.getVehicleTypes().values()) {

            String typeId = type.getId().toString();

            if (typeId.equals("Car_CONVENTIONAL")) {
                type.setDescription(
                        "BEGIN_EMISSIONSPASSENGER_CAR;average;average;averageEND_EMISSIONS"
                );
            }

            if (typeId.equals("Car_ELECTRIC")) {
                type.setDescription(
                        "BEGIN_EMISSIONSNON_HBEFA_VEHICLE;average;average;averageEND_EMISSIONS"
                );
            }
        }

        // Write updated vehicles file
        new MatsimVehicleWriter(vehicles).writeFile(outputVehicles);

        System.out.println("Vehicle types updated with working HBEFA descriptions.");
    }
}
