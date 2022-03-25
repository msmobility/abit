package abm.data.pop;

import abm.data.plans.Mode;
import abm.data.plans.Plan;

public class Person {

    private final int id;
    private final Household household;
    private Mode habitualMode;

    private Plan plan;

    private de.tum.bgu.msm.data.person.Person siloPerson;
    public Person(int id, Household household) {
        this.id = id;
        this.household = household;
    }


    public int getId() {
        return id;
    }

    public Household getHousehold() {
        return household;
    }

    public de.tum.bgu.msm.data.person.Person getSiloPerson() {
        return siloPerson;
    }

    public Mode getHabitualMode() {
        return habitualMode;
    }

    public void setHabitualMode(Mode habitualMode) {
        this.habitualMode = habitualMode;
    }

    public Plan getPlan() {
        return plan;
    }

    public void setPlan(Plan plan) {
        this.plan = plan;
    }
}
