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

public class calculateGC {
    public static void main(String[] args) {
        AbitResources.initializeResources(args[0]);
        AbitUtils.loadHdf5Lib();
        MitoUtil.initializeRandomNumber(AbitUtils.getRandomObject());

        DataSet dataset = new HabitualModeChoiceDataReaderManager().readData();
        NestedLogitHabitualModeChoiceModel nestedLogitHabitualModeChoiceModel = new NestedLogitHabitualModeChoiceModel(dataset);

        Zone zone1 = new Zone(3635);
        Zone zone2 = new Zone(3631);
        Zone zone3 = new Zone(3629);
        Job job = new Job(1, -1, "Mnft", zone2, 445, 480);
        School school = new School(1, "1", 238, 238, zone3, 0, 0);
        Person person_test_1 = new Person(1, new Household(1, zone1, 1), 30, Gender.MALE, Relationship.married, Occupation.EMPLOYED, true, job, 28800, 465, 1059, 4511, school, Disability.WITHOUT);
        System.out.println(dataset.getZones().get(person_test_1.getHousehold().getLocation().getZoneId()).getRegioStaR2Type().code());
        for(HabitualMode habitualMode: HabitualMode.getHabitualModesWithoutUnknown()){
            System.out.println("Mode:" + habitualMode + " ,gc:" + nestedLogitHabitualModeChoiceModel.calculateGeneralizedCosts(person_test_1, habitualMode));
        }
    }
}
