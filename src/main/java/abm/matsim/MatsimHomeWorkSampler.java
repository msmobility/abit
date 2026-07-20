package abm.matsim;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

public class MatsimHomeWorkSampler {

    private static final String PLANS_FILE =
            "output/10pct-sample-size/00pct_10Iter_matsim_output/output_plans.xml.gz";

    private static final String OUTPUT_CSV =
            "output/10pct-sample-size/00pct_10Iter_matsim_output/sample_50_home_work_agents.csv";

    private static final int SAMPLE_SIZE = 50;
    private static final long RANDOM_SEED = 42;

    private static final String FROM_CRS = "EPSG:31468";
    private static final String TO_CRS = "EPSG:4326";

    private static final String REQUIRED_MODE = "Car_CONVENTIONAL";

    public static void main(String[] args) throws Exception {

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        PopulationUtils.readPopulation(
                scenario.getPopulation(),
                PLANS_FILE
        );

        CoordinateTransformation transformation =
                TransformationFactory.getCoordinateTransformation(
                        FROM_CRS,
                        TO_CRS
                );

        List<TripRecord> allTrips = extractDirectHomeWorkTrips(
                scenario.getPopulation(),
                transformation
        );

        System.out.println("Total direct home-work trips found: " + allTrips.size());

        List<TripRecord> sample = sampleTrips(
                allTrips,
                SAMPLE_SIZE,
                RANDOM_SEED
        );

        System.out.println("Sample size: " + sample.size());

        writeCsv(sample, OUTPUT_CSV);

        System.out.println("Saved file: " + OUTPUT_CSV);
    }

    private static List<TripRecord> extractDirectHomeWorkTrips(
            Population population,
            CoordinateTransformation transformation
    ) {
        List<TripRecord> trips = new ArrayList<>();

        for (Person person : population.getPersons().values()) {

            Plan selectedPlan = person.getSelectedPlan();
            if (selectedPlan == null) {
                continue;
            }

            List<PlanElement> elements = selectedPlan.getPlanElements();

            for (int i = 0; i < elements.size() - 2; i++) {

                if (!(elements.get(i) instanceof Activity)) {
                    continue;
                }

                if (!(elements.get(i + 1) instanceof Leg)) {
                    continue;
                }

                if (!(elements.get(i + 2) instanceof Activity)) {
                    continue;
                }

                Activity homeAct = (Activity) elements.get(i);
                Leg leg = (Leg) elements.get(i + 1);
                Activity workAct = (Activity) elements.get(i + 2);

                if (!homeAct.getType().equalsIgnoreCase("home")) {
                    continue;
                }

                if (!workAct.getType().equalsIgnoreCase("work")) {
                    continue;
                }

                if (!leg.getMode().equals(REQUIRED_MODE)) {
                    continue;
                }

                Coord homeCoord = homeAct.getCoord();
                Coord workCoord = workAct.getCoord();

                if (homeCoord == null || workCoord == null) {
                    continue;
                }

                Coord homeWgs84 = transformation.transform(homeCoord);
                Coord workWgs84 = transformation.transform(workCoord);

                double matsimTravelTimeS = leg.getTravelTime().seconds();

                double matsimDistanceM = Double.NaN;

                if (leg.getRoute() != null) {
                    matsimDistanceM = leg.getRoute().getDistance();
                }

                TripRecord record = new TripRecord();

                record.personId = person.getId().toString();
                record.mode = leg.getMode();

                record.homeX = homeCoord.getX();
                record.homeY = homeCoord.getY();

                record.workX = workCoord.getX();
                record.workY = workCoord.getY();

                // After transformation to EPSG:4326:
                // MATSim Coord x = longitude, y = latitude
                record.homeLon = homeWgs84.getX();
                record.homeLat = homeWgs84.getY();

                record.workLon = workWgs84.getX();
                record.workLat = workWgs84.getY();

                record.matsimTravelTimeS = matsimTravelTimeS;
                record.matsimDistanceM = matsimDistanceM;

                trips.add(record);

                // only first direct home → work trip per selected plan
                break;
            }
        }

        return trips;
    }

    private static List<TripRecord> sampleTrips(
            List<TripRecord> trips,
            int sampleSize,
            long seed
    ) {
        List<TripRecord> copy = new ArrayList<>(trips);

        Collections.shuffle(copy, new Random(seed));

        if (copy.size() <= sampleSize) {
            return copy;
        }

        return new ArrayList<>(copy.subList(0, sampleSize));
    }

    private static void writeCsv(
            List<TripRecord> trips,
            String outputCsv
    ) throws Exception {

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputCsv))) {

            writer.println(
                    "person_id,mode," +
                    "home_x,home_y,work_x,work_y," +
                    "home_lat,home_lon,work_lat,work_lon," +
                    "matsim_travel_time_s,matsim_distance_m"
            );

            for (TripRecord trip : trips) {

                writer.printf(
                        Locale.US,
                        "%s,%s,%.3f,%.3f,%.3f,%.3f,%.8f,%.8f,%.8f,%.8f,%.3f,%.3f%n",
                        trip.personId,
                        trip.mode,
                        trip.homeX,
                        trip.homeY,
                        trip.workX,
                        trip.workY,
                        trip.homeLat,
                        trip.homeLon,
                        trip.workLat,
                        trip.workLon,
                        trip.matsimTravelTimeS,
                        trip.matsimDistanceM
                );
            }
        }
    }

    private static class TripRecord {

        String personId;
        String mode;

        double homeX;
        double homeY;

        double workX;
        double workY;

        double homeLat;
        double homeLon;

        double workLat;
        double workLon;

        double matsimTravelTimeS;
        double matsimDistanceM;
    }
}