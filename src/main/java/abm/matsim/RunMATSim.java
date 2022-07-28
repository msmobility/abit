package abm.matsim;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;

/**
 * Runs MATSim with the config created in CreateConfig.java and one of the plan files produced by Abit
 */
public class RunMATSim {

    public static void main(String[] args) {

        Config config = ConfigUtils.loadConfig("./configBase.xml");
        config.plans().setInputFile("./output/matsimPlan_tuesday.xml");
        Scenario scenario = ScenarioUtils.createScenario(config);
        ScenarioUtils.loadScenario(scenario);
        Controler controler = new Controler(scenario);
        controler.run();



    }
}
