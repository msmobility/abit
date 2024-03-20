package abm.analysis;

import abm.data.DataSet;
import abm.data.geo.MicroscopicLocation;
import abm.data.geo.Zone;
import abm.data.plans.*;
import abm.data.pop.*;
import abm.io.input.DefaultDataReaderManager;
import abm.models.ModelSetup;
import abm.models.ModelSetupMuc;
import abm.models.modeChoice.NestedLogitTourModeChoiceModel;
import abm.models.modeChoice.TourModeChoice;
import abm.properties.AbitResources;
import abm.utils.AbitUtils;
import abm.utils.PlanTools;
import de.tum.bgu.msm.common.datafile.TableDataSet;
import de.tum.bgu.msm.data.person.Disability;
import de.tum.bgu.msm.data.person.Gender;
import de.tum.bgu.msm.data.person.Occupation;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.concurrent.ConcurrentExecutor;
import org.apache.log4j.Logger;

import java.io.*;
import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LogsumAnalysis {

    static Logger logger = Logger.getLogger(LogsumAnalysis.class);

    private static final Map<Integer, Map<Integer, Map<Mode, Double>>> logsumTable = new HashMap<>();
    private static final Map<Integer, Boolean> evForbidden = new HashMap<>();


    private static DataSet dataSet;
    private TableDataSet costsPerKm;


    public static void main(String[] args) {

        AbitResources.initializeResources(args[0]);
        AbitUtils.loadHdf5Lib();

        MitoUtil.initializeRandomNumber(AbitUtils.getRandomObject());

        logger.info("Reading data");
        DataSet dataSet = new DefaultDataReaderManager().readData();

        logger.info("Creating the sub-models");
        ModelSetup modelSetup = new ModelSetupMuc(dataSet);

        NestedLogitTourModeChoiceModel modeChoiceModel = (NestedLogitTourModeChoiceModel) modelSetup.getTourModeChoice();

        //Todo Initialize the logsumTable
        for (Zone origin : dataSet.getZones().values()) {
            logsumTable.put(origin.getId(), new HashMap<>());
            for (Zone destination : dataSet.getZones().values()) {
                logsumTable.get(origin.getId()).put(destination.getId(), new HashMap<>());
                for (Mode mode : Mode.values()) {
                    logsumTable.get(origin.getId()).get(destination.getId()).put(mode, 0.0);
                }
            }
        }

        //Todo Create Average Person -: need to extend to multiple average agents
        Household averageHousehold = new Household(1, dataSet.getZones().get(3094), 1);
        averageHousehold.setEconomicStatus(EconomicStatus.from2401);

        Person averagePerson = new Person(1, averageHousehold, 32, Gender.MALE, Relationship.single,
                Occupation.EMPLOYED, true, null, 0, 480, 480,
                0, null, Disability.WITHOUT);
        averagePerson.setHabitualMode(HabitualMode.CAR_DRIVER);


        //Todo read emission zones

        for (Zone zoneId : dataSet.getZones().values()) {
            evForbidden.put(zoneId.getId(), false);
        }

        try {
            final Map<String, Integer> indexes = new HashMap<>();
            BufferedReader br = new BufferedReader(new FileReader("D:/data/abm_temp/paper/shp/lowEmissionZones.csv "));
            processHeader(br, indexes);
            processRecords(br, indexes, dataSet);
        } catch (IOException e) {
            e.printStackTrace();
        }


        //Todo Calculate mode choice logsum -: now onlz utilitz by mode, nesting coefficients are in used in postßprocessing in R, need integration
        for (Zone destination : dataSet.getZones().values()) {

            Plan plan = Plan.initializePlan(averagePerson);

            Activity homeAct = new Activity(averagePerson, Purpose.HOME);
            homeAct.setLocation(dataSet.getZones().get(3094));
            Activity fakeActivity = new Activity(averagePerson, Purpose.WORK);
            fakeActivity.setLocation(dataSet.getZones().get(destination.getId()));
            fakeActivity.setDayOfWeek(DayOfWeek.MONDAY);
            fakeActivity.setStartTime_min(480);
            fakeActivity.setEndTime_min(960);

            PlanTools planTools;
            planTools = new PlanTools(dataSet.getTravelTimes());

            planTools.addMainTour(plan, fakeActivity);


            for (Mode mode : Mode.getModes()) {

                Leg leg1 = new Leg(homeAct, fakeActivity);
                int travelTime1;
                if (mode.equals(Mode.CAR_PASSENGER)){
                    travelTime1 = dataSet.getTravelTimes().getTravelTimeInMinutes(averagePerson.getHousehold().getLocation(), fakeActivity.getLocation(), Mode.UNKNOWN, fakeActivity.getStartTime_min());
                }else{
                    travelTime1 = dataSet.getTravelTimes().getTravelTimeInMinutes(averagePerson.getHousehold().getLocation(), fakeActivity.getLocation(), Mode.UNKNOWN, fakeActivity.getStartTime_min());
                }
                leg1.setTravelTime_min(travelTime1);

                Leg leg2 = new Leg(fakeActivity, homeAct);
                int travelTime2;
                if (mode.equals(Mode.CAR_PASSENGER)){
                    travelTime2 = dataSet.getTravelTimes().getTravelTimeInMinutes(fakeActivity.getLocation(), averagePerson.getHousehold().getLocation(), Mode.UNKNOWN, fakeActivity.getStartTime_min());
                }else{
                    travelTime2 = dataSet.getTravelTimes().getTravelTimeInMinutes(fakeActivity.getLocation(), averagePerson.getHousehold().getLocation(), Mode.UNKNOWN, fakeActivity.getEndTime_min());
                }

                leg2.setTravelTime_min(travelTime2);

                Tour fakeTour = new Tour(fakeActivity, 1);
                fakeTour.getLegs().put(travelTime1, leg1);
                fakeTour.getLegs().put(travelTime2, leg2);
                fakeTour.getActivities().put(0, homeAct);
                fakeTour.getActivities().put(fakeActivity.getStartTime_min(), fakeActivity);
                fakeTour.getActivities().put(fakeActivity.getEndTime_min(), homeAct);
                fakeActivity.setTour(fakeTour);


                try {
                    if ((mode.equals(Mode.CAR_DRIVER) || mode.equals(Mode.CAR_PASSENGER))  && evForbidden.get(destination.getId())) {
                        logsumTable.get(averagePerson.getHousehold().getLocation().getZoneId())
                                .get(destination.getId()).put(mode, -999999.0);
                    } else{
                        logsumTable.get(averagePerson.getHousehold().getLocation().getZoneId())
                                .get(destination.getId()).put(mode, modeChoiceModel.calculateUtilityForThisMode(averagePerson, fakeTour, fakeActivity.getPurpose(), mode, averageHousehold));
                    }
                } catch (Exception e) {
                    System.out.println(mode);
                    System.out.println(destination.getId() + " " + e.getMessage());
                    logsumTable.get(averagePerson.getHousehold().getLocation().getZoneId())
                            .get(destination.getId()).put(mode, -999999.0);

                }
            }
        }

        //Todo Print the mode choice logsum table

        PrintWriter pw = null;
        try {
            pw = new PrintWriter("C:/Users/Wei/Desktop/logsumTable_lowEmission.csv");
            pw.println("origin,destination,mode,logsum");

            for (Zone destination : dataSet.getZones().values()) {
                for (Mode mode : Mode.getModes()) {
                    pw.println(dataSet.getZones().get(3094).getZoneId() + "," + destination.getId() + "," + mode + "," + logsumTable.get(dataSet.getZones().get(3094).getId()).get(destination.getId()).get(mode));
                }
            }

            pw.close();

        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }


    }

    private static void processHeader(BufferedReader br, Map<String, Integer> indexes) throws IOException {
        String[] header = br.readLine().split(",");
        indexes.put("id", MitoUtil.findPositionInArray("id", header));
    }

    private static void processRecords(BufferedReader br, Map<String, Integer> indexes, DataSet dataSet) throws IOException {

        String line;
        while ((line = br.readLine()) != null) {

            String[] splitLine = line.split(",");

            int id = Integer.parseInt(splitLine[indexes.get("id")]);
            evForbidden.put(id, true);

        }


    }

}
