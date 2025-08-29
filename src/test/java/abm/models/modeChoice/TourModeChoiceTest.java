package abm.models.modeChoice;

import abm.data.DataSet;
import abm.data.geo.*;
import abm.data.plans.*;
import abm.data.pop.*;
import abm.io.input.HabitualModeChoiceDataReaderManager;
import abm.properties.AbitResources;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.data.person.Disability;
import de.tum.bgu.msm.data.person.Gender;
import de.tum.bgu.msm.data.person.Occupation;
import de.tum.bgu.msm.util.MitoUtil;
import org.junit.Before;
import org.junit.Test;

import java.time.DayOfWeek;
import java.util.EnumMap;

import static abm.data.plans.Plan.initializePlan;
import static junit.framework.Assert.assertEquals;

public class TourModeChoiceTest {
    public NestedLogitTourModeChoiceModel nestedLogitTourModeChoiceModel;

    @Before
    public void setup(){
        AbitResources.initializeResources("abit_wei.properties");
        AbitUtils.loadHdf5Lib();
        MitoUtil.initializeRandomNumber(AbitUtils.getRandomObject());

        DataSet dataset = new HabitualModeChoiceDataReaderManager().readData();
        nestedLogitTourModeChoiceModel = new NestedLogitTourModeChoiceModel(dataset);
    }

    @Test
    public void testTourModeChoice(){
        EnumMap<Mode, Double> expectmodeutility = new EnumMap<>(Mode.class);
        EnumMap<Mode, Double> actualmodeutility = new EnumMap<>(Mode.class);
        Zone zone1 = new Zone(3612);
        zone1.setRegioStaR2Type(RegioStaR2.URBAN);
        zone1.setRegioStaR7Type(RegioStaR7.URBAN_REGIOPOLIS);
        zone1.setRegioStaRGem5Type(RegioStaRGem5.METROPOLIS);
        Zone zone2 = new Zone(3631);
        Zone zone3 = new Zone(3629);

        Household household = new Household(1, zone1, 1);
        Job job = new Job(1, -1, "Mnft", zone2, 445, 480);
        School school = new School(1, "1", 238, 238, zone3, 0, 0);

        Person person_test_1 = new Person(1, new Household(1, zone1, 1), 30, Gender.MALE, Relationship.married, Occupation.EMPLOYED, true, job, 28800, 465, 1059, 4511, school, Disability.WITHOUT);
        Activity activity = new Activity(person_test_1, Purpose.WORK);
        activity.setLocation(zone2);
        activity.setDayOfWeek(DayOfWeek.MONDAY);
        Tour tour = new Tour(activity, 1);
        person_test_1.setHabitualMode(HabitualMode.WALK);

        initializePlan(person_test_1);

        for (Mode mode : Mode.getModes()){
            Double utility = nestedLogitTourModeChoiceModel.calculateUtilityForThisMode(person_test_1, tour, Purpose.WORK, mode, household);
            actualmodeutility.put(mode, utility);
        }

        expectmodeutility.put(Mode.BUS, -1.757);
        expectmodeutility.put(Mode.CAR_DRIVER, 0.0);
        expectmodeutility.put(Mode.WALK, 7.203);
        expectmodeutility.put(Mode.TRAIN, 1.722);
        expectmodeutility.put(Mode.BIKE, 0.460);
        expectmodeutility.put(Mode.TRAM_METRO, 0.242);
        expectmodeutility.put(Mode.CAR_PASSENGER,-3.792);

        assertEnumMapEquals(expectmodeutility, actualmodeutility, 0.001);
    }
    public static void assertEnumMapEquals(EnumMap<Mode, Double> expected, EnumMap<Mode, Double> actual, double delta) {
        assertEquals("Map sizes differ", expected.size(), actual.size());
        for (Mode key : expected.keySet()) {
            assertEquals("Mismatch for key: " + key, expected.get(key), actual.get(key), delta);
        }
    }
}
