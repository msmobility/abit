package abm.data.pop;

import abm.data.plans.Mode;
import abm.data.plans.Plan;
import de.tum.bgu.msm.data.person.Gender;
import de.tum.bgu.msm.data.person.Occupation;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.util.Optional;

public class Person {

    private final int id;
    private final Household household;
    private Mode habitualMode;

    private int age;

    private Gender gender;
    private Relationship relationship;
    private Occupation occupation;
    private boolean hasLicense;
    private Job job;
    private int monthlyIncome_eur;
    private School schol;
    private final Attributes attributes = new Attributes();
    private Plan plan;

//    public Person(int id, Household household) {
//        this.id = id;
//        this.household = household;
//    }
    public Person(int id, Household household, int age, Gender gender, Relationship relationship, Occupation occupation, boolean hasLicense, Job job, int monthlyIncome_eur, School schol) {
        this.id = id;
        this.household = household;
        this.age = age;
        this.gender = gender;
        this.relationship = relationship;
        this.occupation = occupation;
        this.hasLicense = hasLicense;
        this.job = job;
        this.monthlyIncome_eur = monthlyIncome_eur;
        this.schol = schol;
    }

    public int getAge() {
        return age;
    }

    public Gender getGender() {
        return gender;
    }

    public Relationship getRelationship() {
        return relationship;
    }

    public Occupation getOccupation() {
        return occupation;
    }

    public boolean isHasLicense() {
        return hasLicense;
    }

    public Job getJob() {
        return job;
    }

    public int getMonthlyIncome_eur() {
        return monthlyIncome_eur;
    }

    public School getSchol() {
        return schol;
    }

    public Attributes getAttributes() {
        return attributes;
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


    public boolean hasBicycle() {
        return true;
    }
}
