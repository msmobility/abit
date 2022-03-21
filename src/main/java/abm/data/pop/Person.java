package abm.data.pop;

import abm.data.plans.Mode;

public class Person {

    private final int id;
    private final Household household;
    private Mode habitualMode;
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
}
