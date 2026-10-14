package abm.utils;

import abm.data.geo.Location;
import abm.data.geo.RegioStaR2;
import abm.data.geo.RegioStaR7;
import abm.data.geo.RegioStaRGem5;
import abm.data.geo.Zone;
import abm.data.plans.Activity;
import abm.data.plans.HomeEpisode;
import abm.data.plans.Leg;
import abm.data.plans.Plan;
import abm.data.plans.Purpose;
import abm.data.plans.Tour;
import abm.data.pop.DisabilityMuc;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.data.pop.Relationship;
import de.tum.bgu.msm.data.person.Disability;
import de.tum.bgu.msm.data.person.Gender;
import de.tum.bgu.msm.data.person.Occupation;
import junitx.framework.Assert;
import org.junit.Test;

import java.time.DayOfWeek;

/**
 * Regression tests for three bugs fixed in full-model-bug-fix-plan.md Batch 1, all reachable
 * without AbitResources/CSV setup since PlanTools takes TravelTimes/TravelDistances as plain
 * constructor parameters.
 *
 * <p>Note: PlanTools queries the "outbound" and "return" legs of a tour with the SAME time
 * parameter (only the origin/destination order differs), so stubs below differentiate direction
 * by comparing the origin Location's identity, not by the time argument.
 */
