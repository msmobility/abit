package abm.data.pop;

import abm.data.geo.Location;
import abm.data.geo.MicroscopicLocation;
import org.matsim.vehicles.Vehicles;

import java.util.ArrayList;
import java.util.List;

public class Household {

    private int id;
    private List<Person> persons;
    private Location location;
    private final int numberOfCars;
    private List<Vehicles> vehicles; //uses MATSim vehicles for now


    public Household(int id, Location location, int numberOfCars) {
        this.id = id;
        this.numberOfCars = numberOfCars;
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
