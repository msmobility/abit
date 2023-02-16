package abm.io.output;

import abm.data.DataSet;
import abm.data.plans.Activity;
import abm.data.plans.Tour;
import abm.data.pop.Person;

import java.io.FileNotFoundException;
import java.io.PrintWriter;

public class ActivityPrinter {

    private final DataSet dataSet;

    public ActivityPrinter(DataSet dataSet) {
        this.dataSet = dataSet;
    }


    public void print(String fileName) throws FileNotFoundException {

        PrintWriter pw = new PrintWriter(fileName);

        pw.println(Activity.getHeader());


        for (Person person : dataSet.getPersons().values()) {
            if (person.getPlan() != null){
                for (Tour tour : person.getPlan().getTours().values()) {
                    for (Activity activity : tour.getActivities().values()) {
                        pw.println(activity.toString());
                    }

                }
            }
        }

        pw.close();


    }

}
