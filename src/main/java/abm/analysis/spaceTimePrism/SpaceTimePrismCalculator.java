package abm.analysis.spaceTimePrism;

import abm.analysis.spaceTimePrism.data.SpaceTimePrism;
import abm.analysis.spaceTimePrism.io.SpaceTimePrismDataReaderManager;
import abm.data.DataSet;
import abm.data.geo.Zone;
import abm.data.plans.Activity;
import abm.data.plans.HabitualMode;
import abm.data.plans.Mode;
import abm.data.plans.Purpose;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.properties.AbitResources;
import abm.utils.AbitUtils;
import abm.utils.PlanTools;
import de.tum.bgu.msm.util.MitoUtil;
import org.apache.log4j.Logger;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SpaceTimePrismCalculator {

    static Logger logger = Logger.getLogger(SpaceTimePrismCalculator.class);
    private static DataSet dataSet;

    private static HashMap<Integer, List<Activity>> activityList = new HashMap<>();

    private static HashMap<Integer, List<SpaceTimePrism>> spaceTimePrismList = new HashMap<>();

    private final static int OPEN_MIN = 7 * 60;
    private final static int CLOSE_MIN = 21 * 60;
    private final static int ACTIVITY_DURATION_MIN = 10;

    public static void main(String[] args) throws FileNotFoundException {


        AbitResources.initializeResources(args[0]);
        AbitUtils.loadHdf5Lib();

        MitoUtil.initializeRandomNumber(AbitUtils.getRandomObject());

        logger.info("Reading data");
        dataSet = new SpaceTimePrismDataReaderManager().readData();

        logger.info("Initializing space-time prism calculator");
        generateSyntheticPopulation(); //Todo replace later by reading the plan from ABIT output
        if (ACTIVITY_DURATION_MIN > CLOSE_MIN - OPEN_MIN) {
            logger.error("Activity duration is too short");
            System.exit(1);
        }

        logger.info("Calculating space-time prism");
        calculateSpaceTimePrism();

        logger.info("Printing activity list");
        printActivityList();

        //Todo 8. write out space-time prism list
        logger.info("Printing space-time prism");
        printSpaceTimePrismList();

    }

    private static void printSpaceTimePrismList() throws FileNotFoundException {

        PrintWriter pw = new PrintWriter("////nas.ads.mwn.de//tubv//mob//indiv//wei//spaceTimePrism//spaceTimePrism.csv");
//Todo modify the following
//        pw.println("personId, spaceTimePrismId, departureTimeFromHome, arrivalTimeAtHome, possibleTravelTime, accessibleZones");
//
//        for (Person person : dataSet.getPersons().values()) {
//            for (SpaceTimePrism stp : spaceTimePrismList.get(person.getId())) {
//                pw.println(person.getId() + "," + stp.getId() + "," + stp.getDepartureTimeFromHome() + "," +
//                        stp.getArrivalTimeAtHome() + "," + stp.getPossibleTravelTime() + "," + stp.getAccessibleZones());
//            }
//        }

        pw.close();
    }

    private static void printActivityList() throws FileNotFoundException {

        PrintWriter pw = new PrintWriter("////nas.ads.mwn.de//tubv//mob//indiv//wei//spaceTimePrism//activities.csv");

        pw.println("personId, activityPurpose, location, startTime, endTime, habitualMode");

        for (Person person : dataSet.getPersons().values()) {
            for (Activity act : activityList.get(person.getId())) {
                pw.println(person.getId() + "," + act.getPurpose().toString() + "," +
                        act.getLocation().getZoneId() + "," + act.getStartTime_min() + "," +
                        act.getEndTime_min() + "," + person.getHabitualMode());
            }
        }

        pw.close();
    }

    private static void calculateSpaceTimePrism() {
        for (Person person : dataSet.getPersons().values()) {
            int countOfSpaceTimePrismPerPerson = 1;
            List<SpaceTimePrism> spaceTimePrismOfThisPerson = new ArrayList<>();

            for (Activity act : activityList.get(person.getId())) {
                if (act.getPurpose().equals(Purpose.HOME)) {

                    int freeTimeStarting = act.getStartTime_min(); //Todo, if on the fly, this will be the ending time of the previous tour
                    int freeTimeEnding = act.getEndTime_min(); //Todo, if on the fly, this will be the starting time for the next tour

                    double possibleTravelTime = 0.0;
                    double departureTimeFromHome = 0.0;
                    double arrivalTimeAtHome = 0.0;

                    if (freeTimeStarting < OPEN_MIN && freeTimeEnding < OPEN_MIN) {
                        System.out.println("nothing to do");
                        break;
                    } else if (freeTimeStarting > CLOSE_MIN && freeTimeEnding > CLOSE_MIN) {
                        System.out.println("nothing to do");
                        break;
                    } else if (freeTimeStarting >= OPEN_MIN && freeTimeEnding <= CLOSE_MIN) {

                        possibleTravelTime = (freeTimeEnding - freeTimeStarting - ACTIVITY_DURATION_MIN) / 2.0;
                        departureTimeFromHome = freeTimeStarting;
                        arrivalTimeAtHome = freeTimeStarting + possibleTravelTime + ACTIVITY_DURATION_MIN;

                    } else if (freeTimeStarting < OPEN_MIN && freeTimeEnding <= CLOSE_MIN) {
                        double maxTravelTimeBeforeOpeningHours = OPEN_MIN - freeTimeStarting;
                        double maxTravelTimeAfterOpeningHours = freeTimeEnding - OPEN_MIN - ACTIVITY_DURATION_MIN;
                        possibleTravelTime = Math.min(maxTravelTimeAfterOpeningHours, maxTravelTimeBeforeOpeningHours);
                        if (maxTravelTimeBeforeOpeningHours < maxTravelTimeAfterOpeningHours) {
                            departureTimeFromHome = freeTimeStarting;
                            arrivalTimeAtHome = freeTimeStarting + possibleTravelTime + ACTIVITY_DURATION_MIN;
                        } else {
                            departureTimeFromHome = freeTimeEnding - possibleTravelTime - ACTIVITY_DURATION_MIN;
                            arrivalTimeAtHome = freeTimeEnding;
                        }
                    } else if (freeTimeStarting >= OPEN_MIN && freeTimeEnding > CLOSE_MIN) {
                        double maxTravelTimeBeforeClosingHours = CLOSE_MIN - freeTimeStarting - ACTIVITY_DURATION_MIN;
                        double maxTravelTimeAfterClosingHours = freeTimeEnding - CLOSE_MIN;
                        possibleTravelTime = Math.min(maxTravelTimeBeforeClosingHours, maxTravelTimeAfterClosingHours);
                        if (maxTravelTimeBeforeClosingHours < maxTravelTimeAfterClosingHours) {
                            departureTimeFromHome = freeTimeStarting;
                            arrivalTimeAtHome = freeTimeStarting + possibleTravelTime + ACTIVITY_DURATION_MIN;
                        } else {
                            departureTimeFromHome = freeTimeEnding - possibleTravelTime - ACTIVITY_DURATION_MIN;
                            arrivalTimeAtHome = freeTimeEnding;
                        }
                    } else if (freeTimeStarting < OPEN_MIN && freeTimeEnding > CLOSE_MIN) {
                        double maxTravelingTimeDuringOpeningHours = (CLOSE_MIN - OPEN_MIN - ACTIVITY_DURATION_MIN) / 2.0;
                        double maxTravelingTimeBeforeOpeningHours = OPEN_MIN - freeTimeStarting;
                        double maxTravelingTimeAfterClosingHours = freeTimeEnding - CLOSE_MIN;
                        possibleTravelTime = Math.min(maxTravelingTimeBeforeOpeningHours + maxTravelingTimeDuringOpeningHours, maxTravelingTimeAfterClosingHours + maxTravelingTimeDuringOpeningHours);
                        if (maxTravelingTimeBeforeOpeningHours < maxTravelingTimeAfterClosingHours) {
                            departureTimeFromHome = freeTimeStarting;
                            arrivalTimeAtHome = freeTimeStarting + possibleTravelTime + ACTIVITY_DURATION_MIN;
                        } else {
                            departureTimeFromHome = freeTimeEnding - possibleTravelTime - ACTIVITY_DURATION_MIN;
                            arrivalTimeAtHome = freeTimeEnding;
                        }
                    } else {
                        System.out.println("check this");
                    }

                    if (possibleTravelTime < 0) {
                        break;
                    }

                    // 5. Filter zones by time budget
                    List<Zone> accessibleZones = new ArrayList<>();
                    for (Zone zone : dataSet.getZones().values()) {
                        Mode mode = Mode.CAR_DRIVER;
                        if (person.getHabitualMode().equals(HabitualMode.PT)) {
                            mode = Mode.TRAM_METRO;
                        }
                        if (dataSet.getTravelTimes().getTravelTimeInMinutes(act.getLocation(), zone, mode, freeTimeStarting) <= possibleTravelTime) {
                            accessibleZones.add(zone);
                        }
                    }

                    SpaceTimePrism spaceTimePrism = new SpaceTimePrism(countOfSpaceTimePrismPerPerson, person.getId(), departureTimeFromHome, arrivalTimeAtHome, possibleTravelTime, accessibleZones);
                    spaceTimePrismOfThisPerson.add(spaceTimePrism);

                    countOfSpaceTimePrismPerPerson++;
                }
            }
            spaceTimePrismList.put(person.getId(), spaceTimePrismOfThisPerson);
        }
    }

    private static void generateSyntheticPopulation() {

        Zone homeZone = dataSet.getZones().get(3097);
        Zone workZone = dataSet.getZones().get(1857);
        Zone kittaZone = dataSet.getZones().get(3024);

        activityList = new HashMap<>();

        for (int seq = 1; seq <= 8; seq++) {
            Household household = new Household(seq, homeZone, 1);
            dataSet.getHouseholds().put(seq, household);
        }

        for (int seq = 1; seq <= 8; seq++) {
            Household household = dataSet.getHouseholds().get(seq);
            Person person = new Person(seq, household, 0, null, null, null, false, null, 0, 0, 0, 0, null, null);

            if (seq <= 4) {
                person.setHabitualMode(HabitualMode.CAR_DRIVER);
            } else {
                person.setHabitualMode(HabitualMode.PT);
            }

            household.getPersons().add(person);
            dataSet.getPersons().put(seq, person);
            activityList.putIfAbsent(person.getId(), new ArrayList<>());
        }

        for (Person person : dataSet.getPersons().values()) {

            switch (person.getId()) {

                case 1:
                    Activity homeActivity1_1 = new Activity(person, Purpose.HOME);
                    homeActivity1_1.setStartTime_min(PlanTools.startOfTheWeek());
                    homeActivity1_1.setEndTime_min(8 * 60);
                    homeActivity1_1.setLocation(homeZone);
                    activityList.get(person.getId()).add(homeActivity1_1);

                    Activity workActivity1 = new Activity(person, Purpose.WORK);
                    int arrivalTimeAtWork1 = 8 * 60 + dataSet.getTravelTimes().getTravelTimeInMinutes(homeZone, workZone, Mode.CAR_DRIVER, 8 * 60);
                    int departTimeAtWork1 = arrivalTimeAtWork1 + 8 * 60;
                    workActivity1.setStartTime_min(arrivalTimeAtWork1);
                    workActivity1.setEndTime_min(departTimeAtWork1);
                    workActivity1.setLocation(workZone);
                    activityList.get(person.getId()).add(workActivity1);

                    Activity pickUpKid1 = new Activity(person, Purpose.ACCOMPANY);
                    int arrivalTimeAtAccompany1 = departTimeAtWork1 + dataSet.getTravelTimes().getTravelTimeInMinutes(workZone, kittaZone, Mode.CAR_DRIVER, departTimeAtWork1);
                    int departTimeAtAccompany1 = arrivalTimeAtAccompany1 + 10;
                    pickUpKid1.setStartTime_min(arrivalTimeAtAccompany1);
                    pickUpKid1.setEndTime_min(departTimeAtAccompany1);
                    pickUpKid1.setLocation(kittaZone);
                    activityList.get(person.getId()).add(pickUpKid1);

                    Activity homeActivity1_2 = new Activity(person, Purpose.HOME);
                    int arrivalTimeAtHome1_2 = departTimeAtAccompany1 + dataSet.getTravelTimes().getTravelTimeInMinutes(kittaZone, homeZone, Mode.CAR_DRIVER, departTimeAtAccompany1);
                    int departTimeAtHome1_2 = 24 * 60;
                    homeActivity1_2.setStartTime_min(arrivalTimeAtHome1_2);
                    homeActivity1_2.setEndTime_min(departTimeAtHome1_2);
                    homeActivity1_2.setLocation(homeZone);
                    activityList.get(person.getId()).add(homeActivity1_2);
                    break;

                case 2:
                    Activity homeActivity2_1 = new Activity(person, Purpose.HOME);
                    homeActivity2_1.setStartTime_min(PlanTools.startOfTheWeek());
                    homeActivity2_1.setEndTime_min(8 * 60);
                    homeActivity2_1.setLocation(homeZone);
                    activityList.get(person.getId()).add(homeActivity2_1);

                    Activity dropOffKid2 = new Activity(person, Purpose.ACCOMPANY);
                    int arrivalTimeAtAccompany2 = 8 * 60 + dataSet.getTravelTimes().getTravelTimeInMinutes(homeZone, kittaZone, Mode.CAR_DRIVER, 8 * 60);
                    int departTimeAtAccompany2 = arrivalTimeAtAccompany2 + 10;
                    dropOffKid2.setStartTime_min(arrivalTimeAtAccompany2);
                    dropOffKid2.setEndTime_min(departTimeAtAccompany2);
                    dropOffKid2.setLocation(kittaZone);
                    activityList.get(person.getId()).add(dropOffKid2);

                    Activity homeActivity2_2 = new Activity(person, Purpose.HOME);
                    int arrivalTimeAtHome2_2 = departTimeAtAccompany2 + dataSet.getTravelTimes().getTravelTimeInMinutes(kittaZone, homeZone, Mode.CAR_DRIVER, departTimeAtAccompany2);
                    int departTimeAtHome2_2 = 11 * 60;
                    homeActivity2_2.setStartTime_min(arrivalTimeAtHome2_2);
                    homeActivity2_2.setEndTime_min(departTimeAtHome2_2);
                    homeActivity2_2.setLocation(homeZone);
                    activityList.get(person.getId()).add(homeActivity2_2);

                    Activity workActivity2 = new Activity(person, Purpose.WORK);
                    int arrivalTimeAtWork2 = departTimeAtHome2_2 + dataSet.getTravelTimes().getTravelTimeInMinutes(homeZone, workZone, Mode.CAR_DRIVER, departTimeAtHome2_2);
                    int departTimeAtWork2 = arrivalTimeAtWork2 + 8 * 60;
                    workActivity2.setStartTime_min(arrivalTimeAtWork2);
                    workActivity2.setEndTime_min(departTimeAtWork2);
                    workActivity2.setLocation(workZone);
                    activityList.get(person.getId()).add(workActivity2);

                    Activity homeActivity2_3 = new Activity(person, Purpose.HOME);
                    int arrivalTimeAtHome2_3 = departTimeAtWork2 + dataSet.getTravelTimes().getTravelTimeInMinutes(workZone, homeZone, Mode.CAR_DRIVER, departTimeAtWork2);
                    int departTimeAtHome2_3 = 24 * 60;
                    homeActivity2_3.setStartTime_min(arrivalTimeAtHome2_3);
                    homeActivity2_3.setEndTime_min(departTimeAtHome2_3);
                    homeActivity2_3.setLocation(homeZone);
                    activityList.get(person.getId()).add(homeActivity2_3);
                    break;

                case 3:
                    Activity homeActivity3_1 = new Activity(person, Purpose.HOME);
                    homeActivity3_1.setStartTime_min(PlanTools.startOfTheWeek());
                    homeActivity3_1.setEndTime_min(8 * 60);
                    homeActivity3_1.setLocation(homeZone);
                    activityList.get(person.getId()).add(homeActivity3_1);

                    Activity workActivity3 = new Activity(person, Purpose.WORK);
                    int arrivalTimeAtWork3 = 8 * 60 + dataSet.getTravelTimes().getTravelTimeInMinutes(homeZone, workZone, Mode.CAR_DRIVER, 8 * 60);
                    int departTimeAtWork3 = arrivalTimeAtWork3 + 8 * 60;
                    workActivity3.setStartTime_min(arrivalTimeAtWork3);
                    workActivity3.setEndTime_min(departTimeAtWork3);
                    workActivity3.setLocation(workZone);
                    activityList.get(person.getId()).add(workActivity3);

                    Activity homeActivity3_2 = new Activity(person, Purpose.HOME);
                    int arrivalTimeAtHome3_2 = departTimeAtWork3 + dataSet.getTravelTimes().getTravelTimeInMinutes(workZone, homeZone, Mode.CAR_DRIVER, departTimeAtWork3);
                    int departTimeAtHome3_2 = 24 * 60;
                    homeActivity3_2.setStartTime_min(arrivalTimeAtHome3_2);
                    homeActivity3_2.setEndTime_min(departTimeAtHome3_2);
                    homeActivity3_2.setLocation(homeZone);
                    activityList.get(person.getId()).add(homeActivity3_2);
                    break;

                case 4:
                    Activity homeActivity4_1 = new Activity(person, Purpose.HOME);
                    homeActivity4_1.setStartTime_min(PlanTools.startOfTheWeek());
                    homeActivity4_1.setEndTime_min(11 * 60);
                    homeActivity4_1.setLocation(homeZone);
                    activityList.get(person.getId()).add(homeActivity4_1);

                    Activity workActivity4 = new Activity(person, Purpose.WORK);
                    int arrivalTimeAtWork4 = 11 * 60 + dataSet.getTravelTimes().getTravelTimeInMinutes(homeZone, workZone, Mode.CAR_DRIVER, 11 * 60);
                    int departTimeAtWork4 = arrivalTimeAtWork4 + 8 * 60;
                    workActivity4.setStartTime_min(arrivalTimeAtWork4);
                    workActivity4.setEndTime_min(departTimeAtWork4);
                    workActivity4.setLocation(workZone);
                    activityList.get(person.getId()).add(workActivity4);

                    Activity homeActivity4_2 = new Activity(person, Purpose.HOME);
                    int arrivalTimeAtHome4_2 = departTimeAtWork4 + dataSet.getTravelTimes().getTravelTimeInMinutes(workZone, homeZone, Mode.CAR_DRIVER, departTimeAtWork4);
                    int departTimeAtHome4_2 = 24 * 60;
                    homeActivity4_2.setStartTime_min(arrivalTimeAtHome4_2);
                    homeActivity4_2.setEndTime_min(departTimeAtHome4_2);
                    homeActivity4_2.setLocation(homeZone);
                    activityList.get(person.getId()).add(homeActivity4_2);
                    break;

                case 5:
                    Activity homeActivity5_1 = new Activity(person, Purpose.HOME);
                    homeActivity5_1.setStartTime_min(PlanTools.startOfTheWeek());
                    homeActivity5_1.setEndTime_min(8 * 60);
                    homeActivity5_1.setLocation(homeZone);
                    activityList.get(person.getId()).add(homeActivity5_1);

                    Activity workActivity5 = new Activity(person, Purpose.WORK);
                    int arrivalTimeAtWork5 = 8 * 60 + dataSet.getTravelTimes().getTravelTimeInMinutes(homeZone, workZone, Mode.TRAM_METRO, 8 * 60);
                    int departTimeAtWork5 = arrivalTimeAtWork5 + 8 * 60;
                    workActivity5.setStartTime_min(arrivalTimeAtWork5);
                    workActivity5.setEndTime_min(departTimeAtWork5);
                    workActivity5.setLocation(workZone);
                    activityList.get(person.getId()).add(workActivity5);

                    Activity pickUpKid5 = new Activity(person, Purpose.ACCOMPANY);
                    int arrivalTimeAtAccompany5 = departTimeAtWork5 + dataSet.getTravelTimes().getTravelTimeInMinutes(workZone, kittaZone, Mode.TRAM_METRO, departTimeAtWork5);
                    int departTimeAtAccompany5 = arrivalTimeAtAccompany5 + 10;
                    pickUpKid5.setStartTime_min(arrivalTimeAtAccompany5);
                    pickUpKid5.setEndTime_min(departTimeAtAccompany5);
                    pickUpKid5.setLocation(kittaZone);
                    activityList.get(person.getId()).add(pickUpKid5);

                    Activity homeActivity2_5 = new Activity(person, Purpose.HOME);
                    int arrivalTimeAtHome2_5 = departTimeAtAccompany5 + dataSet.getTravelTimes().getTravelTimeInMinutes(kittaZone, homeZone, Mode.CAR_DRIVER, departTimeAtAccompany5);
                    int departTimeAtHome2_5 = 24 * 60;
                    homeActivity2_5.setStartTime_min(arrivalTimeAtHome2_5);
                    homeActivity2_5.setEndTime_min(departTimeAtHome2_5);
                    homeActivity2_5.setLocation(homeZone);
                    activityList.get(person.getId()).add(homeActivity2_5);
                    break;

                case 6:
                    Activity homeActivity6_1 = new Activity(person, Purpose.HOME);
                    homeActivity6_1.setStartTime_min(PlanTools.startOfTheWeek());
                    homeActivity6_1.setEndTime_min(8 * 60);
                    homeActivity6_1.setLocation(homeZone);
                    activityList.get(person.getId()).add(homeActivity6_1);

                    Activity dropOffKid6 = new Activity(person, Purpose.ACCOMPANY);
                    int arrivalTimeAtAccompany6 = 8 * 60 + dataSet.getTravelTimes().getTravelTimeInMinutes(homeZone, kittaZone, Mode.TRAM_METRO, 8 * 60);
                    int departTimeAtAccompany6 = arrivalTimeAtAccompany6 + 10;
                    dropOffKid6.setStartTime_min(arrivalTimeAtAccompany6);
                    dropOffKid6.setEndTime_min(departTimeAtAccompany6);
                    dropOffKid6.setLocation(kittaZone);
                    activityList.get(person.getId()).add(dropOffKid6);

                    Activity homeActivity6_2 = new Activity(person, Purpose.HOME);
                    int arrivalTimeAtHome6_2 = departTimeAtAccompany6 + dataSet.getTravelTimes().getTravelTimeInMinutes(kittaZone, homeZone, Mode.TRAM_METRO, departTimeAtAccompany6);
                    int departTimeAtHome6_2 = 11 * 60;
                    homeActivity6_2.setStartTime_min(arrivalTimeAtHome6_2);
                    homeActivity6_2.setEndTime_min(departTimeAtHome6_2);
                    homeActivity6_2.setLocation(homeZone);
                    activityList.get(person.getId()).add(homeActivity6_2);

                    Activity workActivity6 = new Activity(person, Purpose.WORK);
                    int arrivalTimeAtWork6 = departTimeAtHome6_2 + dataSet.getTravelTimes().getTravelTimeInMinutes(homeZone, workZone, Mode.TRAM_METRO, departTimeAtHome6_2);
                    int departTimeAtWork6 = arrivalTimeAtWork6 + 8 * 60;
                    workActivity6.setStartTime_min(arrivalTimeAtWork6);
                    workActivity6.setEndTime_min(departTimeAtWork6);
                    workActivity6.setLocation(workZone);
                    activityList.get(person.getId()).add(workActivity6);

                    Activity homeActivity6_3 = new Activity(person, Purpose.HOME);
                    int arrivalTimeAtHome6_3 = departTimeAtWork6 + dataSet.getTravelTimes().getTravelTimeInMinutes(workZone, homeZone, Mode.TRAM_METRO, departTimeAtWork6);
                    int departTimeAtHome6_3 = 24 * 60;
                    homeActivity6_3.setStartTime_min(arrivalTimeAtHome6_3);
                    homeActivity6_3.setEndTime_min(departTimeAtHome6_3);
                    homeActivity6_3.setLocation(homeZone);
                    activityList.get(person.getId()).add(homeActivity6_3);
                    break;

                case 7:
                    Activity homeActivity7_1 = new Activity(person, Purpose.HOME);
                    homeActivity7_1.setStartTime_min(PlanTools.startOfTheWeek());
                    homeActivity7_1.setEndTime_min(8 * 60);
                    homeActivity7_1.setLocation(homeZone);
                    activityList.get(person.getId()).add(homeActivity7_1);

                    Activity workActivity7 = new Activity(person, Purpose.WORK);
                    int arrivalTimeAtWork7 = 8 * 60 + dataSet.getTravelTimes().getTravelTimeInMinutes(homeZone, workZone, Mode.TRAM_METRO, 8 * 60);
                    int departTimeAtWork7 = arrivalTimeAtWork7 + 8 * 60;
                    workActivity7.setStartTime_min(arrivalTimeAtWork7);
                    workActivity7.setEndTime_min(departTimeAtWork7);
                    workActivity7.setLocation(workZone);
                    activityList.get(person.getId()).add(workActivity7);

                    Activity homeActivity7_2 = new Activity(person, Purpose.HOME);
                    int arrivalTimeAtHome7_2 = departTimeAtWork7 + dataSet.getTravelTimes().getTravelTimeInMinutes(workZone, homeZone, Mode.TRAM_METRO, departTimeAtWork7);
                    int departTimeAtHome7_2 = 24 * 60;
                    homeActivity7_2.setStartTime_min(arrivalTimeAtHome7_2);
                    homeActivity7_2.setEndTime_min(departTimeAtHome7_2);
                    homeActivity7_2.setLocation(homeZone);
                    activityList.get(person.getId()).add(homeActivity7_2);
                    break;

                case 8:
                    Activity homeActivity8_1 = new Activity(person, Purpose.HOME);
                    homeActivity8_1.setStartTime_min(PlanTools.startOfTheWeek());
                    homeActivity8_1.setEndTime_min(11 * 60);
                    homeActivity8_1.setLocation(homeZone);
                    activityList.get(person.getId()).add(homeActivity8_1);

                    Activity workActivity8 = new Activity(person, Purpose.WORK);
                    int arrivalTimeAtWork8 = 11 * 60 + dataSet.getTravelTimes().getTravelTimeInMinutes(homeZone, workZone, Mode.TRAM_METRO, 11 * 60);
                    int departTimeAtWork8 = arrivalTimeAtWork8 + 8 * 60;
                    workActivity8.setStartTime_min(arrivalTimeAtWork8);
                    workActivity8.setEndTime_min(departTimeAtWork8);
                    workActivity8.setLocation(workZone);
                    activityList.get(person.getId()).add(workActivity8);

                    Activity homeActivity8_2 = new Activity(person, Purpose.HOME);
                    int arrivalTimeAtHome8_2 = departTimeAtWork8 + dataSet.getTravelTimes().getTravelTimeInMinutes(workZone, homeZone, Mode.TRAM_METRO, departTimeAtWork8);
                    int departTimeAtHome8_2 = 24 * 60;
                    homeActivity8_2.setStartTime_min(arrivalTimeAtHome8_2);
                    homeActivity8_2.setEndTime_min(departTimeAtHome8_2);
                    homeActivity8_2.setLocation(homeZone);
                    activityList.get(person.getId()).add(homeActivity8_2);
                    break;
            }
        }
    }
}
