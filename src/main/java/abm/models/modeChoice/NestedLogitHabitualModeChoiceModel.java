package abm.models.modeChoice;

import abm.data.DataSet;
import abm.data.plans.Mode;
import abm.data.pop.Person;
import abm.io.input.CoefficientsReader;
import abm.properties.AbitResources;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class NestedLogitHabitualModeChoiceModel implements HabitualModeChoice{


    private final DataSet dataSet;
    private Map<Mode,Map<String, Double>> coefficients;


    public NestedLogitHabitualModeChoiceModel(DataSet dataSet) {
        this.dataSet = dataSet;
        this.coefficients = new HashMap<>();
        Path pathToFile = Path.of(AbitResources.instance.getString("habitual.mode.coef"));

        //the following loop will read the coefficient file Mode.getModes().size() times, which is acceptable?
        for (Mode mode : Mode.getModes()){
            final Map<String, Double> modeCoefficients = new CoefficientsReader(dataSet, mode.toString().toLowerCase(), pathToFile).readCoefficients();
            coefficients.put(mode, modeCoefficients);
        }

    }

    @Override
    public void chooseHabitualMode(Person person) {

        Map<Mode, Double> utilities = new HashMap<>();
        for (Mode mode : Mode.getModes()){
            utilities.put(mode, calculateUtilityForThisMode(person));
        }

        //do here the nested logit mode choice calculations, depending on nesting structure and coefficient

        person.setHabitualMode(Mode.UNKNOWN);

    }

    private double calculateUtilityForThisMode(Person person) {
        return 0;
    }


}
