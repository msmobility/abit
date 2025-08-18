package abm.io.input;

import abm.data.DataSet;
import abm.data.geo.Zone;
import abm.data.plans.Purpose;
import abm.properties.AbitResources;
import de.tum.bgu.msm.data.Id;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix2D;
import org.apache.log4j.Logger;

import java.io.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class STLogsumReader implements Reader{
    private static final Logger logger = Logger.getLogger(STLogsumReader.class);
    private int positionOrigin;
    private int positionDestination;
    private int positionValue;
    private BufferedReader reader;
    private int numberOfRecords = 0;
    String delimiter = ",";
    double factor = 1.0;
    private DataSet dataSet;

    private Map<String, Map<Purpose, String>> logsumPaths;
    private final String[] carEmissionClass = {"EURO4", "EURO5", "EURO6", "EUROx"};
    private Map<Integer, Zone> zoneMap;
    private final Map<String, Map<Purpose, IndexedDoubleMatrix2D>> logsumMatrixes;

    public STLogsumReader(DataSet dataSet) {
        this.dataSet = dataSet;

        this.logsumPaths = new HashMap<>();

        this.logsumPaths.putIfAbsent("EURO4", new HashMap<>());
        this.logsumPaths.putIfAbsent("EURO5", new HashMap<>());
        this.logsumPaths.putIfAbsent("EURO6", new HashMap<>());
        this.logsumPaths.putIfAbsent("EUROx", new HashMap<>());

        if (Boolean.parseBoolean(AbitResources.instance.getString("scenario.lowEmissionZone"))){
            this.logsumPaths.get("EURO4").put(Purpose.SHOPPING, AbitResources.instance.getString("logsum.EURO4.SHOPPING.lez"));
            this.logsumPaths.get("EURO4").put(Purpose.ACCOMPANY, AbitResources.instance.getString("logsum.EURO4.ACCOMPANY.lez"));
            this.logsumPaths.get("EURO4").put(Purpose.OTHER, AbitResources.instance.getString("logsum.EURO4.OTHER.lez"));
            this.logsumPaths.get("EURO4").put(Purpose.RECREATION, AbitResources.instance.getString("logsum.EURO4.RECREATION.lez"));
            this.logsumPaths.get("EURO4").put(Purpose.WORK, AbitResources.instance.getString("logsum.EURO4.WORK.lez"));
            this.logsumPaths.get("EURO4").put(Purpose.EDUCATION, AbitResources.instance.getString("logsum.EURO4.EDUCATION.lez"));

            this.logsumPaths.get("EURO5").put(Purpose.SHOPPING, AbitResources.instance.getString("logsum.EURO5.SHOPPING.lez"));
            this.logsumPaths.get("EURO5").put(Purpose.ACCOMPANY, AbitResources.instance.getString("logsum.EURO5.ACCOMPANY.lez"));
            this.logsumPaths.get("EURO5").put(Purpose.OTHER, AbitResources.instance.getString("logsum.EURO5.OTHER.lez"));
            this.logsumPaths.get("EURO5").put(Purpose.RECREATION, AbitResources.instance.getString("logsum.EURO5.RECREATION.lez"));
            this.logsumPaths.get("EURO5").put(Purpose.WORK, AbitResources.instance.getString("logsum.EURO5.WORK.lez"));
            this.logsumPaths.get("EURO5").put(Purpose.EDUCATION, AbitResources.instance.getString("logsum.EURO5.EDUCATION.lez"));

            this.logsumPaths.get("EURO6").put(Purpose.SHOPPING, AbitResources.instance.getString("logsum.EURO6.SHOPPING.lez"));
            this.logsumPaths.get("EURO6").put(Purpose.ACCOMPANY, AbitResources.instance.getString("logsum.EURO6.ACCOMPANY.lez"));
            this.logsumPaths.get("EURO6").put(Purpose.OTHER, AbitResources.instance.getString("logsum.EURO6.OTHER.lez"));
            this.logsumPaths.get("EURO6").put(Purpose.RECREATION, AbitResources.instance.getString("logsum.EURO6.RECREATION.lez"));
            this.logsumPaths.get("EURO6").put(Purpose.WORK, AbitResources.instance.getString("logsum.EURO6.WORK.lez"));
            this.logsumPaths.get("EURO6").put(Purpose.EDUCATION, AbitResources.instance.getString("logsum.EURO6.EDUCATION.lez"));

            this.logsumPaths.get("EUROx").put(Purpose.SHOPPING, AbitResources.instance.getString("logsum.EUROx.SHOPPING.lez"));
            this.logsumPaths.get("EUROx").put(Purpose.ACCOMPANY, AbitResources.instance.getString("logsum.EUROx.ACCOMPANY.lez"));
            this.logsumPaths.get("EUROx").put(Purpose.OTHER, AbitResources.instance.getString("logsum.EUROx.OTHER.lez"));
            this.logsumPaths.get("EUROx").put(Purpose.RECREATION, AbitResources.instance.getString("logsum.EUROx.RECREATION.lez"));
            this.logsumPaths.get("EUROx").put(Purpose.WORK, AbitResources.instance.getString("logsum.EUROx.WORK.lez"));
            this.logsumPaths.get("EUROx").put(Purpose.EDUCATION, AbitResources.instance.getString("logsum.EUROx.EDUCATION.lez"));

        }else{
            this.logsumPaths.get("EURO4").put(Purpose.SHOPPING, AbitResources.instance.getString("logsum.EURO4.SHOPPING.lez"));
            this.logsumPaths.get("EURO4").put(Purpose.ACCOMPANY, AbitResources.instance.getString("logsum.EURO4.ACCOMPANY.lez"));
            this.logsumPaths.get("EURO4").put(Purpose.OTHER, AbitResources.instance.getString("logsum.EURO4.OTHER.lez"));
            this.logsumPaths.get("EURO4").put(Purpose.RECREATION, AbitResources.instance.getString("logsum.EURO4.RECREATION.lez"));
            this.logsumPaths.get("EURO4").put(Purpose.WORK, AbitResources.instance.getString("logsum.EURO4.WORK.lez"));
            this.logsumPaths.get("EURO4").put(Purpose.EDUCATION, AbitResources.instance.getString("logsum.EURO4.EDUCATION.lez"));

            this.logsumPaths.get("EURO5").put(Purpose.SHOPPING, AbitResources.instance.getString("logsum.EURO5.SHOPPING.lez"));
            this.logsumPaths.get("EURO5").put(Purpose.ACCOMPANY, AbitResources.instance.getString("logsum.EURO5.ACCOMPANY.lez"));
            this.logsumPaths.get("EURO5").put(Purpose.OTHER, AbitResources.instance.getString("logsum.EURO5.OTHER.lez"));
            this.logsumPaths.get("EURO5").put(Purpose.RECREATION, AbitResources.instance.getString("logsum.EURO5.RECREATION.lez"));
            this.logsumPaths.get("EURO5").put(Purpose.WORK, AbitResources.instance.getString("logsum.EURO5.WORK.lez"));
            this.logsumPaths.get("EURO5").put(Purpose.EDUCATION, AbitResources.instance.getString("logsum.EURO5.EDUCATION.lez"));

            this.logsumPaths.get("EURO6").put(Purpose.SHOPPING, AbitResources.instance.getString("logsum.EURO6.SHOPPING.lez"));
            this.logsumPaths.get("EURO6").put(Purpose.ACCOMPANY, AbitResources.instance.getString("logsum.EURO6.ACCOMPANY.lez"));
            this.logsumPaths.get("EURO6").put(Purpose.OTHER, AbitResources.instance.getString("logsum.EURO6.OTHER.lez"));
            this.logsumPaths.get("EURO6").put(Purpose.RECREATION, AbitResources.instance.getString("logsum.EURO6.RECREATION.lez"));
            this.logsumPaths.get("EURO6").put(Purpose.WORK, AbitResources.instance.getString("logsum.EURO6.WORK.lez"));
            this.logsumPaths.get("EURO6").put(Purpose.EDUCATION, AbitResources.instance.getString("logsum.EURO6.EDUCATION.lez"));

            this.logsumPaths.get("EUROx").put(Purpose.SHOPPING, AbitResources.instance.getString("logsum.EUROx.SHOPPING.lez"));
            this.logsumPaths.get("EUROx").put(Purpose.ACCOMPANY, AbitResources.instance.getString("logsum.EUROx.ACCOMPANY.lez"));
            this.logsumPaths.get("EUROx").put(Purpose.OTHER, AbitResources.instance.getString("logsum.EUROx.OTHER.lez"));
            this.logsumPaths.get("EUROx").put(Purpose.RECREATION, AbitResources.instance.getString("logsum.EUROx.RECREATION.lez"));
            this.logsumPaths.get("EUROx").put(Purpose.WORK, AbitResources.instance.getString("logsum.EUROx.WORK.lez"));
            this.logsumPaths.get("EUROx").put(Purpose.EDUCATION, AbitResources.instance.getString("logsum.EUROx.EDUCATION.lez"));
        }



        this.logsumMatrixes = this.dataSet.getLogsums();
        this.logsumMatrixes.putIfAbsent("EURO4", new HashMap<>());
        this.logsumMatrixes.putIfAbsent("EURO5", new HashMap<>());
        this.logsumMatrixes.putIfAbsent("EURO6", new HashMap<>());
        this.logsumMatrixes.putIfAbsent("EUROx", new HashMap<>());

        this.zoneMap = this.dataSet.getZones();
    }

    @Override
    public void read() {
        for (String carEmissionClass : this.carEmissionClass) {
            for (Purpose purpose : Purpose.getAllPurposes()) {
                String path = this.logsumPaths.get(carEmissionClass).get(purpose);
                IndexedDoubleMatrix2D logsumMatrix = this.readAndConvertToDoubleMatrix2D(path, this.factor, this.zoneMap.values());
                this.logsumMatrixes.get(carEmissionClass).put(purpose, logsumMatrix);
            }
        }
    }

    private IndexedDoubleMatrix2D readAndConvertToDoubleMatrix2D(String fileName, double factor, Collection<? extends Id> zoneLookup) {
        this.initializeReader(fileName, delimiter);

        IndexedDoubleMatrix2D matrix = new IndexedDoubleMatrix2D(zoneLookup, zoneLookup);
        matrix.assign(Double.MAX_VALUE);

        this.readMatrix(matrix);
        return matrix;
//    }
//       private void initializeReader(String filePath, String delimiter) {
//        try {
//            GZIPInputStream in = new GZIPInputStream(new FileInputStream(filePath));
//            reader = new BufferedReader(new InputStreamReader(in));
//            processHeader(reader.readLine().split(delimiter));
//        } catch (IOException e) {
//            logger.error("Error initializing csv.gz reader: " + e.getMessage(), e);
//        }


    }
    private void initializeReader(String filePath, String delimiter) {
        try {
            System.out.println("Loading logsum file from: " + filePath);
            reader = new BufferedReader(new FileReader(filePath));
            processHeader(reader.readLine().split(delimiter));
        } catch (IOException e) {
            logger.error("Error initializing csv reader: " + e.getMessage(), e);
        }
    }

    private void readMatrix(IndexedDoubleMatrix2D matrix) {
        try {
            String record;
            while ((record = reader.readLine()) != null) {
                numberOfRecords++;
                if (numberOfRecords % 1000000 == 0){
                    logger.info("Read " + numberOfRecords + " records.");
                }

                processRecord(record.split(delimiter), matrix);
            }
        } catch (IOException e) {
            logger.error("Error parsing record number " + numberOfRecords + ": " + e.getMessage(), e);
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        logger.info(this.getClass().getSimpleName() + ": Read " + numberOfRecords + " records.");
    }
    private void processHeader(String[] header) {
        positionOrigin = MitoUtil.findPositionInArray("origin", header);
        positionDestination = MitoUtil.findPositionInArray("destination", header);
        positionValue = MitoUtil.findPositionInArray("logsum", header);
    }

    private void processRecord(String[] record, IndexedDoubleMatrix2D matrix) {
        int origin = Integer.parseInt(record[positionOrigin]);
        int destination = Integer.parseInt(record[positionDestination]);
        double logsum = Double.parseDouble(record[positionValue]) * this.factor;
        if (!Double.isNaN(logsum)){
            matrix.setIndexed(origin, destination, logsum);
        } else {
            logger.warn("Logsum is NaN for origin " + origin + " and destination " + destination);
        }
    }
}
