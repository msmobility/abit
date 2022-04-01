package abm.data.geo;

import org.locationtech.jts.geom.Coordinate;

import java.util.Objects;

public class MicroscopicLocation implements MicroLocation {
    double x;
    double y;

    public MicroscopicLocation(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    @Override
    public Coordinate getCoordinate() {
        return new Coordinate(x, y);
    }

    @Override
    public int getZoneId() {
        return 0;
    }

}
