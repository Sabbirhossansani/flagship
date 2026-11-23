package com.flagship.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
        @NotBlank(message = "Project name must not be blank")
        @Size(max = 255)
        String name,

        @Size(max = 2000)
        String description
) {
}
