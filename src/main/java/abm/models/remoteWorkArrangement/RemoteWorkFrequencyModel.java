package abm.models.remoteWorkArrangement;

import abm.data.DataSet;
import abm.data.plans.Purpose;
import abm.data.pop.Person;
import abm.io.input.CoefficientsReader;
import abm.models.activityGeneration.frequency.FrequencyGenerator;
import abm.models.activityGeneration.frequency.FrequencyGeneratorModel;
import abm.properties.AbitResources;
import org.apache.log4j.Logger;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class RemoteWorkFrequencyModel implements FrequencyGenerator {

    private static final Logger logger = Logger.getLogger(RemoteWorkFrequencyModel.class);

    private final DataSet dataSet;
    private final Purpose purpose;

    private Map<String, Double> zeroCoef;
    private final Map<String, Double> countCoef;

    private boolean runCalibration;

    Map<Integer, Double> updatedCalibrationFactors = new HashMap<>();

    public RemoteWorkFrequencyModel(DataSet dataSet, Purpose purpose) {
        this.dataSet = dataSet;
        this.purpose = purpose;

        this.zeroCoef =
                new CoefficientsReader(dataSet, purpose.toString().toLowerCase(),
                        Path.of(AbitResources.instance.getString("remoteWork.gen.zero"))).readCoefficients();

        this.countCoef =
                new CoefficientsReader(dataSet, purpose.toString().toLowerCase(),
                        Path.of(AbitResources.instance.getString("remoteWork.gen.count"))).readCoefficients();

    }

    public RemoteWorkFrequencyModel(DataSet dataSet, Purpose purpose, boolean runCalibration) {
        this(dataSet, purpose);
        this.runCalibration = runCalibration;
        for (int frequency = 0; frequency <= 15; frequency++) {
            updatedCalibrationFactors.putIfAbsent(frequency, 0.0);
        }
    }

    @Override
    public int calculateNumberOfActivitiesPerWeek(Person person, Purpose purpose) {
        int numOfRemoteWorkingDays;

        person.getJob().

        if (purpose.equals(Purpose.WORK)) {

            numOfRemoteWorkingDays = polrEstimateTrips(person);

        }
    }
}
