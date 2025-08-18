package abm.data.vehicle;

public enum EmissionClass {

//    EURO_4, EURO_5, EURO_6, EUROx;
//
//    public static int getWeight(EmissionClass emissionClass) {
//        switch (emissionClass) {
//            case EURO_4: return 4;
//            case EURO_5: return 5;
//            case EURO_6: return 6;
//            case EUROx: return 7;
//            default: return -1; // Unknown
//        }
//    }
//
//    public static int getWeight(String emissionName) {
//        try {
//            return getWeight(EmissionClass.valueOf(emissionName));
//        } catch (IllegalArgumentException e) {
//            return -1; // If string doesn't match any enum
//        }
//    }

    EURO_4(4),
    EURO_5(5),
    EURO_6(6),
    EUROx(7); // Unknown or special

    private final int weight;

    EmissionClass(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }


}
