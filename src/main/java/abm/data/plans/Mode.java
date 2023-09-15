package abm.data.plans;

import org.matsim.api.core.v01.TransportMode;

import java.util.*;

public enum Mode {

    TRAIN,
    TRAM_METRO,
    BUS,
    CAR_DRIVER,
    CAR_PASSENGER,
    BIKE,
    WALK,
    UNKNOWN;


    //if someone needs to keep the mode UNKNOWN (but also allow for more mode set implementations,
    // e.g. with new modes such as ride hailing) it would be nice to extract an interface Mode and keep the enum
    // behind it, so the interface cannot access to Mode.values() (that someone could call by mistake now) for looping
    // across all modes, which would end up, e.g. looking for coefficients for Mode.UNKNOWN or final choices of it.
    // However, this may not be obvious because getModes() is here a static method but with the interface it
    // would be non-static, so an instance of the used Mode (here Mode as interface) shoudl exist e.g. in the dataset.
    public static SortedSet<Mode> getModes(){
        SortedSet<Mode> modes = new TreeSet<>();
        modes.add(TRAIN);
        modes.add(TRAM_METRO);
        modes.add(BUS);
        modes.add(CAR_DRIVER);
        modes.add(CAR_PASSENGER);
        modes.add(BIKE);
        modes.add(WALK);
        return modes;
    }

    public static String getMatsimMode(Mode abitMode){
        switch (abitMode) {
            case CAR_DRIVER:
                return TransportMode.car;
            case CAR_PASSENGER:
                return "car_passenger";
            case BUS:
                return TransportMode.pt;
            case TRAM_METRO:
                return TransportMode.pt;
            case TRAIN:
                return TransportMode.pt;
            case WALK:
                return TransportMode.walk;
            case BIKE:
                return TransportMode.bike;
            default:
                return null;
        }
    }

}
