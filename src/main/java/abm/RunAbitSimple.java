package abm;

import abm.data.DataSet;
import abm.io.input.SimpleDataReaderManager;
import abm.io.output.OutputWriter;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.util.MitoUtil;
import org.apache.log4j.Logger;

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

        logger.info("Generating plans");
        new PlanGenerator(dataSet).run();

        logger.info("Printing out results");
        new OutputWriter(dataSet).run();
    }
}
