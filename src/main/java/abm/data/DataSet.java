package abm.data;

import abm.data.travelInformation.MitoBasedTravelDistances;
import abm.data.travelInformation.TravelDistances;
import abm.data.travelInformation.TravelTimes;
import abm.data.pop.Household;
import abm.data.pop.Person;

import java.util.HashMap;
import java.util.Map;

public class DataSet {

    final private Map<Integer, Household> households = new HashMap<>();
    final private Map<Integer, Person> persons = new HashMap<>();
    private TravelTimes travelTimes;
    private TravelDistances travelDistances;

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

    public TravelDistances getTravelDistances() {
        return travelDistances;
    }

    public void setTravelDistances(TravelDistances travelDistances) {
        this.travelDistances = travelDistances;
    }
}
