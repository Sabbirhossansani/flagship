package com.flagship.server.service;

import com.flagship.server.dto.VariationStatsDto;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExperimentStatisticsService {

    private static final double SRM_ALPHA = 0.01;
    private static final double Z_CRITICAL_95 = 1.959963984540054; // 95% two-sided z critical

    /**
     * Evaluates Sample Ratio Mismatch (SRM) using Chi-square Goodness-of-Fit test.
     */
    public SrmEvaluationResult evaluateSrm(
            List<String> variations,
            Map<String, Long> observedExposures,
            Map<String, Double> expectedWeights
    ) {
        if (variations == null || variations.size() < 2) {
            return new SrmEvaluationResult(0.0, 1.0, false, "Insufficient variations for SRM check (need >= 2)");
        }

        long totalObserved = 0;
        for (String varKey : variations) {
            totalObserved += observedExposures.getOrDefault(varKey, 0L);
        }

        if (totalObserved == 0) {
            return new SrmEvaluationResult(0.0, 1.0, false, "No exposures recorded yet");
        }

        double totalWeight = 0.0;
        for (String varKey : variations) {
            totalWeight += expectedWeights.getOrDefault(varKey, 1.0);
        }
        if (totalWeight <= 0.0) {
            totalWeight = variations.size();
        }

        double chiSquare = 0.0;
        for (String varKey : variations) {
            double weight = expectedWeights.getOrDefault(varKey, 1.0);
            double expected = totalObserved * (weight / totalWeight);
            if (expected > 0) {
                double observed = observedExposures.getOrDefault(varKey, 0L);
                double diff = observed - expected;
                chiSquare += (diff * diff) / expected;
            }
        }

        int degreesOfFreedom = variations.size() - 1;
        double pValue = chiSquarePValue(chiSquare, degreesOfFreedom);
        boolean hasSrm = pValue < SRM_ALPHA;
        String warning = hasSrm
                ? String.format("Sample Ratio Mismatch detected (chi2=%.3f, p=%.4f < %.2f). Traffic allocation may be corrupted.",
                chiSquare, pValue, SRM_ALPHA)
                : "No SRM detected. Observed sample distribution matches expected weights.";

        return new SrmEvaluationResult(chiSquare, pValue, hasSrm, warning);
    }

    /**
     * Computes conversion statistics and two-proportion z-test significance.
     */
    public Map<String, VariationStatsDto> computeVariationStats(
            List<String> variations,
            String baselineVariationKey,
            Map<String, Long> exposures,
            Map<String, Long> conversions
    ) {
        Map<String, VariationStatsDto> results = new HashMap<>();

        long baseExposures = exposures.getOrDefault(baselineVariationKey, 0L);
        long baseConversions = conversions.getOrDefault(baselineVariationKey, 0L);
        double baseRate = baseExposures > 0 ? (double) baseConversions / baseExposures : 0.0;

        for (String varKey : variations) {
            long n = exposures.getOrDefault(varKey, 0L);
            long x = conversions.getOrDefault(varKey, 0L);
            double rate = n > 0 ? (double) x / n : 0.0;

            if (varKey.equals(baselineVariationKey)) {
                results.put(varKey, new VariationStatsDto(
                        varKey,
                        n,
                        x,
                        rate,
                        0.0,
                        0.0,
                        1.0,
                        false,
                        0.0,
                        0.0
                ));
            } else {
                TwoProportionZTestResult zTest = computeTwoProportionZTest(
                        baseConversions,
                        baseExposures,
                        x,
                        n
                );
                double relativeUplift = baseRate > 0 ? ((rate - baseRate) / baseRate) * 100.0 : 0.0;

                results.put(varKey, new VariationStatsDto(
                        varKey,
                        n,
                        x,
                        rate,
                        relativeUplift,
                        zTest.zScore(),
                        zTest.pValue(),
                        zTest.statisticallySignificant(),
                        zTest.ciLower(),
                        zTest.ciUpper()
                ));
            }
        }

        return results;
    }

    /**
     * Two-proportion z-test comparing baseline (x1, n1) with variation (x2, n2).
     */
    public TwoProportionZTestResult computeTwoProportionZTest(
            long x1, long n1,
            long x2, long n2
    ) {
        if (n1 <= 0 || n2 <= 0) {
            return new TwoProportionZTestResult(0.0, 1.0, false, 0.0, 0.0);
        }

        double p1 = (double) x1 / n1;
        double p2 = (double) x2 / n2;

        double pooledP = (double) (x1 + x2) / (n1 + n2);
        double se = Math.sqrt(pooledP * (1.0 - pooledP) * ((1.0 / n1) + (1.0 / n2)));

        if (se == 0.0 || Double.isNaN(se)) {
            return new TwoProportionZTestResult(0.0, 1.0, false, 0.0, 0.0);
        }

        double z = (p2 - p1) / se;
        double pValue = 2.0 * (1.0 - standardNormalCdf(Math.abs(z)));
        boolean significant = pValue < 0.05;

        // Unpooled standard error for confidence interval of difference
        double seDiff = Math.sqrt((p1 * (1.0 - p1) / n1) + (p2 * (1.0 - p2) / n2));
        double ciLower = (p2 - p1) - (Z_CRITICAL_95 * seDiff);
        double ciUpper = (p2 - p1) + (Z_CRITICAL_95 * seDiff);

        return new TwoProportionZTestResult(z, pValue, significant, ciLower, ciUpper);
    }

    public static double standardNormalCdf(double z) {
        if (Double.isNaN(z)) return Double.NaN;
        if (z < -8.0) return 0.0;
        if (z > 8.0) return 1.0;

        double b1 = 0.319381530;
        double b2 = -0.356563782;
        double b3 = 1.781477937;
        double b4 = -1.821255978;
        double b5 = 1.330274429;
        double p = 0.2316419;
        double c = 0.3989422804014327; // 1 / sqrt(2 * PI)

        double x = Math.abs(z);
        double t = 1.0 / (1.0 + p * x);
        double poly = ((((b5 * t + b4) * t + b3) * t + b2) * t + b1) * t;
        double cdf = 1.0 - c * Math.exp(-0.5 * x * x) * poly;
        return z >= 0.0 ? cdf : 1.0 - cdf;
    }

    public static double chiSquarePValue(double chiSquare, int df) {
        if (chiSquare <= 0 || df <= 0) {
            return 1.0;
        }
        return regularizedGammaQ(df / 2.0, chiSquare / 2.0);
    }

    public static double regularizedGammaQ(double a, double x) {
        if (x < 0.0 || a <= 0.0) return 1.0;
        if (x == 0.0) return 1.0;

        if (x < a + 1.0) {
            return 1.0 - regularizedGammaPBySeries(a, x);
        } else {
            return regularizedGammaQByContinuedFraction(a, x);
        }
    }

    private static double regularizedGammaPBySeries(double a, double x) {
        double sum = 1.0 / a;
        double term = sum;
        for (int n = 1; n < 200; n++) {
            term *= x / (a + n);
            sum += term;
            if (term < sum * 1e-15) {
                break;
            }
        }
        double gln = logGamma(a);
        return sum * Math.exp(-x + a * Math.log(x) - gln);
    }

    private static double regularizedGammaQByContinuedFraction(double a, double x) {
        double gln = logGamma(a);
        double b = x + 1.0 - a;
        double c = 1.0 / 1e-30;
        double d = 1.0 / b;
        double h = d;

        for (int i = 1; i <= 200; i++) {
            double an = -i * (i - a);
            b += 2.0;
            d = an * d + b;
            if (Math.abs(d) < 1e-30) d = 1e-30;
            c = b + an / c;
            if (Math.abs(c) < 1e-30) c = 1e-30;
            d = 1.0 / d;
            double del = d * c;
            h *= del;
            if (Math.abs(del - 1.0) < 1e-15) {
                break;
            }
        }
        return Math.exp(-x + a * Math.log(x) - gln) * h;
    }

    public static double logGamma(double x) {
        if (x <= 0.0) return 0.0;
        double shift = 0.0;
        double current = x;
        while (current < 10.0) {
            shift += Math.log(current);
            current += 1.0;
        }
        double inv = 1.0 / (current * current);
        double series = (current - 0.5) * Math.log(current) - current + 0.5 * Math.log(2.0 * Math.PI)
                + (1.0 / (12.0 * current)) * (1.0 - inv * (1.0 / 30.0 - inv * (1.0 / 105.0)));
        return series - shift;
    }

    public record SrmEvaluationResult(
            double chiSquare,
            double pValue,
            boolean hasSrm,
            String warningMessage
    ) {
    }

    public record TwoProportionZTestResult(
            double zScore,
            double pValue,
            boolean statisticallySignificant,
            double ciLower,
            double ciUpper
    ) {
    }
}
