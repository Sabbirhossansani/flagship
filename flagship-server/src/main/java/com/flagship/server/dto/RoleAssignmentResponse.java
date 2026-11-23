package com.flagship.server.dto;

import com.flagship.core.model.ProjectRole;

import java.time.Instant;

public record RoleAssignmentResponse(
        String projectKey,
        String username,
        ProjectRole role,
        Instant createdAt,
        Instant updatedAt
) {
}
