package abm.io.output;

import abm.data.DataSet;
import abm.properties.AbitResources;

import javax.naming.PartialResultException;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;

public class OutputWriter {


    private DataSet dataSet;

    public OutputWriter(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    public void run(){
        try {
            final String runId = AbitResources.instance.getString("run.id");
            String outputFolder = AbitResources.instance.getString("base.directory") + "/output/" + runId + "/";
            new File(outputFolder).mkdir();

            new ActivityPrinter(dataSet).print(outputFolder + "/activities.csv");
            new LegPrinter(dataSet).print(outputFolder + "/legs.csv");
            //new PersonSummaryPrinter(dataSet).print("output/person_summary.csv"); really needed? only if something more complex is required.
            new PersonUseOfTimePrinter(dataSet).print(outputFolder + "/use_of_time.csv");
            new PlansToMATSimPlans(dataSet).print(outputFolder);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        //new LegPrinter().print("legs.csv");
        //new MATSimPlanPrinter().print("plans.xml.gz");

    }

}
