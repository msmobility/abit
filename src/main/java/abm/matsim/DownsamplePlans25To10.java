package abm.matsim;

import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DownsamplePlans25To10 {

    // From 100 to 10% means keep 10/100 = 0.1
    private static final double KEEP_RATIO = 25.0 / 100.0;
    private static final long RANDOM_SEED = 987654321L;

    // >>> WRITE YOUR FILE PATHS HERE <<<
    private static final String INPUT_PLANS =
            "output/calibration-100pct-sample-size/no-telework-100pct-sample-size/matsimVehiclePlan_monday.xml";

    private static final String OUTPUT_PLANS =
            "output/25pct-calibrated/baseline_plan_monday.xml";

    public static void main(String[] args) {
        File outFile = new File(OUTPUT_PLANS);
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) {
            boolean created = parent.mkdirs();
            if (!created) {
                System.out.println("Warning: could not create output directory automatically: " + parent.getAbsolutePath());
            }
        }

        Config config = ConfigUtils.createConfig();

        Population inputPopulation = PopulationUtils.readPopulation(INPUT_PLANS);
        Population outputPopulation = PopulationUtils.createPopulation(config);

        Random random = new Random(RANDOM_SEED);

        int totalPersons = inputPopulation.getPersons().size();
        int keptPersons = 0;
        int fixedPersons = 0;
        int fixedActivityEnds = 0;

        List<Person> persons = new ArrayList<>(inputPopulation.getPersons().values());

        for (Person person : persons) {
            if (random.nextDouble() < KEEP_RATIO) {
                boolean personWasFixed = false;

                for (Plan plan : person.getPlans()) {
                    FixStats stats = fixNegativeTimesByChangingSign(plan);

                    if (stats.totalFixes() > 0) {
                        personWasFixed = true;
                    }

                    fixedActivityEnds += stats.fixedActivityEnds;
                }

                if (personWasFixed) {
                    fixedPersons++;
                }

                outputPopulation.addPerson(person);
                keptPersons++;
            }
        }

        PopulationUtils.writePopulation(outputPopulation, OUTPUT_PLANS);

        System.out.println("Input file              : " + INPUT_PLANS);
        System.out.println("Output file             : " + OUTPUT_PLANS);
        System.out.println("Input persons           : " + totalPersons);
        System.out.println("Kept persons            : " + keptPersons);
        System.out.println("Keep ratio              : " + KEEP_RATIO);
        System.out.println("Persons with fixed times: " + fixedPersons);
        System.out.println("Fixed activity ends     : " + fixedActivityEnds);
    }

    private static FixStats fixNegativeTimesByChangingSign(Plan plan) {
        FixStats stats = new FixStats();

        for (PlanElement pe : plan.getPlanElements()) {
            if (pe instanceof Activity act) {
                if (act.getEndTime().isDefined() && act.getEndTime().seconds() < 0) {
                    act.setEndTime(-act.getEndTime().seconds());
                    stats.fixedActivityEnds++;
                }
            }
        }
        return stats;
    }

    private static class FixStats {
        int fixedActivityEnds = 0;


        int totalFixes() {
            return fixedActivityEnds;

        }
    }
}