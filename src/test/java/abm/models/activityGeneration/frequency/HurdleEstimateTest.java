package abm.models.activityGeneration.frequency;

import abm.data.DataSet;
import abm.data.geo.RegioStaR2;
import abm.data.geo.RegioStaR7;
import abm.data.geo.RegioStaRGem5;
import abm.data.geo.Zone;
import abm.data.plans.HabitualMode;
import abm.data.plans.Purpose;
import abm.data.pop.*;
import abm.io.input.FrequencyTestDataReaderManager;
import abm.properties.AbitResources;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.data.person.Disability;
import de.tum.bgu.msm.data.person.Gender;
import de.tum.bgu.msm.data.person.Occupation;
import de.tum.bgu.msm.util.MitoUtil;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static abm.data.plans.Plan.initializePlan;

public class HurdleEstimateTest {
    private Logger logger = Logger.getLogger(HurdleEstimateTest.class);
    private FrequencyGeneratorModel frequencyGeneratorModel;
    private DataSet dataset;

    @Before
    public void setup(){
        AbitResources.initializeResources("abit_wei.properties");
        AbitUtils.loadHdf5Lib();
        MitoUtil.initializeRandomNumber(AbitUtils.getRandomObject());

        dataset = new FrequencyTestDataReaderManager().readData();

        frequencyGeneratorModel = new FrequencyGeneratorModel(dataset, Purpose.ACCOMPANY);
    }



    @Test
    public void test() {
        Zone zone1 = new Zone(3612);
        zone1.setRegioStaR2Type(RegioStaR2.URBAN);
        zone1.setRegioStaR7Type(RegioStaR7.URBAN_REGIOPOLIS);
        zone1.setRegioStaRGem5Type(RegioStaRGem5.METROPOLIS);

        Zone zone2 = new Zone(3631);
        Zone zone3 = new Zone(3629);

        Job job = new Job(1, -1, "Mnft", zone2, 445, 480);
        School school = new School(1, "1", 238, 238, zone3, 0, 0);
        Household household = new Household(1, zone1, 1);
        household.setEconomicStatus(EconomicStatus.from2401);

        Person person_test_2 = new Person(1, household, 30, Gender.MALE, Relationship.married, Occupation.EMPLOYED, true,job , 28800, 465, 1059, 4511, school, Disability.WITHOUT);
        person_test_2.setHabitualMode(HabitualMode.WALK);
        person_test_2.setEmploymentStatus(EmploymentStatus.FULLTIME_EMPLOYED);

        initializePlan(person_test_2);

        double[] param = frequencyGeneratorModel.hurdleEstimateParam(person_test_2);


        //Set up reference values, e.g. probabilities
        double[] expectParam = new double[]{
                1.357,//theta
                -0.906,//p_0
                1.068,//mu
        };


        assertArrayEquals(expectParam, param,0.001);
    }

    public static void assertArrayEquals(double[] expected, double[] actual, double delta) {
        if (expected == null || actual == null) {
            throw new AssertionError("One of the arrays is null");
        }

        if (expected.length != actual.length) {
            throw new AssertionError("Array lengths differ. Expected length: "
                    + expected.length + ", Actual length: " + actual.length);
        }

        for (int i = 0; i < expected.length; i++) {
            if (Math.abs(expected[i] - actual[i]) > delta) {
                throw new AssertionError("Arrays differ at index " + i
                        + ": expected: " + Arrays.toString(expected)
                        + " actual: " + Arrays.toString(actual));
            }
        }
    }
}
