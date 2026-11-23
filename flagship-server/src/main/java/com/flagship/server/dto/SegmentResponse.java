package com.flagship.server.dto;

import java.time.Instant;
import java.util.Set;

public record SegmentResponse(
        String projectKey,
        String environmentKey,
        String key,
        String name,
        String description,
        RuleNodeDto condition,
        String conditionDescription,
        Set<String> includedUsers,
        Set<String> excludedUsers,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
