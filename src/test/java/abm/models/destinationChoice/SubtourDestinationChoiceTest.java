package abm.models.destinationChoice;

import abm.data.DataSet;
import abm.data.geo.RegioStaR2;
import abm.data.geo.RegioStaR7;
import abm.data.geo.RegioStaRGem5;
import abm.data.geo.Zone;
import abm.data.plans.*;
import abm.data.pop.*;
import abm.io.input.HabitualModeChoiceDataReaderManager;
import abm.io.input.SubtourDestinationChoiceDataReaderManager;
import abm.models.activityGeneration.frequency.SubtourGeneratorModel;
import abm.properties.AbitResources;
import abm.utils.AbitUtils;
import de.tum.bgu.msm.data.person.Disability;
import de.tum.bgu.msm.data.person.Gender;
import de.tum.bgu.msm.data.person.Occupation;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix2D;
import org.junit.Before;
import org.junit.Test;

import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.Map;

import static abm.data.plans.Plan.initializePlan;

public class SubtourDestinationChoiceTest {

    SubtourDestinationChoiceModel subtourDestinationChoiceModel;
    Map<Zone, Double> zoneAttractors;
    IndexedDoubleMatrix2D utilityMatrix;
    DataSet dataset;

    @Before
    public void setup() {
        AbitResources.initializeResources("abit_wei.properties");
        AbitUtils.loadHdf5Lib();
        MitoUtil.initializeRandomNumber(AbitUtils.getRandomObject());

        DataSet dataset = new SubtourDestinationChoiceDataReaderManager().readData();
        subtourDestinationChoiceModel = new SubtourDestinationChoiceModel(dataset);
        zoneAttractors = subtourDestinationChoiceModel.loadBasicAttraction();
        utilityMatrix = subtourDestinationChoiceModel.loadUtilities();
    }

    @Test
    public void testTourModeChoice(){
        double utility = utilityMatrix.getIndexed(1,2);

        assertEquals(2725.536, utility, 0.001);

    }

    public static void assertEquals(double expected, double actual, double delta) {
        if (Double.isNaN(expected) && Double.isNaN(actual)) {
            return; // both NaN, consider equal
        }
        if (Math.abs(expected - actual) > delta) {
            throw new AssertionError("Expected " + expected + " but got " + actual
                    + " (tolerance " + delta + ")");
        }
    }


}
