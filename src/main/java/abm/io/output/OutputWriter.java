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
            new ActivityPrinter(dataSet).print("activities.csv");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        //new LegPrinter().print("legs.csv");
        //new MATSimPlanPrinter().print("plans.xml.gz");

    }

}
