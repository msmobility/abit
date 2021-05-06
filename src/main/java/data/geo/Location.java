package data.geo;

public class Location {

    public Location(Coordinates coordinates) {
        this.coordinates = coordinates;
    }

    private Coordinates coordinates;
    private Zone zone;

    public Coordinates getCoordinates() {
        return coordinates;
    }
}
