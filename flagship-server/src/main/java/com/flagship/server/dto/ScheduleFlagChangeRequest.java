package com.flagship.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ScheduleFlagChangeRequest(
        @NotNull Instant scheduledTime,
        @NotBlank String action,
        @NotBlank String payloadJson,
        @NotBlank String reason
) {
}
