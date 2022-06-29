package abm;

import abm.data.DataSet;
import abm.io.input.DefaultDataReaderManager;
import abm.io.output.OutputWriter;
import abm.properties.Resources;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.util.MitoUtil;
import org.apache.log4j.Logger;

public class RunAbit {

    static Logger logger = Logger.getLogger(RunAbit.class);


    /**
     * Runs a default implementation of the AB model
     * @param args
     */
    public static void main(String[] args) {

        Resources.initializeResources(args[0]);
        AbitUtils.loadHdf5Lib();

        MitoUtil.initializeRandomNumber(AbitUtils.getRandomObject());

        logger.info("Reading data");
        DataSet dataSet = new DefaultDataReaderManager().readData();

        logger.info("Generating plans");
        new PlanGenerator(dataSet).run();

        logger.info("Printing out results");
        new OutputWriter(dataSet).run();
    }
}
