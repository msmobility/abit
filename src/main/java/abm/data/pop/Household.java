package abm.data.pop;

import abm.data.geo.Location;

import java.util.ArrayList;
import java.util.List;

public class Household {

    private int id;
    private List<Person> persons;
    private Location location;


    public Household(int id, Location location) {
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

    public Location getLocation() {
        return location;
    }
}
