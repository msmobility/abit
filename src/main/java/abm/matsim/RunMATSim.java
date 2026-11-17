package abm.matsim;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
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

    public static void main(String[] args) {

        Config config = ConfigUtils.createConfig();

        // 1. GLOBAL
        config.global().setRandomSeed(987654321L);
        config.global().setNumberOfThreads(12);

        // 2. CONTROLER
        config.controler().setOutputDirectory("output/uncalibrated_no_telework_25_pct_population/2011_scenario/matsim_wednesday");
        config.controler().setLastIteration(150);
        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        config.controler().setMobsim("qsim");
        config.controler().setRoutingAlgorithmType(ControlerConfigGroup.RoutingAlgorithmType.SpeedyALT);
        config.linkStats().setWriteLinkStatsInterval(0);

        // 3. INPUT FILES
        config.network().setInputFile("output/uncalibrated_no_telework_25_pct_population/2011_scenario/cleaned_network_hbefa.xml");
        config.plans().setInputFile("output/uncalibrated_no_telework_25_pct_population/2011_scenario/matsimVehiclePlan_wednesday.xml");
        config.transit().setUseTransit(false);

        // 4. QSIM + ROUTING MODES
        config.qsim().setStartTime(0.0);
        config.qsim().setEndTime(30.0 * 3600.0);
        config.qsim().setFlowCapFactor(0.25);
        config.qsim().setStorageCapFactor(0.25);
        config.qsim().setNumberOfThreads(12);
        config.qsim().setMainModes(Arrays.asList("Car_GASOLINE", "Car_DIESEL", "truck"));
        config.qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.modeVehicleTypesFromVehiclesData);

        config.plansCalcRoute().setNetworkModes(Arrays.asList("Car_GASOLINE", "Car_DIESEL", "truck"));

        // 5. TRAVEL TIMES (all vehicles share the same congestion)
        config.travelTimeCalculator().setSeparateModes(false);
        config.travelTimeCalculator().setFilterModes(false);
        config.travelTimeCalculator().setAnalyzedModes(new HashSet<>(Arrays.asList("Car_GASOLINE", "Car_DIESEL", "truck")));

        // 6. REPLANNING STRATEGIES
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

        // 7. SCORING - GENERAL
        config.planCalcScore().setPerforming_utils_hr(6.0);
        config.planCalcScore().setLateArrival_utils_hr(-18.0);
        config.planCalcScore().setEarlyDeparture_utils_hr(0.0);
        config.planCalcScore().setMarginalUtilityOfMoney(1.0);
        config.planCalcScore().setUtilityOfLineSwitch(-1.0);

        // 8. SCORING - ACTIVITIES (persons)
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

        // 9. SCORING - ACTIVITIES (trucks: start / end, not scored)
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

        // 10. SCORING - MODES
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

        // 11. LOAD SCENARIO + MERGE TRUCK PLANS
        //TODO: check the scale factor for truck plan, is it 10% or 100% truck demand? - Qin
        Scenario scenario = ScenarioUtils.loadScenario(config);

        String truckPlanFile = "output/uncalibrated_no_telework_25_pct_population/truck_plans_muc.xml";
        new PopulationReader(scenario).readFile(truckPlanFile);

        System.out.println("Truck plans merged into scenario: " + truckPlanFile);
        System.out.println("Total persons: " + scenario.getPopulation().getPersons().size());

        // 12. VEHICLE TYPES (id must equal the leg mode)
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

        // 14. RUN
        Controler controler = new Controler(scenario);
        controler.run();
    }
}