package abm;

import abm.data.DataSet;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.io.input.DefaultDataReaderManager;
import abm.io.output.OutputWriter;
import abm.models.*;
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
        ModelSetup modelSetup = new ModelSetupMuc(dataSet);

        logger.info("Generating plans");
        int threads = Runtime.getRuntime().availableProcessors();
        ConcurrentExecutor executor = ConcurrentExecutor.fixedPoolService(threads);
        Map<Integer, List<Household>> householdsByThread = new HashMap();

        logger.info("Running plan generator using " + threads + " threads");

        long start = System.currentTimeMillis();

        //TODO: parallelize by household not person because of vehicle assignment. Later, for joint travel/coordination destination, need to move parallelization into model steps?
        for (Household household : dataSet.getHouseholds().values()) {
            if (AbitUtils.getRandomObject().nextDouble() < AbitResources.instance.getDouble("scale.factor", 1.0)) {
                final int i = AbitUtils.getRandomObject().nextInt(threads);
                householdsByThread.putIfAbsent(i, new ArrayList<>());
                householdsByThread.get(i).add(household);
            }

        }

        for (int i = 0; i < threads; i++) {
            executor.addTaskToQueue(new PlanGenerator3(dataSet, modelSetup, i).setHouseholds(householdsByThread.get(i)));
        }

        executor.execute();

        long end = System.currentTimeMillis();



        long time = (end - start)/1000;

        logger.info("Runtime = " + time + " Persons = " + dataSet.getPersons().size());

        logger.info("Printing out results");
        new OutputWriter(dataSet).run();
    }
}
