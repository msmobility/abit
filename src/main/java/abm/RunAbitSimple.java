package abm;

import abm.data.DataSet;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.io.input.SimpleDataReaderManager;
import abm.io.output.OutputWriter;
import abm.models.DefaultModelSetup;
import abm.models.ModelSetup;
import abm.models.PlanGenerator;
import abm.models.SimpleModelSetup;
import abm.properties.AbitResources;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.concurrent.ConcurrentExecutor;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RunAbitSimple {

    static Logger logger = Logger.getLogger(RunAbitSimple.class);


    /**
     * Runs a simple implementation of the AB model with dummy parameters and with no location-specific data or properties
     * @param args
     */
    public static void main(String[] args) {

        MitoUtil.initializeRandomNumber(AbitUtils.getRandomObject());

        logger.info("Reading data");
        DataSet dataSet = new SimpleDataReaderManager(Integer.parseInt(args[0])).readData();

        logger.info("Creating the sub-models");
        ModelSetup modelSetup = new SimpleModelSetup(dataSet);

        logger.info("Generating plans");
        int threads = 1;
        ConcurrentExecutor executor = ConcurrentExecutor.fixedPoolService(threads);
        Map<Integer, List<Person>> personsByThread = new HashMap();

        logger.info("Running plan generator using " + threads + " threads");

        for (Household household : dataSet.getHouseholds().values()) {
            for (Person person : household.getPersons()) {
                if (AbitUtils.getRandomObject().nextDouble() < AbitResources.instance.getDouble("scale.factor", 1.0)) {
                    final int i = AbitUtils.getRandomObject().nextInt(threads);
                    personsByThread.putIfAbsent(i, new ArrayList<>());
                    personsByThread.get(i).add(person);
                }

            }

        }

        for (int i = 0; i < threads; i++) {
            executor.addTaskToQueue(new PlanGenerator(dataSet, modelSetup, i).setPersons(personsByThread.get(i)));
        }

        executor.execute();

        logger.info("Printing out results");
        new OutputWriter(dataSet).run();
    }
}
