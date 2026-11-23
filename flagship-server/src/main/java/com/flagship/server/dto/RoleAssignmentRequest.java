package com.flagship.server.dto;

import com.flagship.core.model.ProjectRole;
import jakarta.validation.constraints.NotNull;

public record RoleAssignmentRequest(
        @NotNull(message = "ProjectRole is required (VIEWER, EDITOR, APPROVER, ADMIN)")
        ProjectRole role
) {
}
