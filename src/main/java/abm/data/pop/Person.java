package abm.data.pop;

public class Person {

    private int id;
    private Household household;
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
}
