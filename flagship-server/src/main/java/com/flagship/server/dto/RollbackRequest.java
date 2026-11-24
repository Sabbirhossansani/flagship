package com.flagship.server.dto;

import jakarta.validation.constraints.NotNull;

public record RollbackRequest(
        @NotNull Long targetVersion,
        String reason
) {
}
