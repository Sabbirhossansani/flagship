package com.flagship.core.strategy;

/**
 * Strategy interface for deterministic user bucketing in gradual percentage rollouts (FR3).
 * Maps {@code (flagKey, userKey)} deterministically into the range {@code [0, 99999]}.
 */
public interface BucketingStrategy {

    int TOTAL_BUCKETS = 100_000;

    /**
     * Computes a deterministic bucket in {@code [0, 99999]} for {@code flagKey + ":" + userKey}.
     *
     * @param flagKey the feature flag key
     * @param userKey the user identifier
     * @return bucket index in {@code 0..99999}, or {@code -1} if {@code flagKey} or {@code userKey} is null/empty
     */
    int computeBucket(String flagKey, String userKey);
}
