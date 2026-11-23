package com.flagship.core.strategy;

import com.flagship.core.rule.RuleOperatorType;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Strategy implementation for {@link RuleOperatorType#SEMVER_GT},
 * {@link RuleOperatorType#SEMVER_LT}, and {@link RuleOperatorType#SEMVER_EQ} (FR2).
 */
public final class SemverOperator implements RuleOperatorStrategy {

    private static final int MAX_CACHE_SIZE = 2048;
    private static final Map<String, SemanticVersion> CACHE = new ConcurrentHashMap<>(128);

    private final RuleOperatorType operatorType;

    public SemverOperator(RuleOperatorType operatorType) {
        this.operatorType = Objects.requireNonNull(operatorType);
        if (operatorType != RuleOperatorType.SEMVER_GT
                && operatorType != RuleOperatorType.SEMVER_LT
                && operatorType != RuleOperatorType.SEMVER_EQ) {
            throw new IllegalArgumentException("Unsupported semver operator: " + operatorType);
        }
    }

    @Override
    public RuleOperatorType supportedType() {
        return operatorType;
    }

    @Override
    public boolean evaluate(Object actualValue, List<String> expectedValues) {
        if (actualValue == null || expectedValues == null || expectedValues.isEmpty()) {
            return false;
        }
        String expectedRaw = expectedValues.get(0);
        if (expectedRaw == null) {
            return false;
        }
        SemanticVersion actualVersion = parseCached(String.valueOf(actualValue));
        SemanticVersion expectedVersion = parseCached(expectedRaw);
        if (actualVersion == null || expectedVersion == null) {
            return false;
        }
        int cmp = actualVersion.compareTo(expectedVersion);
        return switch (operatorType) {
            case SEMVER_GT -> cmp > 0;
            case SEMVER_LT -> cmp < 0;
            case SEMVER_EQ -> cmp == 0;
            default -> false;
        };
    }

    private static SemanticVersion parseCached(String raw) {
        SemanticVersion cached = CACHE.get(raw);
        if (cached != null) {
            return cached;
        }
        SemanticVersion parsed = SemanticVersion.tryParse(raw);
        if (parsed != null && CACHE.size() < MAX_CACHE_SIZE) {
            CACHE.putIfAbsent(raw, parsed);
        }
        return parsed;
    }
}
