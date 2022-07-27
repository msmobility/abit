package abm.data.geo;

import de.tum.bgu.msm.data.Id;
import de.tum.bgu.msm.utils.SeededRandomPointsBuilder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.shape.random.RandomPointsBuilder;
import org.matsim.core.utils.geometry.geotools.MGC;

import java.util.Random;

public class Zone implements Location, Id {

    private int id;
    private BBSRType BBSRType;
    private RegionalType regioType;
    private UrbanRuralType urbanRuralType;
    private Geometry geometry;
    private String name;

    public Zone(int id) {
        this.id = id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public int getZoneId() {
        return id;
    }

    public BBSRType getBBSRType() {
        return BBSRType;
    }

    public void setAreaType1(BBSRType BBSRType) {
        this.BBSRType = BBSRType;
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RegionalType getRegionalType() {
        return regioType;
    }

    public UrbanRuralType getUrbanRuralType() {
        return urbanRuralType;
    }

    @Override
    public int getId() {
        return id;
    }

    public Coordinate getRandomCoordinate(Random random) {
        RandomPointsBuilder randomPointsBuilder = new SeededRandomPointsBuilder(new GeometryFactory(), random);
        randomPointsBuilder.setNumPoints(1);
        randomPointsBuilder.setExtent(geometry);
        Coordinate coordinate = randomPointsBuilder.getGeometry().getCoordinates()[0];
        Point p = MGC.coordinate2Point(coordinate);
        return new Coordinate(p.getX(), p.getY());
    }
}
