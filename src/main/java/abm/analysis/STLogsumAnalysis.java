package abm.analysis;

import abm.data.DataSet;
import abm.data.geo.Zone;
import abm.data.plans.*;
import abm.data.pop.*;
import abm.io.input.DefaultDataReaderManager;
import abm.models.ModelSetup;
import abm.models.ModelSetupMuc;
import abm.models.modeChoice.NestedLogitTourModeChoiceModel;
import abm.properties.AbitResources;
import abm.scenarios.lowEmissionZones.ModelSetupMucLowEmissionZone;
import abm.scenarios.lowEmissionZones.io.LowEmissionZoneReader;
import abm.scenarios.lowEmissionZones.models.modeChoice.NestedLogitTourModeChoiceModelLowEmissionZones;
import abm.scenarios.lowEmissionZones.models.modeChoice.STNestedLogitTourModeChoiceModelLowEmissionZones;
import abm.utils.AbitUtils;
import abm.utils.PlanTools;
import de.tum.bgu.msm.data.person.Disability;
import de.tum.bgu.msm.data.person.Gender;
import de.tum.bgu.msm.data.person.Occupation;
import de.tum.bgu.msm.util.MitoUtil;
import org.apache.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class STLogsumAnalysis {

    static Logger logger = Logger.getLogger(STLogsumAnalysis.class);
    private static Map<String, Map<Purpose, Map<Integer, Map<Integer, Double>>>> logsumTableByPurpose_base;
    private static Map<String, Map<Purpose, Map<Integer, Map<Integer, Double>>>> logsumTableByPurpose_lowEmissionRestriction;
    private static final List<Person> personArrayList = new ArrayList<>();


    private static final  Map<String, Double> averageAgentAttributes_eur4Owner = new HashMap<>();
    private static final  Map<String, Double> averageAgentAttributes_eur5Owner = new HashMap<>();
    private static final  Map<String, Double> averageAgentAttributes_eur6Owner = new HashMap<>();
    private static final  Map<String, Double> averageAgentAttributes_eurxOwner = new HashMap<>();


    private static Map<Integer, Boolean> evForbidden = new HashMap<>();
    private static DataSet dataSet;

    public static void main(String[] args) throws FileNotFoundException {

        AbitResources.initializeResources(args[0]);
        AbitUtils.loadHdf5Lib();

        MitoUtil.initializeRandomNumber(AbitUtils.getRandomObject());

        logger.info("Reading data");
        dataSet = new DefaultDataReaderManager().readData();

        logger.info("Creating the sub-models");
        ModelSetup modelSetup = new ModelSetupMuc(dataSet);
        //ModelSetup modelSetup = new ModelSetupMucLowEmissionZone(dataSet);
        //NestedLogitTourModeChoiceModel modeChoiceModel = (NestedLogitTourModeChoiceModel) modelSetup.getTourModeChoice();
        STNestedLogitTourModeChoiceModelLowEmissionZones modeChoiceModel = (STNestedLogitTourModeChoiceModelLowEmissionZones) modelSetup.getTourModeChoice();

        logger.info("Initializing logsum calculator");
        generateSyntheticPopulation();
        evForbidden = new LowEmissionZoneReader(dataSet).readLowEmissionZones();

        for (Purpose purpose: Purpose.getAllPurposes()) {
            logger.info("Initializing logsum table for " + purpose + " purpose");
            initializeLogsumTable(purpose);

            logger.info("Calculating logsums for " + purpose + " purpose");
            personArrayList.stream().parallel().forEach(person -> calculateLogsums(person, modeChoiceModel, purpose));

            logger.info("Printing logsums for  " + purpose + " purpose");
            printLogsums(purpose);
        }
    }


    private static void generateSyntheticPopulation() {

        for (Zone originZone : dataSet.getZones().values()){
            Household averageHouseholdPerZone = new Household(originZone.getZoneId(), originZone, 0);
            averageHouseholdPerZone.setEconomicStatus(EconomicStatus.from0to800);
            Person averagePersonPerZone = new Person(originZone.getZoneId(), averageHouseholdPerZone, 32, Gender.MALE, Relationship.single,
                    Occupation.EMPLOYED, true, null, 0, 480, 480,
                    0, null, Disability.WITHOUT);
            averagePersonPerZone.setHabitualMode(HabitualMode.CAR_DRIVER);
            personArrayList.add(averagePersonPerZone);
        }

        averageAgentAttributes_eur4Owner.put("female", 0.4742);
        averageAgentAttributes_eur4Owner.put("age_0_18", 0.0153);
        averageAgentAttributes_eur4Owner.put("age_19_29", 0.1760);
        averageAgentAttributes_eur4Owner.put("age_30_49", 0.4620);
        averageAgentAttributes_eur4Owner.put("age_50_59", 0.1786);
        averageAgentAttributes_eur4Owner.put("age_60_69", 0.1008);
        averageAgentAttributes_eur4Owner.put("age_70", 0.0673);
        averageAgentAttributes_eur4Owner.put("mobilityRestricted", 0.0756);
        averageAgentAttributes_eur4Owner.put("averageHouseholdSize", 2.07);
        averageAgentAttributes_eur4Owner.put("hasChildren", 0.2453);
        averageAgentAttributes_eur4Owner.put("income_0_1500", 0.1303);
        averageAgentAttributes_eur4Owner.put("income_1501_5600", 0.7737);
        averageAgentAttributes_eur4Owner.put("income_5601", 0.0960);
        averageAgentAttributes_eur4Owner.put("numDaysWork", 0.0);
        averageAgentAttributes_eur4Owner.put("numDaysEducation", 0.0);
        averageAgentAttributes_eur4Owner.put("numDaysMandatory", 0.0);
        averageAgentAttributes_eur4Owner.put("habitualMode_carDriver", 0.0);
        averageAgentAttributes_eur4Owner.put("habitualMode_carPassenger", 0.0);
        averageAgentAttributes_eur4Owner.put("habitualMode_pt", 0.0);
        averageAgentAttributes_eur4Owner.put("habitualMode_bike", 0.0);
        averageAgentAttributes_eur4Owner.put("habitualMode_walk", 0.0);

        averageAgentAttributes_eur5Owner.put("female", 0.4721);
        averageAgentAttributes_eur5Owner.put("age_0_18", 0.0148);
        averageAgentAttributes_eur5Owner.put("age_19_29", 0.1720);
        averageAgentAttributes_eur5Owner.put("age_30_49", 0.4544);
        averageAgentAttributes_eur5Owner.put("age_50_59", 0.1832);
        averageAgentAttributes_eur5Owner.put("age_60_69", 0.1034);
        averageAgentAttributes_eur5Owner.put("age_70", 0.0722);
        averageAgentAttributes_eur5Owner.put("mobilityRestricted", 0.0800);
        averageAgentAttributes_eur5Owner.put("averageHouseholdSize", 2.06);
        averageAgentAttributes_eur5Owner.put("hasChildren", 0.2463);
        averageAgentAttributes_eur5Owner.put("income_0_1500", 0.1296);
        averageAgentAttributes_eur5Owner.put("income_1501_5600", 0.7720);
        averageAgentAttributes_eur5Owner.put("income_5601", 0.0983);
        averageAgentAttributes_eur5Owner.put("numDaysWork", 0.0);
        averageAgentAttributes_eur5Owner.put("numDaysEducation", 0.0);
        averageAgentAttributes_eur5Owner.put("numDaysMandatory", 0.0);
        averageAgentAttributes_eur5Owner.put("habitualMode_carDriver", 0.0);
        averageAgentAttributes_eur5Owner.put("habitualMode_carPassenger", 0.0);
        averageAgentAttributes_eur5Owner.put("habitualMode_pt", 0.0);
        averageAgentAttributes_eur5Owner.put("habitualMode_bike", 0.0);
        averageAgentAttributes_eur5Owner.put("habitualMode_walk", 0.0);

        averageAgentAttributes_eur6Owner.put("female", 0.4700);
        averageAgentAttributes_eur6Owner.put("age_0_18", 0.0788);
        averageAgentAttributes_eur6Owner.put("age_19_29", 0.1795);
        averageAgentAttributes_eur6Owner.put("age_30_49", 0.4555);
        averageAgentAttributes_eur6Owner.put("age_50_59", 0.1795);
        averageAgentAttributes_eur6Owner.put("age_60_69", 0.1010);
        averageAgentAttributes_eur6Owner.put("age_70", 0.1302);
        averageAgentAttributes_eur6Owner.put("mobilityRestricted", 0.0788);
        averageAgentAttributes_eur6Owner.put("averageHouseholdSize", 1.9989);
        averageAgentAttributes_eur6Owner.put("hasChildren", 0.2285);
        averageAgentAttributes_eur6Owner.put("income_0_1500", 0.7813);
        averageAgentAttributes_eur6Owner.put("income_1501_5600", 0.6770);
        averageAgentAttributes_eur6Owner.put("income_5601", 0.0954);
        averageAgentAttributes_eur6Owner.put("numDaysWork", 0.0);
        averageAgentAttributes_eur6Owner.put("numDaysEducation", 0.0);
        averageAgentAttributes_eur6Owner.put("numDaysMandatory", 0.0);
        averageAgentAttributes_eur6Owner.put("habitualMode_carDriver", 0.0);
        averageAgentAttributes_eur6Owner.put("habitualMode_carPassenger", 0.0);
        averageAgentAttributes_eur6Owner.put("habitualMode_pt", 0.0);
        averageAgentAttributes_eur6Owner.put("habitualMode_bike", 0.0);
        averageAgentAttributes_eur6Owner.put("habitualMode_walk", 0.0);


        averageAgentAttributes_eurxOwner.put("female", 0.4904);
        averageAgentAttributes_eurxOwner.put("age_0_18", 0.0111);
        averageAgentAttributes_eurxOwner.put("age_19_29", 0.1691);
        averageAgentAttributes_eurxOwner.put("age_30_49", 0.3812);
        averageAgentAttributes_eurxOwner.put("age_50_59", 0.1749);
        averageAgentAttributes_eurxOwner.put("age_60_69", 0.1336);
        averageAgentAttributes_eurxOwner.put("age_70", 0.1302);
        averageAgentAttributes_eurxOwner.put("mobilityRestricted", 0.0963);
        averageAgentAttributes_eurxOwner.put("averageHouseholdSize", 1.79);
        averageAgentAttributes_eurxOwner.put("hasChildren", 0.1716);
        averageAgentAttributes_eurxOwner.put("income_0_1500", 0.1112);
        averageAgentAttributes_eurxOwner.put("income_1501_5600", 0.7836);
        averageAgentAttributes_eurxOwner.put("income_5601", 0.1052);
        averageAgentAttributes_eurxOwner.put("numDaysWork", 0.0);
        averageAgentAttributes_eurxOwner.put("numDaysEducation", 0.0);
        averageAgentAttributes_eurxOwner.put("numDaysMandatory", 0.0);
        averageAgentAttributes_eurxOwner.put("habitualMode_carDriver", 0.0);
        averageAgentAttributes_eurxOwner.put("habitualMode_carPassenger", 0.0);
        averageAgentAttributes_eurxOwner.put("habitualMode_pt", 0.0);
        averageAgentAttributes_eurxOwner.put("habitualMode_bike", 0.0);
        averageAgentAttributes_eurxOwner.put("habitualMode_walk", 0.0);
    }

    private static void initializeLogsumTable(Purpose purpose) {
        logsumTableByPurpose_base = new ConcurrentHashMap<>();
        logsumTableByPurpose_lowEmissionRestriction = new ConcurrentHashMap<>();

        //String[] roles = {"evOwner", "nonEvOwner"};
        String[] carEmissionClasses = {"EURO4", "EURO5", "EURO6", "EUROx"};

        for (String carEmissionClass : carEmissionClasses) {
            logsumTableByPurpose_base.putIfAbsent(carEmissionClass, new HashMap<>());
            logsumTableByPurpose_base.get(carEmissionClass).putIfAbsent(purpose, new HashMap<>());
            logsumTableByPurpose_lowEmissionRestriction.putIfAbsent(carEmissionClass, new HashMap<>());
            logsumTableByPurpose_lowEmissionRestriction.get(carEmissionClass).putIfAbsent(purpose, new HashMap<>());
            for (Zone origin : dataSet.getZones().values()) {
                logsumTableByPurpose_base.get(carEmissionClass).get(purpose).putIfAbsent(origin.getId(), new HashMap<>());
                logsumTableByPurpose_lowEmissionRestriction.get(carEmissionClass).get(purpose).putIfAbsent(origin.getId(), new HashMap<>());
                for (Zone destination : dataSet.getZones().values()) {
                    logsumTableByPurpose_base.get(carEmissionClass).get(purpose).get(origin.getId()).putIfAbsent(destination.getId(), 0.0);
                    logsumTableByPurpose_lowEmissionRestriction.get(carEmissionClass).get(purpose).get(origin.getId()).putIfAbsent(destination.getId(), 0.0);
                }
            }
        }
    }
    private static void calculateLogsums(Person person, STNestedLogitTourModeChoiceModelLowEmissionZones modeChoiceModel, Purpose purpose) {

        for (Zone destinationZone : dataSet.getZones().values()){

            Plan plan = Plan.initializePlan(person);

            Activity homeAct = new Activity(person, Purpose.HOME);
            homeAct.setLocation(person.getHousehold().getLocation());

            Activity fakeActivity = new Activity(person, purpose);
            fakeActivity.setLocation(destinationZone);
            fakeActivity.setDayOfWeek(DayOfWeek.MONDAY);
            fakeActivity.setStartTime_min(480);
            fakeActivity.setEndTime_min(960);

            PlanTools planTools;
            planTools = new PlanTools(dataSet.getTravelTimes());
            planTools.addMainTour(plan, fakeActivity);

            Leg leg1 = new Leg(homeAct, fakeActivity);
            int travelTime1 = dataSet.getTravelTimes().getTravelTimeInMinutes(person.getHousehold().getLocation(), fakeActivity.getLocation(), Mode.UNKNOWN, fakeActivity.getStartTime_min());
            leg1.setTravelTime_min(travelTime1);

            Leg leg2 = new Leg(fakeActivity, homeAct);
            int travelTime2 = dataSet.getTravelTimes().getTravelTimeInMinutes(fakeActivity.getLocation(), person.getHousehold().getLocation(), Mode.UNKNOWN, fakeActivity.getEndTime_min());
            leg2.setTravelTime_min(travelTime2);

            Tour fakeTour = new Tour(fakeActivity, 1);
            fakeTour.getLegs().put(travelTime1, leg1);
            fakeTour.getLegs().put(travelTime2, leg2);
            fakeTour.getActivities().put(0, homeAct);
            fakeTour.getActivities().put(fakeActivity.getStartTime_min(), fakeActivity);
            fakeTour.getActivities().put(fakeActivity.getEndTime_min(), homeAct);
            fakeActivity.setTour(fakeTour);

            // if has non compliant car and lives outside LEZ: TRUE - if has non compliant car and lives inside LEZ: FALSE
            boolean isLowEmissionZone = evForbidden.get(destinationZone.getId()) && !evForbidden.get(person.getHousehold().getLocation().getZoneId());
            logsumTableByPurpose_base.get("EURO5").get(purpose).get(person.getHousehold().getLocation().getZoneId()).put(destinationZone.getId(), modeChoiceModel.calculateModeChoiceLogsumForThisODPairForBase(person, fakeTour, fakeActivity.getPurpose(), averageAgentAttributes_eur5Owner));
            logsumTableByPurpose_base.get("EURO6").get(purpose).get(person.getHousehold().getLocation().getZoneId()).put(destinationZone.getId(), modeChoiceModel.calculateModeChoiceLogsumForThisODPairForBase(person, fakeTour, fakeActivity.getPurpose(), averageAgentAttributes_eur6Owner));
            logsumTableByPurpose_base.get("EUROx").get(purpose).get(person.getHousehold().getLocation().getZoneId()).put(destinationZone.getId(), modeChoiceModel.calculateModeChoiceLogsumForThisODPairForBase(person, fakeTour, fakeActivity.getPurpose(), averageAgentAttributes_eurxOwner));
            logsumTableByPurpose_base.get("EURO4").get(purpose).get(person.getHousehold().getLocation().getZoneId()).put(destinationZone.getId(), modeChoiceModel.calculateModeChoiceLogsumForThisODPairForLowEmissionZoneRestriction(person, fakeTour, fakeActivity.getPurpose(), averageAgentAttributes_eur4Owner, isLowEmissionZone));

            logsumTableByPurpose_lowEmissionRestriction.get("EURO6").get(purpose).get(person.getHousehold().getLocation().getZoneId()).put(destinationZone.getId(), modeChoiceModel.calculateModeChoiceLogsumForThisODPairForBase(person, fakeTour, fakeActivity.getPurpose(), averageAgentAttributes_eur6Owner));
            logsumTableByPurpose_lowEmissionRestriction.get("EUROx").get(purpose).get(person.getHousehold().getLocation().getZoneId()).put(destinationZone.getId(), modeChoiceModel.calculateModeChoiceLogsumForThisODPairForBase(person, fakeTour, fakeActivity.getPurpose(), averageAgentAttributes_eurxOwner));
            logsumTableByPurpose_lowEmissionRestriction.get("EURO5").get(purpose).get(person.getHousehold().getLocation().getZoneId()).put(destinationZone.getId(), modeChoiceModel.calculateModeChoiceLogsumForThisODPairForLowEmissionZoneRestriction(person, fakeTour, fakeActivity.getPurpose(), averageAgentAttributes_eur5Owner, isLowEmissionZone));
            logsumTableByPurpose_lowEmissionRestriction.get("EURO4").get(purpose).get(person.getHousehold().getLocation().getZoneId()).put(destinationZone.getId(), modeChoiceModel.calculateModeChoiceLogsumForThisODPairForLowEmissionZoneRestriction(person, fakeTour, fakeActivity.getPurpose(), averageAgentAttributes_eur5Owner, isLowEmissionZone));



        }
    }
    private static void printLogsums(Purpose purpose) {

        PrintWriter pw_base_euroxOwner;
        try {
            String carEmissionClasses = "EUROx";
            pw_base_euroxOwner = new PrintWriter("C:/Users/Sonja/Documents/data/abit_standalone/input/policyScenarios/logsums/logsumTable_"+ purpose + "_" + carEmissionClasses + "_base.csv");
            pw_base_euroxOwner.println("origin,destination,logsum");
            for (Zone origin : dataSet.getZones().values()) {
                for (Zone destination : dataSet.getZones().values()) {
                    pw_base_euroxOwner.println(origin.getId() + "," + destination.getId() + "," + logsumTableByPurpose_base.get(carEmissionClasses).get(purpose).get(origin.getId()).get(destination.getId()));
                }
            }
            pw_base_euroxOwner.close();
            pw_base_euroxOwner.close();

        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }


        PrintWriter pw_base_euro6Owner;
        try {
            String carEmissionClasses = "EURO6";
            pw_base_euro6Owner = new PrintWriter("C:/Users/Sonja/Documents/data/abit_standalone/input/policyScenarios/logsums/logsumTable_"+ purpose + "_" + carEmissionClasses + "_base.csv");
            pw_base_euro6Owner.println("origin,destination,logsum");
            for (Zone origin : dataSet.getZones().values()) {
                for (Zone destination : dataSet.getZones().values()) {
                    pw_base_euro6Owner.println(origin.getId() + "," + destination.getId() + "," + logsumTableByPurpose_base.get(carEmissionClasses).get(purpose).get(origin.getId()).get(destination.getId()));
                }
            }
            pw_base_euro6Owner.close();
            pw_base_euro6Owner.close();

        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        PrintWriter pw_base_euro5Owner;
        try {
            String carEmissionClasses = "EURO5";
            pw_base_euro5Owner = new PrintWriter("C:/Users/Sonja/Documents/data/abit_standalone/input/policyScenarios/logsums/logsumTable_"+ purpose + "_" + carEmissionClasses + "_base.csv");
            pw_base_euro5Owner.println("origin,destination,logsum");
            for (Zone origin : dataSet.getZones().values()) {
                for (Zone destination : dataSet.getZones().values()) {
                    pw_base_euro5Owner.println(origin.getId() + "," + destination.getId() + "," + logsumTableByPurpose_base.get(carEmissionClasses).get(purpose).get(origin.getId()).get(destination.getId()));
                }
            }
            pw_base_euro5Owner.close();
            pw_base_euro5Owner.close();

        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        PrintWriter pw_base_euro4Owner;
        try {
            String carEmissionClasses = "EURO4";
            pw_base_euro4Owner = new PrintWriter("C:/Users/Sonja/Documents/data/abit_standalone/input/policyScenarios/logsums/logsumTable_"+ purpose + "_" + carEmissionClasses + "_base.csv");
            pw_base_euro4Owner.println("origin,destination,logsum");
            for (Zone origin : dataSet.getZones().values()) {
                for (Zone destination : dataSet.getZones().values()) {
                    pw_base_euro4Owner.println(origin.getId() + "," + destination.getId() + "," + logsumTableByPurpose_base.get(carEmissionClasses).get(purpose).get(origin.getId()).get(destination.getId()));
                }
            }
            pw_base_euro4Owner.close();
            pw_base_euro4Owner.close();

        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

//        PrintWriter pw_lowEmission_euro5Owner;
//        try {
//            String carEmissionClasses = "EURO4";
//            pw_lowEmission_euro5Owner = new PrintWriter("C:/Users/Sonja/Documents/data/abit_standalone/input/policyScenarios/logsums/logsumTable_"+ purpose + "_" + carEmissionClasses + "_lowEmissionZoneRestriction.csv");
//            pw_lowEmission_euro5Owner.println("origin,destination,logsum");
//            for (Zone origin : dataSet.getZones().values()) {
//                for (Zone destination : dataSet.getZones().values()) {
//                    pw_lowEmission_euro5Owner.println(origin.getId() + "," + destination.getId() + "," + logsumTableByPurpose_lowEmissionRestriction.get(carEmissionClasses).get(purpose).get(origin.getId()).get(destination.getId()));
//                }
//            }
//            pw_lowEmission_euro5Owner.close();
//            pw_lowEmission_euro5Owner.close();
//
//        } catch (FileNotFoundException e) {
//            throw new RuntimeException(e);
//        }

    }


}
