package com.flagship.server.dto;

import com.flagship.server.domain.ChangeRequestStatus;
import java.time.Instant;

public record ChangeRequestResponse(
        Long id,
        String projectKey,
        String envKey,
        String entityType,
        String entityKey,
        String action,
        String payloadJson,
        String author,
        String reason,
        ChangeRequestStatus status,
        String approvedBy,
        Instant approvedAt,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt
) {
}
