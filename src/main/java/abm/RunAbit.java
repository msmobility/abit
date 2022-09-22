package abm;

import abm.data.DataSet;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.io.input.DefaultDataReaderManager;
import abm.io.output.OutputWriter;
import abm.models.DefaultModelSetup;
import abm.models.ModelSetup;
import abm.models.PlanGenerator;
import abm.properties.AbitResources;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.concurrent.ConcurrentExecutor;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RunAbit {

    static Logger logger = Logger.getLogger(RunAbit.class);


    /**
     * Runs a default implementation of the AB model
     *
     * @param args
     */
    public static void main(String[] args) {

        AbitResources.initializeResources(args[0]);
        AbitUtils.loadHdf5Lib();

        MitoUtil.initializeRandomNumber(AbitUtils.getRandomObject());

        logger.info("Reading data");
        DataSet dataSet = new DefaultDataReaderManager().readData();

        logger.info("Creating the sub-models");
        ModelSetup modelSetup = new DefaultModelSetup(dataSet);

        logger.info("Generating plans");
        int threads = Runtime.getRuntime().availableProcessors();
        ConcurrentExecutor executor = ConcurrentExecutor.fixedPoolService(threads);
        Map<Integer, List<Person>> personsByThread = new HashMap();

        logger.info("Running plan generator using " + threads + " threads");

        long start = System.currentTimeMillis();

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

        long end = System.currentTimeMillis();

        long time = (end - start)/1000;

        logger.info("Runtime = " + time + " Persons = " + dataSet.getPersons().size());

        logger.info("Printing out results");
        new OutputWriter(dataSet).run();
    }
}
