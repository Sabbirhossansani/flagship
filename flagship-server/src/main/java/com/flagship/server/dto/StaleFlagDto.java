package com.flagship.server.dto;

import java.time.Instant;

public record StaleFlagDto(
        String projectKey,
        String envKey,
        String flagKey,
        String name,
        String owner,
        String staleReason,
        Long daysInactive,
        Long daysFullyRolledOut,
        Instant lastEvaluatedAt,
        Instant fullRolloutSince,
        String suggestedAction
) {
}
