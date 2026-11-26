package abm.matsim;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.emissions.EmissionModule;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Injector;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.scenario.ScenarioUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RunDetailedEmissionToolOffline {

    public static final String EMISSION_EVENTS_FILE = "emission.events.offline.detailed.xml.gz";

    public static void main(String[] args) throws Exception {

        String scenarioDir = "output/uncalibrated_no_telework_25_pct_population/2011_scenario";
        String outputDir   = scenarioDir + "/matsim_monday1";

        // original HBEFA files (';' separated)
        String hotCsv  = scenarioDir + "/EFA_HOT_Concept_2011_scenario.csv";
        String coldCsv = scenarioDir + "/EFA_ColdStart_Concept_2011_scenario.csv";

        // new files
        String hotDetailed  = scenarioDir + "/EFA_HOT_detailed_2011.csv";               // cars
        String coldDetailed = scenarioDir + "/EFA_ColdStart_detailed_2011.csv";         // cars
        String coldAverage  = scenarioDir + "/EFA_ColdStart_average_withHGV_2011.csv";  // truck (cold = 0)

        // 0. CREATE TABLES
        makeDetailedCsv(hotCsv,  hotDetailed);
        makeDetailedCsv(coldCsv, coldDetailed);
        addZeroHgvColdRows(coldCsv, coldAverage);

        // 1. CONFIG
        Config config = ConfigUtils.createConfig();
        EmissionsConfigGroup ecg = ConfigUtils.addOrGetModule(config, EmissionsConfigGroup.class);

        config.network().setInputFile("output/uncalibrated_no_telework_25_pct_population/2011_scenario/network_hbefa.xml");
        config.vehicles().setVehiclesFile(outputDir + "/hbefa_vehicles.xml.gz");
        config.controler().setOutputDirectory(outputDir);

        // 2. EMISSION TABLES
        ecg.setDetailedWarmEmissionFactorsFile(hotDetailed);   // cars: petrol / diesel
        ecg.setDetailedColdEmissionFactorsFile(coldDetailed);  // cars: petrol / diesel
        ecg.setAverageWarmEmissionFactorsFile(hotCsv);         // truck: HGV average
        ecg.setAverageColdEmissionFactorsFile(coldAverage);    // truck: no cold start (0)

        // 3. EMISSION SETTINGS
        ecg.setDetailedVsAverageLookupBehavior(
                EmissionsConfigGroup.DetailedVsAverageLookupBehavior.tryDetailedThenTechnologyAverageThenAverageTable);
        ecg.setEmissionsComputationMethod(EmissionsConfigGroup.EmissionsComputationMethod.AverageSpeed);
        ecg.setHbefaVehicleDescriptionSource(
                EmissionsConfigGroup.HbefaVehicleDescriptionSource.fromVehicleTypeDescription);
        ecg.setHbefaTableConsistencyCheckingLevel(
                EmissionsConfigGroup.HbefaTableConsistencyCheckingLevel.consistent);
        ecg.setWritingEmissionsEvents(true);
        ecg.setHandlesHighAverageSpeeds(true);
        ecg.setNonScenarioVehicles(EmissionsConfigGroup.NonScenarioVehicles.ignore);

        // 4. SCENARIO + HBEFA ROAD TYPES
        Scenario scenario = ScenarioUtils.loadScenario(config);
//        OsmHbefaMapping.build().addHbefaMappings(scenario.getNetwork());

        // 5. EMISSION MODULE
        EventsManager eventsManager = EventsUtils.createEventsManager();

        AbstractModule module = new AbstractModule() {
            @Override
            public void install() {
                bind(Scenario.class).toInstance(scenario);
                bind(EventsManager.class).toInstance(eventsManager);
                bind(EmissionModule.class);
            }
        };

        com.google.inject.Injector injector = Injector.createInjector(config, module);
        EmissionModule emissionModule = injector.getInstance(EmissionModule.class);

        // 6. OUTPUT WRITER
        EventWriterXML emissionWriter = new EventWriterXML(outputDir + "/" + EMISSION_EVENTS_FILE);
        emissionModule.getEmissionEventsManager().addHandler(emissionWriter);

        // 7. READ EVENTS
        long start = System.currentTimeMillis();
        eventsManager.initProcessing();
        new MatsimEventsReader(eventsManager).readFile(outputDir + "/output_events.xml.gz");
        eventsManager.finishProcessing();
        emissionWriter.closeFile();

        System.out.println("Detailed emission run finished: " + outputDir + "/" + EMISSION_EVENTS_FILE);
        System.out.println("Runtime: " + (System.currentTimeMillis() - start) / 1000.0 + " s");
    }

    // Makes a detailed copy: EmConcept -> Technology;SizeClasse;EmConcept (average)
    private static void makeDetailedCsv(String in, String out) throws Exception {
        if (in.equals(out)) throw new RuntimeException("Input and output must be different files!");

        List<String> lines = Files.readAllLines(Path.of(in));
        String[] header = lines.get(0).replace("\uFEFF", "").split(";", -1);
        int idx = Arrays.asList(header).indexOf("EmConcept");
        if (idx < 0) throw new RuntimeException("No 'EmConcept' column in " + in);

        List<String> result = new ArrayList<>();
        for (int n = 0; n < lines.size(); n++) {
            String line = (n == 0) ? lines.get(0).replace("\uFEFF", "") : lines.get(n);
            if (line.isBlank()) continue;
            List<String> cols = new ArrayList<>(Arrays.asList(line.split(";", -1)));
            if (n == 0) {
                cols.set(idx, "Technology");
                cols.add(idx + 1, "SizeClasse");
                cols.add(idx + 2, "EmConcept");
            } else {
                cols.add(idx + 1, "average");
                cols.add(idx + 2, "average");
            }
            result.add(String.join(";", cols));
        }
        Files.write(Path.of(out), result);
        System.out.println("Detailed table written: " + out + " (" + (result.size() - 1) + " rows)");
    }

    // Copies the cold table and adds HGV rows with 0 emissions
    // (HBEFA has no cold-start factors for HGV) -> one row per Component + AmbientCondPattern
    private static void addZeroHgvColdRows(String in, String out) throws Exception {
        if (in.equals(out)) throw new RuntimeException("Input and output must be different files!");

        List<String> lines = Files.readAllLines(Path.of(in));
        List<String> header = Arrays.asList(lines.get(0).replace("\uFEFF", "").split(";", -1));
        int vehCat  = header.indexOf("VehCat");
        int concept = header.indexOf("EmConcept");
        int share   = header.indexOf("%OfEmConcept");
        int efa     = header.indexOf("EFA");
        int efaW    = header.indexOf("EFA_weighted");

        List<String> result = new ArrayList<>();
        result.add(lines.get(0).replace("\uFEFF", ""));
        int added = 0;

        for (int n = 1; n < lines.size(); n++) {
            String line = lines.get(n);
            if (line.isBlank()) continue;
            result.add(line); // keep original row

            String[] cols = line.split(";", -1);
            if (cols[vehCat].equals("pass. car") && cols[concept].equals("petrol (4S)")) {
                cols[vehCat]  = "HGV";
                cols[concept] = "diesel";
                cols[share]   = "1";
                cols[efa]     = "0";
                cols[efaW]    = "0";
                result.add(String.join(";", cols));
                added++;
            }
        }
        Files.write(Path.of(out), result);
        System.out.println("Cold average table with HGV zero rows: " + out + " (" + added + " HGV rows added)");
    }
}