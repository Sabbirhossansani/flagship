package com.flagship.server.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateProjectRequest(
        @NotBlank(message = "Project key must not be blank")
        @Pattern(regexp = "^[a-z0-9_-]{2,100}$", message = "Project key must be 2-100 lowercase alphanumeric, hyphen, or underscore characters")
        String key,

        @NotBlank(message = "Project name must not be blank")
        @Size(max = 255)
        String name,

        @Size(max = 2000)
        String description,

        @Valid
        List<CreateEnvironmentRequest> environments
) {
    public CreateProjectRequest(String key, String name, String description) {
        this(key, name, description, List.of());
    }
}
