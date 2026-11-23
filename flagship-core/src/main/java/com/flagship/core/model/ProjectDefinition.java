package com.flagship.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Top-level project domain model containing environments and feature flags (FR1).
 */
public record ProjectDefinition(
        String key,
        String name,
        String description,
        List<EnvironmentDefinition> environments,
        Instant createdAt
) {
    public ProjectDefinition {
        Objects.requireNonNull(key, "project key must not be null");
        if (key.isBlank()) {
            throw new IllegalArgumentException("project key must not be blank");
        }
        name = (name != null && !name.isBlank()) ? name : key;
        environments = (environments != null && !environments.isEmpty())
                ? List.copyOf(environments)
                : EnvironmentDefinition.defaultEnvironments();
        createdAt = createdAt != null ? createdAt : Instant.EPOCH;
    }

    public static ProjectDefinition of(String key, String name, String description) {
        return new ProjectDefinition(key, name, description, EnvironmentDefinition.defaultEnvironments(), Instant.now());
    }
}
