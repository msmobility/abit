package abm.data;

import abm.data.travelTimes.TravelTimes;
import abm.data.pop.Household;
import abm.data.pop.Person;

import java.util.HashMap;
import java.util.Map;

public class DataSet {

    final private Map<Integer, Household> households = new HashMap<>();
    final private Map<Integer, Person> persons = new HashMap<>();
    private TravelTimes travelTimes;

    public Map<Integer, Person> getPersons() {
        return persons;
    }

    public TravelTimes getTravelTimes() {
        return travelTimes;
    }

    public void setTravelTimes(TravelTimes travelTimes) {
        this.travelTimes = travelTimes;
    }

    public Map<Integer, Household> getHouseholds() {
        return households;
    }

}
