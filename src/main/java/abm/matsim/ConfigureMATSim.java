package abm.matsim;

import abm.properties.AbitResources;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.controler.OutputDirectoryHierarchy;

import java.util.Set;

/**
 * Creates a basic config for MATSim that is compatible wth modes and activity purposes of ABIT.
 * It does not point to the plans file, so this has to be decided in RunMATSim.java
 */
public class ConfigureMATSim {

    public static void main(String[] args) {

        AbitResources.initializeResources(args[0]);

        new ConfigureMATSim().createMatsimConfig();
    }

    void createMatsimConfig(){

        Config config = ConfigUtils.createConfig();

        config.controler().setFirstIteration(0);
        config.controler().setLastIteration(AbitResources.instance.getInt("matsim.iterations"));
        config.controler().setMobsim("qsim");
        config.controler().setWritePlansInterval(config.controler().getLastIteration());
        config.controler().setWriteEventsInterval(config.controler().getLastIteration());

        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        config.controler().setOutputDirectory("./output/matsim");

        config.qsim().setStartTime(0);
        config.qsim().setEndTime(30 * 3600);

        {
            StrategyConfigGroup.StrategySettings strategySettings = new StrategyConfigGroup.StrategySettings();
            strategySettings.setStrategyName("ChangeExpBeta");
            strategySettings.setWeight(0.8);
            config.strategy().addStrategySettings(strategySettings);
        }
        {
            StrategyConfigGroup.StrategySettings strategySettings = new StrategyConfigGroup.StrategySettings();
            strategySettings.setStrategyName("ReRoute");
            strategySettings.setWeight(0.2);
            config.strategy().addStrategySettings(strategySettings);
        }

//        {
//            config.timeAllocationMutator().setMutationRange(1800);
//            config.timeAllocationMutator().setAffectingDuration(true);
//            StrategyConfigGroup.StrategySettings strategySettings = new StrategyConfigGroup.StrategySettings();
//            strategySettings.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator_ReRoute);
//            strategySettings.setWeight(0.1);
//            config.strategy().addStrategySettings(strategySettings);
//        }

        config.strategy().setFractionOfIterationsToDisableInnovation(0.7);
        config.strategy().setMaxAgentPlanMemorySize(4);

        PlanCalcScoreConfigGroup.ActivityParams homeActivity = new PlanCalcScoreConfigGroup.ActivityParams("home");
        homeActivity.setTypicalDuration(12 * 60 * 60);
        config.planCalcScore().addActivityParams(homeActivity);

        PlanCalcScoreConfigGroup.ActivityParams workActivity = new PlanCalcScoreConfigGroup.ActivityParams("work");
        workActivity.setTypicalDuration(8 * 60 * 60);
        config.planCalcScore().addActivityParams(workActivity);

        PlanCalcScoreConfigGroup.ActivityParams educationActivity = new PlanCalcScoreConfigGroup.ActivityParams("education");
        educationActivity.setTypicalDuration(8 * 60 * 60);
        config.planCalcScore().addActivityParams(educationActivity);

        PlanCalcScoreConfigGroup.ActivityParams shoppingActivity = new PlanCalcScoreConfigGroup.ActivityParams("shopping");
        shoppingActivity.setTypicalDuration(1 * 60 * 60);
        config.planCalcScore().addActivityParams(shoppingActivity);

        PlanCalcScoreConfigGroup.ActivityParams otherActivity = new PlanCalcScoreConfigGroup.ActivityParams("other");
        otherActivity.setTypicalDuration(1 * 60 * 60);
        config.planCalcScore().addActivityParams(otherActivity);

        PlanCalcScoreConfigGroup.ActivityParams recreationActivity = new PlanCalcScoreConfigGroup.ActivityParams("recreation");
        recreationActivity.setTypicalDuration(1 * 60 * 60);
        config.planCalcScore().addActivityParams(recreationActivity);

        PlanCalcScoreConfigGroup.ActivityParams accompanyActivity = new PlanCalcScoreConfigGroup.ActivityParams("accompany");
        accompanyActivity.setTypicalDuration(1 * 60 * 60);
        config.planCalcScore().addActivityParams(accompanyActivity);

        PlansCalcRouteConfigGroup.TeleportedModeParams carPassengerParams = new PlansCalcRouteConfigGroup.TeleportedModeParams("car_passenger");
        carPassengerParams.setTeleportedModeFreespeedFactor(1.0);
        config.plansCalcRoute().addTeleportedModeParams(carPassengerParams);

//        PlansCalcRouteConfigGroup.TeleportedModeParams ptParams = new PlansCalcRouteConfigGroup.TeleportedModeParams("pt");
//        ptParams.setBeelineDistanceFactor(1.5);
//        ptParams.setTeleportedModeSpeed(50 / 3.6);
//        config.plansCalcRoute().addTeleportedModeParams(ptParams);

        PlansCalcRouteConfigGroup.TeleportedModeParams bicycleParams = new PlansCalcRouteConfigGroup.TeleportedModeParams("bike");
        bicycleParams.setBeelineDistanceFactor(1.3);
        bicycleParams.setTeleportedModeSpeed(15 / 3.6);
        config.plansCalcRoute().addTeleportedModeParams(bicycleParams);

        PlansCalcRouteConfigGroup.TeleportedModeParams walkParams = new PlansCalcRouteConfigGroup.TeleportedModeParams("walk");
        walkParams.setBeelineDistanceFactor(1.3);
        walkParams.setTeleportedModeSpeed(5 / 3.6);
        config.plansCalcRoute().addTeleportedModeParams(walkParams);

        String runId = "abit";
        config.controler().setRunId(runId);
        config.network().setInputFile(AbitResources.instance.getString("matsim.network"));

        config.qsim().setNumberOfThreads(16);
        config.global().setNumberOfThreads(16);
        config.parallelEventHandling().setNumberOfThreads(16);

        double abitScaleFactor = AbitResources.instance.getDouble("scale.factor", 1.0);
        config.qsim().setFlowCapFactor(1.0);
        config.qsim().setStorageCapFactor(1.0);

        config.plansCalcRoute().setNetworkModes(Set.of("car"));
        config.transit().setUseTransit(true);
        config.transit().setTransitScheduleFile(AbitResources.instance.getString("matsim.pt.schedule"));
        config.transit().setVehiclesFile(AbitResources.instance.getString("matsim.pt.vehicles"));
        config.transit().setTransitModes(Set.of("train", "bus", "tram_metro"));
        config.transit().setUsingTransitInMobsim(false);

        ConfigUtils.writeConfig(config, "./configBase.xml");

    }
}
