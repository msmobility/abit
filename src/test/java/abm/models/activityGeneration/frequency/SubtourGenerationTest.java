package abm.models.activityGeneration.frequency;

import abm.data.DataSet;
import abm.data.geo.RegioStaR2;
import abm.data.geo.RegioStaR7;
import abm.data.geo.RegioStaRGem5;
import abm.data.geo.Zone;
import abm.data.plans.*;
import abm.data.pop.*;
import abm.io.input.HabitualModeChoiceDataReaderManager;
import abm.models.modeChoice.NestedLogitTourModeChoiceModel;
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
import static junitx.framework.Assert.assertEquals;

public class SubtourGenerationTest {

    public SubtourGeneratorModel subtourGeneratorModel;
    @Before
    public void setup() {
        AbitResources.initializeResources("abit_wei.properties");
        AbitUtils.loadHdf5Lib();
        MitoUtil.initializeRandomNumber(AbitUtils.getRandomObject());

        DataSet dataset = new HabitualModeChoiceDataReaderManager().readData();
        subtourGeneratorModel = new SubtourGeneratorModel(dataset);
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

        Person person_test_1 = new Person(1, new Household(1, zone1, 1), 30, Gender.MALE, Relationship.married,
                Occupation.EMPLOYED, true, job, 28800, 465, 1059,
                4511, school, Disability.WITHOUT);
        person_test_1.setEmploymentStatus(EmploymentStatus.FULLTIME_EMPLOYED);
        Activity activity = new Activity(person_test_1, Purpose.WORK);
        activity.setLocation(zone2);
        activity.setDayOfWeek(DayOfWeek.MONDAY);
        activity.setStartTime_min(480);
        activity.setEndTime_min(1050);
        Tour tour = new Tour(activity, 1);
        person_test_1.setHabitualMode(HabitualMode.WALK);

        initializePlan(person_test_1);

        double utility = subtourGeneratorModel.calculateUtility(Purpose.WORK, person_test_1,tour);

        assertEquals(-3.042, utility, 0.001);

    }

    public static void assertEquals(double expected, double actual, double delta) {
        if (Double.isNaN(expected) && Double.isNaN(actual)) {
            return; // both NaN, consider equal
        }
        if (Math.abs(expected - actual) > delta) {
            throw new AssertionError("Expected " + expected + " but got " + actual
                    + " (tolerance " + delta + ")");
        }
    }


}