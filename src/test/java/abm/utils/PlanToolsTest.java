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
                Occupation.EMPLOYED, true, null, 0, 480, 480, 0, null, Disability.WITHOUT);
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
}
