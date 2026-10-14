package abm.models.planConsistency;

import abm.data.geo.RegioStaR2;
import abm.data.geo.RegioStaR7;
import abm.data.geo.RegioStaRGem5;
import abm.data.geo.Zone;
import abm.data.plans.Activity;
import abm.data.plans.Plan;
import abm.data.plans.Purpose;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.data.pop.Relationship;
import abm.utils.PlanTools;
import de.tum.bgu.msm.data.person.Disability;
import de.tum.bgu.msm.data.person.Gender;
import de.tum.bgu.msm.data.person.Occupation;
import junitx.framework.Assert;
import org.junit.Test;

import java.time.DayOfWeek;

/**
 * Regression tests for the residual week-wraparound step added to PlanConsistencyRepairModel by
 * cross-midnight-scheduling-plan.md Part 2. Uses a constant travel time matching what PlanTools
 * already used at construction, so repairLeg's own delta-based drift correction is a no-op and
 * the wraparound step's effect can be observed in isolation.
 */
public class PlanConsistencyRepairModelTest {

    private static final int TRAVEL_TIME_MIN = 15;
    private static final int TRAVEL_DISTANCE_M = 1000;

    private Zone homeZone;
    private Zone workZone;
    private Person person;

    private Person newPerson() {
        homeZone = new Zone(1);
        homeZone.setRegioStaR2Type(RegioStaR2.URBAN);
        homeZone.setRegioStaRGem5Type(RegioStaRGem5.METROPOLIS);
        homeZone.setRegioStaR7Type(RegioStaR7.URBAN_METROPOLIS);

        workZone = new Zone(2);
        workZone.setRegioStaR2Type(RegioStaR2.URBAN);
        workZone.setRegioStaRGem5Type(RegioStaRGem5.METROPOLIS);
        workZone.setRegioStaR7Type(RegioStaR7.URBAN_METROPOLIS);

        Household household = new Household(1, homeZone, 1);
        Person p = new Person(1, household, 36, Gender.FEMALE, Relationship.married,
                Occupation.EMPLOYED, true, null, 0, 480, 480, 0, null, Disability.WITHOUT);
        household.getPersons().add(p);
        Plan.initializePlan(p);
        this.person = p;
        return p;
    }

    private int minute(DayOfWeek day, int minuteOfDay) {
        return day.ordinal() * 24 * 60 + minuteOfDay;
    }

    private Activity workActivity(DayOfWeek day, int startMinuteOfDay, int endMinuteOfDay) {
        Activity activity = new Activity(person, Purpose.WORK);
        activity.setDayOfWeek(day);
        activity.setLocation(workZone);
        activity.setStartTime_min(minute(day, startMinuteOfDay));
        activity.setEndTime_min(minute(day, endMinuteOfDay));
        return activity;
    }

    @Test
    public void repairPlan_singleTour_trailingBookendWrapsToOwnDeparturePlusWeek() {
        newPerson();
        Plan plan = person.getPlan();
        PlanTools planTools = new PlanTools(
                (origin, destination, mode, time) -> TRAVEL_TIME_MIN,
                (origin, destination, mode, time) -> TRAVEL_DISTANCE_M);

        Activity monday = workActivity(DayOfWeek.MONDAY, 480, 1020);
        planTools.addMainTour(plan, monday);
        int mondayDeparture = monday.getTour().getLegs().firstKey();

        PlanConsistencyRepairModel repair = new PlanConsistencyRepairModel(
                (origin, destination, mode, time) -> TRAVEL_TIME_MIN);
        repair.repairPlan(plan);

        Activity trailing = monday.getTour().getTrailingBookend();
        Assert.assertEquals(mondayDeparture + PlanTools.endOfTheWeek(), trailing.getEndTime_min());
    }

    @Test
    public void repairPlan_lastOfSeveralTours_wrapsToFirstTourDeparturePlusWeek() {
        newPerson();
        Plan plan = person.getPlan();
        PlanTools planTools = new PlanTools(
                (origin, destination, mode, time) -> TRAVEL_TIME_MIN,
                (origin, destination, mode, time) -> TRAVEL_DISTANCE_M);

        Activity monday = workActivity(DayOfWeek.MONDAY, 480, 1020);
        planTools.addMainTour(plan, monday);
        Activity friday = workActivity(DayOfWeek.FRIDAY, 480, 1020);
        planTools.addMainTour(plan, friday);
        int mondayDeparture = monday.getTour().getLegs().firstKey();

        // Monday's own trailing bookend is already correctly merged with Friday's leading
        // bookend by PlanTools - not the placeholder needing correction here
        Activity mondayTrailingBeforeRepair = monday.getTour().getTrailingBookend();

        PlanConsistencyRepairModel repair = new PlanConsistencyRepairModel(
                (origin, destination, mode, time) -> TRAVEL_TIME_MIN);
        repair.repairPlan(plan);

        // untouched by the wraparound step - only the true last tour's own trailing bookend gets it
        Assert.assertSame(mondayTrailingBeforeRepair, monday.getTour().getTrailingBookend());
        Assert.assertSame(monday.getTour().getTrailingBookend(), friday.getTour().getLeadingBookend());

        Activity fridayTrailing = friday.getTour().getTrailingBookend();
        Assert.assertEquals(mondayDeparture + PlanTools.endOfTheWeek(), fridayTrailing.getEndTime_min());
    }

    @Test
    public void repairPlan_calledTwice_isIdempotent() {
        newPerson();
        Plan plan = person.getPlan();
        PlanTools planTools = new PlanTools(
                (origin, destination, mode, time) -> TRAVEL_TIME_MIN,
                (origin, destination, mode, time) -> TRAVEL_DISTANCE_M);

        Activity monday = workActivity(DayOfWeek.MONDAY, 480, 1020);
        planTools.addMainTour(plan, monday);

        PlanConsistencyRepairModel repair = new PlanConsistencyRepairModel(
                (origin, destination, mode, time) -> TRAVEL_TIME_MIN);
        repair.repairPlan(plan);
        int afterFirstRun = monday.getTour().getTrailingBookend().getEndTime_min();
        repair.repairPlan(plan);
        int afterSecondRun = monday.getTour().getTrailingBookend().getEndTime_min();

        Assert.assertEquals(afterFirstRun, afterSecondRun);
    }

    @Test
    public void repairPlan_noTours_doesNotThrow() {
        newPerson();
        Plan plan = person.getPlan();
        PlanConsistencyRepairModel repair = new PlanConsistencyRepairModel(
                (origin, destination, mode, time) -> TRAVEL_TIME_MIN);
        repair.repairPlan(plan);
    }
}
