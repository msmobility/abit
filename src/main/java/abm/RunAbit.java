package abm;

import abm.data.DataSet;
import abm.io.input.SimpleDataReader;
import abm.io.output.OutputWriter;
import de.tum.bgu.msm.util.MitoUtil;
import jdk.jshell.execution.Util;
import org.apache.log4j.Logger;

public class RunAbit {

    static Logger logger = Logger.getLogger(RunAbit.class);


    public static void main(String[] args) {

        MitoUtil.initializeRandomNumber(Utils.getRandomObject());

        logger.info("Reading data");
        DataSet dataSet = new SimpleDataReader().readData();

        logger.info("Generating plans");
        new PlanGenerator(dataSet).run();

        logger.info("Printing out results");
        new OutputWriter(dataSet).run();
    }
}
