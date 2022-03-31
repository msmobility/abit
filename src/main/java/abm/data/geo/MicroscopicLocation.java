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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MicroscopicLocation that = (MicroscopicLocation) o;
        return Double.compare(that.x, x) == 0 && Double.compare(that.y, y) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}
