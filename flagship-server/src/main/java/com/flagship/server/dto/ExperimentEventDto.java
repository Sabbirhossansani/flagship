package com.flagship.server.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record ExperimentEventDto(
        @NotBlank String eventType, // EXPOSURE or GOAL
        @NotBlank String flagKey,
        String variationKey,
        String metricName,
        @NotBlank String userKey,
        Double value,
        Instant timestamp
) {
}
