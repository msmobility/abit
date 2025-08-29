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
import abm.io.input.HabitualModeChoiceDataReaderManager;
import abm.models.modeChoice.NestedLogitHabitualModeChoiceModel;
import abm.properties.AbitResources;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.data.person.Disability;
import de.tum.bgu.msm.data.person.Gender;
import de.tum.bgu.msm.data.person.Occupation;
import de.tum.bgu.msm.util.MitoUtil;

import java.util.Arrays;

import static abm.data.plans.Plan.initializePlan;

public class ZoneInfo {


    public static void main(String[] args) {
        int numUnemployedInHh = 0;


        AbitResources.initializeResources(args[0]);
        AbitUtils.loadHdf5Lib();
        MitoUtil.initializeRandomNumber(AbitUtils.getRandomObject());


        DataSet dataset_1 = new HabitualModeChoiceDataReaderManager().readData();
        DataSet dataset_2 = new FrequencyTestDataReaderManager().readData();
        NestedLogitHabitualModeChoiceModel nestedLogitHabitualModeChoiceModel = new NestedLogitHabitualModeChoiceModel(dataset_1);
        FrequencyGeneratorModel frequencyGeneratorModel = new FrequencyGeneratorModel(dataset_2, Purpose.WORK);

        Zone zone1 = new Zone(3612);
        zone1.setRegioStaR2Type(RegioStaR2.URBAN);
        zone1.setRegioStaR7Type(RegioStaR7.RURAL_CENTRAL_CITY);
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
        nestedLogitHabitualModeChoiceModel.chooseHabitualMode(person_test_2);
        initializePlan(person_test_2);
        double[] probability = frequencyGeneratorModel.polrEstimateProb(person_test_2);
        int[] dayCount = frequencyGeneratorModel.getDayCount(person_test_2);

        HabitualMode habitualMode = person_test_2.getHabitualMode();
        for (Person person : person_test_2.getHousehold().getPersons()) {
            if (!person.getOccupation().equals(Occupation.EMPLOYED)) {
                numUnemployedInHh += 1;
            }
        }



        System.out.println("HabitualMode : " + habitualMode);
        System.out.println("Number of unemployed : " + numUnemployedInHh);
        System.out.println(Arrays.toString(probability));
        System.out.println(Arrays.toString(dayCount));

    }
}
