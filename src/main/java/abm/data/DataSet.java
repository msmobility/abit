package abm.data;

import abm.data.travelTimes.TravelTimes;
import abm.data.pop.Household;
import abm.data.pop.Person;

import java.util.Map;

public class DataSet {

    private Map<Integer, Household> households;
    private Map<Integer, Person> persons;
    private TravelTimes travelTimes;
}
