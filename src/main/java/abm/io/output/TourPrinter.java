package abm.io.output;

import abm.data.DataSet;
import abm.data.plans.Activity;
import abm.data.plans.HomeEpisode;
import abm.data.plans.Tour;
import abm.data.pop.Person;

import java.io.FileNotFoundException;
import java.io.PrintWriter;

public class TourPrinter {

    private final DataSet dataSet;

    public TourPrinter(DataSet dataSet) {
        this.dataSet = dataSet;
    }


    public void print(String fileName) throws FileNotFoundException {

        PrintWriter pw = new PrintWriter(fileName);

        pw.println(Tour.getHeader());


        for (Person person : dataSet.getPersons().values()) {
            if (person.getPlan() != null){
                for (Tour tour : person.getPlan().getTours().values()) {
                    if (!(tour instanceof HomeEpisode)) {
                        // leg-less (in-home WORK) tours have no legs to report - Tour.toString()
                        // calls legs.firstKey()/lastKey(), which throw on an empty map
                        pw.println(tour.toString());
                    }
                }
            }
        }

        pw.close();


    }

}
