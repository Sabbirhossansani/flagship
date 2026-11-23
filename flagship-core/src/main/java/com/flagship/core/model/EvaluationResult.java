package com.flagship.core.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Result of evaluating a feature flag against an {@link EvaluationContext} (FR1, FR2, FR3).
 */
public record EvaluationResult(
        String flagKey,
        String variationKey,
        VariationType type,
        Object value,
        EvaluationReason reason,
        String matchedRuleId,
        int bucket,
        long flagVersion,
        Instant evaluatedAt
) {
    public static final int NO_BUCKET = -1;

    public EvaluationResult {
        Objects.requireNonNull(flagKey, "flagKey must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
    }

    public static EvaluationResult fromVariation(
            FlagDefinition flag,
            Variation variation,
            EvaluationReason reason,
            String matchedRuleId,
            int bucket,
            Instant evaluatedAt
    ) {
        if (variation == null) {
            return new EvaluationResult(
                    flag.getKey(),
                    null,
                    flag.getType(),
                    null,
                    EvaluationReason.ERROR,
                    matchedRuleId,
                    bucket,
                    flag.getVersion(),
                    evaluatedAt
            );
        }
        return new EvaluationResult(
                flag.getKey(),
                variation.key(),
                variation.type(),
                variation.value(),
                reason,
                matchedRuleId,
                bucket,
                flag.getVersion(),
                evaluatedAt
        );
    }

    public static EvaluationResult notFound(String flagKey, VariationType expectedType, Object defaultValue, Instant evaluatedAt) {
        return new EvaluationResult(
                flagKey,
                null,
                expectedType,
                defaultValue,
                EvaluationReason.FLAG_NOT_FOUND,
                null,
                NO_BUCKET,
                0L,
                evaluatedAt
        );
    }

    public static EvaluationResult typeMismatch(
            FlagDefinition flag,
            VariationType expectedType,
            Object defaultValue,
            Instant evaluatedAt
    ) {
        return new EvaluationResult(
                flag.getKey(),
                null,
                expectedType,
                defaultValue,
                EvaluationReason.TYPE_MISMATCH,
                null,
                NO_BUCKET,
                flag.getVersion(),
                evaluatedAt
        );
    }

    public boolean asBoolean(boolean fallback) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            if ("true".equalsIgnoreCase(s)) return true;
            if ("false".equalsIgnoreCase(s)) return false;
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

    public String asJsonString(String fallback) {
        return value != null ? String.valueOf(value) : fallback;
    }
}
