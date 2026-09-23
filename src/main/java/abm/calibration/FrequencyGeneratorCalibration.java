package abm.calibration;

import abm.data.DataSet;
import abm.data.plans.*;
import abm.data.pop.EmploymentStatus;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.data.pop.RemoteWorkable;
import abm.models.activityGeneration.frequency.FrequencyGenerator;
import abm.models.activityGeneration.frequency.FrequencyGeneratorModel;
import abm.properties.AbitResources;

import de.tum.bgu.msm.data.person.Disability;
import de.tum.bgu.msm.data.person.Occupation;

import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class FrequencyGeneratorCalibration implements ModelComponent {

    //Todo define a few calibration parameters
    static Logger logger = Logger.getLogger(HabitualModeChoiceCalibration.class);
    DataSet dataSet;
    private static final int MAX_ITERATION = 2_000;
    private static final double TERMINATION_THRESHOLD = 0.06;
    double stepSize = 0.8;

    Map<CalibrationOccupation, Map<EmploymentStatus, Map<Integer, Double>>> objectiveWorkFrequencyShare = new HashMap<>();
    Map<Integer, Double> objectiveEducationFrequencyShare = new HashMap<>();
    Map<Purpose, Map<CalibrationOccupation, Map<RemoteWorkable, Map<DisabilityMuc, Map<Integer, Double>>>>> objectiveDiscretionaryFrequencyShare = new HashMap<>();

    Map<CalibrationOccupation, Map<EmploymentStatus, Map<Integer, Integer>>> simulatedWorkFrequencyCount = new HashMap<>();
    Map<Integer, Integer> simulatedEducationFrequencyCount = new HashMap<>();
    Map<Purpose, Map<CalibrationOccupation, Map<RemoteWorkable, Map<DisabilityMuc, Map<Integer, Integer>>>>> simulatedDiscretionaryFrequencyCount = new HashMap<>();

    Map<CalibrationOccupation, Map<EmploymentStatus, Map<Integer, Integer>>> simulatedWorkFrequencyCountOnTheFly = new HashMap<>();
    Map<Integer, Integer> simulatedEducationFrequencyCountOnTheFly = new HashMap<>();
    Map<Purpose, Map<CalibrationOccupation, Map<RemoteWorkable, Map<DisabilityMuc, Map<Integer, Integer>>>>> simulatedDiscretionaryFrequencyCountOnTheFly = new HashMap<>();

    Map<CalibrationOccupation, Map<EmploymentStatus, Map<Integer, Double>>> simulatedWorkFrequencyShare = new HashMap<>();
    Map<Integer, Double> simulatedEducationFrequencyShare = new HashMap<>();
    Map<Purpose, Map<CalibrationOccupation, Map<RemoteWorkable, Map<DisabilityMuc, Map<Integer, Double>>>>> simulatedDiscretionaryFrequencyShare = new HashMap<>();

    Map<CalibrationOccupation, Map<EmploymentStatus, Map<Integer, Double>>> simulatedWorkFrequencyShareOnTheFly = new HashMap<>();
    Map<Integer, Double> simulatedEducationFrequencyShareOnTheFly = new HashMap<>();
    Map<Purpose, Map<CalibrationOccupation, Map<RemoteWorkable, Map<DisabilityMuc, Map<Integer, Double>>>>> simulatedDiscretionaryFrequencyShareOnTheFly = new HashMap<>();

    Map<CalibrationOccupation, Map<EmploymentStatus, Map<Integer, Double>>> workCalibrationFactors = new HashMap<>();
    Map<Integer, Double> educationCalibrationFactors = new HashMap<>();
    Map<Purpose, Map<CalibrationOccupation, Map<RemoteWorkable, Map<DisabilityMuc, Map<Integer, Double>>>>> discretionaryCalibrationFactors = new HashMap<>();

    private final Map<Purpose, FrequencyGenerator> frequencyGeneratorsForCalibration = new HashMap<>();

    Map<CalibrationOccupation, Map<EmploymentStatus, Map<String, Double>>> finalWorkCoefficients = new HashMap<>();
    Map<String, Double> finalEducationCoefficients = new HashMap<>();
    Map<Purpose, Map<CalibrationOccupation, Map<RemoteWorkable, Map<DisabilityMuc, Map<String, Double>>>>> finalDiscretionaryCoefficients = new HashMap<>();

    public FrequencyGeneratorCalibration(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    // coefficients path
    String negBinZeroCoefficientsPath = AbitResources.instance.getString("actgen.negbin.zero.output");
    String negBinZeroCalibrationFactorsPath = AbitResources.instance.getString("actgen.negbin.zero.calibration.output");
    String polrCountCoefficientsPath = AbitResources.instance.getString("actgen.polr.count.output");
    String polrCountCalibrationFactorsPath = AbitResources.instance.getString("actgen.polr.count.calibration.output");
    String negBinCountCoefficientsPath = AbitResources.instance.getString("actgen.negbin.count.output");
    String negBinCountCalibrationFactorsPath = AbitResources.instance.getString("actgen.negbin.count.calibration.output");
    String glmNegBinCoefficientsPath = AbitResources.instance.getString("actgen.glm.negbin.output");
    String glmNegBinCalibrationFactorsPath = AbitResources.instance.getString("actgen.glm.negbin.calibration.output");


    // objectives path
    String workFrequencyObjectivesPath = AbitResources.instance.getString("actgen.frequency.calibration.objectives.work");
    String educationFrequencyObjectivesPath = AbitResources.instance.getString("actgen.frequency.calibration.objectives.education");
    String discretionaryFrequencyObjectivesPath = AbitResources.instance.getString("actgen.frequency.calibration.objectives.discretionary");

    // simulated path
    String workFrequencySimulatedPath = AbitResources.instance.getString("actgen.frequency.calibration.simulated.work");
    String educationFrequencySimulatedPath = AbitResources.instance.getString("actgen.frequency.calibration.simulated.education");
    String discretionaryFrequencySimulatedPath = AbitResources.instance.getString("actgen.frequency.calibration.simulated.discretionary");

    boolean calibrateMandatoryActGen;
    boolean calibrateDiscretionaryActGen;
    boolean calibrateAccompanyActGen;

    @Override
    public void setup() {
        //Todo: read boolean input from the property file and create the model which needs to be calibrated
        calibrateMandatoryActGen = Boolean.parseBoolean(AbitResources.instance.getString("actgen.mand.calibration"));
        for (Purpose purpose : Purpose.getMandatoryPurposes()) {
            frequencyGeneratorsForCalibration.put(purpose, new FrequencyGeneratorModel(dataSet, purpose, calibrateMandatoryActGen));
        }

        calibrateDiscretionaryActGen =
                Boolean.parseBoolean(
                        AbitResources.instance.getString(
                                "actgen.disc.calibration"));
        calibrateAccompanyActGen =
                FrequencyGeneratorModel.ENABLE_ACCOMPANY_CALIBRATION;

        for (Purpose purpose : Purpose.getDiscretionaryPurposes()) {
            boolean runPurposeCalibration =
                    calibrateDiscretionaryActGen
                            && (purpose != Purpose.ACCOMPANY
                            || calibrateAccompanyActGen);

            frequencyGeneratorsForCalibration.put(
                    purpose,
                    new FrequencyGeneratorModel(
                            dataSet,
                            purpose,
                            runPurposeCalibration));
        }

        //Todo: initialize all the data containers that might be needed for calibration

        for (CalibrationOccupation occupation : CalibrationOccupation.values()) {
            objectiveWorkFrequencyShare.putIfAbsent(occupation, new HashMap<>());
            simulatedWorkFrequencyCount.putIfAbsent(occupation, new HashMap<>());
            simulatedWorkFrequencyCountOnTheFly.putIfAbsent(occupation, new HashMap<>());
            simulatedWorkFrequencyShare.putIfAbsent(occupation, new HashMap<>());
            simulatedWorkFrequencyShareOnTheFly.putIfAbsent(occupation, new HashMap<>());
            workCalibrationFactors.putIfAbsent(occupation, new HashMap<>());
            finalWorkCoefficients.putIfAbsent(occupation, new HashMap<>());
            for (EmploymentStatus status : EmploymentStatus.values()) {
                objectiveWorkFrequencyShare.get(occupation).putIfAbsent(status, new HashMap<>());
                simulatedWorkFrequencyCount.get(occupation).putIfAbsent(status, new HashMap<>());
                simulatedWorkFrequencyCountOnTheFly.get(occupation).putIfAbsent(status, new HashMap<>());
                simulatedWorkFrequencyShare.get(occupation).putIfAbsent(status, new HashMap<>());
                simulatedWorkFrequencyShareOnTheFly.get(occupation).putIfAbsent(status, new HashMap<>());
                workCalibrationFactors.get(occupation).putIfAbsent(status, new HashMap<>());
                finalWorkCoefficients.get(occupation).putIfAbsent(status, new HashMap<>());
                for (int freq = 0; freq <= 7; freq++) {
                    objectiveWorkFrequencyShare.get(occupation).get(status).putIfAbsent(freq, 0.0);
                    simulatedWorkFrequencyCount.get(occupation).get(status).putIfAbsent(freq, 0);
                    simulatedWorkFrequencyCountOnTheFly.get(occupation).get(status).putIfAbsent(freq, 0);
                    simulatedWorkFrequencyShare.get(occupation).get(status).putIfAbsent(freq, 0.0);
                    simulatedWorkFrequencyShareOnTheFly.get(occupation).get(status).putIfAbsent(freq, 0.0);
                    workCalibrationFactors.get(occupation).get(status).putIfAbsent(freq, 0.0);
                }
            }
        }



        finalEducationCoefficients = new HashMap<>();
        for (int freq = 0; freq <= 7; freq++) {
            objectiveEducationFrequencyShare.putIfAbsent(freq, 0.0);
            simulatedEducationFrequencyCount.putIfAbsent(freq, 0);
            simulatedEducationFrequencyCountOnTheFly.putIfAbsent(freq, 0);
            simulatedEducationFrequencyShare.putIfAbsent(freq, 0.0);
            simulatedEducationFrequencyShareOnTheFly.putIfAbsent(freq, 0.0);
            educationCalibrationFactors.putIfAbsent(freq, 0.0);
        }


        for (Purpose purpose : Purpose.getDiscretionaryPurposes()) {

            objectiveDiscretionaryFrequencyShare
                    .putIfAbsent(purpose, new HashMap<>());

            simulatedDiscretionaryFrequencyCount
                    .putIfAbsent(purpose, new HashMap<>());

            simulatedDiscretionaryFrequencyCountOnTheFly
                    .putIfAbsent(purpose, new HashMap<>());

            simulatedDiscretionaryFrequencyShare
                    .putIfAbsent(purpose, new HashMap<>());

            simulatedDiscretionaryFrequencyShareOnTheFly
                    .putIfAbsent(purpose, new HashMap<>());

            discretionaryCalibrationFactors
                    .putIfAbsent(purpose, new HashMap<>());

            finalDiscretionaryCoefficients
                    .putIfAbsent(purpose, new HashMap<>());


            for (CalibrationOccupation occupation :
                    CalibrationOccupation.values()) {

                objectiveDiscretionaryFrequencyShare
                        .get(purpose)
                        .putIfAbsent(occupation, new HashMap<>());

                simulatedDiscretionaryFrequencyCount
                        .get(purpose)
                        .putIfAbsent(occupation, new HashMap<>());

                simulatedDiscretionaryFrequencyCountOnTheFly
                        .get(purpose)
                        .putIfAbsent(occupation, new HashMap<>());

                simulatedDiscretionaryFrequencyShare
                        .get(purpose)
                        .putIfAbsent(occupation, new HashMap<>());

                simulatedDiscretionaryFrequencyShareOnTheFly
                        .get(purpose)
                        .putIfAbsent(occupation, new HashMap<>());

                discretionaryCalibrationFactors
                        .get(purpose)
                        .putIfAbsent(occupation, new HashMap<>());

                finalDiscretionaryCoefficients
                        .get(purpose)
                        .putIfAbsent(occupation, new HashMap<>());


                for (RemoteWorkable remoteWorkable :
                        RemoteWorkable.values()) {

                    if (!isValidDiscretionarySegment(
                            occupation,
                            remoteWorkable)) {
                        continue;
                    }

                    objectiveDiscretionaryFrequencyShare
                            .get(purpose)
                            .get(occupation)
                            .putIfAbsent(remoteWorkable, new HashMap<>());

                    simulatedDiscretionaryFrequencyCount
                            .get(purpose)
                            .get(occupation)
                            .putIfAbsent(remoteWorkable, new HashMap<>());

                    simulatedDiscretionaryFrequencyCountOnTheFly
                            .get(purpose)
                            .get(occupation)
                            .putIfAbsent(remoteWorkable, new HashMap<>());

                    simulatedDiscretionaryFrequencyShare
                            .get(purpose)
                            .get(occupation)
                            .putIfAbsent(remoteWorkable, new HashMap<>());

                    simulatedDiscretionaryFrequencyShareOnTheFly
                            .get(purpose)
                            .get(occupation)
                            .putIfAbsent(remoteWorkable, new HashMap<>());

                    discretionaryCalibrationFactors
                            .get(purpose)
                            .get(occupation)
                            .putIfAbsent(remoteWorkable, new HashMap<>());

                    finalDiscretionaryCoefficients
                            .get(purpose)
                            .get(occupation)
                            .putIfAbsent(remoteWorkable, new HashMap<>());


                    for (DisabilityMuc disability :
                            DisabilityMuc.values()) {

                        objectiveDiscretionaryFrequencyShare
                                .get(purpose)
                                .get(occupation)
                                .get(remoteWorkable)
                                .putIfAbsent(disability, new HashMap<>());

                        simulatedDiscretionaryFrequencyCount
                                .get(purpose)
                                .get(occupation)
                                .get(remoteWorkable)
                                .putIfAbsent(disability, new HashMap<>());

                        simulatedDiscretionaryFrequencyCountOnTheFly
                                .get(purpose)
                                .get(occupation)
                                .get(remoteWorkable)
                                .putIfAbsent(disability, new HashMap<>());

                        simulatedDiscretionaryFrequencyShare
                                .get(purpose)
                                .get(occupation)
                                .get(remoteWorkable)
                                .putIfAbsent(disability, new HashMap<>());

                        simulatedDiscretionaryFrequencyShareOnTheFly
                                .get(purpose)
                                .get(occupation)
                                .get(remoteWorkable)
                                .putIfAbsent(disability, new HashMap<>());

                        discretionaryCalibrationFactors
                                .get(purpose)
                                .get(occupation)
                                .get(remoteWorkable)
                                .putIfAbsent(disability, new HashMap<>());

                        finalDiscretionaryCoefficients
                                .get(purpose)
                                .get(occupation)
                                .get(remoteWorkable)
                                .putIfAbsent(disability, new HashMap<>());


                        int maxFrequency =
                                purpose == Purpose.ACCOMPANY ? 7 : 15;

                        for (int freq = 0;
                             freq <= maxFrequency;
                             freq++) {

                            objectiveDiscretionaryFrequencyShare
                                    .get(purpose)
                                    .get(occupation)
                                    .get(remoteWorkable)
                                    .get(disability)
                                    .putIfAbsent(freq, 0.0);

                            simulatedDiscretionaryFrequencyCount
                                    .get(purpose)
                                    .get(occupation)
                                    .get(remoteWorkable)
                                    .get(disability)
                                    .putIfAbsent(freq, 0);

                            simulatedDiscretionaryFrequencyCountOnTheFly
                                    .get(purpose)
                                    .get(occupation)
                                    .get(remoteWorkable)
                                    .get(disability)
                                    .putIfAbsent(freq, 0);

                            simulatedDiscretionaryFrequencyShare
                                    .get(purpose)
                                    .get(occupation)
                                    .get(remoteWorkable)
                                    .get(disability)
                                    .putIfAbsent(freq, 0.0);

                            simulatedDiscretionaryFrequencyShareOnTheFly
                                    .get(purpose)
                                    .get(occupation)
                                    .get(remoteWorkable)
                                    .get(disability)
                                    .putIfAbsent(freq, 0.0);

                            discretionaryCalibrationFactors
                                    .get(purpose)
                                    .get(occupation)
                                    .get(remoteWorkable)
                                    .get(disability)
                                    .putIfAbsent(freq, 0.0);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void load() {
        readObjectiveValues();
        summarizeSimulatedResult();
    }

    @Override
    public void run() {
        logger.info("Start calibrating the frequency of trip model......");

        //Todo: loop through the calibration process until criteria are met
        for (int iteration = 0; iteration < MAX_ITERATION; iteration++) {
            logger.info("Iteration......" + iteration);

            double maxDifference = 0.0;

            if (calibrateMandatoryActGen) {
                for (CalibrationOccupation occupation : CalibrationOccupation.values()) {
                    for (EmploymentStatus status : EmploymentStatus.values()) {
                        if (!isValidWorkSegment(occupation, status)) {
                            continue;
                        }
                        for (int freq = 0; freq <= 7; freq++) {
                            double observed = objectiveWorkFrequencyShare.get(occupation).get(status).get(freq);
                            double simulated = simulatedWorkFrequencyShare.get(occupation).get(status).get(freq);
                            double difference = observed - simulated;
                            double factor = stepSize * difference;
                            if (freq == 0) {
                                factor *= -1;
                            }
                            workCalibrationFactors.get(occupation).get(status).replace(freq, factor);
                            logger.info("WORK | " + occupation + " | " + status + " | " + freq + " diff = " + difference);
                            maxDifference = Math.max(maxDifference, Math.abs(difference));
                        }
                    }
                }
                ((FrequencyGeneratorModel) frequencyGeneratorsForCalibration.get(Purpose.WORK)).updateWorkCalibrationFactor(workCalibrationFactors);

                for (int freq = 0; freq <= 7; freq++) {
                    double observed = objectiveEducationFrequencyShare.get(freq);
                    double simulated = simulatedEducationFrequencyShare.get(freq);
                    double difference = observed - simulated;
                    double factor = stepSize * difference;
                    if (freq == 0) {
                        factor *= -1;
                    }
                    educationCalibrationFactors.replace(freq, factor);
                    logger.info("EDUCATION | " + freq + " diff = " + difference);
                    maxDifference = Math.max(maxDifference, Math.abs(difference));
                }
                ((FrequencyGeneratorModel) frequencyGeneratorsForCalibration.get(Purpose.EDUCATION)).updateEducationCalibrationFactor(educationCalibrationFactors);
            }

            if (calibrateDiscretionaryActGen) {
                for (Purpose purpose : Purpose.getDiscretionaryPurposes()) {

                    if (purpose == Purpose.ACCOMPANY
                            && !calibrateAccompanyActGen) {
                        continue;
                    }

                    int maxFrequency =
                            purpose == Purpose.ACCOMPANY ? 7 : 15;
                    for (CalibrationOccupation occupation : CalibrationOccupation.values()) {
                        for (RemoteWorkable remoteWorkable : RemoteWorkable.values()) {
                            if (!isValidDiscretionarySegment(occupation, remoteWorkable)) {
                                continue;
                            }
                            for (DisabilityMuc disability : DisabilityMuc.values()) {
                                for (int freq = 0; freq <= maxFrequency; freq++) {
                                    double observed = objectiveDiscretionaryFrequencyShare.get(purpose).get(occupation).get(remoteWorkable).get(disability).get(freq);
                                    double simulated = simulatedDiscretionaryFrequencyShare.get(purpose).get(occupation).get(remoteWorkable).get(disability).get(freq);
                                    double difference = observed - simulated;
                                    double factor = -stepSize * difference;
                                    discretionaryCalibrationFactors.get(purpose).get(occupation).get(remoteWorkable).get(disability).put(freq, factor);
                                    logger.info(purpose + " | " + occupation + " | " + remoteWorkable + " | " + disability + " | " + freq + " diff = " + difference);
                                    maxDifference = Math.max(maxDifference, Math.abs(difference));
                                }
                            }
                        }
                    }

                    FrequencyGeneratorModel model =
                            (FrequencyGeneratorModel)
                                    frequencyGeneratorsForCalibration
                                            .get(purpose);

                    model.updateDiscretionaryCalibrationFactor(
                            discretionaryCalibrationFactors.get(purpose));
                }
            }

            for (Person person : dataSet.getPersons().values()) {
                if (!person.getHousehold().getSimulated()) {
                    continue;
                }
                CalibrationOccupation occupation = getCalibrationOccupation(person);
                EmploymentStatus employmentStatus = getCalibrationEmploymentStatus(person);
                RemoteWorkable remoteWorkable = getRemoteWorkable(person);
                DisabilityMuc disability = hasDisability(person);
                for (Purpose purpose : Purpose.getAllPurposes()) {
                    int numOfAct = frequencyGeneratorsForCalibration.get(purpose).calculateNumberOfActivitiesPerWeek(person, purpose);
                    if (purpose == Purpose.WORK && person.getAge() >= 15 && person.getAge() <= 70) {
                        simulatedWorkFrequencyCountOnTheFly.get(occupation).get(employmentStatus).merge(numOfAct, 1, Integer::sum);
                        continue;
                    }
                    if (purpose == Purpose.EDUCATION && person.getOccupation() == Occupation.STUDENT && person.getAge() >= 10) {
                        simulatedEducationFrequencyCountOnTheFly.merge(numOfAct, 1, Integer::sum);
                        continue;
                    }
                    if (Purpose.getDiscretionaryPurposes().contains(purpose)) {

                        if (!isValidDiscretionarySegment(occupation, remoteWorkable)) {
                            continue;
                        }

                        simulatedDiscretionaryFrequencyCountOnTheFly.get(purpose).get(occupation).get(remoteWorkable).get(disability).merge(numOfAct, 1, Integer::sum);
                    }
                }
            }

            calculateSimulatedShares();

            if (maxDifference <= TERMINATION_THRESHOLD) {
                break;
            } else {
                logger.info("MAX Diff: " + maxDifference);
            }


            for (CalibrationOccupation occupation : CalibrationOccupation.values()) {
                for (EmploymentStatus status : EmploymentStatus.values()) {
                    for (int freq = 0; freq <= 7; freq++) {
                        simulatedWorkFrequencyCountOnTheFly.get(occupation).get(status).put(freq, 0);
                        simulatedWorkFrequencyShare.get(occupation).get(status).putIfAbsent(freq, 0.0);
                    }
                }
            }



            for (int freq = 0; freq <= 7; freq++) {
                simulatedEducationFrequencyCountOnTheFly.put(freq, 0);
                simulatedEducationFrequencyShare.putIfAbsent(freq, 0.0);
            }

            for (Purpose purpose : Purpose.getDiscretionaryPurposes()) {
                int maxFreq = purpose.equals(Purpose.ACCOMPANY) ? 7 : 15;
                for (CalibrationOccupation occupation : CalibrationOccupation.values()) {
                    for (RemoteWorkable rw : RemoteWorkable.values()) {
                        if (!isValidDiscretionarySegment(occupation, rw)) {
                            continue;
                        }
                        for (DisabilityMuc disability : DisabilityMuc.values()) {
                            for (int freq = 0; freq <= maxFreq; freq++) {
                                simulatedDiscretionaryFrequencyCountOnTheFly.get(purpose).get(occupation).get(rw).get(disability).put(freq, 0);
                                simulatedDiscretionaryFrequencyShare.get(purpose).get(occupation).get(rw).get(disability).putIfAbsent(freq, 0.0);
                            }
                        }
                    }
                }
            }
        }
        logger.info("Finished the calibration of activity frequency generation model.");

        try {
            writeSimulatedWorkValues(workFrequencySimulatedPath);
            writeSimulatedEducationValues(educationFrequencySimulatedPath);
            writeSimulatedDiscretionaryValues(discretionaryFrequencySimulatedPath);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(
                    "Could not write simulated frequency distributions.", e);
        }

        //Todo: obtain the updated coefficients + calibration factors


        FrequencyGeneratorModel workModel = (FrequencyGeneratorModel) frequencyGeneratorsForCalibration.get(Purpose.WORK);
        for (CalibrationOccupation occupation : CalibrationOccupation.values()) {
            for (EmploymentStatus status : finalWorkCoefficients.get(occupation).keySet()) {
                Map<String, Double> coeffs = workModel.obtainCountWorkCoefficients(occupation, status);
                finalWorkCoefficients.get(occupation).put(status, coeffs);
            }
        }

        FrequencyGeneratorModel educationModel = (FrequencyGeneratorModel) frequencyGeneratorsForCalibration.get(Purpose.EDUCATION);
        finalEducationCoefficients.clear();
        finalEducationCoefficients.putAll(educationModel.obtainCountEducationCoefficients());
        for (Purpose purpose : Purpose.getDiscretionaryPurposes()) {
            FrequencyGeneratorModel model = (FrequencyGeneratorModel) frequencyGeneratorsForCalibration.get(purpose);
            for (CalibrationOccupation occupation : CalibrationOccupation.values()) {
                for (RemoteWorkable rw : RemoteWorkable.values()) {
                    if (!isValidDiscretionarySegment(occupation, rw)) {
                        continue;
                    }
                    for (DisabilityMuc disability : DisabilityMuc.values()) {
                        Map<String, Double> coeffs = model.obtainDiscretionaryCountCoefficients(occupation, rw, disability);
                        finalDiscretionaryCoefficients.get(purpose).get(occupation).get(rw).put(disability, coeffs);
                    }
                }
            }
        }

        //Todo: print the coefficients table to input folder
        try {

            printNegBinZeroCoefficients();
            printNegBinZeroCalibrationCoefficients();

            printPolrCountCoefficients();
            printPolrCountCalibrationCoefficients();


            printNegBinCountCoefficients();
            printNegBinCountCalibrationCoefficients();


            printGlmNegBinCoefficients();
            printGlmNegBinCalibrationCoefficients();

        } catch (FileNotFoundException e) {
            System.err.println("Output path of the coefficient table is not correct.");
        }

    }

    private void readObjectiveValues() {
        try {
            readWorkObjectives(Path.of(workFrequencyObjectivesPath));
            readEducationObjectives(Path.of(educationFrequencyObjectivesPath));
            readDiscretionaryObjectives(Path.of(discretionaryFrequencyObjectivesPath));
        } catch (IOException e) {
            throw new RuntimeException(
                    "Error reading calibration objective files", e);
        }
    }

    private void readWorkObjectives(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] record = line.split(",");
                CalibrationOccupation occupation = CalibrationOccupation.valueOf(record[0].trim().toUpperCase());
                EmploymentStatus status = EmploymentStatus.valueOf(record[1].trim().toUpperCase());
                int frequency = Integer.parseInt(record[2].trim());
                double share = Double.parseDouble(record[3].trim());
                objectiveWorkFrequencyShare.get(occupation).get(status).put(frequency, share);
            }
        }
    }

    private void readEducationObjectives(Path path)
            throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] record = line.split(",");
                int frequency = Integer.parseInt(record[0].trim());
                double share = Double.parseDouble(record[1].trim());
                objectiveEducationFrequencyShare.put(frequency, share);
            }
        }
    }

    private void readDiscretionaryObjectives(Path path)
            throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] record = line.split(",");
                Purpose purpose = Purpose.valueOf(record[0].trim().toUpperCase());
                CalibrationOccupation occupation = CalibrationOccupation.valueOf(record[1].trim().toUpperCase());
                RemoteWorkable rw = RemoteWorkable.valueOf(record[2].trim().toUpperCase());
                String value = record[3].trim();
                DisabilityMuc disability = value.equals("TRUE")
                        ? DisabilityMuc.WITH
                        : DisabilityMuc.WITHOUT;
                int frequency = Integer.parseInt(record[4].trim());
                double share = Double.parseDouble(record[5].trim());
                objectiveDiscretionaryFrequencyShare.computeIfAbsent(purpose, p -> new HashMap<>()).computeIfAbsent(occupation, o -> new HashMap<>()).computeIfAbsent(rw, r -> new HashMap<>()).computeIfAbsent(disability, d -> new HashMap<>()).put(frequency, share);
            }
        }
    }

    private void summarizeSimulatedResult() {
        for (Household household : dataSet.getHouseholds().values()) {
            if (household.getSimulated()) {
                int numberDaysOfWorkPerWeek = 0;
                int numberDaysOfEducationPerWeek = 0;
                int numberDaysOfAccompanyPerWeek = 0;
                int numberActsOfRecreationPerWeek = 0;
                int numberActsOfOtherPerWeek = 0;
                int numberActsOfShoppingPerWeek = 0;

                for (Person person : household.getPersons()) {
                    numberDaysOfWorkPerWeek = 0;
                    numberDaysOfEducationPerWeek = 0;
                    numberDaysOfAccompanyPerWeek = 0;
                    numberActsOfRecreationPerWeek = 0;
                    numberActsOfOtherPerWeek = 0;
                    numberActsOfShoppingPerWeek = 0;
                    if (person.getPlan() != null) {
                        Plan plan = person.getPlan();
                        for (Tour tour : plan.getTours().values()) {
                            for (Activity act : tour.getActivities().values()) {
                                if (person.getAge() <= 70 && person.getAge() >= 15 && act.getPurpose().equals(Purpose.WORK) && numberDaysOfWorkPerWeek < 7) {
                                    numberDaysOfWorkPerWeek += 1;
                                }
                                if (person.getAge() >= 10 && person.getOccupation().equals(Occupation.STUDENT) && act.getPurpose().equals(Purpose.EDUCATION) && numberDaysOfEducationPerWeek < 7) {
                                    numberDaysOfEducationPerWeek += 1;
                                }
                                if (act.getPurpose().equals(Purpose.ACCOMPANY) && numberDaysOfAccompanyPerWeek < 7) {
                                    numberDaysOfAccompanyPerWeek += 1;
                                }
                                if (act.getPurpose().equals(Purpose.RECREATION) && numberActsOfRecreationPerWeek < 15) {
                                    numberActsOfRecreationPerWeek += 1;
                                }
                                if (act.getPurpose().equals(Purpose.OTHER) && numberActsOfOtherPerWeek < 15) {
                                    numberActsOfOtherPerWeek += 1;
                                }
                                if (act.getPurpose().equals(Purpose.SHOPPING) && numberActsOfShoppingPerWeek < 15) {
                                    numberActsOfShoppingPerWeek += 1;
                                }
                            }
                        }

                        for (Activity unfittedActs : plan.getUnmetActivities().values()) {
                            if (person.getAge() <= 70 && person.getAge() >= 15 && unfittedActs.getPurpose().equals(Purpose.WORK)) {
                                numberDaysOfWorkPerWeek += 1;
                            }
                            if (person.getAge() >= 10 && person.getOccupation().equals(Occupation.STUDENT) && unfittedActs.getPurpose().equals(Purpose.EDUCATION)) {
                                numberDaysOfEducationPerWeek += 1;
                            }
                            if (unfittedActs.getPurpose().equals(Purpose.ACCOMPANY)) {
                                numberDaysOfAccompanyPerWeek += 1;
                            }
                            if (unfittedActs.getPurpose().equals(Purpose.RECREATION)) {
                                numberActsOfRecreationPerWeek += 1;
                            }
                            if (unfittedActs.getPurpose().equals(Purpose.OTHER)) {
                                numberActsOfOtherPerWeek += 1;
                            }
                            if (unfittedActs.getPurpose().equals(Purpose.SHOPPING)) {
                                numberActsOfShoppingPerWeek += 1;
                            }
                        }
                    }

                    if (numberDaysOfWorkPerWeek > 7 || numberDaysOfEducationPerWeek > 7 || numberDaysOfAccompanyPerWeek > 7 ||
                            numberActsOfOtherPerWeek > 15 || numberActsOfRecreationPerWeek > 15 || numberActsOfShoppingPerWeek > 15) {
                        System.out.println("scheck here");
                    }

                    if (person.getAge() >= 15 && person.getAge() <= 70) {
                        CalibrationOccupation occupation = getCalibrationOccupation(person);
                        EmploymentStatus status = getCalibrationEmploymentStatus(person);
                        simulatedWorkFrequencyCount.get(occupation).get(status).merge(numberDaysOfWorkPerWeek, 1, Integer::sum);
                    }

                    if (person.getAge() >= 10 && person.getOccupation() == Occupation.STUDENT) {
                        simulatedEducationFrequencyCount.merge(numberDaysOfEducationPerWeek, 1, Integer::sum);
                    }

                    CalibrationOccupation occupation = getCalibrationOccupation(person);
                    RemoteWorkable remoteWorkable = getRemoteWorkable(person);
                    DisabilityMuc disability = hasDisability(person);
                    if (!isValidDiscretionarySegment(occupation, remoteWorkable)) {
                        continue;
                    }
                    simulatedDiscretionaryFrequencyCount.get(Purpose.ACCOMPANY).get(occupation).get(remoteWorkable).get(disability).merge(Math.min(numberDaysOfAccompanyPerWeek, 7), 1, Integer::sum);
                    simulatedDiscretionaryFrequencyCount.get(Purpose.SHOPPING).get(occupation).get(remoteWorkable).get(disability).merge(Math.min(numberActsOfShoppingPerWeek, 15), 1, Integer::sum);
                    simulatedDiscretionaryFrequencyCount.get(Purpose.OTHER).get(occupation).get(remoteWorkable).get(disability).merge(Math.min(numberActsOfOtherPerWeek, 15), 1, Integer::sum);
                    simulatedDiscretionaryFrequencyCount.get(Purpose.RECREATION).get(occupation).get(remoteWorkable).get(disability).merge(Math.min(numberActsOfRecreationPerWeek, 15), 1, Integer::sum);}
            }
        }
        calculateInitialSimulatedShares();
    }

    private void writeSimulatedWorkValues(String fileName)
            throws FileNotFoundException {
        PrintWriter pw = new PrintWriter(fileName);
        pw.println("occupation,employmentStatus,frequency,share");
        for (CalibrationOccupation occupation : CalibrationOccupation.values()) {
            for (EmploymentStatus status : simulatedWorkFrequencyShare.get(occupation).keySet()) {
                if (!isValidWorkSegment(occupation, status)) {
                    continue;
                }
                for (int freq = 0; freq <= 7; freq++) {
                    pw.println(occupation + "," + status + "," + freq + "," + simulatedWorkFrequencyShare.get(occupation).get(status).get(freq));
                }
            }
        }
        pw.close();
    }

    private void writeSimulatedEducationValues(String fileName)
            throws FileNotFoundException {
        PrintWriter pw = new PrintWriter(fileName);
        pw.println("frequency,share");
        for (int freq = 0; freq <= 7; freq++) {
            pw.println(freq + "," + simulatedEducationFrequencyShare.get(freq));
        }
        pw.close();
    }

    private void writeSimulatedDiscretionaryValues(String fileName)
            throws FileNotFoundException {
        PrintWriter pw = new PrintWriter(fileName);
        pw.println("purpose,occupation,remote_work_allowance,disability,frequency,share");
        for (Purpose purpose : Purpose.getDiscretionaryPurposes()) {
            int maxFreq = purpose == Purpose.ACCOMPANY ? 7 : 15;
            for (CalibrationOccupation occupation : CalibrationOccupation.values()) {
                for (RemoteWorkable rw : RemoteWorkable.values()) {
                    if (!isValidDiscretionarySegment(occupation, rw)) {
                        continue;
                    }
                    for (DisabilityMuc disability : DisabilityMuc.values()) {
                        for (int freq = 0; freq <= maxFreq; freq++) {
                            pw.println(purpose + "," + occupation + "," + rw + "," + (disability == DisabilityMuc.WITH) + "," + freq + "," + simulatedDiscretionaryFrequencyShare.get(purpose).get(occupation).get(rw).get(disability).get(freq));
                        }
                    }
                }
            }
        }
        pw.close();
    }

    private void printNegBinZeroCoefficients() throws FileNotFoundException {

        logger.info("Writing NegBinZero coefficients: " + negBinZeroCoefficientsPath);

        PrintWriter pw = new PrintWriter(negBinZeroCoefficientsPath);

        pw.println("variable,work,education,accompany");

        FrequencyGeneratorModel workModel =
                (FrequencyGeneratorModel) frequencyGeneratorsForCalibration.get(Purpose.WORK);

        FrequencyGeneratorModel educationModel =
                (FrequencyGeneratorModel) frequencyGeneratorsForCalibration.get(Purpose.EDUCATION);

        FrequencyGeneratorModel accompanyModel =
                (FrequencyGeneratorModel) frequencyGeneratorsForCalibration.get(Purpose.ACCOMPANY);

        Map<String, Double> work =
                workModel.obtainWorkZeroCoefficients(
                        CalibrationOccupation.EMPLOYED,
                        EmploymentStatus.FULLTIME_EMPLOYED);

        Map<String, Double> education =
                educationModel.obtainEducationZeroCoefficients();

        Map<String, Double> accompany =
                accompanyModel.obtainAccompanyZeroCoefficients(
                        CalibrationOccupation.EMPLOYED,
                        RemoteWorkable.TRUE,
                        DisabilityMuc.WITH);

        for (String variable : work.keySet()) {
            if (variable.startsWith("calibration")) {
                continue;
            }

            Double workValue = work.get(variable);
            Double educationValue = education.get(variable);
            Double accompanyValue = accompany.get(variable);

            pw.println(
                    variable + "," +
                            (workValue != null ? workValue : "") + "," +
                            (educationValue != null ? educationValue : "") + "," +
                            (accompanyValue != null ? accompanyValue : "")
            );
        }

        pw.close();
    }

    private void printNegBinZeroCalibrationCoefficients()
            throws FileNotFoundException {

        logger.info(
                "Writing NegBinZero calibration coefficients: "
                        + negBinZeroCalibrationFactorsPath);

        PrintWriter pw =
                new PrintWriter(negBinZeroCalibrationFactorsPath);

        pw.println("variable,work,education,accompany");

        FrequencyGeneratorModel workModel =
                (FrequencyGeneratorModel) frequencyGeneratorsForCalibration.get(Purpose.WORK);

        FrequencyGeneratorModel educationModel =
                (FrequencyGeneratorModel) frequencyGeneratorsForCalibration.get(Purpose.EDUCATION);

        FrequencyGeneratorModel accompanyModel =
                (FrequencyGeneratorModel) frequencyGeneratorsForCalibration.get(Purpose.ACCOMPANY);

        Map<String, Double> workFulltime =
                workModel.obtainWorkZeroCoefficients(
                        CalibrationOccupation.EMPLOYED,
                        EmploymentStatus.FULLTIME_EMPLOYED);

        Map<String, Double> workHalftime =
                workModel.obtainWorkZeroCoefficients(
                        CalibrationOccupation.EMPLOYED,
                        EmploymentStatus.HALFTIME_EMPLOYED);

        Map<String, Double> workOther =
                workModel.obtainWorkZeroCoefficients(
                        CalibrationOccupation.OTHER,
                        EmploymentStatus.NO_INFO);

        Map<String, Double> accompanyTrueWith =
                accompanyModel.obtainAccompanyZeroCoefficients(
                        CalibrationOccupation.EMPLOYED,
                        RemoteWorkable.TRUE,
                        DisabilityMuc.WITH);

        Map<String, Double> accompanyTrueWithout =
                accompanyModel.obtainAccompanyZeroCoefficients(
                        CalibrationOccupation.EMPLOYED,
                        RemoteWorkable.TRUE,
                        DisabilityMuc.WITHOUT);

        Map<String, Double> accompanyFalseWith =
                accompanyModel.obtainAccompanyZeroCoefficients(
                        CalibrationOccupation.EMPLOYED,
                        RemoteWorkable.FALSE,
                        DisabilityMuc.WITH);

        Map<String, Double> accompanyFalseWithout =
                accompanyModel.obtainAccompanyZeroCoefficients(
                        CalibrationOccupation.EMPLOYED,
                        RemoteWorkable.FALSE,
                        DisabilityMuc.WITHOUT);

        Map<String, Double> accompanyOtherWith =
                accompanyModel.obtainAccompanyZeroCoefficients(
                        CalibrationOccupation.OTHER,
                        RemoteWorkable.FALSE,
                        DisabilityMuc.WITH);

        Map<String, Double> accompanyOtherWithout =
                accompanyModel.obtainAccompanyZeroCoefficients(
                        CalibrationOccupation.OTHER,
                        RemoteWorkable.FALSE,
                        DisabilityMuc.WITHOUT);

        Double educationCalibration =
                educationModel
                        .obtainEducationZeroCoefficients()
                        .get("calibration");

        pw.println(
                "calibration_employed_fulltime," +
                        workFulltime.get("calibration") + ",,");

        pw.println(
                "calibration_employed_halftime," +
                        workHalftime.get("calibration") + ",,");

        pw.println(
                "calibration_other," +
                        workOther.get("calibration") + ",,");


        pw.println(
                "calibration_education,," +
                        educationCalibration + ",");


        pw.println(
                "calibration_employed_remote_working_with_disability,," +
                        "," + accompanyTrueWith.get("calibration"));

        pw.println(
                "calibration_employed_remote_working_without_disability,," +
                        "," + accompanyTrueWithout.get("calibration"));

        pw.println(
                "calibration_employed_no_remote_working_with_disability,," +
                        "," + accompanyFalseWith.get("calibration"));

        pw.println(
                "calibration_employed_no_remote_working_without_disability,," +
                        "," + accompanyFalseWithout.get("calibration"));

        pw.println(
                "calibration_other_no_remote_working_with_disability,," +
                        "," + accompanyOtherWith.get("calibration"));

        pw.println(
                "calibration_other_no_remote_working_without_disability,," +
                        "," + accompanyOtherWithout.get("calibration"));


        pw.close();
    }

    private void printPolrCountCoefficients()
            throws FileNotFoundException {

        logger.info(
                "Writing PolrCount coefficients: "
                        + polrCountCoefficientsPath);

        PrintWriter pw =
                new PrintWriter(polrCountCoefficientsPath);

        pw.println("variable,work,education");

        FrequencyGeneratorModel workModel =
                (FrequencyGeneratorModel) frequencyGeneratorsForCalibration.get(Purpose.WORK);

        FrequencyGeneratorModel educationModel =
                (FrequencyGeneratorModel) frequencyGeneratorsForCalibration.get(Purpose.EDUCATION);


        Map<String, Double> workFulltime =
                workModel.obtainCountWorkCoefficients(
                        CalibrationOccupation.EMPLOYED,
                        EmploymentStatus.FULLTIME_EMPLOYED);

        Map<String, Double> education =
                educationModel.obtainCountEducationCoefficients();


        for (String variable : workFulltime.keySet()) {

            if (variable.startsWith("calibration")) {
                continue;
            }

            Double workValue = workFulltime.get(variable);
            Double educationValue = education.get(variable);

            pw.println(
                    variable + "," +
                            (workValue != null ? workValue : "") + "," +
                            (educationValue != null ? educationValue : "")
            );
        }

        pw.close();
    }

    private void printPolrCountCalibrationCoefficients()
            throws FileNotFoundException {

        logger.info(
                "Writing PolrCount calibration coefficients: "
                        + polrCountCalibrationFactorsPath);

        PrintWriter pw =
                new PrintWriter(polrCountCalibrationFactorsPath);

        pw.println("variable,work,education");

        FrequencyGeneratorModel workModel =
                (FrequencyGeneratorModel)
                        frequencyGeneratorsForCalibration.get(
                                Purpose.WORK);

        FrequencyGeneratorModel educationModel =
                (FrequencyGeneratorModel)
                        frequencyGeneratorsForCalibration.get(
                                Purpose.EDUCATION);

        Map<String, Double> workFulltime =
                workModel.obtainCountWorkCoefficients(
                        CalibrationOccupation.EMPLOYED,
                        EmploymentStatus.FULLTIME_EMPLOYED);

        Map<String, Double> workHalftime =
                workModel.obtainCountWorkCoefficients(
                        CalibrationOccupation.EMPLOYED,
                        EmploymentStatus.HALFTIME_EMPLOYED);

        Map<String, Double> workOther =
                workModel.obtainCountWorkCoefficients(
                        CalibrationOccupation.OTHER,
                        EmploymentStatus.NO_INFO);

        Map<String, Double> education =
                educationModel.obtainCountEducationCoefficients();


        for (int frequencyInterval = 1;
             frequencyInterval <= 6;
             frequencyInterval++) {

            String calibrationVariable =
                    "calibration_"
                            + frequencyInterval
                            + "|"
                            + (frequencyInterval + 1);

            Double fulltime =
                    workFulltime.get(calibrationVariable);

            Double halftime =
                    workHalftime.get(calibrationVariable);

            Double other =
                    workOther.get(calibrationVariable);

            if (fulltime != null ||
                    halftime != null ||
                    other != null) {

                pw.println(
                        "calibration_employed_fulltime_"
                                + frequencyInterval
                                + "|"
                                + (frequencyInterval + 1)
                                + ","
                                + fulltime
                                + ","
                );

                pw.println(
                        "calibration_employed_halftime_"
                                + frequencyInterval
                                + "|"
                                + (frequencyInterval + 1)
                                + ","
                                + halftime
                                + ","
                );

                pw.println(
                        "calibration_other_"
                                + frequencyInterval
                                + "|"
                                + (frequencyInterval + 1)
                                + ","
                                + other
                                + ","
                );
            }
        }

        for (String variable : education.keySet()) {

            if (variable.startsWith("calibration")) {

                pw.println(
                        variable + ",," +
                                education.get(variable)
                );
            }
        }

        pw.close();
    }

    private void printNegBinCountCoefficients()
            throws FileNotFoundException {

        logger.info(
                "Writing NegBinCount coefficients: "
                        + negBinCountCoefficientsPath);

        PrintWriter pw =
                new PrintWriter(negBinCountCoefficientsPath);

        pw.println("variable,accompany");

        FrequencyGeneratorModel accompanyModel =
                (FrequencyGeneratorModel)
                        frequencyGeneratorsForCalibration.get(
                                Purpose.ACCOMPANY);

        Map<String, Double> coefficients =
                accompanyModel.obtainAccompanyCountCoefficients(
                        CalibrationOccupation.EMPLOYED,
                        RemoteWorkable.TRUE,
                        DisabilityMuc.WITH);

        for (String variable : coefficients.keySet()) {

            if (variable.startsWith("calibration")) {
                continue;
            }

            pw.println(
                    variable + "," +
                            coefficients.get(variable)
            );
        }

        pw.close();
    }

    private void printNegBinCountCalibrationCoefficients()
            throws FileNotFoundException {

        logger.info(
                "Writing NegBinCount calibration coefficients: "
                        + negBinCountCalibrationFactorsPath);

        PrintWriter pw =
                new PrintWriter(negBinCountCalibrationFactorsPath);

        pw.println("variable,accompany");

        FrequencyGeneratorModel accompanyModel =
                (FrequencyGeneratorModel) frequencyGeneratorsForCalibration.get(Purpose.ACCOMPANY);

        Map<String, Double> employedTrueWith =
                accompanyModel.obtainAccompanyCountCoefficients(
                        CalibrationOccupation.EMPLOYED,
                        RemoteWorkable.TRUE,
                        DisabilityMuc.WITH);

        pw.println(
                "calibration_employed_remote_working_with_disability," +
                        employedTrueWith.get("calibration")
        );

        Map<String, Double> employedTrueWithout =
                accompanyModel.obtainAccompanyCountCoefficients(
                        CalibrationOccupation.EMPLOYED,
                        RemoteWorkable.TRUE,
                        DisabilityMuc.WITHOUT);

        pw.println(
                "calibration_employed_remote_working_without_disability," +
                        employedTrueWithout.get("calibration")
        );

        Map<String, Double> employedFalseWith =
                accompanyModel.obtainAccompanyCountCoefficients(
                        CalibrationOccupation.EMPLOYED,
                        RemoteWorkable.FALSE,
                        DisabilityMuc.WITH);

        pw.println(
                "calibration_employed_no_remote_working_with_disability," +
                        employedFalseWith.get("calibration")
        );

        Map<String, Double> employedFalseWithout =
                accompanyModel.obtainAccompanyCountCoefficients(
                        CalibrationOccupation.EMPLOYED,
                        RemoteWorkable.FALSE,
                        DisabilityMuc.WITHOUT);

        pw.println(
                "calibration_employed_no_remote_working_without_disability," +
                        employedFalseWithout.get("calibration")
        );

        Map<String, Double> otherWith =
                accompanyModel.obtainAccompanyCountCoefficients(
                        CalibrationOccupation.OTHER,
                        RemoteWorkable.FALSE,
                        DisabilityMuc.WITH);

        pw.println(
                "calibration_other_no_remote_working_with_disability," +
                        otherWith.get("calibration")
        );

        Map<String, Double> otherWithout =
                accompanyModel.obtainAccompanyCountCoefficients(
                        CalibrationOccupation.OTHER,
                        RemoteWorkable.FALSE,
                        DisabilityMuc.WITHOUT);

        pw.println(
                "calibration_other_no_remote_working_without_disability," +
                        otherWithout.get("calibration")
        );


        pw.close();
    }

    private void printGlmNegBinCoefficients()
            throws FileNotFoundException {

        logger.info(
                "Writing GlmNegBin coefficients: "
                        + glmNegBinCoefficientsPath);

        PrintWriter pw =
                new PrintWriter(glmNegBinCoefficientsPath);

        pw.println("variable,shopping,recreation,other");


        FrequencyGeneratorModel model =
                (FrequencyGeneratorModel)
                        frequencyGeneratorsForCalibration.get(
                                Purpose.SHOPPING);


        Map<String, Double> shopping =
                model.obtainDiscretionaryCountCoefficients(
                        CalibrationOccupation.EMPLOYED,
                        RemoteWorkable.TRUE,
                        DisabilityMuc.WITH);

        Map<String, Double> recreation =
                ((FrequencyGeneratorModel)
                        frequencyGeneratorsForCalibration.get(
                                Purpose.RECREATION))
                        .obtainDiscretionaryCountCoefficients(
                                CalibrationOccupation.EMPLOYED,
                                RemoteWorkable.TRUE,
                                DisabilityMuc.WITH);

        Map<String, Double> other =
                ((FrequencyGeneratorModel)
                        frequencyGeneratorsForCalibration.get(
                                Purpose.OTHER))
                        .obtainDiscretionaryCountCoefficients(
                                CalibrationOccupation.EMPLOYED,
                                RemoteWorkable.TRUE,
                                DisabilityMuc.WITH);


        for (String variable : shopping.keySet()) {

            if (variable.startsWith("calibration")) {
                continue;
            }

            Double shoppingValue = shopping.get(variable);
            Double recreationValue = recreation.get(variable);
            Double otherValue = other.get(variable);

            pw.println(
                    variable + "," +
                            (shoppingValue != null ? shoppingValue : "") + "," +
                            (recreationValue != null ? recreationValue : "") + "," +
                            (otherValue != null ? otherValue : "")
            );
        }

        pw.close();
    }

    private void printGlmNegBinCalibrationCoefficients()
            throws FileNotFoundException {

        logger.info(
                "Writing GlmNegBin calibration coefficients: "
                        + glmNegBinCalibrationFactorsPath);

        PrintWriter pw =
                new PrintWriter(glmNegBinCalibrationFactorsPath);

        pw.println("variable,shopping,recreation,other");


        for (CalibrationOccupation occupation :
                CalibrationOccupation.values()) {

            for (RemoteWorkable rw :
                    RemoteWorkable.values()) {

                if (!isValidDiscretionarySegment(occupation, rw)) {
                    continue;
                }

                for (DisabilityMuc disability :
                        DisabilityMuc.values()) {

                    FrequencyGeneratorModel shoppingModel =
                            (FrequencyGeneratorModel)
                                    frequencyGeneratorsForCalibration.get(
                                            Purpose.SHOPPING);

                    Map<String, Double> shopping =
                            shoppingModel.obtainDiscretionaryCountCoefficients(
                                    occupation,
                                    rw,
                                    disability);


                    FrequencyGeneratorModel recreationModel =
                            (FrequencyGeneratorModel)
                                    frequencyGeneratorsForCalibration.get(
                                            Purpose.RECREATION);

                    Map<String, Double> recreation =
                            recreationModel.obtainDiscretionaryCountCoefficients(
                                    occupation,
                                    rw,
                                    disability);

                    FrequencyGeneratorModel otherModel =
                            (FrequencyGeneratorModel)
                                    frequencyGeneratorsForCalibration.get(
                                            Purpose.OTHER);

                    Map<String, Double> other =
                            otherModel.obtainDiscretionaryCountCoefficients(
                                    occupation,
                                    rw,
                                    disability);

                    String segmentKey;

                    if (occupation == CalibrationOccupation.EMPLOYED) {

                        if (rw == RemoteWorkable.TRUE) {

                            if (disability == DisabilityMuc.WITH) {
                                segmentKey =
                                        "employed_remote_working_with_disability";
                            } else {
                                segmentKey =
                                        "employed_remote_working_without_disability";
                            }

                        } else {

                            if (disability == DisabilityMuc.WITH) {
                                segmentKey =
                                        "employed_no_remote_working_with_disability";
                            } else {
                                segmentKey =
                                        "employed_no_remote_working_without_disability";
                            }
                        }

                    } else {

                        if (disability == DisabilityMuc.WITH) {
                            segmentKey =
                                    "other_no_remote_working_with_disability";
                        } else {
                            segmentKey =
                                    "other_no_remote_working_without_disability";
                        }
                    }

                    String calibrationName =
                            "calibration_" + segmentKey;

                    pw.println(
                            calibrationName + "," +
                                    shopping.get("calibration") + "," +
                                    recreation.get("calibration") + "," +
                                    other.get("calibration")
                    );
                }
            }
        }

        pw.close();
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

    private CalibrationOccupation getCalibrationOccupation(Person person) {
        if (person.getOccupation() == Occupation.EMPLOYED) {
            return CalibrationOccupation.EMPLOYED;
        }
        return CalibrationOccupation.OTHER;
    }

    private EmploymentStatus getCalibrationEmploymentStatus(Person person) {
        if (person.getOccupation() == Occupation.EMPLOYED) {
            if (person.getEmploymentStatus() == EmploymentStatus.FULLTIME_EMPLOYED) {
                return EmploymentStatus.FULLTIME_EMPLOYED;
            }
            if (person.getEmploymentStatus() == EmploymentStatus.HALFTIME_EMPLOYED) {
                return EmploymentStatus.HALFTIME_EMPLOYED;
            }
        }
        return EmploymentStatus.NO_INFO;
    }

    private boolean isValidWorkSegment(CalibrationOccupation occupation, EmploymentStatus employmentStatus) {

        return (occupation == CalibrationOccupation.EMPLOYED &&
                (employmentStatus == EmploymentStatus.FULLTIME_EMPLOYED ||
                        employmentStatus == EmploymentStatus.HALFTIME_EMPLOYED)) ||
                (occupation == CalibrationOccupation.OTHER &&
                        employmentStatus == EmploymentStatus.NO_INFO);
    }

    private boolean isValidDiscretionarySegment(CalibrationOccupation occupation, RemoteWorkable remoteWorkable) {
        return occupation == CalibrationOccupation.EMPLOYED || remoteWorkable == RemoteWorkable.FALSE;
    }

    private void calculateInitialSimulatedShares() {

        for (CalibrationOccupation occupation :
                CalibrationOccupation.values()) {

            for (EmploymentStatus status :
                    EmploymentStatus.values()) {

                if (!isValidWorkSegment(occupation, status)) {
                    continue;
                }

                Map<Integer, Integer> counts =
                        simulatedWorkFrequencyCount
                                .get(occupation)
                                .get(status);

                Map<Integer, Double> shares =
                        simulatedWorkFrequencyShare
                                .get(occupation)
                                .get(status);

                int totalCount = 0;
                for (int freq = 0; freq <= 7; freq++) {
                    totalCount += counts.getOrDefault(freq, 0);
                }

                for (int freq = 0; freq <= 7; freq++) {
                    shares.put(
                            freq,
                            totalCount == 0
                                    ? 0.0
                                    : counts.getOrDefault(freq, 0)
                                    / (double) totalCount);
                }
            }
        }

        int educationTotalCount = 0;
        for (int freq = 0; freq <= 7; freq++) {
            educationTotalCount +=
                    simulatedEducationFrequencyCount
                            .getOrDefault(freq, 0);
        }

        for (int freq = 0; freq <= 7; freq++) {
            simulatedEducationFrequencyShare.put(
                    freq,
                    educationTotalCount == 0
                            ? 0.0
                            : simulatedEducationFrequencyCount
                            .getOrDefault(freq, 0)
                            / (double) educationTotalCount);
        }

        for (Purpose purpose :
                Purpose.getDiscretionaryPurposes()) {

            int maxFrequency =
                    purpose == Purpose.ACCOMPANY ? 7 : 15;

            for (CalibrationOccupation occupation :
                    CalibrationOccupation.values()) {

                for (RemoteWorkable remoteWorkable :
                        RemoteWorkable.values()) {

                    if (!isValidDiscretionarySegment(
                            occupation,
                            remoteWorkable)) {
                        continue;
                    }

                    for (DisabilityMuc disability :
                            DisabilityMuc.values()) {

                        Map<Integer, Integer> counts =
                                simulatedDiscretionaryFrequencyCount
                                        .get(purpose)
                                        .get(occupation)
                                        .get(remoteWorkable)
                                        .get(disability);

                        Map<Integer, Double> shares =
                                simulatedDiscretionaryFrequencyShare
                                        .get(purpose)
                                        .get(occupation)
                                        .get(remoteWorkable)
                                        .get(disability);

                        if (counts == null || shares == null) {
                            continue;
                        }

                        int totalCount = 0;
                        for (int freq = 0;
                             freq <= maxFrequency;
                             freq++) {
                            totalCount += counts.getOrDefault(freq, 0);
                        }

                        for (int freq = 0;
                             freq <= maxFrequency;
                             freq++) {
                            shares.put(
                                    freq,
                                    totalCount == 0
                                            ? 0.0
                                            : counts.getOrDefault(freq, 0)
                                            / (double) totalCount);
                        }
                    }
                }
            }
        }
    }

    private void calculateSimulatedShares() {

        for (CalibrationOccupation occupation :
                CalibrationOccupation.values()) {

            for (EmploymentStatus status :
                    EmploymentStatus.values()) {

                if (!isValidWorkSegment(
                        occupation,
                        status)) {
                    continue;
                }

                Map<Integer, Integer> counts =
                        simulatedWorkFrequencyCountOnTheFly
                                .get(occupation)
                                .get(status);

                Map<Integer, Double> shares =
                        simulatedWorkFrequencyShare
                                .get(occupation)
                                .get(status);

                if (counts == null || shares == null) {
                    continue;
                }

                int totalCount = 0;

                for (int freq = 0;
                     freq <= 7;
                     freq++) {

                    totalCount +=
                            counts.getOrDefault(freq, 0);
                }

                if (totalCount == 0) {
                    continue;
                }

                for (int freq = 0;
                     freq <= 7;
                     freq++) {

                    double share =
                            counts.getOrDefault(freq, 0)
                                    / (double) totalCount;

                    shares.put(freq, share);
                }
            }
        }

        int educationTotalCount = 0;

        for (int freq = 0;
             freq <= 7;
             freq++) {

            educationTotalCount +=
                    simulatedEducationFrequencyCountOnTheFly
                            .getOrDefault(freq, 0);
        }

        if (educationTotalCount > 0) {

            for (int freq = 0;
                 freq <= 7;
                 freq++) {

                double share =
                        simulatedEducationFrequencyCountOnTheFly
                                .getOrDefault(freq, 0)
                                / (double) educationTotalCount;

                simulatedEducationFrequencyShare
                        .put(freq, share);
            }
        }

        for (Purpose purpose :
                Purpose.getDiscretionaryPurposes()) {

            int maxFrequency =
                    purpose == Purpose.ACCOMPANY
                            ? 7
                            : 15;


            for (CalibrationOccupation occupation :
                    CalibrationOccupation.values()) {

                for (RemoteWorkable remoteWorkable :
                        RemoteWorkable.values()) {

                    if (!isValidDiscretionarySegment(
                            occupation,
                            remoteWorkable)) {
                        continue;
                    }


                    for (DisabilityMuc disability :
                            DisabilityMuc.values()) {

                        Map<Integer, Integer> counts =
                                simulatedDiscretionaryFrequencyCountOnTheFly
                                        .get(purpose)
                                        .get(occupation)
                                        .get(remoteWorkable)
                                        .get(disability);

                        Map<Integer, Double> shares =
                                simulatedDiscretionaryFrequencyShare
                                        .get(purpose)
                                        .get(occupation)
                                        .get(remoteWorkable)
                                        .get(disability);

                        if (counts == null || shares == null) {
                            continue;
                        }


                        int totalCount = 0;

                        for (int freq = 0;
                             freq <= maxFrequency;
                             freq++) {

                            totalCount +=
                                    counts.getOrDefault(
                                            freq,
                                            0);
                        }


                        if (totalCount == 0) {
                            continue;
                        }


                        for (int freq = 0;
                             freq <= maxFrequency;
                             freq++) {

                            double share =
                                    counts.getOrDefault(
                                            freq,
                                            0)
                                            / (double) totalCount;

                            shares.put(
                                    freq,
                                    share);
                        }
                    }
                }
            }
        }
    }
}
