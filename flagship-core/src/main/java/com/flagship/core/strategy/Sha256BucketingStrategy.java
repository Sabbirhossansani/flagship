package com.flagship.core.strategy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Cryptographic SHA-256 deterministic bucketing strategy (FR3).
 * Alternative {@link BucketingStrategy} mapping {@code flagKey + ":" + userKey} to {@code [0, 99999]}.
 */
public final class Sha256BucketingStrategy implements BucketingStrategy {

    @Override
    public int computeBucket(String flagKey, String userKey) {
        if (flagKey == null || flagKey.isEmpty() || userKey == null || userKey.isEmpty()) {
            return -1;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((flagKey + ":" + userKey).getBytes(StandardCharsets.UTF_8));
            int firstFourBytes = ByteBuffer.wrap(hash, 0, 4).getInt();
            return (int) (Integer.toUnsignedLong(firstFourBytes) % TOTAL_BUCKETS);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
