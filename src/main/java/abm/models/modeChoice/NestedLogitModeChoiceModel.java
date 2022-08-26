package abm.models.modeChoice;

import abm.data.DataSet;
import abm.data.plans.Mode;
import abm.data.plans.Purpose;
import abm.data.plans.Tour;
import abm.data.pop.Person;
import abm.io.input.CoefficientsReader;
import abm.properties.AbitResources;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class NestedLogitModeChoiceModel implements TourModeChoice{


    private final Purpose purpose;
    private final DataSet dataSet;
    private Map<Mode,Map<String, Double>> coefficients;


    public NestedLogitModeChoiceModel(Purpose purpose, DataSet dataSet) {
        this.purpose = purpose;
        this.dataSet = dataSet;
        this.coefficients = new HashMap<>();
        Path pathToFile = Path.of(AbitResources.instance.getString("habitual.mode.coef"));

        //the following loop will read the coefficient file Mode.getModes().size() times, which is acceptable?
        for (Mode mode : Mode.getModes()){
            String columnName = mode.toString().toLowerCase() + "_" + purpose.toString().toLowerCase(); //todo review this
            Map<String, Double> modeCoefficients = new CoefficientsReader(dataSet, columnName, pathToFile).readCoefficients();
            coefficients.put(mode, modeCoefficients);
        }

    }


    @Override
    public void chooseMode(Person person, Tour tour) {
        Map<Mode, Double> utilities = new HashMap<>();
        for (Mode mode : Mode.getModes()){
            utilities.put(mode, calculateUtilityForThisMode(person, tour));
        }

        //do here the nested logit mode choice calculations, depending on nesting structure and coefficient


        tour.getLegs().values().forEach(leg -> leg.setLegMode(Mode.UNKNOWN));

    }

    private double calculateUtilityForThisMode(Person person, Tour tour) {
        return 0;
    }
}
