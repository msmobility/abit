package abm.io.input;

import abm.data.DataSet;
import abm.data.geo.BBSRType;
import abm.data.geo.Zone;
import abm.properties.AbitResources;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.opengis.feature.simple.SimpleFeature;

import java.util.Collection;


public class ZoneReader implements Reader {

    private static Logger logger = Logger.getLogger(ZoneReader.class);
    private String zoneIdField = "id";
    private String zoneNameField = "AGS";
    private String areaTypeField = "BBSR_type";

    private final DataSet dataSet;


    public ZoneReader(DataSet dataSet) {
        this.dataSet = dataSet;
    }


    @Override
    public void read() {

        String zoneShapeFilePath = AbitResources.instance.getString("zones.shp");

        int zoneCounter = 0;

        final Collection<SimpleFeature> allFeatures = ShapeFileReader.getAllFeatures(zoneShapeFilePath);
        for (SimpleFeature feature : allFeatures) {
            int zoneId = Integer.parseInt(feature.getAttribute(zoneIdField).toString());

            Zone zone = new Zone(zoneId);

            zone.setGeometry((Geometry) feature.getDefaultGeometry());
            final Object name = feature.getAttribute(zoneNameField);
            if (name != null) {
                zone.setName(name.toString());
            }
            final Object areaType = feature.getAttribute(areaTypeField);
            if (areaType != null) {
                zone.setAreaType1(BBSRType.valueOf(Integer.parseInt(areaType.toString())));
            }

            dataSet.getZones().put(zoneId, zone);
            zoneCounter++;

        }
        logger.info("Loaded " + zoneCounter + " zones.");


    }
}
