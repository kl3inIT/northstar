package com.northstar.core.study;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Ebisu v2 — Bayesian memory model. A card's recall probability is a Beta
 * distribution ({@code alpha}, {@code beta}) pinned at elapsed time
 * {@code time}; {@link #predictRecall} answers "how likely is recall right
 * now" at ANY moment (no due dates — the whole point for a pull-based review
 * flow), and {@link #updateRecall} folds a quiz result back in.
 *
 * <p>Vendored from the public-domain (Unlicense) reference implementation
 * <a href="https://github.com/fasiha/ebisu-java">fasiha/ebisu-java</a>, with
 * its two tiny dependencies replaced: log-gamma by the standard Lanczos
 * approximation, and the golden-section minimizer by a bisection root-find
 * (the objective is strictly monotonic in log-time, so bisection is exact
 * enough and dependency-free). Property tests pin the port to the reference
 * values from the Ebisu paper.
 */
final class Ebisu {

    /** The three-number memory model; time units are the caller's (we use hours). */
    record Model(double time, double alpha, double beta) {
    }

    private static final double LN_SQRT_2PI = 0.5 * Math.log(2 * Math.PI);
    private static final double[] LANCZOS = {
            0.99999999999980993, 676.5203681218851, -1259.1392167224028,
            771.32342877765313, -176.61502916214059, 12.507343278686905,
            -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7};

    private static final Map<Double, Double> LOGGAMMA_CACHE = new ConcurrentHashMap<>();

    private Ebisu() {
    }

    /** Lanczos log-gamma (g=7, n=9) — |error| well under 1e-10 over our domain. */
    static double gammaln(double x) {
        if (x < 0.5) {
            return Math.log(Math.PI / Math.sin(Math.PI * x)) - gammaln(1 - x);
        }
        double xm1 = x - 1;
        double a = LANCZOS[0];
        double t = xm1 + 7.5;
        for (int i = 1; i < LANCZOS.length; i++) {
            a += LANCZOS[i] / (xm1 + i);
        }
        return LN_SQRT_2PI + (xm1 + 0.5) * Math.log(t) - t + Math.log(a);
    }

    private static double logGammaCached(double x) {
        return LOGGAMMA_CACHE.computeIfAbsent(x, Ebisu::gammaln);
    }

    /** {@code log(Beta(a1, b) / Beta(a, b))} */
    private static double logBetaRatio(double a1, double a, double b) {
        return gammaln(a1) - gammaln(a1 + b) + logGammaCached(a + b) - logGammaCached(a);
    }

    /** {@code log(Beta(a, b))} */
    private static double logBeta(double a, double b) {
        return logGammaCached(a) + logGammaCached(b) - logGammaCached(a + b);
    }

    /** Probability of recall {@code tnow} time units after the model's last update. */
    static double predictRecall(Model prior, double tnow) {
        double dt = tnow / prior.time();
        return Math.exp(logBetaRatio(prior.alpha() + dt, prior.alpha(), prior.beta()));
    }

    /**
     * Posterior model after observing {@code successes} of {@code total}
     * (binary quiz: 0 or 1 of 1) at elapsed time {@code tnow}.
     */
    static Model updateRecall(Model prior, int successes, int total, double tnow) {
        return updateRecall(prior, successes, total, tnow, true, prior.time());
    }

    private static double logBinom(int n, int k) {
        return -logBeta(1.0 + n - k, 1.0 + k) - Math.log(n + 1.0);
    }

    private static Model updateRecall(Model prior, int successes, int total, double tnow,
            boolean rebalance, double tback) {
        double alpha = prior.alpha();
        double beta = prior.beta();
        double dt = tnow / prior.time();
        double et = tback / tnow;

        double[] binomlns = IntStream.range(0, total - successes + 1)
                .mapToDouble(i -> logBinom(total - successes, i)).toArray();
        double[] logs = IntStream.range(0, 3)
                .mapToDouble(m -> {
                    List<Double> a = IntStream.range(0, total - successes + 1)
                            .mapToDouble(i -> binomlns[i]
                                    + logBeta(beta, alpha + dt * (successes + i) + m * dt * et))
                            .boxed().collect(Collectors.toList());
                    List<Double> b = IntStream.range(0, total - successes + 1)
                            .mapToDouble(i -> Math.pow(-1.0, i))
                            .boxed().collect(Collectors.toList());
                    return logSumExp(a, b)[0];
                })
                .toArray();

        double mean = Math.exp(logs[1] - logs[0]);
        double m2 = Math.exp(logs[2] - logs[0]);
        double sig2 = m2 - Math.exp(2 * (logs[1] - logs[0]));

        if (mean <= 0 || m2 <= 0 || sig2 <= 0) {
            throw new IllegalStateException(
                    "Ebisu update produced invalid moments: mean=%g m2=%g sig2=%g (a=%g b=%g t=%g k=%d n=%d tnow=%g)"
                            .formatted(mean, m2, sig2, alpha, beta, prior.time(), successes, total,
                                    tnow));
        }
        double tmp = mean * (1 - mean) / sig2 - 1;
        Model proposed = new Model(tback, mean * tmp, (1 - mean) * tmp);
        return rebalance ? rebalance(prior, successes, total, tnow, proposed) : proposed;
    }

    /** Re-anchor a lopsided posterior near its half-life for numerical stability. */
    private static Model rebalance(Model prior, int successes, int total, double tnow,
            Model proposed) {
        if (proposed.alpha() > 2 * proposed.beta() || proposed.beta() > 2 * proposed.alpha()) {
            double roughHalflife = modelToPercentileDecay(proposed, 0.5, true, 1e-4);
            return updateRecall(prior, successes, total, tnow, false, roughHalflife);
        }
        return proposed;
    }

    /** Time at which {@link #predictRecall} would return 0.5 — the model's half-life. */
    static double modelToPercentileDecay(Model model) {
        return modelToPercentileDecay(model, 0.5, false, 1e-6);
    }

    private static double modelToPercentileDecay(Model model, double percentile, boolean coarse,
            double tolerance) {
        if (percentile <= 0 || percentile >= 1) {
            throw new IllegalArgumentException("percentile must be in (0, 1) exclusive");
        }
        double alpha = model.alpha();
        double beta = model.beta();
        double logBab = logBeta(alpha, beta);
        double logPercentile = Math.log(percentile);
        // Strictly decreasing in lndelta: recall drops as elapsed time grows.
        DoubleUnaryOperator f =
                lndelta -> (logBeta(alpha + Math.exp(lndelta), beta) - logBab) - logPercentile;

        double bracketWidth = coarse ? 1.0 : 6.0;
        double blow = -bracketWidth / 2.0;
        double bhigh = bracketWidth / 2.0;
        double flow = f.applyAsDouble(blow);
        double fhigh = f.applyAsDouble(bhigh);
        while (flow > 0 && fhigh > 0) {
            blow = bhigh;
            flow = fhigh;
            bhigh += bracketWidth;
            fhigh = f.applyAsDouble(bhigh);
        }
        while (flow < 0 && fhigh < 0) {
            bhigh = blow;
            fhigh = flow;
            blow -= bracketWidth;
            flow = f.applyAsDouble(blow);
        }
        if (!(flow > 0 && fhigh < 0)) {
            throw new IllegalStateException("failed to bracket the percentile-decay solution");
        }
        if (coarse) {
            return (Math.exp(blow) + Math.exp(bhigh)) / 2 * model.time();
        }
        while (bhigh - blow > tolerance) {
            double mid = (blow + bhigh) / 2;
            if (f.applyAsDouble(mid) > 0) {
                blow = mid;
            } else {
                bhigh = mid;
            }
        }
        return Math.exp((blow + bhigh) / 2) * model.time();
    }

    /** Stable {@code log(sum(b .* exp(a)))}; returns {absolute value, sign}. */
    private static double[] logSumExp(List<Double> a, List<Double> b) {
        double amax = a.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        double sum = IntStream.range(0, a.size())
                .mapToDouble(i -> Math.exp(a.get(i) - amax) * (i < b.size() ? b.get(i) : 1.0))
                .reduce(0.0, Double::sum);
        double sign = Math.signum(sum);
        return new double[] {Math.log(sum * sign) + amax, sign};
    }
}
