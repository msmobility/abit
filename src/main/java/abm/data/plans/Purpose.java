package abm.data.plans;

import java.util.HashSet;
import java.util.Set;

public enum Purpose {

    HOME,
    WORK,
    EDUCATION,
    SHOPPING,
    RECREATION,
    ACCOMPANY,
    OTHER;

    public static Set<Purpose> getMandatoryPurposes(){
        Set<Purpose> purposes = new HashSet<>();
        purposes.add(WORK);
        purposes.add(EDUCATION);
        return purposes;
    }

    public static Set<Purpose> getDiscretionaryPurposes(){
        Set<Purpose> purposes = new HashSet<>();
        purposes.add(RECREATION);
        purposes.add(SHOPPING);
        purposes.add(ACCOMPANY);
        purposes.add(OTHER);
        return purposes;
    }

}