public class PlanToolsTest {

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
                Occupation.EMPLOYED, true, null, 0, 480, 480, 0, null, DisabilityMuc.WITHOUT);
        household.getPersons().add(p);
        Plan.initializePlan(p);
        this.person = p;
        return p;
    }

    private int mondayMinute(int minuteOfDay) {
        return DayOfWeek.MONDAY.ordinal() * 24 * 60 + minuteOfDay;
    }

    @Test
    public void addMainTour_firstTourOfDay_returnLegUsesReturnTravelTimeAndDistance() {
        newPerson();
        Plan plan = person.getPlan();

        PlanTools planTools = new PlanTools(
                (origin, destination, mode, time) -> origin == homeZone ? 20 : 35,
                (origin, destination, mode, time) -> origin == homeZone ? 2000 : 4500);

        Activity work = new Activity(person, Purpose.WORK);
        work.setDayOfWeek(DayOfWeek.MONDAY);
        work.setLocation(workZone);
        work.setStartTime_min(mondayMinute(600));
        work.setEndTime_min(mondayMinute(1080));

        planTools.addMainTour(plan, work);

        Tour tour = plan.getTours().get(work.getStartTime_min());
        Leg outboundLeg = tour.getLegs().get(tour.getLegs().firstKey());
        Leg returnLeg = tour.getLegs().get(tour.getLegs().lastKey());

        Assert.assertEquals(20, outboundLeg.getTravelTime_min());
        Assert.assertEquals(2000.0, outboundLeg.getDistance(), 0.01);

        // the bug reused the outbound values here - assert the genuinely-computed return values
        Assert.assertEquals(35, returnLeg.getTravelTime_min());
        Assert.assertEquals(4500.0, returnLeg.getDistance(), 0.01);
    }

    @Test
    public void addMainTour_secondTourOfDay_returnLegUsesReturnTravelTimeAndDistance() {
        newPerson();
        Plan plan = person.getPlan();
        Zone shoppingZone = new Zone(3);
        shoppingZone.setRegioStaR2Type(RegioStaR2.URBAN);
        shoppingZone.setRegioStaRGem5Type(RegioStaRGem5.METROPOLIS);
        shoppingZone.setRegioStaR7Type(RegioStaR7.URBAN_METROPOLIS);

        PlanTools planTools = new PlanTools(
                (origin, destination, mode, time) -> origin == homeZone ? 15 : 25,
                (origin, destination, mode, time) -> origin == homeZone ? 1000 : 3000);

        Activity work = new Activity(person, Purpose.WORK);
        work.setDayOfWeek(DayOfWeek.MONDAY);
        work.setLocation(workZone);
        work.setStartTime_min(mondayMinute(600));
        work.setEndTime_min(mondayMinute(1080));
        planTools.addMainTour(plan, work);

        // second activity fits entirely before the first tour's departure (600 - 15 = 585)
        Activity shopping = new Activity(person, Purpose.SHOPPING);
        shopping.setDayOfWeek(DayOfWeek.MONDAY);
        shopping.setLocation(shoppingZone);
        shopping.setStartTime_min(mondayMinute(400));
        shopping.setEndTime_min(mondayMinute(430));
        planTools.addMainTour(plan, shopping);

        Tour shoppingTour = plan.getTours().get(shopping.getStartTime_min());
        Leg returnLeg = shoppingTour.getLegs().get(shoppingTour.getLegs().lastKey());

        Assert.assertEquals(25, returnLeg.getTravelTime_min());
        Assert.assertEquals(3000.0, returnLeg.getDistance(), 0.01);
    }

    @Test
    public void splitInHomeWorkAroundActivity_newLegsCarryComputedDistance() {
        newPerson();
        Plan plan = person.getPlan();
        Zone shoppingZone = new Zone(3);
        shoppingZone.setRegioStaR2Type(RegioStaR2.URBAN);
        shoppingZone.setRegioStaRGem5Type(RegioStaRGem5.METROPOLIS);
        shoppingZone.setRegioStaR7Type(RegioStaR7.URBAN_METROPOLIS);

        PlanTools planTools = new PlanTools(
                (origin, destination, mode, time) -> 10,
                (origin, destination, mode, time) -> origin == homeZone ? 1500 : 1800);

        Activity work = new Activity(person, Purpose.WORK);
        work.setDayOfWeek(DayOfWeek.MONDAY);
        work.setLocation(homeZone); // an in-home WORK day happens at the household's own location
        work.setStartTime_min(mondayMinute(600));
        work.setEndTime_min(mondayMinute(1080));
        planTools.addInHomeTour(plan, work);
        Assert.assertTrue(plan.getTours().get(work.getStartTime_min()) instanceof HomeEpisode);

        Activity shopping = new Activity(person, Purpose.SHOPPING);
        shopping.setDayOfWeek(DayOfWeek.MONDAY);
        shopping.setLocation(shoppingZone);
        shopping.setStartTime_min(mondayMinute(700));
        shopping.setEndTime_min(mondayMinute(730));

        planTools.splitInHomeWorkAroundActivity(plan, work, shopping);

        Assert.assertTrue(plan.getUnmetActivities().isEmpty());
        Tour shoppingTour = shopping.getTour();
        Leg outboundLeg = shoppingTour.getLegs().get(shoppingTour.getLegs().firstKey());
        Leg inboundLeg = shoppingTour.getLegs().get(shoppingTour.getLegs().lastKey());

        // before the fix, these legs never had setDistance called at all, so they'd read 0.0
        Assert.assertEquals(1500.0, outboundLeg.getDistance(), 0.01);
        Assert.assertEquals(1800.0, inboundLeg.getDistance(), 0.01);
    }

    @Test
    public void addStopBefore_rejectsRatherThanCorruptsScheduleWhenWindowInverts() {
        newPerson();
        Plan plan = person.getPlan();

        // constant 2-minute travel time/distance on both legs
        PlanTools planTools = new PlanTools(
                (origin, destination, mode, time) -> 2,
                (origin, destination, mode, time) -> 200);

        Activity work = new Activity(person, Purpose.WORK);
        work.setDayOfWeek(DayOfWeek.MONDAY);
        work.setLocation(workZone);
        work.setStartTime_min(mondayMinute(600));
        work.setEndTime_min(mondayMinute(1080));
        planTools.addMainTour(plan, work);
        Tour tour = plan.getTours().get(work.getStartTime_min());

        // a 5-minute stop with 2+2 min of travel totals 9 min < SEARCH_INTERVAL_MIN (15) -
        // exactly the condition that inverted the old isAvailable range check
        Activity stopBefore = new Activity(person, Purpose.SHOPPING);
        stopBefore.setDayOfWeek(DayOfWeek.MONDAY);
        stopBefore.setLocation(homeZone);
        stopBefore.setStartTime_min(work.getStartTime_min() - 10);
        stopBefore.setEndTime_min(work.getStartTime_min() - 5);

        planTools.addStopBefore(plan, stopBefore, tour);

        Assert.assertTrue(plan.getUnmetActivities().containsValue(stopBefore));
        Assert.assertEquals(1, tour.getActivities().size());
    }

    // --- cross-midnight-scheduling-plan.md Part 1: inline live-neighbor lookup + merge ---

    private Activity workActivity(DayOfWeek day, int startMinuteOfDay, int endMinuteOfDay, Location location) {
        Activity activity = new Activity(person, Purpose.WORK);
        activity.setDayOfWeek(day);
        activity.setLocation(location);
        activity.setStartTime_min(day.ordinal() * 24 * 60 + startMinuteOfDay);
        activity.setEndTime_min(day.ordinal() * 24 * 60 + endMinuteOfDay);
        return activity;
    }

    @Test
    public void addMainTour_secondTourOnLaterDay_mergesTrailingAndLeadingBookend() {
        newPerson();
        Plan plan = person.getPlan();
        PlanTools planTools = new PlanTools(
                (origin, destination, mode, time) -> 15,
                (origin, destination, mode, time) -> 1000);

        Activity monday = workActivity(DayOfWeek.MONDAY, 480, 1020, workZone);
        planTools.addMainTour(plan, monday);
        Activity wednesday = workActivity(DayOfWeek.WEDNESDAY, 480, 1020, workZone);
        planTools.addMainTour(plan, wednesday);

        Tour mondayTour = monday.getTour();
        Tour wednesdayTour = wednesday.getTour();
        int wednesdayDeparture = wednesdayTour.getLegs().firstKey();

        // genuinely the same shared object, not just a matching value - so ActivityPrinter's/
        // PlansToMATSimPlans's identity-based dedup exports it once
        Assert.assertSame(mondayTour.getTrailingBookend(), wednesdayTour.getLeadingBookend());
        // Monday's trailing bookend now extends to Wednesday's own departure, not to day's end
        Assert.assertEquals(wednesdayDeparture, mondayTour.getTrailingBookend().getEndTime_min());
    }

    @Test
    public void addMainTour_insertedBetweenTwoMergedTours_splitsTheSharedBookend() {
        newPerson();
        Plan plan = person.getPlan();
        PlanTools planTools = new PlanTools(
                (origin, destination, mode, time) -> 15,
                (origin, destination, mode, time) -> 1000);

        Activity monday = workActivity(DayOfWeek.MONDAY, 480, 1020, workZone);
        planTools.addMainTour(plan, monday);
        Activity wednesday = workActivity(DayOfWeek.WEDNESDAY, 480, 1020, workZone);
        planTools.addMainTour(plan, wednesday);

        Activity sharedBefore = monday.getTour().getTrailingBookend();
        Assert.assertSame(sharedBefore, wednesday.getTour().getLeadingBookend());

        // Tuesday, inserted last but chronologically between Monday and Wednesday
        Activity tuesday = workActivity(DayOfWeek.TUESDAY, 480, 1020, workZone);
        planTools.addMainTour(plan, tuesday);

        Tour mondayTour = monday.getTour();
        Tour tuesdayTour = tuesday.getTour();
        Tour wednesdayTour = wednesday.getTour();
        int tuesdayDeparture = tuesdayTour.getLegs().firstKey();
        int wednesdayDeparture = wednesdayTour.getLegs().firstKey();

        // Monday's trailing bookend now shrinks to Tuesday's own departure, shared with it
        Assert.assertSame(mondayTour.getTrailingBookend(), tuesdayTour.getLeadingBookend());
        Assert.assertEquals(tuesdayDeparture, mondayTour.getTrailingBookend().getEndTime_min());
        // the old Monday/Wednesday shared object no longer serves as Wednesday's leading bookend -
        // Tuesday's own trailing bookend takes over that role instead, far edge preserved
        Assert.assertTrue(wednesdayTour.getLeadingBookend() != sharedBefore);
        Assert.assertSame(tuesdayTour.getTrailingBookend(), wednesdayTour.getLeadingBookend());
        Assert.assertEquals(wednesdayDeparture, tuesdayTour.getTrailingBookend().getEndTime_min());
    }

    @Test
    public void addInHomeTour_insertedBetweenTwoMergedTours_splitsTheSharedBookend() {
        newPerson();
        Plan plan = person.getPlan();
        PlanTools planTools = new PlanTools(
                (origin, destination, mode, time) -> 15,
                (origin, destination, mode, time) -> 1000);

        Activity monday = workActivity(DayOfWeek.MONDAY, 480, 1020, workZone);
        planTools.addMainTour(plan, monday);
        Activity wednesday = workActivity(DayOfWeek.WEDNESDAY, 480, 1020, workZone);
        planTools.addMainTour(plan, wednesday);
        Activity sharedBefore = monday.getTour().getTrailingBookend();

        Activity tuesdayWfh = workActivity(DayOfWeek.TUESDAY, 480, 1020, homeZone);
        planTools.addInHomeTour(plan, tuesdayWfh);

        Tour mondayTour = monday.getTour();
        HomeEpisode tuesdayEpisode = (HomeEpisode) tuesdayWfh.getTour();
        Tour wednesdayTour = wednesday.getTour();

        Assert.assertSame(mondayTour.getTrailingBookend(), tuesdayEpisode.getLeadingBookend());
        Assert.assertEquals(tuesdayWfh.getStartTime_min(), mondayTour.getTrailingBookend().getEndTime_min());
        Assert.assertTrue(wednesdayTour.getLeadingBookend() != sharedBefore);
        Assert.assertSame(tuesdayEpisode.getTrailingBookend(), wednesdayTour.getLeadingBookend());
        Assert.assertEquals(tuesdayWfh.getEndTime_min(), tuesdayEpisode.getTrailingBookend().getStartTime_min());
    }

    @Test
    public void addMainTour_adjacentToExistingHomeEpisode_mergesWithItsBookend() {
        newPerson();
        Plan plan = person.getPlan();
        PlanTools planTools = new PlanTools(
                (origin, destination, mode, time) -> 15,
                (origin, destination, mode, time) -> 1000);

        Activity mondayWfh = workActivity(DayOfWeek.MONDAY, 480, 1020, homeZone);
        planTools.addInHomeTour(plan, mondayWfh);
        Activity tuesday = workActivity(DayOfWeek.TUESDAY, 480, 1020, workZone);
        planTools.addMainTour(plan, tuesday);

        HomeEpisode mondayEpisode = (HomeEpisode) mondayWfh.getTour();
        Tour tuesdayTour = tuesday.getTour();

        // genuinely shares the object now, not just reads a matching value
        Assert.assertSame(mondayEpisode.getTrailingBookend(), tuesdayTour.getLeadingBookend());
    }

    @Test
    public void addMainTour_arrivalPastEndOfWeek_placeholderNeverInverts() {
        newPerson();
        Plan plan = person.getPlan();
        PlanTools planTools = new PlanTools(
                (origin, destination, mode, time) -> 30,
                (origin, destination, mode, time) -> 1000);

        // Sunday 22:00 start, 8h shift - arrival home lands past PlanTools.endOfTheWeek() (10080)
        Activity sundayNightShift = workActivity(DayOfWeek.SUNDAY, 1320, 1320 + 8 * 60, workZone);
        planTools.addMainTour(plan, sundayNightShift);

        Activity trailing = sundayNightShift.getTour().getTrailingBookend();
        Assert.assertTrue(trailing.getStartTime_min() <= trailing.getEndTime_min());
    }

    @Test
    public void splitInHomeWorkAroundActivity_relocatesOriginalTrailingBookendToWorkAfterEpisode() {
        newPerson();
        Plan plan = person.getPlan();
        PlanTools planTools = new PlanTools(
                (origin, destination, mode, time) -> 10,
                (origin, destination, mode, time) -> 1000);

        Activity mondayWfh = workActivity(DayOfWeek.MONDAY, 600, 1080, homeZone);
        planTools.addInHomeTour(plan, mondayWfh);

        // a later tour gives the WFH episode's own trailing bookend a real, known far edge
        Activity wednesday = workActivity(DayOfWeek.WEDNESDAY, 480, 1020, workZone);
        planTools.addMainTour(plan, wednesday);

        HomeEpisode originalEpisode = (HomeEpisode) mondayWfh.getTour();
        Activity originalTrailing = originalEpisode.getTrailingBookend();
        int wednesdayDeparture = wednesday.getTour().getLegs().firstKey();
        Assert.assertEquals(wednesdayDeparture, originalTrailing.getEndTime_min());

        Activity shopping = new Activity(person, Purpose.SHOPPING);
        shopping.setDayOfWeek(DayOfWeek.MONDAY);
        shopping.setLocation(workZone);
        shopping.setStartTime_min(mondayMinute(700));
        shopping.setEndTime_min(mondayMinute(730));

        planTools.splitInHomeWorkAroundActivity(plan, mondayWfh, shopping);

        Assert.assertTrue(plan.getUnmetActivities().isEmpty());
        Activity workAfter = shopping.getTour().getLegs().get(shopping.getTour().getLegs().lastKey()).getNextActivity();
        HomeEpisode workAfterEpisode = (HomeEpisode) workAfter.getTour();

        // relocated: same object, far edge (Wednesday's departure) preserved, near edge updated
        Assert.assertSame(originalTrailing, workAfterEpisode.getTrailingBookend());
        Assert.assertEquals(wednesdayDeparture, workAfterEpisode.getTrailingBookend().getEndTime_min());
        Assert.assertEquals(workAfter.getEndTime_min(), originalTrailing.getStartTime_min());
        // originalEpisode itself no longer has a separate trailing bookend
        Assert.assertSame(mondayWfh, originalEpisode.getTrailingBookend());
    }

    @Test
    public void splitInHomeWorkAroundActivity_caseB_repointsDependentTourLeadingBookend() {
        newPerson();
        Plan plan = person.getPlan();
        PlanTools planTools = new PlanTools(
                (origin, destination, mode, time) -> 10,
                (origin, destination, mode, time) -> 1000);

        Activity mondayWfh = workActivity(DayOfWeek.MONDAY, 480, 1080, homeZone);
        planTools.addInHomeTour(plan, mondayWfh);

        // manually establish the Case B precondition (as arises in practice from a prior chained
        // interruption): a real tour already using mondayWfh directly as its own departure point
        Activity dependentActivity = workActivity(DayOfWeek.MONDAY, 1100, 1130, workZone);
        Activity dependentTourHomeAfter = new Activity(person, Purpose.HOME);
        dependentTourHomeAfter.setLocation(homeZone);
        dependentTourHomeAfter.setStartTime_min(mondayMinute(1140));
        dependentTourHomeAfter.setEndTime_min(PlanTools.endOfTheWeek());
        Tour dependentTour = new Tour(dependentActivity, 99);
        Leg departureLeg = new Leg(mondayWfh, dependentActivity);
        departureLeg.setTravelTime_min(10);
        departureLeg.setDistance(1000);
        Leg returnLeg = new Leg(dependentActivity, dependentTourHomeAfter);
        returnLeg.setTravelTime_min(10);
        returnLeg.setDistance(1000);
        dependentTour.getLegs().put(mondayWfh.getEndTime_min(), departureLeg);
        dependentTour.getLegs().put(dependentActivity.getEndTime_min(), returnLeg);
        dependentActivity.setTour(dependentTour);
        plan.getTours().put(dependentActivity.getStartTime_min(), dependentTour);

        Activity secondStop = new Activity(person, Purpose.SHOPPING);
        secondStop.setDayOfWeek(DayOfWeek.MONDAY);
        secondStop.setLocation(workZone);
        secondStop.setStartTime_min(mondayMinute(700));
        secondStop.setEndTime_min(mondayMinute(720));

        planTools.splitInHomeWorkAroundActivity(plan, mondayWfh, secondStop);

        Assert.assertTrue(plan.getUnmetActivities().isEmpty());
        Activity workMiddle = secondStop.getTour().getLegs().get(secondStop.getTour().getLegs().lastKey()).getNextActivity();
        Assert.assertSame(workMiddle, dependentTour.getLeadingBookend());
    }
}
