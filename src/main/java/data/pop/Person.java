package data.pop;

public class Person {

    private int id;
    private Household household;

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
}
