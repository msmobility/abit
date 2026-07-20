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

public class RunAverageEmissionToolOffline {

    public static final String EMISSION_EVENTS_FILE = "emission.events.offline.xml.gz";

    public static void main(String[] args) {

        // 1. CONFIG
        Config config = ConfigUtils.createConfig();
        EmissionsConfigGroup ecg = ConfigUtils.addOrGetModule(config, EmissionsConfigGroup.class);

        String outputDir = "output/10pct-sample-size/80pct_10Iter_matsim_output";

        // INPUT FILES
        config.network().setInputFile(outputDir + "/output_network.xml.gz");

        // Must contain EXACT SAME vehicle IDs as used in output_events.xml.gz
        config.vehicles().setVehiclesFile(
                "output/10pct-sample-size/vehicles_80pct_hbefa.xml"
        );

        config.controler().setOutputDirectory(outputDir);

        // 3. EMISSION CONFIG
        ecg.setAverageWarmEmissionFactorsFile(
                "output/10pct-sample-size/EFA_HOT_Vehcat_Average.txt"
        );

        ecg.setAverageColdEmissionFactorsFile(
                "output/10pct-sample-size/EFA_ColdStart_Vehcat_Average.txt"
        );

        ecg.setDetailedVsAverageLookupBehavior(
                EmissionsConfigGroup.DetailedVsAverageLookupBehavior.directlyTryAverageTable
        );

        ecg.setEmissionsComputationMethod(
                EmissionsConfigGroup.EmissionsComputationMethod.AverageSpeed
        );

        ecg.setHbefaVehicleDescriptionSource(
                EmissionsConfigGroup.HbefaVehicleDescriptionSource.fromVehicleTypeDescription
        );

        ecg.setHbefaTableConsistencyCheckingLevel(
                EmissionsConfigGroup.HbefaTableConsistencyCheckingLevel.consistent
        );

        ecg.setWritingEmissionsEvents(true);
        ecg.setHandlesHighAverageSpeeds(true);
        ecg.setNonScenarioVehicles(
                EmissionsConfigGroup.NonScenarioVehicles.ignore
        );

        // 4. LOAD SCENARIO
        Scenario scenario = ScenarioUtils.loadScenario(config);

        // IMPORTANT:
        // for OFFLINE replay, use the normal events manager
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

        // 5. OUTPUT WRITER
        EventWriterXML emissionWriter = new EventWriterXML(outputDir + "/" + EMISSION_EVENTS_FILE);
        emissionModule.getEmissionEventsManager().addHandler(emissionWriter);

        // 6. READ EVENTS
        long startTime = System.currentTimeMillis();

        eventsManager.initProcessing();

        MatsimEventsReader reader = new MatsimEventsReader(eventsManager);
        reader.readFile(outputDir + "/output_events.xml.gz");

        eventsManager.finishProcessing();
        emissionWriter.closeFile();

        long endTime = System.currentTimeMillis();

        System.out.println("Offline emission calculation finished.");
        System.out.println("Emission events written to: " + outputDir + "/" + EMISSION_EVENTS_FILE);
        System.out.println("Runtime: " + ((endTime - startTime) / 1000.0) + " seconds");
    }
}