package com.flagship.core.model;

import java.util.List;
import java.util.Objects;

/**
 * Represents a variation allocation in a percentage rollout or A/B/n split (FR2, FR3, FR8).
 * Weights are represented in buckets out of 100,000 (where 100,000 = 100.000% and 1,000 = 1.000%).
 */
public record WeightedVariation(
        String variationKey,
        int weightBuckets
) {
    public static final int MAX_BUCKETS = 100_000;

    public WeightedVariation {
        Objects.requireNonNull(variationKey, "variationKey must not be null");
        if (weightBuckets < 0 || weightBuckets > MAX_BUCKETS) {
            throw new IllegalArgumentException("weightBuckets must be between 0 and " + MAX_BUCKETS);
        }
    }

    public static WeightedVariation ofPercentage(String variationKey, double percentage) {
        if (percentage < 0.0 || percentage > 100.0) {
            throw new IllegalArgumentException("percentage must be between 0.0 and 100.0");
        }
        int buckets = (int) Math.round(percentage * 1000.0);
        return new WeightedVariation(variationKey, buckets);
    }

    public static void validateTotalWeight(List<WeightedVariation> distributions) {
        if (distributions == null || distributions.isEmpty()) {
            return;
        }
        long total = 0;
        for (WeightedVariation item : distributions) {
            Objects.requireNonNull(item, "WeightedVariation item must not be null");
            total += item.weightBuckets();
        }
        if (total > MAX_BUCKETS) {
            throw new IllegalArgumentException(
                    "Total rollout weightBuckets (" + total + ") must not exceed " + MAX_BUCKETS);
        }
    }

    public double percentage() {
        return weightBuckets / 1000.0;
    }
}
