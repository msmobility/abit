package abm.data.geo;

public class Zone implements Location {

    private int id;
    private AreaType1 areaType1;



    @Override
    public int getZoneId() {
        return id;
    }

    public AreaType1 getAreaType1() {
        return areaType1;
    }

    public void setAreaType1(AreaType1 areaType1) {
        this.areaType1 = areaType1;
    }
}
