package abm;

import abm.data.DataSet;
import abm.data.plans.Activity;
import abm.data.plans.Leg;
import abm.data.plans.Tour;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.io.input.DefaultDataReaderManager;
import abm.io.output.OutputWriter;
import abm.models.DefaultModelSetup;
import abm.models.ModelSetup;
import abm.models.ModelSetupMuc;
import abm.models.PlanGenerator;
import abm.properties.AbitResources;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.concurrent.ConcurrentExecutor;
import org.apache.log4j.Logger;

import java.util.*;

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
        Map<Integer, List<Person>> personsByThread = new HashMap();

        logger.info("Running plan generator using " + threads + " threads");

        long start = System.currentTimeMillis();

        for (Household household : dataSet.getHouseholds().values()) {
            if (AbitUtils.getRandomObject().nextDouble() < AbitResources.instance.getDouble("scale.factor", 1.0)) {
                for (Person person : household.getPersons()) {
                    final int i = AbitUtils.getRandomObject().nextInt(threads);
                    personsByThread.putIfAbsent(i, new ArrayList<>());
                    personsByThread.get(i).add(person);
                }
            }

        }

//        for (Household household : dataSet.getHouseholds().values()) {
//            for (Person person : household.getPersons()) {
//                if (AbitUtils.getRandomObject().nextDouble() < AbitResources.instance.getDouble("scale.factor", 1.0)) {
//                    final int i = AbitUtils.getRandomObject().nextInt(threads);
//                    personsByThread.putIfAbsent(i, new ArrayList<>());
//                    personsByThread.get(i).add(person);
//                }
//            }
//
//        }

        for (int i = 0; i < threads; i++) {
            executor.addTaskToQueue(new PlanGenerator(dataSet, modelSetup, i).setPersons(personsByThread.get(i)));
        }

        executor.execute();

        //check schedule conflict_0531

//        for (Person persons : dataSet.getPersons().values()){
//            int[] weekTimeSlots = new int[11500];
//            int numOfConflict = 0;
//            if(persons.getPlan()!=null){
//                for(Tour tour : persons.getPlan().getTours().values()){
//                    for(Activity activity : tour.getActivities().values()){
//                        int actStartTime = activity.getStartTime_min();
//                        int actEndTime = activity.getEndTime_min();
//                        for (int i=actStartTime;i<=actEndTime;i++){
//
//                            if (weekTimeSlots[i]!= 1){
//                                weekTimeSlots[i] = 1;
//                            } else {
//                                numOfConflict+=1;
//                            }
//
//                        }
////                    Arrays.fill(weekTimeSlots,actStartTime,actEndTime+1,1);
//                    }
//
////                for(Leg leg : tour.getLegs().values()){
////                    int legStartTime = leg
////                }
//                }
//            }
//            System.out.println(numOfConflict);
//        }



        long end = System.currentTimeMillis();

        long time = (end - start)/1000;

        logger.info("Runtime = " + time + " Persons = " + dataSet.getPersons().size());

        logger.info("Printing out results");
        new OutputWriter(dataSet).run();
    }
}
