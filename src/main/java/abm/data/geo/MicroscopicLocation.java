package abm.data.geo;

import de.tum.bgu.msm.data.MicroLocation;
import org.locationtech.jts.geom.Coordinate;

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
