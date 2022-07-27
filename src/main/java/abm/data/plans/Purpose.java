package abm.data.plans;



import de.tum.bgu.msm.data.Id;

import java.util.HashSet;
import java.util.Set;

public enum Purpose implements Id {


    HOME,
    WORK,
    EDUCATION,
    ACCOMPANY,
    SHOPPING,
    OTHER,
    RECREATION;

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

    public static Set<Purpose> getAllPurposes(){
        Set<Purpose> purposes = new HashSet<>();
        purposes.add(WORK);
        purposes.add(EDUCATION);
        purposes.add(RECREATION);
        purposes.add(SHOPPING);
        purposes.add(ACCOMPANY);
        purposes.add(OTHER);
        return purposes;
    }


    @Override
    public int getId() {
        return this.ordinal();
    }
}
