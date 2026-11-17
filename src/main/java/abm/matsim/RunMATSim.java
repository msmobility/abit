package abm.matsim;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class RunMATSim {

    private static final double CHANGE_EXP_BETA = 0.8;
    private static final double WEIGHT_REROUTE = 0.2;
    // sample of the person plans (25 pct population); also used for flow/storage capacity
    private static final double POPULATION_SCALE_FACTOR = 0.25;
    // FOCA truck day-of-week factor (thursday 1.278066, saturday 0.430817, sunday 0.178852); weekday uses thursday
    private static final double TRUCK_DAY_FACTOR = 1.278066;

    // file paths
    private static final String SCENARIO_FOLDER = "output/uncalibrated_no_telework_25_pct_population/2011_scenario/";
    private static final String OUTPUT_DIRECTORY = SCENARIO_FOLDER + "matsim_wednesday";
    private static final String NETWORK_FILE = SCENARIO_FOLDER + "cleaned_network_hbefa.xml";
    private static final String PLANS_FILE = SCENARIO_FOLDER + "matsimPlan_wednesday.xml";
    private static final String TRUCK_PLANS_FILE = "output/uncalibrated_no_telework_25_pct_population/truck_plans_muc.xml";

    public static void main(String[] args) {

        Config config = ConfigUtils.createConfig();

        // 1. GLOBAL
        config.global().setNumberOfThreads(12);

        // 2. CONTROLER
        config.controler().setOutputDirectory(OUTPUT_DIRECTORY);
        config.controler().setLastIteration(150);
        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        config.controler().setMobsim("qsim");
        config.controler().setRoutingAlgorithmType(ControlerConfigGroup.RoutingAlgorithmType.SpeedyALT);
        config.linkStats().setWriteLinkStatsInterval(0);

        // 3. INPUT FILES
        config.network().setInputFile(NETWORK_FILE);
        config.plans().setInputFile(PLANS_FILE);
        config.transit().setUseTransit(false);

        // 4. QSIM + ROUTING MODES
        config.qsim().setStartTime(0.0);
        config.qsim().setEndTime(30.0 * 3600.0);
        config.qsim().setFlowCapFactor(POPULATION_SCALE_FACTOR);
        config.qsim().setStorageCapFactor(POPULATION_SCALE_FACTOR);
        config.qsim().setNumberOfThreads(12);
        config.qsim().setMainModes(Arrays.asList("Car_GASOLINE", "Car_DIESEL", "truck"));
        config.qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.modeVehicleTypesFromVehiclesData);

        config.plansCalcRoute().setNetworkModes(Arrays.asList("Car_GASOLINE", "Car_DIESEL", "truck"));

        //5. Teleported modes
        // (a) beeline teleportation: speed in m/s, beeline distance factor
        PlansCalcRouteConfigGroup.TeleportedModeParams trainParams = new PlansCalcRouteConfigGroup.TeleportedModeParams("train");
        trainParams.setTeleportedModeSpeed(50.0 / 3.6);
        trainParams.setBeelineDistanceFactor(1.3);
        config.plansCalcRoute().addTeleportedModeParams(trainParams);

        PlansCalcRouteConfigGroup.TeleportedModeParams tramMetroParams = new PlansCalcRouteConfigGroup.TeleportedModeParams("tram_metro");
        tramMetroParams.setTeleportedModeSpeed(25.0 / 3.6);
        tramMetroParams.setBeelineDistanceFactor(1.3);
        config.plansCalcRoute().addTeleportedModeParams(tramMetroParams);

        PlansCalcRouteConfigGroup.TeleportedModeParams busParams = new PlansCalcRouteConfigGroup.TeleportedModeParams("bus");
        busParams.setTeleportedModeSpeed(18.0 / 3.6);
        busParams.setBeelineDistanceFactor(1.3);
        config.plansCalcRoute().addTeleportedModeParams(busParams);

        PlansCalcRouteConfigGroup.TeleportedModeParams walkParams = new PlansCalcRouteConfigGroup.TeleportedModeParams("walk");
        walkParams.setTeleportedModeSpeed(5.0 / 3.6);
        walkParams.setBeelineDistanceFactor(1.3);
        config.plansCalcRoute().addTeleportedModeParams(walkParams);

        PlansCalcRouteConfigGroup.TeleportedModeParams bikeParams = new PlansCalcRouteConfigGroup.TeleportedModeParams("bike");
        bikeParams.setTeleportedModeSpeed(15.0 / 3.6);
        bikeParams.setBeelineDistanceFactor(1.3);
        config.plansCalcRoute().addTeleportedModeParams(bikeParams);

        // (b) network-routed teleportation: car_passenger
        PlansCalcRouteConfigGroup.TeleportedModeParams routeParams = new PlansCalcRouteConfigGroup.TeleportedModeParams("car_passenger");
        routeParams.setTeleportedModeFreespeedFactor(1.0);
        config.plansCalcRoute().addTeleportedModeParams(routeParams);

        // 6. TRAVEL TIMES (all vehicles share the same congestion)
        config.travelTimeCalculator().setSeparateModes(false);
        config.travelTimeCalculator().setFilterModes(false);
        config.travelTimeCalculator().setAnalyzedModes(new HashSet<>(Arrays.asList("Car_GASOLINE", "Car_DIESEL", "truck")));

        // 7. REPLANNING STRATEGIES
        config.strategy().clearStrategySettings();

        StrategyConfigGroup.StrategySettings reroute = new StrategyConfigGroup.StrategySettings();
        reroute.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.ReRoute);
        reroute.setWeight(WEIGHT_REROUTE);
        config.strategy().addStrategySettings(reroute);

        StrategyConfigGroup.StrategySettings planSelection = new StrategyConfigGroup.StrategySettings();
        planSelection.setStrategyName(DefaultPlanStrategiesModule.DefaultSelector.ChangeExpBeta);
        planSelection.setWeight(CHANGE_EXP_BETA);
        config.strategy().addStrategySettings(planSelection);

        config.strategy().setFractionOfIterationsToDisableInnovation(0.8);

        // 8. SCORING - GENERAL
        config.planCalcScore().setPerforming_utils_hr(6.0);
        config.planCalcScore().setLateArrival_utils_hr(-18.0);
        config.planCalcScore().setEarlyDeparture_utils_hr(0.0);
        config.planCalcScore().setMarginalUtilityOfMoney(1.0);
        config.planCalcScore().setUtilityOfLineSwitch(-1.0);

        // 9. SCORING - ACTIVITIES (persons)
        PlanCalcScoreConfigGroup.ActivityParams homeParams =
                new PlanCalcScoreConfigGroup.ActivityParams("home");
        homeParams.setTypicalDuration(12.0 * 3600.0);
        config.planCalcScore().addActivityParams(homeParams);

        PlanCalcScoreConfigGroup.ActivityParams workParams =
                new PlanCalcScoreConfigGroup.ActivityParams("work");
        workParams.setTypicalDuration(9.0 * 3600.0);
        config.planCalcScore().addActivityParams(workParams);

        PlanCalcScoreConfigGroup.ActivityParams educationParams =
                new PlanCalcScoreConfigGroup.ActivityParams("education");
        educationParams.setTypicalDuration(7.0 * 3600.0);
        config.planCalcScore().addActivityParams(educationParams);

        PlanCalcScoreConfigGroup.ActivityParams shoppingParams =
                new PlanCalcScoreConfigGroup.ActivityParams("shopping");
        shoppingParams.setTypicalDuration(2.0 * 3600.0);
        config.planCalcScore().addActivityParams(shoppingParams);

        PlanCalcScoreConfigGroup.ActivityParams subtourParams =
                new PlanCalcScoreConfigGroup.ActivityParams("subtour");
        subtourParams.setTypicalDuration(2.0 * 3600.0);
        config.planCalcScore().addActivityParams(subtourParams);

        PlanCalcScoreConfigGroup.ActivityParams accompanyParams =
                new PlanCalcScoreConfigGroup.ActivityParams("accompany");
        accompanyParams.setTypicalDuration(2.0 * 3600.0);
        config.planCalcScore().addActivityParams(accompanyParams);

        PlanCalcScoreConfigGroup.ActivityParams recreationParams =
                new PlanCalcScoreConfigGroup.ActivityParams("recreation");
        recreationParams.setTypicalDuration(2.0 * 3600.0);
        config.planCalcScore().addActivityParams(recreationParams);

        PlanCalcScoreConfigGroup.ActivityParams otherParams =
                new PlanCalcScoreConfigGroup.ActivityParams("other");
        otherParams.setTypicalDuration(1.0 * 3600.0);
        config.planCalcScore().addActivityParams(otherParams);

        // 10. SCORING - ACTIVITIES (trucks: start / end, not scored)
        PlanCalcScoreConfigGroup.ActivityParams startParams =
                new PlanCalcScoreConfigGroup.ActivityParams("start");
        startParams.setTypicalDuration(1.0 * 3600.0);
        startParams.setScoringThisActivityAtAll(false);
        config.planCalcScore().addActivityParams(startParams);

        PlanCalcScoreConfigGroup.ActivityParams endParams =
                new PlanCalcScoreConfigGroup.ActivityParams("end");
        endParams.setTypicalDuration(1.0 * 3600.0);
        endParams.setScoringThisActivityAtAll(false);
        config.planCalcScore().addActivityParams(endParams);

        // 11. SCORING - MODES
        PlanCalcScoreConfigGroup.ModeParams carGasolineParams =
                new PlanCalcScoreConfigGroup.ModeParams("Car_GASOLINE");
        carGasolineParams.setMarginalUtilityOfTraveling(-6.0);
        carGasolineParams.setMonetaryDistanceRate(0.0);
        carGasolineParams.setConstant(0.0);
        config.planCalcScore().addModeParams(carGasolineParams);

        PlanCalcScoreConfigGroup.ModeParams carDieselParams =
                new PlanCalcScoreConfigGroup.ModeParams("Car_DIESEL");
        carDieselParams.setMarginalUtilityOfTraveling(-6.0);
        carDieselParams.setMonetaryDistanceRate(0.0);
        carDieselParams.setConstant(0.0);
        config.planCalcScore().addModeParams(carDieselParams);

        PlanCalcScoreConfigGroup.ModeParams truckParams =
                new PlanCalcScoreConfigGroup.ModeParams("truck");
        truckParams.setMarginalUtilityOfTraveling(-6.0);
        truckParams.setMonetaryDistanceRate(0.0);
        truckParams.setConstant(0.0);
        config.planCalcScore().addModeParams(truckParams);

        // teleported modes (walk and bike already have MATSim default scoring params)
        for (String teleportedMode : Arrays.asList("train", "tram_metro", "bus", "car_passenger")) {
            PlanCalcScoreConfigGroup.ModeParams teleportedParams =
                    new PlanCalcScoreConfigGroup.ModeParams(teleportedMode);
            teleportedParams.setMarginalUtilityOfTraveling(-6.0);
            teleportedParams.setMonetaryDistanceRate(0.0);
            teleportedParams.setConstant(0.0);
            config.planCalcScore().addModeParams(teleportedParams);
        }

        // 12. LOAD SCENARIO
        Scenario scenario = ScenarioUtils.loadScenario(config);

        // 13.  SAMPLE DOWN AND MERGE TRUCK PLANS
        // truck_plans_muc.xml is 100% truck demand (FOCA); sample it down to the person sample,
        double truckSample = POPULATION_SCALE_FACTOR * TRUCK_DAY_FACTOR;
        System.out.println("Truck sample: " + truckSample);
        if (truckSample >= 1.) {
            throw new RuntimeException("Requested truck sample " + truckSample + " > 1. The truck plan file has too few trucks.");
        }

        Scenario truckScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(truckScenario).readFile(TRUCK_PLANS_FILE);
        Population truckPopulation = truckScenario.getPopulation();
        PopulationUtils.sampleDown(truckPopulation, truckSample);

        int personsBefore = scenario.getPopulation().getPersons().size();
        for (Person truck : truckPopulation.getPersons().values()) {
            scenario.getPopulation().addPerson(truck);
        }

        System.out.println("Truck plans merged into scenario: " + TRUCK_PLANS_FILE);
        System.out.println("Persons: " + personsBefore + ", trucks added: " + truckPopulation.getPersons().size()
                + ", total agents: " + scenario.getPopulation().getPersons().size());

        // 14. VEHICLE TYPES (id must equal the leg mode)
        // Gasoline car
        VehicleType gasolineType = VehicleUtils.getFactory().createVehicleType(Id.create("Car_GASOLINE", VehicleType.class));
        gasolineType.setNetworkMode("Car_GASOLINE");
        gasolineType.getCapacity().setSeats(4);
        scenario.getVehicles().addVehicleType(gasolineType);

        // Diesel car
        VehicleType dieselType = VehicleUtils.getFactory().createVehicleType(Id.create("Car_DIESEL", VehicleType.class));
        dieselType.setNetworkMode("Car_DIESEL");
        dieselType.getCapacity().setSeats(4);
        scenario.getVehicles().addVehicleType(dieselType);

        // Truck
        VehicleType truckType = VehicleUtils.getFactory().createVehicleType(Id.create("truck", VehicleType.class));
        truckType.setNetworkMode("truck");
        truckType.getCapacity().setSeats(1);
        truckType.setPcuEquivalents(2.5);
        truckType.setLength(15.0);
        truckType.setMaximumVelocity(80.0 / 3.6);
        scenario.getVehicles().addVehicleType(truckType);

        // 15. RUN
        Controler controler = new Controler(scenario);
        controler.run();
    }
}