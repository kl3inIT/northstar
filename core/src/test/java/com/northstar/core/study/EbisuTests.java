package com.northstar.core.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * Pins the vendored Ebisu port to analytically-known reference values, so the
 * Lanczos-gammaln and bisection substitutions cannot silently drift from the
 * upstream implementation.
 */
class EbisuTests {

    @Test
    void predictRecallAtHalflifeIsBetaMean() {
        // dt = 1 → E[p] = a / (a + b) exactly.
        Ebisu.Model model = new Ebisu.Model(7, 3, 3);
        assertThat(Ebisu.predictRecall(model, 7)).isCloseTo(0.5, within(1e-9));

        Ebisu.Model skewed = new Ebisu.Model(24, 4, 2);
        assertThat(Ebisu.predictRecall(skewed, 24)).isCloseTo(4.0 / 6.0, within(1e-9));
    }

    @Test
    void predictRecallAtTwiceHalflifeIsSecondMoment() {
        // dt = 2 → E[p^2] = a(a+1) / ((a+b)(a+b+1)) = 12/42 for a=b=3.
        Ebisu.Model model = new Ebisu.Model(7, 3, 3);
        assertThat(Ebisu.predictRecall(model, 14)).isCloseTo(12.0 / 42.0, within(1e-9));
    }

    @Test
    void updateAtExactlyHalflifeIsConjugate() {
        // dt = 1 makes the Beta prior conjugate: success → Beta(a+1, b), failure → Beta(a, b+1).
        Ebisu.Model model = new Ebisu.Model(7, 3, 3);

        Ebisu.Model afterSuccess = Ebisu.updateRecall(model, 1, 1, 7);
        assertThat(afterSuccess.alpha()).isCloseTo(4, within(1e-6));
        assertThat(afterSuccess.beta()).isCloseTo(3, within(1e-6));
        assertThat(afterSuccess.time()).isCloseTo(7, within(1e-9));

        Ebisu.Model afterFailure = Ebisu.updateRecall(model, 0, 1, 7);
        assertThat(afterFailure.alpha()).isCloseTo(3, within(1e-6));
        assertThat(afterFailure.beta()).isCloseTo(4, within(1e-6));
    }

    @Test
    void successGrowsHalflifeAndFailureShrinksIt() {
        Ebisu.Model model = new Ebisu.Model(24, 3, 3);
        double before = Ebisu.modelToPercentileDecay(model);

        double afterSuccess = Ebisu.modelToPercentileDecay(Ebisu.updateRecall(model, 1, 1, 48));
        double afterFailure = Ebisu.modelToPercentileDecay(Ebisu.updateRecall(model, 0, 1, 2));

        assertThat(afterSuccess).isGreaterThan(before);
        assertThat(afterFailure).isLessThan(before);
    }

    @Test
    void halflifeOfBalancedModelIsItsTime() {
        // a = b → recall at t is exactly 0.5, so the decay search must return t itself.
        Ebisu.Model model = new Ebisu.Model(7, 3, 3);
        assertThat(Ebisu.modelToPercentileDecay(model)).isCloseTo(7, within(1e-3));
    }

    @Test
    void recallDecaysMonotonically() {
        Ebisu.Model model = new Ebisu.Model(24, 3, 3);
        double previous = 1.0;
        for (double tnow = 1; tnow <= 24 * 30; tnow *= 2) {
            double recall = Ebisu.predictRecall(model, tnow);
            assertThat(recall).isLessThan(previous).isBetween(0.0, 1.0);
            previous = recall;
        }
    }

    @Test
    void gammalnMatchesKnownValues() {
        assertThat(Ebisu.gammaln(1)).isCloseTo(0, within(1e-10));
        assertThat(Ebisu.gammaln(5)).isCloseTo(Math.log(24), within(1e-10));
        assertThat(Ebisu.gammaln(0.5)).isCloseTo(0.5 * Math.log(Math.PI), within(1e-10));
    }

    @Test
    void repeatedLapsesStayNumericallyStable() {
        // The rebalance path must keep producing valid models under a hostile
        // pattern (long gaps, alternating results) — the backlog-catch-up story.
        Ebisu.Model model = new Ebisu.Model(24, 3, 3);
        for (int i = 0; i < 30; i++) {
            double gap = (i % 2 == 0) ? model.time() * 8 : model.time() / 8;
            model = Ebisu.updateRecall(model, i % 3 == 0 ? 0 : 1, 1, gap);
            assertThat(model.alpha()).isFinite().isPositive();
            assertThat(model.beta()).isFinite().isPositive();
            assertThat(model.time()).isFinite().isPositive();
        }
    }
}
