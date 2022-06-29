package abm.data.pop;

import abm.data.plans.Mode;
import abm.data.plans.Plan;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.util.Optional;

public class Person {

    private final int id;
    private final Household household;
    private Mode habitualMode;
    private final Attributes attributes = new Attributes();
    private Plan plan;

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

    public Optional<Object> getAttribute(String key) {
        return Optional.ofNullable(attributes.getAttribute(key));
    }

    public void setAttribute(String key, Object value) {
        attributes.putAttribute(key, value);
    }


}
