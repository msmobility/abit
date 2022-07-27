package abm.io.output;

import abm.data.DataSet;

import java.io.FileNotFoundException;

public class OutputWriter {


    private DataSet dataSet;

    public OutputWriter(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    public void run(){
        try {
            new ActivityPrinter(dataSet).print("output/activities.csv");
            new LegPrinter(dataSet).print("output/legs.csv");
            //new PersonSummaryPrinter(dataSet).print("output/person_summary.csv"); really needed? only if something more complex is required.
            new PersonUseOfTimePrinter(dataSet).print("output/use_of_time.csv");
            new PlansToMATSimPlans(dataSet).print();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        //new LegPrinter().print("legs.csv");
        //new MATSimPlanPrinter().print("plans.xml.gz");

    }

}
