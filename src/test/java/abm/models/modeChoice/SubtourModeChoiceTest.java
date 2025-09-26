package abm.models.modeChoice;

import abm.data.DataSet;
import abm.data.geo.RegioStaR2;
import abm.data.geo.RegioStaR7;
import abm.data.geo.RegioStaRGem5;
import abm.data.geo.Zone;
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

public class SubtourModeChoiceTest {

    public SubtourModeChoiceModel subtourModeChoiceModel;
    @Before
    public void setup() {

        AbitResources.initializeResources("abit_wei.properties");
        AbitUtils.loadHdf5Lib();
        MitoUtil.initializeRandomNumber(AbitUtils.getRandomObject());

        DataSet dataset = new HabitualModeChoiceDataReaderManager().readData();
        subtourModeChoiceModel = new SubtourModeChoiceModel(dataset);

    }

    @Test
    public void testTourModeChoice(){

        Zone zone1 = new Zone(3612);
        Zone zone2 = new Zone(3631);
        Zone zone3 = new Zone(3629);

        Household household = new Household(1, zone1, 1);
        Job job = new Job(1, -1, "Mnft", zone2, 445, 480);
        School school = new School(1, "1", 238, 238, zone3, 0, 0);

        Person person_test_1 = new Person(1, new Household(1, zone1, 1), 30, Gender.MALE, Relationship.married, Occupation.EMPLOYED, true, job, 28800, 465, 1059, 4511, school, Disability.WITHOUT);
        person_test_1.setEmploymentStatus(EmploymentStatus.FULLTIME_EMPLOYED);
        Activity mainactivity = new Activity(person_test_1, Purpose.WORK);
        mainactivity.setLocation(zone2);
        mainactivity.setDayOfWeek(DayOfWeek.MONDAY);
        mainactivity.setStartTime_min(480);
        mainactivity.setEndTime_min(1050);


        Activity subtourActivity = new Activity(person_test_1, Purpose.SUBTOUR);
        subtourActivity.setLocation(zone3);
        subtourActivity.setStartTime_min(720);
        subtourActivity.setEndTime_min(780);

        Subtour subtour = new Subtour(mainactivity, subtourActivity, 10);
        mainactivity.setSubtour(subtour);
        Tour tour = new Tour(mainactivity,1);
        mainactivity.setTour(tour);
        tour.setTourMode(Mode.CAR_DRIVER);

        double exactutility = subtourModeChoiceModel.calculateUtility(tour);
        assertEquals(-0.139, exactutility, 0.001);
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
