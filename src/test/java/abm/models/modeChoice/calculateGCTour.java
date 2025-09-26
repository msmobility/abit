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

import java.time.DayOfWeek;
import java.util.EnumMap;

import static abm.data.plans.Plan.initializePlan;
import static abm.io.input.CalibrationZoneToRegionTypeReader.getRegionForZone;

public class calculateGCTour {

    public static void main(String[] args) {
        AbitResources.initializeResources(args[0]);
        AbitUtils.loadHdf5Lib();
        MitoUtil.initializeRandomNumber(AbitUtils.getRandomObject());

        DataSet dataset = new HabitualModeChoiceDataReaderManager().readData();
        NestedLogitTourModeChoiceModel nestedLogitTourModeChoiceModel = new NestedLogitTourModeChoiceModel(dataset);

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

        EnumMap<Mode, Double> GC = nestedLogitTourModeChoiceModel.calculateGeneralizedCosts(Purpose.WORK, household, tour);

        for(Mode mode : Mode.getModes()){
            System.out.println("mode:"+ mode + ",gc:" + GC.get(mode));
        }
        String region = getRegionForZone(tour.getActivities().get(tour.getActivities().firstKey()).
                getLocation().getZoneId());

        System.out.println(region.toLowerCase());
    }
}
