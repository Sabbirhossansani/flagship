package com.flagship.server.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateChangeRequestDto(
        @NotBlank String entityType,
        @NotBlank String entityKey,
        @NotBlank String action,
        @NotBlank String payloadJson,
        @NotBlank String reason
) {
}
