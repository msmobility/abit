package abm.matsim;

import abm.properties.AbitResources;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import java.util.Arrays;

public class RunMATSim {

    private static final double CHANGE_EXP_BETA = 0.8;
    private static final double WEIGHT_REROUTE = 0.2;

    public static void main(String[] args) {

        Config config = ConfigUtils.createConfig();

        config.global().setRandomSeed(987654321L);
        config.global().setNumberOfThreads(10);

        config.controler().setOutputDirectory("output/25pct-calibrated/baseline_matsim_output");
        config.controler().setLastIteration(100);
        config.controler().setOverwriteFileSetting(
                OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists
        );
        config.controler().setMobsim("qsim");
        config.controler().setRoutingAlgorithmType(
                ControlerConfigGroup.RoutingAlgorithmType.SpeedyALT
        );
        config.linkStats().setWriteLinkStatsInterval(0);

        config.network().setInputFile("output/25pct-calibrated/matsim_network_hbefa.xml");
        config.plans().setInputFile("output/25pct-calibrated/baseline_plan_monday.xml");

        config.transit().setUseTransit(false);

        config.qsim().setStartTime(0.0);
        config.qsim().setEndTime(30.0 * 3600.0);
        config.qsim().setFlowCapFactor(0.25);
        config.qsim().setStorageCapFactor(0.25);
        config.qsim().setNumberOfThreads(10);
        config.qsim().setMainModes(Arrays.asList("Car_CONVENTIONAL", "Car_ELECTRIC"));
        config.qsim().setVehiclesSource(
                QSimConfigGroup.VehiclesSource.modeVehicleTypesFromVehiclesData
        );

        config.plansCalcRoute().setNetworkModes(Arrays.asList("Car_CONVENTIONAL", "Car_ELECTRIC"));

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

        config.planCalcScore().setPerforming_utils_hr(6.0);
        config.planCalcScore().setLateArrival_utils_hr(-18.0);
        config.planCalcScore().setEarlyDeparture_utils_hr(0.0);
        config.planCalcScore().setMarginalUtilityOfMoney(1.0);
        config.planCalcScore().setUtilityOfLineSwitch(-1.0);

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

        PlanCalcScoreConfigGroup.ModeParams carConvParams =
                new PlanCalcScoreConfigGroup.ModeParams("Car_CONVENTIONAL");
        carConvParams.setMarginalUtilityOfTraveling(-6.0);
        carConvParams.setMonetaryDistanceRate(0.0);
        carConvParams.setConstant(0.0);
        config.planCalcScore().addModeParams(carConvParams);

        PlanCalcScoreConfigGroup.ModeParams carElecParams =
                new PlanCalcScoreConfigGroup.ModeParams("Car_ELECTRIC");
        carElecParams.setMarginalUtilityOfTraveling(-6.0);
        carElecParams.setMonetaryDistanceRate(0.0);
        carElecParams.setConstant(0.0);
        config.planCalcScore().addModeParams(carElecParams);

        Scenario scenario = ScenarioUtils.loadScenario(config);

// Create vehicle type for Car_CONVENTIONAL
        VehicleType convType = VehicleUtils.getFactory()
                .createVehicleType(Id.create("Car_CONVENTIONAL", VehicleType.class));
        convType.setNetworkMode("Car_CONVENTIONAL");
        convType.getCapacity().setSeats(4);
        scenario.getVehicles().addVehicleType(convType);

// Create vehicle type for Car_ELECTRIC
        VehicleType elecType = VehicleUtils.getFactory()
                .createVehicleType(Id.create("Car_ELECTRIC", VehicleType.class));
        elecType.setNetworkMode("Car_ELECTRIC");
        elecType.getCapacity().setSeats(4);
        scenario.getVehicles().addVehicleType(elecType);

        Controler controler = new Controler(scenario);
        controler.run();
    }
}