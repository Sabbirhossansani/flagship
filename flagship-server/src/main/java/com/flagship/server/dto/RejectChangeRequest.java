package com.flagship.server.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectChangeRequest(
        @NotBlank String reason
) {
}
