package com.flagship.server.dto;

import java.time.Instant;

public record EnvironmentResponse(
        String key,
        String name,
        boolean requiresApproval,
        long revision,
        Instant createdAt,
        Instant updatedAt
) {
}
