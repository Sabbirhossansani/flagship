package com.flagship.core.model;

import java.util.List;
import java.util.Objects;

/**
 * Environment domain model within a FlagShip project (FR1, FR7).
 * Standard environments are {@code development}, {@code staging}, and {@code production}.
 */
public record EnvironmentDefinition(
        String key,
        String name,
        boolean requiresApproval
) {
    public static final String DEVELOPMENT = "development";
    public static final String STAGING = "staging";
    public static final String PRODUCTION = "production";

    public EnvironmentDefinition {
        Objects.requireNonNull(key, "environment key must not be null");
        if (key.isBlank()) {
            throw new IllegalArgumentException("environment key must not be blank");
        }
        name = (name != null && !name.isBlank()) ? name : key;
    }

    public static List<EnvironmentDefinition> defaultEnvironments() {
        return List.of(
                new EnvironmentDefinition(DEVELOPMENT, "Development", false),
                new EnvironmentDefinition(STAGING, "Staging", false),
                new EnvironmentDefinition(PRODUCTION, "Production", true)
        );
    }
}
