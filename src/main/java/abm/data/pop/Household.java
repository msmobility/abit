package abm.data.pop;

import abm.data.geo.MicroscopicLocation;

import java.util.ArrayList;
import java.util.List;

public class Household {

    private int id;
    private List<Person> persons;
    private MicroscopicLocation location;
    private de.tum.bgu.msm.data.household.Household siloHousehold;

    public Household(int id, MicroscopicLocation location) {
        this.id = id;
        this.persons = new ArrayList<>();
        this.location = location;
    }

    public int getId() {
        return id;
    }

    public List<Person> getPersons() {
        return persons;
    }

    public MicroscopicLocation getLocation() {
        return location;
    }

    public de.tum.bgu.msm.data.household.Household getSiloHousehold(){
        return siloHousehold;
    }
}
