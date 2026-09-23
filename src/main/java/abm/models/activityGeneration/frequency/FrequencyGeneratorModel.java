package abm.models.activityGeneration.frequency;

import abm.calibration.CalibrationOccupation;
import abm.data.DataSet;
import abm.data.geo.RegioStaR2;
import abm.data.geo.RegioStaR7;
import abm.data.geo.RegioStaRGem5;
import abm.data.geo.Zone;
import abm.data.plans.DisabilityMuc;
import abm.data.plans.Purpose;
import abm.data.plans.Tour;
import abm.data.pop.*;
import abm.io.input.CoefficientsReader;
import abm.properties.AbitResources;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.data.person.Disability;
import de.tum.bgu.msm.data.person.Gender;
import de.tum.bgu.msm.data.person.Occupation;
import org.apache.log4j.Logger;
import umontreal.ssj.probdist.NegativeBinomialDist;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class FrequencyGeneratorModel implements FrequencyGenerator {

    private static final Logger logger = Logger.getLogger(FrequencyGeneratorModel.class);
    private final DataSet dataSet;
    private final Purpose purpose;
    private Map<String, Double> zeroCoef;
    private final Map<String, Double> countCoef;
    private boolean runCalibration;
    private final boolean applyAccompanyCalibration;
    private static final Map<String, CalibrationSegment> CALIBRATION_SEGMENTS = createCalibrationSegmentMapping();

    public static final boolean ENABLE_ACCOMPANY_CALIBRATION = false;


    Map<CalibrationOccupation, Map<EmploymentStatus, Map<Integer, Double>>> updatedWorkCalibrationFactors;
    Map<Integer, Double> updatedEducationCalibrationFactors = new HashMap<>();
    Map<CalibrationOccupation, Map<RemoteWorkable, Map<DisabilityMuc, Map<Integer, Double>>>> updatedDiscretionaryCalibrationFactors;

    private final Path mandatoryZeroInputPath =
            Path.of(AbitResources.instance.getString("actgen.mand.zero"));
    private final Path mandatoryCountInputPath =
            Path.of(AbitResources.instance.getString("actgen.mand.count"));
    private final Path accompanyZeroInputPath =
            Path.of(AbitResources.instance.getString("actgen.ac-rr.zero"));
    private final Path accompanyCountInputPath =
            Path.of(AbitResources.instance.getString("actgen.ac-rr.count"));
    private final Path discretionaryCountInputPath =
            Path.of(AbitResources.instance.getString("actgen.sh-re-ot.count"));

    private final Path negBinZeroCoefficientsPath =
            Path.of(AbitResources.instance.getString("actgen.negbin.zero.output"));
    private final Path negBinZeroCalibrationPath =
            Path.of(AbitResources.instance.getString("actgen.negbin.zero.calibration.output"));
    private final Path polrCountCoefficientsPath =
            Path.of(AbitResources.instance.getString("actgen.polr.count.output"));
    private final Path polrCountCalibrationPath =
            Path.of(AbitResources.instance.getString("actgen.polr.count.calibration.output"));
    private final Path negBinCountCoefficientsPath =
            Path.of(AbitResources.instance.getString("actgen.negbin.count.output"));
    private final Path negBinCountCalibrationPath =
            Path.of(AbitResources.instance.getString("actgen.negbin.count.calibration.output"));
    private final Path glmNegBinCoefficientsPath =
            Path.of(AbitResources.instance.getString("actgen.glm.negbin.output"));
    private final Path glmNegBinCalibrationPath =
            Path.of(AbitResources.instance.getString("actgen.glm.negbin.calibration.output"));

    public FrequencyGeneratorModel(DataSet dataSet, Purpose purpose, boolean runCalibration) {

        this.dataSet = dataSet;
        this.purpose = purpose;
        this.runCalibration = runCalibration;
        this.applyAccompanyCalibration =
                purpose != Purpose.ACCOMPANY
                        || ENABLE_ACCOMPANY_CALIBRATION;


        if (purpose.equals(Purpose.WORK)
                || purpose.equals(Purpose.EDUCATION)
                || purpose.equals(Purpose.ACCOMPANY)) {

            Path zeroCoefficientPath;

            if (purpose.equals(Purpose.ACCOMPANY)) {
                zeroCoefficientPath =
                        selectCoefficientInput(
                                accompanyZeroInputPath,
                                negBinZeroCoefficientsPath);
            } else {
                zeroCoefficientPath =
                        selectCoefficientInput(
                                mandatoryZeroInputPath,
                                negBinZeroCoefficientsPath);
            }

            this.zeroCoef =
                    new CoefficientsReader(
                            dataSet,
                            purpose.toString().toLowerCase(),
                            zeroCoefficientPath)
                            .readCoefficients();
        }

        if (purpose.equals(Purpose.WORK)
                || purpose.equals(Purpose.EDUCATION)) {

            Path countCoefficientPath =
                    selectCoefficientInput(
                            mandatoryCountInputPath,
                            polrCountCoefficientsPath);

            this.countCoef =
                    new CoefficientsReader(
                            dataSet,
                            purpose.toString().toLowerCase(),
                            countCoefficientPath)
                            .readCoefficients();

        } else if (purpose.equals(Purpose.ACCOMPANY)) {

            Path countCoefficientPath =
                    selectCoefficientInput(
                            accompanyCountInputPath,
                            negBinCountCoefficientsPath);

            this.countCoef =
                    new CoefficientsReader(
                            dataSet,
                            "accompany",
                            countCoefficientPath)
                            .readCoefficients();

        } else {

            Path countCoefficientPath =
                    selectCoefficientInput(
                            discretionaryCountInputPath,
                            glmNegBinCoefficientsPath);

            this.countCoef =
                    new CoefficientsReader(
                            dataSet,
                            purpose.toString().toLowerCase(),
                            countCoefficientPath)
                            .readCoefficients();
        }


        if (runCalibration || (purpose == Purpose.ACCOMPANY && !applyAccompanyCalibration)) {

            initializeCalibrationFactors();

        } else {

            loadCalibrationFactors();
        }
    }

    private Path selectCoefficientInput(Path baseInputPath, Path calibratedOutputPath) {
        if (runCalibration) {
            requireReadableFile(baseInputPath, "base coefficient input");
            return baseInputPath;
        }

        if (Files.isRegularFile(calibratedOutputPath)
                && Files.isReadable(calibratedOutputPath)) {
            return calibratedOutputPath;
        }

        requireReadableFile(baseInputPath, "base coefficient input");
        return baseInputPath;
    }

    private void requireReadableFile(Path path, String description) {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalStateException(
                    "Cannot read " + description + ": " + path.toAbsolutePath());
        }
    }

    public FrequencyGeneratorModel(DataSet dataSet, Purpose purpose) {

        this(dataSet, purpose, false);
    }

    private void initializeCalibrationFactors() {

        updatedWorkCalibrationFactors = new HashMap<>();

        for (CalibrationOccupation occupation : CalibrationOccupation.values()) {
            updatedWorkCalibrationFactors.put(occupation, new HashMap<>());

            for (EmploymentStatus employmentStatus : EmploymentStatus.values()) {
                updatedWorkCalibrationFactors.get(occupation).put(employmentStatus, new HashMap<>());

                for (int freq = 0; freq <= 7; freq++) {
                    updatedWorkCalibrationFactors.get(occupation).get(employmentStatus).put(freq, 0.0);
                }
            }
        }

        updatedEducationCalibrationFactors = new HashMap<>();

        for (int freq = 0; freq <= 7; freq++) {
            updatedEducationCalibrationFactors.put(freq, 0.0);
        }

        updatedDiscretionaryCalibrationFactors = new HashMap<>();

        int maxFrequency = purpose.equals(Purpose.ACCOMPANY) ? 7 : 15;

        for (CalibrationOccupation occupation : CalibrationOccupation.values()) {
            updatedDiscretionaryCalibrationFactors.put(occupation, new HashMap<>());

            for (RemoteWorkable remoteWorkable : RemoteWorkable.values()) {
                updatedDiscretionaryCalibrationFactors.get(occupation).put(remoteWorkable, new HashMap<>());

                for (DisabilityMuc disability : DisabilityMuc.values()) {
                    updatedDiscretionaryCalibrationFactors.get(occupation).get(remoteWorkable).put(disability, new HashMap<>());

                    for (int freq = 0; freq <= maxFrequency; freq++) {
                        updatedDiscretionaryCalibrationFactors.get(occupation).get(remoteWorkable).get(disability).put(freq, 0.0);
                    }
                }
            }
        }
    }

    private void loadCalibrationFactors() {

        initializeCalibrationFactors();

        if (purpose.equals(Purpose.WORK)) {

            if (Files.isRegularFile(negBinZeroCalibrationPath)) {
                loadWorkCalibrationFactors(negBinZeroCalibrationPath);
            }
            if (Files.isRegularFile(polrCountCalibrationPath)) {
                loadWorkCalibrationFactors(polrCountCalibrationPath);
            }

        } else if (purpose.equals(Purpose.EDUCATION)) {

            if (Files.isRegularFile(negBinZeroCalibrationPath)) {
                loadEducationCalibrationFactors(negBinZeroCalibrationPath);
            }
            if (Files.isRegularFile(polrCountCalibrationPath)) {
                loadEducationCalibrationFactors(polrCountCalibrationPath);
            }

        } else if (purpose.equals(Purpose.ACCOMPANY)) {

            if (applyAccompanyCalibration) {
                if (Files.isRegularFile(negBinZeroCalibrationPath)) {
                    loadAccompanyCalibrationFactors(negBinZeroCalibrationPath);
                }
                if (Files.isRegularFile(negBinCountCalibrationPath)) {
                    loadAccompanyCountCalibrationFactors(negBinCountCalibrationPath);
                }
            }

        } else {

            if (Files.isRegularFile(glmNegBinCalibrationPath)) {
                loadDiscretionaryCalibrationFactors(
                        glmNegBinCalibrationPath,
                        purpose.toString().toLowerCase());
            }
        }
    }

    private Map<String, Double> readCalibrationValues(Path calibrationPath, String columnName) {

        Map<String, Double> calibrationFactors = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(calibrationPath)) {
            String header = reader.readLine();
            if (header == null) {
                throw new IllegalStateException("Calibration file is empty: " + calibrationPath);
            }

            String[] columns = header.split(",", -1);

            int columnIndex = -1;

            for (int i = 0; i < columns.length; i++) {
                if (columns[i].trim().equals(columnName)) {
                    columnIndex = i;
                    break;
                }
            }

            if (columnIndex == -1) {
                throw new IllegalStateException(
                        "Column '" + columnName + "' not found in calibration file: " + calibrationPath);
            }

            String line;

            while ((line = reader.readLine()) != null) {

                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] values = line.split(",", -1);

                if (values.length <= columnIndex) {
                    continue;
                }

                String variable = values[0].trim();
                String value = values[columnIndex].trim();

                if (variable.isEmpty() || value.isEmpty()) {
                    continue;
                }

                calibrationFactors.put(
                        variable,
                        Double.parseDouble(value));
            }

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read calibration file: " +
                            calibrationPath,
                    e);
        }

        return calibrationFactors;
    }

    private void loadWorkCalibrationFactors(Path calibrationPath) {

        Map<String, Double> values =
                readCalibrationValues(calibrationPath, "work");

        for (Map.Entry<String, Double> entry : values.entrySet()) {

            String variable = entry.getKey();
            double value = entry.getValue();

            if (!variable.startsWith("calibration_")) {
                continue;
            }

            String name =
                    variable.substring("calibration_".length());

            int frequencyInterval = 0;
            String segmentKey = name;

            int separatorIndex =
                    name.lastIndexOf("_");

            if (separatorIndex > 0) {

                String interval =
                        name.substring(separatorIndex + 1);

                if (interval.matches("\\d+\\|\\d+")) {

                    frequencyInterval =
                            Integer.parseInt(
                                    interval.substring(
                                            0,
                                            interval.indexOf("|")));

                    segmentKey =
                            name.substring(
                                    0,
                                    separatorIndex);
                }
            }

            CalibrationSegment segment =
                    CALIBRATION_SEGMENTS.get(segmentKey);

            if (segment == null) {
                continue;
            }

            updatedWorkCalibrationFactors
                    .get(segment.getOccupation())
                    .get(segment.getEmploymentStatus())
                    .put(frequencyInterval, value);
        }
    }

    private void loadEducationCalibrationFactors(Path calibrationPath) {

        Map<String, Double> values =
                readCalibrationValues(calibrationPath, "education");

        for (Map.Entry<String, Double> entry : values.entrySet()) {

            String variable = entry.getKey();
            double value = entry.getValue();

            if (!variable.startsWith("calibration_")) {
                continue;
            }

            String name =
                    variable.substring("calibration_".length());

            int frequencyInterval = 0;

            if (!name.equals("education")) {

                int separatorIndex = name.lastIndexOf("_");

                if (separatorIndex > 0) {

                    String interval =
                            name.substring(separatorIndex + 1);

                    if (interval.matches("\\d+\\|\\d+")) {

                        frequencyInterval =
                                Integer.parseInt(
                                        interval.substring(
                                                0,
                                                interval.indexOf("|")));
                    }
                }
            }

            updatedEducationCalibrationFactors
                    .put(frequencyInterval, value);
        }
    }

    private void loadAccompanyCalibrationFactors(Path calibrationPath) {

        Map<String, Double> values =
                readCalibrationValues(
                        calibrationPath,
                        "accompany");

        for (Map.Entry<String, Double> entry : values.entrySet()) {

            String variable = entry.getKey();
            double value = entry.getValue();

            if (!variable.startsWith("calibration_")) {
                continue;
            }

            String segmentKey =
                    variable.substring("calibration_".length());

            CalibrationSegment segment =
                    CALIBRATION_SEGMENTS.get(segmentKey);

            if (segment == null) {
                continue;
            }

            if (segment.getRemoteWorkable() == null
                    || segment.getDisability() == null) {
                continue;
            }

            updatedDiscretionaryCalibrationFactors
                    .get(segment.getOccupation())
                    .get(segment.getRemoteWorkable())
                    .get(segment.getDisability())
                    .put(0, value);
        }
    }

    private void loadDiscretionaryCalibrationFactors(Path calibrationPath, String purposeColumn) {

        Map<String, Double> values =
                readCalibrationValues(calibrationPath, purposeColumn);

        for (Map.Entry<String, Double> entry : values.entrySet()) {

            String variable = entry.getKey();
            double value = entry.getValue();

            if (!variable.startsWith("calibration_")) {
                continue;
            }

            String segmentKey =
                    variable.substring("calibration_".length());

            CalibrationSegment segment =
                    CALIBRATION_SEGMENTS.get(segmentKey);

            if (segment == null) {
                continue;
            }

            if (segment.getRemoteWorkable() == null
                    || segment.getDisability() == null) {
                continue;
            }

            updatedDiscretionaryCalibrationFactors
                    .get(segment.getOccupation())
                    .get(segment.getRemoteWorkable())
                    .get(segment.getDisability())
                    .put(0, value);
        }
    }

    private void loadAccompanyCountCalibrationFactors(Path calibrationPath) {

        Map<String, Double> values =
                readCalibrationValues(calibrationPath, "accompany");

        for (Map.Entry<String, Double> entry : values.entrySet()) {

            String variable = entry.getKey();
            double value = entry.getValue();

            switch (variable) {

                case "calibration_employed_remote_working_with_disability":
                    putDiscretionaryCalibration(
                            CalibrationOccupation.EMPLOYED,
                            RemoteWorkable.TRUE,
                            DisabilityMuc.WITH,
                            1,
                            value);
                    break;

                case "calibration_employed_remote_working_without_disability":
                    putDiscretionaryCalibration(
                            CalibrationOccupation.EMPLOYED,
                            RemoteWorkable.TRUE,
                            DisabilityMuc.WITHOUT,
                            1,
                            value);
                    break;

                case "calibration_employed_no_remote_working_with_disability":
                    putDiscretionaryCalibration(
                            CalibrationOccupation.EMPLOYED,
                            RemoteWorkable.FALSE,
                            DisabilityMuc.WITH,
                            1,
                            value);
                    break;

                case "calibration_employed_no_remote_working_without_disability":
                    putDiscretionaryCalibration(
                            CalibrationOccupation.EMPLOYED,
                            RemoteWorkable.FALSE,
                            DisabilityMuc.WITHOUT,
                            1,
                            value);
                    break;

                case "calibration_other_no_remote_working_with_disability":
                    putDiscretionaryCalibration(
                            CalibrationOccupation.OTHER,
                            RemoteWorkable.FALSE,
                            DisabilityMuc.WITH,
                            1,
                            value);
                    break;

                case "calibration_other_no_remote_working_without_disability":
                    putDiscretionaryCalibration(
                            CalibrationOccupation.OTHER,
                            RemoteWorkable.FALSE,
                            DisabilityMuc.WITHOUT,
                            1,
                            value);
                    break;

                default:
                    break;
            }
        }
    }

    @Override
    public int calculateNumberOfActivitiesPerWeek(Person person, Purpose purpose) {
        int numOfActivity;

        if (purpose.equals(Purpose.WORK)) {

            if (person.getAge() < 15 || person.getAge() > 70) {
                numOfActivity = 0;
            } else {
                numOfActivity = polrEstimateTrips(person);
            }

            if (numOfActivity > 7) {
                numOfActivity = 7;
            }


        } else if (purpose.equals(Purpose.EDUCATION)) {

            if (! person.getOccupation().equals(Occupation.STUDENT)) {
                numOfActivity = 0;
            } else {
                numOfActivity = polrEstimateTrips(person);
            }

            if (numOfActivity > 7) {
                numOfActivity = 7;
            }

        } else if (purpose.equals(Purpose.ACCOMPANY)) {
            numOfActivity = hurdleEstimateTrips(person);
            if (numOfActivity > 7) {
                numOfActivity = 7;
            }
        } else {
            numOfActivity = nbEstimateTrips(person);
            if (numOfActivity > 15){
                numOfActivity = 15;
            }
        }
        return numOfActivity;
    }

    /**
     * Calculate 0-inflated binary + ordered logit
     *
     * @param pp
     * @return
     */
    private int polrEstimateTrips(Person pp) {

        double randomNumber =
                AbitUtils.getRandomObject().nextDouble();

        double binaryUtility =
                getPredictor(pp, zeroCoef);

        CalibrationSegment workSegment = null;

        if (purpose.equals(Purpose.WORK)) {

            workSegment =
                    getWorkCalibrationSegment(pp);

            binaryUtility +=
                    getWorkCalibrationFactor(
                            workSegment,
                            0);

        } else if (purpose.equals(Purpose.EDUCATION)) {

            binaryUtility +=
                    updatedEducationCalibrationFactors.get(0);
        }

        double phi =
                Math.exp(binaryUtility)
                        / (1 + Math.exp(binaryUtility));

        /*
         * Ordered-logit count component
         */
        double mu =
                getPredictor(pp, countCoef);

        double[] intercepts =
                new double[6];

        for (int frequencyInterval = 1;
             frequencyInterval <= 6;
             frequencyInterval++) {

            intercepts[frequencyInterval - 1] =
                    countCoef.get(
                            String.valueOf(frequencyInterval)
                                    + "|"
                                    + (frequencyInterval + 1));

            if (purpose.equals(Purpose.WORK)) {

                intercepts[frequencyInterval - 1] +=
                        getWorkCalibrationFactor(
                                workSegment,
                                frequencyInterval);

            } else if (purpose.equals(Purpose.EDUCATION)) {

                intercepts[frequencyInterval - 1] +=
                        updatedEducationCalibrationFactors
                                .get(frequencyInterval);
            }
        }

        /*
         * Draw frequency from the probability distribution.
         */
        int i = 0;
        double cumProb = 0;

        double prob = 1 - phi;
        cumProb += prob;

        while (cumProb < randomNumber) {

            i++;

            if (i < 7) {

                prob =
                        1 / (
                                1 + Math.exp(
                                        mu - intercepts[i - 1])
                        );

            } else {

                prob = 1;
            }

            if (i > 1) {

                prob -=
                        1 / (
                                1 + Math.exp(
                                        mu - intercepts[i - 2])
                        );
            }

            cumProb += phi * prob;
        }

        return i;
    }

    /**
     * Binary + negative binomial
     *
     * @param pp
     * @return
     */
    private int hurdleEstimateTrips(Person pp) {

        double randomNumber =
                AbitUtils.getRandomObject().nextDouble();

        CalibrationSegment segment =
                getDiscretionaryCalibrationSegment(pp);

        double binaryUtility =
                getPredictor(pp, zeroCoef)
                        + getDiscretionaryCalibrationFactor(
                        segment,
                        0);

        double phi =
                Math.exp(binaryUtility)
                        / (1 + Math.exp(binaryUtility));


        double mu =
                Math.exp(
                        getPredictor(pp, countCoef)
                                + getDiscretionaryCalibrationFactor(
                                segment,
                                1));

        double theta =
                countCoef.get("theta");

        NegativeBinomialDist nb =
                new NegativeBinomialDist(
                        theta,
                        theta / (theta + mu));
        double p0_zero =
                Math.log(phi);

        double p0_count =
                Math.log(1 - nb.cdf(0));

        double logphi =
                p0_zero - p0_count;

        int i = 0;

        double cumProb = 0;

        double prob =
                1 - Math.exp(p0_zero);

        cumProb += prob;

        while (randomNumber > cumProb) {

            i++;

            prob =
                    Math.exp(
                            logphi
                                    + Math.log(nb.prob(i)));

            cumProb += prob;
        }

        return i;
    }

    /**
     * Negative binomial
     *
     * @param pp
     * @return
     */
    private int nbEstimateTrips(Person pp) {

        double randomNumber =
                AbitUtils.getRandomObject().nextDouble();

        CalibrationSegment segment =
                getDiscretionaryCalibrationSegment(pp);

        double calibrationFactor =
                getDiscretionaryCalibrationFactor(
                        segment,
                        0);

        double mu =
                Math.exp(
                        getPredictor(pp, countCoef)
                                + calibrationFactor);

        double theta =
                countCoef.get("theta");

        if (!runCalibration) {
            theta += calibrationFactor;
        }

        if (!Double.isFinite(theta) || theta <= 0.0) {
            throw new IllegalStateException(
                    "Invalid negative-binomial theta for "
                            + purpose
                            + " | segment=" + segment
                            + " | baseTheta="
                            + countCoef.get("theta")
                            + " | calibrationFactor="
                            + calibrationFactor
                            + " | theta="
                            + theta);
        }

        double probability =
                theta / (theta + mu);

        NegativeBinomialDist nb =
                new NegativeBinomialDist(
                        theta,
                        probability);

        int i = 0;

        double cumProb =
                nb.prob(0);

        while (randomNumber > cumProb) {
            i++;
            cumProb += nb.prob(i);
        }

        return i;
    }

    /**
     * Calculate the linear predictor for the model ()
     *
     * @param pp
     * @param coefficients
     * @return
     */
    public double getPredictor(Person pp, Map<String, Double> coefficients) {
        Household hh = pp.getHousehold();
        double predictor = 0.;

        // Intercept
        predictor += coefficients.get("(Intercept)");

        Zone zone = dataSet.getZones().get(hh.getLocation().getZoneId());

        //Todo It seems like MOP doesn't have BBSR type, but the regioStaRGem5. Ask Joanna for double checking
        RegioStaR2 regioStrR2 = zone.getRegioStaR2Type();
        switch (regioStrR2) {
            case URBAN:
                predictor += coefficients.get("hh.urban");
                break;
        }

        RegioStaR7 regioStaR7 = zone.getRegioStaR7Type();
        switch (regioStaR7) {
            case URBAN_METROPOLIS:
                predictor += coefficients.get("hh.regionType_71");
                break;
            case URBAN_REGIOPOLIS:
                predictor += coefficients.get("hh.regionType_72");
                break;
            case URBAN_MEDIUM_SIZED_CITY:
                predictor += coefficients.get("hh.regionType_73");
                break;
            case URBAN_PROVINCIAL:
                predictor += coefficients.get("hh.regionType_74");
                break;
            case RURAL_CENTRAL_CITY:
                predictor += coefficients.get("hh.regionType_75");
                break;
            case RURAL_URBAN_AREA:
                predictor += coefficients.get("hh.regionType_76");
                break;
            case RURAL_PROVICIAL:
                predictor += coefficients.get("hh.regionType_77");
                break;
        }

        RegioStaRGem5 regioStaRGem5 = zone.getRegioStaRGem5Type();
        switch (regioStaRGem5) {
            case METROPOLIS:
                predictor += coefficients.get("hh.municipalityType_51");
                break;
            case REGIOPOLIS_LARGE_CITY:
                predictor += coefficients.get("hh.municipalityType_52");
                break;
            case CENTRAL_CITY:
                predictor += coefficients.get("hh.municipalityType_53");
                break;
            case URBAN_AREA:
                predictor += coefficients.get("hh.municipalityType_54");
                break;
            case PROVINCIAL_RURAL:
                predictor += coefficients.get("hh.municipalityType_55");
                break;
        }

        // Refer to the EconomicStatus class for more information
        EconomicStatus economicStatus = pp.getHousehold().getEconomicStatus();
        switch (economicStatus) {
            case from0to800:
                predictor += coefficients.get("hh.econStatus_1");
                break;
            case from801to1600:
                predictor += coefficients.get("hh.econStatus_2");
                break;
            case from1601to2400:
                predictor += coefficients.get("hh.econStatus_3");
                break;
            case from2401:
                //predictor += coefficients.get("hh.econStatus_4");
                break;
        }

        int numUnemployedInHh = 0;
        for (Person person : pp.getHousehold().getPersons()) {
            if (!person.getOccupation().equals(Occupation.EMPLOYED)) {
                numUnemployedInHh += 1;
            }
        }
        predictor += numUnemployedInHh * coefficients.get("hh.notEmployed");


        int householdSize = hh.getPersons().size();
        if (householdSize == 2) {
            predictor += coefficients.get("hh.size_2");
        } else if (householdSize == 3) {
            predictor += coefficients.get("hh.size_3");
        } else if (householdSize == 4) {
            predictor += coefficients.get("hh.size_4");
        } else if (householdSize >= 5) {
            //assert (householdSize >= 5); what is assert?
            predictor += coefficients.get("hh.size_5");
        }

        // Number of children in household
        int householdChildren = (int) hh.getPersons().stream().filter(person -> person.getAge() < 18).count();
        if (householdChildren == 1) {
            predictor += coefficients.get("hh.children_1");
        } else if (householdChildren == 2) {
            predictor += coefficients.get("hh.children_2");
        } else if (householdChildren >= 3) {
            predictor += coefficients.get("hh.children_3");
        }

        int householdAdult = householdSize - householdChildren;
        if (householdAdult == 1) {
            predictor += coefficients.get("hh.adults_1");
        } else if (householdAdult == 2) {
            predictor += coefficients.get("hh.adults_2");
        } else if (householdAdult >= 3) {
            predictor += coefficients.get("hh.adults_3");
        } else if (householdAdult >= 4) {
            predictor += coefficients.get("hh.adults_4");
        }

        if (householdChildren != 0) {
            double adultsPerChild = householdAdult / householdChildren;
            if (adultsPerChild < 1) {
                predictor += coefficients.get("hh.adults_per_child_0");
            } else if (adultsPerChild == 1) {
                predictor += coefficients.get("hh.adults_per_child_1");
            } else {
                predictor += coefficients.get("hh.adults_per_child_2");
            }
        }

        int age = pp.getAge();
        predictor += age * coefficients.get("p.age");

        AgeGroup ageGroup = AgeGroup.assignAgeGroup(age);
        switch (ageGroup) {
            case from0to18:
                predictor += coefficients.get("p.age_gr_1");
                break;
            case from19to29:
                predictor += coefficients.get("p.age_gr_2");
                break;
            case from30to49:
                predictor += coefficients.get("p.age_gr_3");
                break;
            case from50to59:
                predictor += coefficients.get("p.age_gr_4");
                break;
            case from60to69:
                predictor += coefficients.get("p.age_gr_5");
                break;
            case from70:
                predictor += coefficients.get("p.age_gr_6");
                break;
        }

        AgeGroupFine ageGroupFine = AgeGroupFine.assignAgeGroupFine(age);
        switch (ageGroupFine) {
            case from0to18:
                predictor += coefficients.get("p.age_gr_fine_1");
                break;
            case from19to24:
                predictor += coefficients.get("p.age_gr_fine_2");
                break;
            case from25to29:
                predictor += coefficients.get("p.age_gr_fine_3");
                break;
            case from30to49:
                //predictor += coefficients.get("p.age_gr_fine_4");
                //is the reference and it is not added to the tables (perhaps it should)
                break;
            case from50to59:
                predictor += coefficients.get("p.age_gr_fine_5");
                break;
            case from60to69:
                predictor += coefficients.get("p.age_gr_fine_6");
                break;
            case from70:
                predictor += coefficients.get("p.age_gr_fine_7");
                break;
        }

        switch (pp.getOccupation()) {
            case STUDENT:
                predictor += coefficients.get("p.occupationStatus_Student");
                break;
            case EMPLOYED:
                //todo move this into the person reader and then define a partTime variable status?
                if (pp.getEmploymentStatus().equals(EmploymentStatus.HALFTIME_EMPLOYED)){
                    predictor += coefficients.get("p.occupationStatus_Halftime");
                    break;
                }else{
                    predictor += coefficients.get("p.occupationStatus_Employed");
                    break;
                }
            case UNEMPLOYED:
                predictor += coefficients.get("p.occupationStatus_Unemployed");
                break;
            case RETIREE:
                //todo is this like unemployed?
                predictor += coefficients.get("p.occupationStatus_Unemployed");
                break;
            case TODDLER:
                //todo is this like unemployed? Joanna: MOP has data from age 11+
                predictor += coefficients.get("p.occupationStatus_Unemployed");
                break;
        }
        //carlos added this for testing - needs check


        if (pp.getGender().equals(Gender.FEMALE)) {
            predictor += coefficients.get("p.female");
        }

        if (pp.isHasLicense()) {
            predictor += coefficients.get("p.driversLicense");
        }

        if (pp.hasBicycle()) {
            predictor += coefficients.get("p.ownBicycle");
        }

        int householdAutos = hh.getNumberOfCars();
        if (householdAutos == 1) {
            predictor += coefficients.get("hh.cars_1");
        } else if (householdAutos == 2) {
            predictor += coefficients.get("hh.cars_2");
        } else if (householdAutos >= 3) {
            predictor += coefficients.get("hh.cars_3");
        }

        switch (pp.getHabitualMode()) {
            case CAR_DRIVER:
                predictor += coefficients.get("p.t_mand_habmode_car");
                break;
            case BIKE:
                predictor += coefficients.get("p.t_mand_habmode_cycle");
                break;
            case PT:
                predictor += coefficients.get("p.t_mand_habmode_PT");
                break;
            case WALK:
                predictor += coefficients.get("p.t_mand_habmode_walk");
                break;
        }
        int numDaysWork = 0;
        int numDaysEducation = 0;

        if (Purpose.getDiscretionaryPurposes().contains(purpose)) {

            final List<Tour> tourList = pp.getPlan().getTours().values().stream().filter(tour -> Purpose.getMandatoryPurposes().contains(tour.getMainActivity().getPurpose())).collect(Collectors.toList());

            int[] daysOfWork = new int[]{0, 0, 0, 0, 0, 0, 0};
            int[] daysOfEducation = new int[]{0, 0, 0, 0, 0, 0, 0};

            for (Tour tour : tourList) {
                if (tour.getMainActivity().getPurpose().equals(Purpose.WORK)) {
                    int dayOfWeek = tour.getMainActivity().getDayOfWeek().getValue();
                    if (daysOfWork[dayOfWeek - 1] == 0) {
                        daysOfWork[dayOfWeek - 1] = 1;
                    }
                } else {
                    int dayOfWeek = tour.getMainActivity().getDayOfWeek().getValue();
                    if (daysOfEducation[dayOfWeek - 1] == 0) {
                        daysOfEducation[dayOfWeek - 1] = 1;
                    }
                }
            }

            numDaysWork = Arrays.stream(daysOfWork).sum();
            numDaysEducation = Arrays.stream(daysOfEducation).sum();
        }

        predictor += numDaysWork * coefficients.get("num_days_edu");
        predictor += numDaysEducation * coefficients.get("num_days_work");
        //predictor += coefficients.get("calibration");

        return predictor;
    }

    private CalibrationOccupation getCalibrationOccupation(Person person) {
        if (person.getOccupation() == Occupation.EMPLOYED) {
            return CalibrationOccupation.EMPLOYED;
        }
        return CalibrationOccupation.OTHER;
    }

    public void updateWorkCalibrationFactor(Map<CalibrationOccupation, Map<EmploymentStatus, Map<Integer, Double>>> newCalibrationFactors) {
        for (CalibrationOccupation occupation : CalibrationOccupation.values()) {
            for (EmploymentStatus employmentStatus : EmploymentStatus.values()) {

                for (int freq = 0; freq <= 7; freq++) {

                    double calibrationFactorFromLastIteration = updatedWorkCalibrationFactors.get(occupation).get(employmentStatus).get(freq);
                    double updatedCalibrationFactor = calibrationFactorFromLastIteration + newCalibrationFactors.get(occupation).get(employmentStatus).get(freq);
                    updatedWorkCalibrationFactors.get(occupation).get(employmentStatus).put(freq, updatedCalibrationFactor);
                    logger.info("Calibration factor for " + purpose + " | " + occupation + " | " + employmentStatus + " | " + freq + " : " + updatedCalibrationFactor);
                }
            }
        }
    }

    public void updateEducationCalibrationFactor(Map<Integer, Double> newCalibrationFactors) {

        for (int freq = 0; freq <= 7; freq++) {
            double calibrationFactorFromLastIteration = updatedEducationCalibrationFactors.get(freq);
            double updatedCalibrationFactor = calibrationFactorFromLastIteration + newCalibrationFactors.get(freq);
            updatedEducationCalibrationFactors.put(freq, updatedCalibrationFactor);
            logger.info("Calibration factor for " + purpose + " | " + freq + " : " + updatedCalibrationFactor);
        }
    }

    public void updateDiscretionaryCalibrationFactor(Map<CalibrationOccupation, Map<RemoteWorkable, Map<DisabilityMuc, Map<Integer, Double>>>> newCalibrationFactors) {

        int maxFrequency = purpose == Purpose.ACCOMPANY ? 7 : 15;

        for (CalibrationOccupation occupation : CalibrationOccupation.values()) {

            for (RemoteWorkable remoteWorkable : RemoteWorkable.values()) {

                if (occupation == CalibrationOccupation.OTHER
                        && remoteWorkable == RemoteWorkable.TRUE) {
                    continue;
                }

                for (DisabilityMuc disability : DisabilityMuc.values()) {
                    Map<Integer, Double> previousFactors = updatedDiscretionaryCalibrationFactors.get(occupation).get(remoteWorkable).get(disability);
                    Map<Integer, Double> newFactors = newCalibrationFactors.get(occupation).get(remoteWorkable).get(disability);

                    // Safety check: skip a segment if it is not present.
                    if (previousFactors == null || newFactors == null) {
                        continue;
                    }

                    for (int freq = 0; freq <= maxFrequency; freq++) {
                        double calibrationFactorFromLastIteration = previousFactors.getOrDefault(freq, 0.0);
                        double calibrationFactorThisIteration = newFactors.getOrDefault(freq, 0.0);
                        double updatedCalibrationFactor = calibrationFactorFromLastIteration + calibrationFactorThisIteration;
                        previousFactors.put(freq, updatedCalibrationFactor);
                        logger.info("Calibration factor for " + purpose + " | " + occupation + " | " + remoteWorkable + " | " + disability + " | " + freq + " : " + updatedCalibrationFactor);
                    }
                }
            }
        }
    }

    public Map<String, Double> obtainWorkZeroCoefficients(CalibrationOccupation occupation, EmploymentStatus employmentStatus) {

        Map<String, Double> coefficients =
                new HashMap<>(zeroCoef);


        double updatedCalibrationFactor =
                updatedWorkCalibrationFactors
                        .get(occupation)
                        .get(employmentStatus)
                        .get(0);


        coefficients.put(
                "calibration",
                updatedCalibrationFactor);


        return coefficients;
    }

    public Map<String, Double> obtainEducationZeroCoefficients() {

        Map<String, Double> coefficients =
                new HashMap<>(zeroCoef);


        double updatedCalibrationFactor =
                updatedEducationCalibrationFactors.get(0);

        coefficients.put(
                "calibration",
                updatedCalibrationFactor);


        return coefficients;
    }

    public Map<String, Double> obtainAccompanyZeroCoefficients(CalibrationOccupation occupation, RemoteWorkable remoteWorkable, DisabilityMuc disability) {

        Map<String, Double> coefficients =
                new HashMap<>(zeroCoef);


        double updatedCalibrationFactor =
                updatedDiscretionaryCalibrationFactors
                        .get(occupation)
                        .get(remoteWorkable)
                        .get(disability)
                        .get(0);


        coefficients.put(
                "calibration",
                updatedCalibrationFactor);


        return coefficients;
    }

    public Map<String, Double> obtainCountWorkCoefficients(CalibrationOccupation occupation, EmploymentStatus employmentStatus) {

        Map<String, Double> coefficients =
                new HashMap<>(countCoef);


        Map<Integer, Double> calibrationFactors =
                updatedWorkCalibrationFactors
                        .get(occupation)
                        .get(employmentStatus);


        for (int frequencyInterval = 1;
             frequencyInterval <= 6;
             frequencyInterval++) {

            String calibrationVariable =
                    "calibration_"
                            + frequencyInterval
                            + "|"
                            + (frequencyInterval + 1);


            double updatedCalibrationFactor =
                    calibrationFactors.get(
                            frequencyInterval);


            coefficients.put(
                    calibrationVariable,
                    updatedCalibrationFactor);
        }


        return coefficients;
    }

    public Map<String, Double> obtainCountEducationCoefficients() {

        Map<String, Double> coefficients =
                new HashMap<>(countCoef);


        for (int frequencyInterval = 1;
             frequencyInterval <= 6;
             frequencyInterval++) {

            String calibrationVariable =
                    "calibration_"
                            + frequencyInterval
                            + "|"
                            + (frequencyInterval + 1);


            double updatedCalibrationFactor =
                    updatedEducationCalibrationFactors
                            .get(frequencyInterval);

            coefficients.put(
                    calibrationVariable,
                    updatedCalibrationFactor);
        }


        return coefficients;
    }

    public Map<String, Double> obtainAccompanyCountCoefficients(CalibrationOccupation occupation, RemoteWorkable remoteWorkable, DisabilityMuc disability) {

        Map<String, Double> coefficients =
                new HashMap<>(countCoef);


        double updatedCalibrationFactor =
                updatedDiscretionaryCalibrationFactors
                        .get(occupation)
                        .get(remoteWorkable)
                        .get(disability)
                        .get(1);

        coefficients.put(
                "calibration",
                updatedCalibrationFactor);


        return coefficients;
    }

    public Map<String, Double> obtainDiscretionaryCountCoefficients(CalibrationOccupation occupation, RemoteWorkable remoteWorkable, DisabilityMuc disability) {

        Map<String, Double> coefficients =
                new HashMap<>(countCoef);


        double updatedCalibrationFactor =
                updatedDiscretionaryCalibrationFactors
                        .get(occupation)
                        .get(remoteWorkable)
                        .get(disability)
                        .get(0);

        coefficients.put(
                "calibration",
                updatedCalibrationFactor);


        return coefficients;
    }

    private RemoteWorkable getRemoteWorkable(Person person) {

        return person.canRemoteWork()
                ? RemoteWorkable.TRUE
                : RemoteWorkable.FALSE;
    }

    private DisabilityMuc hasDisability(Person person) {

        return person.getDisability() == Disability.WITHOUT
                ? DisabilityMuc.WITHOUT
                : DisabilityMuc.WITH;
    }

    private void putDiscretionaryCalibration(CalibrationOccupation occupation, RemoteWorkable remoteWorkable, DisabilityMuc disability, int frequency, double value) {

        updatedDiscretionaryCalibrationFactors
                .get(occupation)
                .get(remoteWorkable)
                .get(disability)
                .put(frequency, value);
    }

    private static class CalibrationSegment {

        private final CalibrationOccupation occupation;
        private final EmploymentStatus employmentStatus;
        private final RemoteWorkable remoteWorkable;
        private final DisabilityMuc disability;

        private CalibrationSegment(
                CalibrationOccupation occupation,
                EmploymentStatus employmentStatus,
                RemoteWorkable remoteWorkable,
                DisabilityMuc disability) {

            this.occupation = occupation;
            this.employmentStatus = employmentStatus;
            this.remoteWorkable = remoteWorkable;
            this.disability = disability;
        }

        public CalibrationOccupation getOccupation() {
            return occupation;
        }

        public EmploymentStatus getEmploymentStatus() {
            return employmentStatus;
        }

        public RemoteWorkable getRemoteWorkable() {
            return remoteWorkable;
        }

        public DisabilityMuc getDisability() {
            return disability;
        }
    }

    private static Map<String, CalibrationSegment> createCalibrationSegmentMapping() {

        Map<String, CalibrationSegment> map = new HashMap<>();

        map.put(
                "employed_fulltime",
                new CalibrationSegment(
                        CalibrationOccupation.EMPLOYED,
                        EmploymentStatus.FULLTIME_EMPLOYED,
                        null,
                        null));

        map.put(
                "employed_halftime",
                new CalibrationSegment(
                        CalibrationOccupation.EMPLOYED,
                        EmploymentStatus.HALFTIME_EMPLOYED,
                        null,
                        null));

        map.put(
                "other",
                new CalibrationSegment(
                        CalibrationOccupation.OTHER,
                        EmploymentStatus.NO_INFO,
                        null,
                        null));


        map.put(
                "employed_remote_working_with_disability",
                new CalibrationSegment(
                        CalibrationOccupation.EMPLOYED,
                        null,
                        RemoteWorkable.TRUE,
                        DisabilityMuc.WITH));

        map.put(
                "employed_remote_working_without_disability",
                new CalibrationSegment(
                        CalibrationOccupation.EMPLOYED,
                        null,
                        RemoteWorkable.TRUE,
                        DisabilityMuc.WITHOUT));

        map.put(
                "employed_no_remote_working_with_disability",
                new CalibrationSegment(
                        CalibrationOccupation.EMPLOYED,
                        null,
                        RemoteWorkable.FALSE,
                        DisabilityMuc.WITH));

        map.put(
                "employed_no_remote_working_without_disability",
                new CalibrationSegment(
                        CalibrationOccupation.EMPLOYED,
                        null,
                        RemoteWorkable.FALSE,
                        DisabilityMuc.WITHOUT));

        map.put(
                "other_no_remote_working_with_disability",
                new CalibrationSegment(
                        CalibrationOccupation.OTHER,
                        null,
                        RemoteWorkable.FALSE,
                        DisabilityMuc.WITH));

        map.put(
                "other_no_remote_working_without_disability",
                new CalibrationSegment(
                        CalibrationOccupation.OTHER,
                        null,
                        RemoteWorkable.FALSE,
                        DisabilityMuc.WITHOUT));

        return map;
    }

    private CalibrationSegment getWorkCalibrationSegment(Person person) {

        if (person.getOccupation() == Occupation.EMPLOYED) {

            if (person.getEmploymentStatus() == EmploymentStatus.FULLTIME_EMPLOYED) {
                return CALIBRATION_SEGMENTS.get("employed_fulltime");
            }

            if (person.getEmploymentStatus() == EmploymentStatus.HALFTIME_EMPLOYED) {
                return CALIBRATION_SEGMENTS.get("employed_halftime");
            }
        }

        return CALIBRATION_SEGMENTS.get("other");
    }

    private CalibrationSegment getDiscretionaryCalibrationSegment(Person person) {

        CalibrationOccupation occupation = getCalibrationOccupation(person);
        RemoteWorkable remoteWorkable = getRemoteWorkable(person);
        DisabilityMuc disability = hasDisability(person);

        // OTHER + remote-workable is not a calibrated segment.
        if (occupation == CalibrationOccupation.OTHER
                && remoteWorkable == RemoteWorkable.TRUE) {
            return null;
        }

        if (occupation == CalibrationOccupation.EMPLOYED) {

            if (remoteWorkable == RemoteWorkable.TRUE) {

                if (disability == DisabilityMuc.WITH) {
                    return CALIBRATION_SEGMENTS.get(
                            "employed_remote_working_with_disability");
                } else {
                    return CALIBRATION_SEGMENTS.get(
                            "employed_remote_working_without_disability");
                }

            } else {

                if (disability == DisabilityMuc.WITH) {
                    return CALIBRATION_SEGMENTS.get(
                            "employed_no_remote_working_with_disability");
                } else {
                    return CALIBRATION_SEGMENTS.get(
                            "employed_no_remote_working_without_disability");
                }
            }

        } else {

            if (disability == DisabilityMuc.WITH) {
                return CALIBRATION_SEGMENTS.get(
                        "other_no_remote_working_with_disability");
            } else {
                return CALIBRATION_SEGMENTS.get(
                        "other_no_remote_working_without_disability");
            }
        }
    }

    private double getWorkCalibrationFactor(CalibrationSegment segment, int frequencyInterval) {

        if (segment == null) {
            return 0.0;
        }

        return updatedWorkCalibrationFactors
                .get(segment.getOccupation())
                .get(segment.getEmploymentStatus())
                .get(frequencyInterval);
    }

    private double getDiscretionaryCalibrationFactor(CalibrationSegment segment, int frequencyInterval) {

        if (segment == null) {
            return 0.0;
        }

        return updatedDiscretionaryCalibrationFactors
                .get(segment.getOccupation())
                .get(segment.getRemoteWorkable())
                .get(segment.getDisability())
                .get(frequencyInterval);
    }
}