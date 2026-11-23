package com.flagship.core.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable point-in-time snapshot of all flags and segments for a project environment (FR5, FR6).
 * Shared between server preview and starter in-memory/disk cache.
 */
public record EnvironmentSnapshot(
        String projectKey,
        String environmentKey,
        long revision,
        Instant generatedAt,
        Map<String, FlagDefinition> flags,
        Map<String, SegmentDefinition> segments
) {
    public EnvironmentSnapshot {
        Objects.requireNonNull(projectKey, "projectKey must not be null");
        Objects.requireNonNull(environmentKey, "environmentKey must not be null");
        generatedAt = generatedAt != null ? generatedAt : Instant.now();
        flags = flags != null ? Map.copyOf(flags) : Map.of();
        segments = segments != null ? Map.copyOf(segments) : Map.of();
    }

    public static EnvironmentSnapshot empty(String projectKey, String environmentKey) {
        return new EnvironmentSnapshot(projectKey, environmentKey, 0L, Instant.EPOCH, Map.of(), Map.of());
    }

    public FlagDefinition getFlag(String flagKey) {
        return flagKey != null ? flags.get(flagKey) : null;
    }

    public SegmentDefinition getSegment(String segmentKey) {
        return segmentKey != null ? segments.get(segmentKey) : null;
    }
}
