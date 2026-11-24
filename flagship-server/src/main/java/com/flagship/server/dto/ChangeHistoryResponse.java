package com.flagship.server.dto;

import java.time.Instant;

public record ChangeHistoryResponse(
        Long id,
        String projectKey,
        String envKey,
        String entityType,
        String entityKey,
        Long version,
        String author,
        String reason,
        String action,
        String beforeJson,
        String afterJson,
        String diffSummary,
        Instant createdAt
) {
}
