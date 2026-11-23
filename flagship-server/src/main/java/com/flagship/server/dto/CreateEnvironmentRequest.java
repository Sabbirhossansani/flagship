package com.flagship.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateEnvironmentRequest(
        @NotBlank(message = "Environment key must not be blank")
        @Pattern(regexp = "^[a-z0-9_-]{2,100}$", message = "Environment key must be 2-100 lowercase alphanumeric, hyphen, or underscore characters")
        String key,

        @NotBlank(message = "Environment name must not be blank")
        @Size(max = 255)
        String name,

        boolean requiresApproval
) {
}
