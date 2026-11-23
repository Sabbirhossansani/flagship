package com.flagship.core.model;

import java.util.Objects;

/**
 * Named variation for a feature flag (FR1).
 */
public record Variation(
        String key,
        VariationType type,
        Object value,
        String description
) {
    public Variation {
        Objects.requireNonNull(key, "variation key must not be null");
        Objects.requireNonNull(type, "variation type must not be null");
    }

    public static Variation ofBoolean(String key, boolean value) {
        return new Variation(key, VariationType.BOOLEAN, value, null);
    }

    public static Variation ofString(String key, String value) {
        return new Variation(key, VariationType.STRING, value, null);
    }

    public static Variation ofNumber(String key, Number value) {
        return new Variation(key, VariationType.NUMBER, value, null);
    }

    public static Variation ofJson(String key, String jsonValue) {
        return new Variation(key, VariationType.JSON, jsonValue, null);
    }

    public boolean asBoolean(boolean fallback) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return fallback;
    }

    public String asString(String fallback) {
        return value != null ? String.valueOf(value) : fallback;
    }

    public double asDouble(double fallback) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public long asLong(long fallback) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
