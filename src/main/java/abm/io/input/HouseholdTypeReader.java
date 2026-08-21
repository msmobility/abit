package abm.io.input;

import abm.data.DataSet;
import abm.data.pop.Household;
import abm.data.pop.Person;
import abm.data.pop.Relationship;
import abm.models.remoteWorkArrangement.HouseholdType;
import de.tum.bgu.msm.data.person.Gender;
import de.tum.bgu.msm.data.person.Occupation;
import org.apache.log4j.Logger;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class HouseholdTypeReader implements Reader{
    private static final Logger logger = Logger.getLogger(HouseholdTypeReader.class);
    private final DataSet dataSet;
    private Map<HouseholdType, Integer> householdTypeMap = new HashMap<>();

    public HouseholdTypeReader(DataSet dataSet){
        this.dataSet = dataSet;
        householdTypeMap.put(HouseholdType.NONE, 0);
        householdTypeMap.put(HouseholdType.SINGLE_NO_WORKER, 0);
        householdTypeMap.put(HouseholdType.SINGLE_WORKER, 0);
        householdTypeMap.put(HouseholdType.PARTNERED_NO_WORKER, 0);
        householdTypeMap.put(HouseholdType.PARTNERED_SINGLE_EARNER_MALE, 0);
        householdTypeMap.put(HouseholdType.PARTNERED_SINGLE_EARNER_FEMALE, 0);
        householdTypeMap.put(HouseholdType.PARTNERED_DUAL_EARNER, 0);
        householdTypeMap.put(HouseholdType.OTHER_NO_WORKER, 0);
        householdTypeMap.put(HouseholdType.OTHER_WORKER, 0);
    }

    @Override
    public void read() {

        for (Household household: this.dataSet.getHouseholds().values()){

            if (household == null) {
                continue;
            }

            if (household.getPersons().isEmpty()) {
                assign(household, HouseholdType.NONE);
                continue;
            }

            boolean partnered = household.getPersons().stream()
                    .anyMatch(p -> p.getRelationship() == Relationship.married);

            if (partnered) {
                Person employedMan   = findWorkingMale(household, true);
                Person employedWoman = findWorkingFemale(household, true);

                if (employedMan != null && employedWoman != null) {
                    assign(household, HouseholdType.PARTNERED_DUAL_EARNER);
                } else if (employedMan == null && employedWoman == null){
                    assign(household, HouseholdType.PARTNERED_NO_WORKER);
                } else if (employedMan != null) {
                    assign(household, HouseholdType.PARTNERED_SINGLE_EARNER_MALE);
                } else {
                    assign(household, HouseholdType.PARTNERED_SINGLE_EARNER_FEMALE);
                }
            } else if(household.getPersons().size() == 1) {

                Person p = household.getPersons().get(0);
                if (isWorking(p)) {
                    assign(household, HouseholdType.SINGLE_WORKER);
                } else{
                    assign(household, HouseholdType.SINGLE_NO_WORKER);
                }

            } else{

                Person employedMan   = findWorkingMale(household, false);
                Person employedWoman = findWorkingFemale(household, false);

                if (employedMan == null && employedWoman == null) {
                    assign(household, HouseholdType.OTHER_NO_WORKER);
                } else {
                    assign(household, HouseholdType.OTHER_WORKER);
                }

            }

        }

        for (HouseholdType householdType: householdTypeMap.keySet()){
            logger.info(householdType.toString() + " has " + householdTypeMap.get(householdType));
        }
    }

    private void assign(Household household, HouseholdType type) {
        household.setHouseholdType(type);
        householdTypeMap.merge(type, 1, Integer::sum);
    }

    private Person findWorkingMale(Household hh, boolean restrictToMarried) {
        if (hh == null) return null;
        return hh.getPersons().stream()
                .sorted(Comparator.comparingInt(Person::getAge).reversed())
                .filter(p -> p.getGender() == Gender.MALE && isWorking(p))
                .filter(p -> !restrictToMarried || p.getRelationship() == Relationship.married)
                .findFirst().orElse(null);
    }

    private Person findWorkingFemale(Household hh, boolean restrictToMarried) {
        if (hh == null) return null;

        return hh.getPersons().stream()
                .sorted(Comparator.comparingInt(Person::getAge).reversed())
                .filter(p -> p.getGender() == Gender.FEMALE && isWorking(p))
                .filter(p -> !restrictToMarried || p.getRelationship() == Relationship.married)
                .findFirst().orElse(null);
    }

    private boolean isWorking(Person p) {
        return p != null && p.getOccupation() == Occupation.EMPLOYED;
    }

}
