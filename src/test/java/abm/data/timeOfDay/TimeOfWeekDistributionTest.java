package abm.data.timeOfDay;

import junitx.framework.Assert;
import org.junit.Test;

import java.util.Random;

/**
 * Regression test for the Batch 1 fix (full-model-bug-fix-plan.md): selectTime(Random) used to
 * draw its jitter term from the shared static AbitUtils RNG instead of the passed-in Random,
 * defeating per-person-RNG reproducibility under parallel plan generation.
 */
public class TimeOfWeekDistributionTest {

    @Test
    public void selectTime_isFullyReproducibleFromThePassedInRandom() {
        TimeOfWeekDistribution distribution = new TimeOfWeekDistribution();
        distribution.setProbability(600, 1.0);

        int result1 = distribution.selectTime(new Random(42));
        int result2 = distribution.selectTime(new Random(42));

        // before the fix, the jitter term consulted a shared, uncontrolled static Random, so two
        // otherwise-identically-seeded calls would not reliably produce the same result
        Assert.assertEquals(result1, result2);
    }
}
