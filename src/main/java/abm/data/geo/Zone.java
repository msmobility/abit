package abm.data.geo;

import org.locationtech.jts.geom.Geometry;

public class Zone implements Location {

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
}
