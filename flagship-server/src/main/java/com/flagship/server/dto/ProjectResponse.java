package com.flagship.server.dto;

import java.time.Instant;
import java.util.List;

public record ProjectResponse(
        String key,
        String name,
        String description,
        List<EnvironmentResponse> environments,
        Instant createdAt,
        Instant updatedAt
) {
}
