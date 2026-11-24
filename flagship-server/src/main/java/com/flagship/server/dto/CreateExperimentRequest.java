package com.flagship.server.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateExperimentRequest(
        @NotBlank String key,
        @NotBlank String name,
        @NotBlank String flagKey,
        @NotBlank String metricName,
        @NotBlank String baselineVariationKey
) {
}
