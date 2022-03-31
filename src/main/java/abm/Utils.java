package abm;

import java.util.Random;

public class Utils {

    public static final String SEPARATOR = ",";
    private static final int RANDOM_SEED = 1;
    public static final Random randomObject = new Random(RANDOM_SEED);

    public static Random getRandomObject(){
        return randomObject;
    }

}
