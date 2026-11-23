package com.flagship.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateSegmentRequest(
        @NotBlank(message = "Segment name must not be blank")
        @Size(max = 255)
        String name,

        @Size(max = 2000)
        String description,

        RuleNodeDto condition,
        Set<String> includedUsers,
        Set<String> excludedUsers
) {
}
