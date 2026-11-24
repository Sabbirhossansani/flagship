package com.flagship.server.dto;

import java.time.Instant;

public record ScheduledChangeResponse(
        Long id,
        String projectKey,
        String envKey,
        String flagKey,
        String action,
        String payloadJson,
        String author,
        String reason,
        Instant scheduledTime,
        boolean executed,
        Instant executedAt,
        Instant createdAt
) {
}
