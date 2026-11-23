package com.flagship.core.strategy;

import java.nio.charset.StandardCharsets;

/**
 * Deterministic MurmurHash3 (x86_32) bucketing strategy (FR3, QA4).
 * Hashes {@code flagKey + ":" + userKey} and maps uniformly into {@code [0, 99999]}.
 * Guarantees sticky gradual rollouts (ramping 10% -> 20% preserves 100% of the initial 10% cohort).
 */
public final class Murmur3BucketingStrategy implements BucketingStrategy {

    private static final int C1 = 0xcc9e2d51;
    private static final int C2 = 0x1b873593;
    private static final int DEFAULT_SEED = 0;

    private final int seed;

    public Murmur3BucketingStrategy() {
        this(DEFAULT_SEED);
    }

    public Murmur3BucketingStrategy(int seed) {
        this.seed = seed;
    }

    @Override
    public int computeBucket(String flagKey, String userKey) {
        if (flagKey == null || flagKey.isEmpty() || userKey == null || userKey.isEmpty()) {
            return -1;
        }
        String compositeKey = flagKey + ":" + userKey;
        byte[] bytes = compositeKey.getBytes(StandardCharsets.UTF_8);
        int hash = murmur3_32(bytes, seed);
        return (int) (Integer.toUnsignedLong(hash) % TOTAL_BUCKETS);
    }

    /**
     * Standard MurmurHash3 32-bit x86 algorithm.
     */
    public static int murmur3_32(byte[] data, int seed) {
        int length = data.length;
        int h1 = seed;
        int roundedEnd = length & 0xfffffffc; // round down to 4-byte block

        for (int i = 0; i < roundedEnd; i += 4) {
            int k1 = (data[i] & 0xff)
                    | ((data[i + 1] & 0xff) << 8)
                    | ((data[i + 2] & 0xff) << 16)
                    | (data[i + 3] << 24);
            k1 *= C1;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= C2;

            h1 ^= k1;
            h1 = Integer.rotateLeft(h1, 13);
            h1 = h1 * 5 + 0xe6546b64;
        }

        int k1 = 0;
        int tail = length & 0x03;
        if (tail == 3) {
            k1 ^= (data[roundedEnd + 2] & 0xff) << 16;
        }
        if (tail >= 2) {
            k1 ^= (data[roundedEnd + 1] & 0xff) << 8;
        }
        if (tail >= 1) {
            k1 ^= (data[roundedEnd] & 0xff);
            k1 *= C1;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= C2;
            h1 ^= k1;
        }

        h1 ^= length;
        h1 ^= (h1 >>> 16);
        h1 *= 0x85ebca6b;
        h1 ^= (h1 >>> 13);
        h1 *= 0xc2b2ae35;
        h1 ^= (h1 >>> 16);
        return h1;
    }
}
