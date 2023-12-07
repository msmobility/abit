package abm.io.input;

import abm.data.DataSet;
import abm.data.geo.*;
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
    private String distToRailMeter = "distToTransit_m";

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

            final Object distToRail_meter = feature.getAttribute(distToRailMeter);
            if (distToRail_meter != null) {
                zone.setDistToRail_meter((Double) distToRail_meter);
            }else{
                zone.setDistToRail_meter(Double.POSITIVE_INFINITY);
            }

            //todo temporary assignment of zones, need to be extracted from the shapefile or any other file
            zone.setRegioStaR2Type(RegioStaR2.URBAN);
            zone.setRegioStaRGem5Type(RegioStaRGem5.METROPOLIS);
            zone.setRegioStaR7Type(RegioStaR7.URBAN_METROPOLIS);


            dataSet.getZones().put(zoneId, zone);
            zoneCounter++;

        }
        logger.info("Loaded " + zoneCounter + " zones.");


    }
}
