package abm.models.remoteWorkArrangement;

import junitx.framework.Assert;
import org.junit.Test;

/**
 * Tests for RemoteWorkFrequencyModel.drawTruncatedCategory - the pure-function core of the
 * remote-work-day-count-cap-plan.md fix (docs/remote-work-day-count-cap-plan.md), package-private
 * and static so it's directly testable without constructing a real RemoteWorkFrequencyModel
 * (which needs AbitResources/coefficient files not available in this sandbox).
 * <p>
 * All non-structural tests below share one hand-computed distribution: phi=0.6, mu=0.3,
 * intercepts={-1.0,-0.5,0.0,0.5,1.0,1.5}. Per-category cumulative probability (derived by hand
 * from the same formula the implementation uses, decoupled from the draw):
 * cumProbByCategory = [0.4000, 0.5285, 0.5860, 0.6553, 0.7299, 0.8009, 0.8611, 1.0000]
 * (index i = P(result &lt;= i) under the untruncated distribution; index 7 is exactly 1.0 by
 * construction - the "prob" terms telescope to sum to 1 regardless of the specific
 * intercepts/mu, so this is a mathematical identity, not just a numerical coincidence, and
 * serves as a sanity check on the hand computation itself).
 */
public class RemoteWorkFrequencyModelTest {

    private static final double PHI = 0.6;
    private static final double MU = 0.3;
    private static final double[] INTERCEPTS = {-1.0, -0.5, 0.0, 0.5, 1.0, 1.5};

    @Test
    public void drawTruncatedCategory_effectiveMaxZero_alwaysReturnsZero() {
        for (double phi : new double[]{0.01, 0.5, 0.99}) {
            for (double randomNumber : new double[]{0.0, 0.3, 0.7, 0.999}) {
                int result = RemoteWorkFrequencyModel.drawTruncatedCategory(phi, MU, INTERCEPTS, 0, randomNumber);
                Assert.assertEquals(0, result);
            }
        }
    }

    @Test
    public void drawTruncatedCategory_effectiveMaxSeven_matchesHandComputedBoundaries() {
        // untruncated (effectiveMax == the model's own maximum) - confirms the refactor didn't
        // change the original, uncapped behavior
        Assert.assertEquals(0, RemoteWorkFrequencyModel.drawTruncatedCategory(PHI, MU, INTERCEPTS, 7, 0.3));
        Assert.assertEquals(1, RemoteWorkFrequencyModel.drawTruncatedCategory(PHI, MU, INTERCEPTS, 7, 0.5));
        Assert.assertEquals(4, RemoteWorkFrequencyModel.drawTruncatedCategory(PHI, MU, INTERCEPTS, 7, 0.7));
        Assert.assertEquals(7, RemoteWorkFrequencyModel.drawTruncatedCategory(PHI, MU, INTERCEPTS, 7, 0.99));
    }

    @Test
    public void drawTruncatedCategory_partialTruncation_handComputedBoundaries() {
        // same distribution, truncated to effectiveMax=3; totalMassWithinCap = cumProbByCategory[3]
        // = 0.6553 (hand-computed) - the draw is rescaled against this, not against 1.0
        int effectiveMax = 3;
        Assert.assertEquals(0, RemoteWorkFrequencyModel.drawTruncatedCategory(PHI, MU, INTERCEPTS, effectiveMax, 0.5));
        Assert.assertEquals(1, RemoteWorkFrequencyModel.drawTruncatedCategory(PHI, MU, INTERCEPTS, effectiveMax, 0.75));
        Assert.assertEquals(2, RemoteWorkFrequencyModel.drawTruncatedCategory(PHI, MU, INTERCEPTS, effectiveMax, 0.85));
        Assert.assertEquals(3, RemoteWorkFrequencyModel.drawTruncatedCategory(PHI, MU, INTERCEPTS, effectiveMax, 0.92));
        // never exceeds the cap even as randomNumber approaches 1
        Assert.assertEquals(3, RemoteWorkFrequencyModel.drawTruncatedCategory(PHI, MU, INTERCEPTS, effectiveMax, 0.99));
    }

    @Test
    public void drawTruncatedCategory_partialTruncation_neverExceedsCapAndStepsMonotonically() {
        int effectiveMax = 3;
        int previous = -1;
        for (double randomNumber = 0.0; randomNumber < 1.0; randomNumber += 0.05) {
            int result = RemoteWorkFrequencyModel.drawTruncatedCategory(PHI, MU, INTERCEPTS, effectiveMax, randomNumber);
            Assert.assertTrue(result >= 0 && result <= effectiveMax);
            Assert.assertTrue(result >= previous);
            previous = result;
        }

        // extremes: randomNumber == 0 -> category 0; randomNumber just under 1 -> the cap itself
        Assert.assertEquals(0, RemoteWorkFrequencyModel.drawTruncatedCategory(PHI, MU, INTERCEPTS, effectiveMax, 0.0));
        Assert.assertEquals(effectiveMax, RemoteWorkFrequencyModel.drawTruncatedCategory(PHI, MU, INTERCEPTS, effectiveMax, 0.999999));
    }
}
