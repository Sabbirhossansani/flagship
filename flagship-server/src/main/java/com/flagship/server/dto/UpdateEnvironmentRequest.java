package com.flagship.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateEnvironmentRequest(
        @NotBlank(message = "Environment name must not be blank")
        @Size(max = 255)
        String name,

        boolean requiresApproval
) {
}
