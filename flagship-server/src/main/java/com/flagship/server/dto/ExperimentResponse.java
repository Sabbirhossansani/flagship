package com.flagship.server.dto;

import com.flagship.server.domain.ExperimentStatus;
import java.time.Instant;

public record ExperimentResponse(
        Long id,
        String projectKey,
        String envKey,
        String key,
        String name,
        String flagKey,
        String metricName,
        String baselineVariationKey,
        ExperimentStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
