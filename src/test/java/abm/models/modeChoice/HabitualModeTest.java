package abm.models.modeChoice;


import abm.data.DataSet;

import abm.data.geo.Zone;
import abm.data.plans.HabitualMode;
import abm.data.pop.*;
import abm.io.input.FrequencyTestDataReaderManager;
import abm.io.input.HabitualModeChoiceDataReaderManager;
import abm.properties.AbitResources;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.data.person.Disability;
import de.tum.bgu.msm.data.person.Gender;
import de.tum.bgu.msm.data.person.Occupation;
import de.tum.bgu.msm.util.MitoUtil;
import org.junit.Before;
import org.junit.Test;

import java.util.EnumMap;

import static junitx.framework.Assert.assertEquals;

public class HabitualModeTest {
    private NestedLogitHabitualModeChoiceModel nestedLogitHabitualModeChoiceModel;

    @Before
    public void setup(){
        AbitResources.initializeResources("abit_wei.properties");
        AbitUtils.loadHdf5Lib();
        MitoUtil.initializeRandomNumber(AbitUtils.getRandomObject());

        DataSet dataset = new HabitualModeChoiceDataReaderManager().readData();
        nestedLogitHabitualModeChoiceModel = new NestedLogitHabitualModeChoiceModel(dataset);
    }

    @Test
    public void testHabitualMode() {
        Zone zone1 = new Zone(3635);
        Zone zone2 = new Zone(3631);
        Zone zone3 = new Zone(3629);

        Job job = new Job(1, -1, "Mnft", zone2, 445, 480);
        School school = new School(1, "1", 238, 238, zone3, 0, 0);
        Person person_test_1 = new Person(1, new Household(1, zone1, 1), 30, Gender.MALE, Relationship.married, Occupation.EMPLOYED, true, job, 28800, 465, 1059, 4511, school, Disability.WITHOUT);

        EnumMap<HabitualMode, Double> actualProbabilities = nestedLogitHabitualModeChoiceModel.calculateProbabilities(person_test_1);

        EnumMap<HabitualMode, Double> expectProbabilities = new EnumMap<>(HabitualMode.class);
        expectProbabilities.put(HabitualMode.CAR_DRIVER, 0.259);
        expectProbabilities.put(HabitualMode.CAR_PASSENGER, 0.009);
        expectProbabilities.put(HabitualMode.PT, 0.046);
        expectProbabilities.put(HabitualMode.BIKE, 0.063);
        expectProbabilities.put(HabitualMode.WALK,0.622);
        assertEnumMapEquals(expectProbabilities, actualProbabilities, 0.001);
    }

    public static void assertEnumMapEquals(EnumMap<HabitualMode, Double> expected, EnumMap<HabitualMode, Double> actual, double delta) {
        assertEquals("Map sizes differ", expected.size(), actual.size());
        for (HabitualMode key : expected.keySet()) {
            assertEquals("Mismatch for key: " + key, expected.get(key), actual.get(key), delta);
        }
    }
}
