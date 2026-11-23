package com.flagship.core.model;

import com.flagship.core.rule.RuleNode;

import java.util.List;
import java.util.Objects;

/**
 * A targeting rule attached to a feature flag (FR2).
 * Can reference a named segment, evaluate a Composite {@link RuleNode} tree, or both,
 * and either serve a fixed variation or a percentage rollout distribution across variations.
 */
public record TargetingRule(
        String id,
        String name,
        String segmentKey,
        RuleNode condition,
        String variationKey,
        List<WeightedVariation> rolloutDistributions
) {
    public TargetingRule {
        Objects.requireNonNull(id, "rule id must not be null");
        rolloutDistributions = rolloutDistributions != null ? List.copyOf(rolloutDistributions) : List.of();
        WeightedVariation.validateTotalWeight(rolloutDistributions);
    }

    public static TargetingRule ofVariation(String id, String name, RuleNode condition, String variationKey) {
        return new TargetingRule(id, name, null, condition, variationKey, List.of());
    }

    public static TargetingRule ofSegment(String id, String name, String segmentKey, String variationKey) {
        return new TargetingRule(id, name, segmentKey, null, variationKey, List.of());
    }

    public static TargetingRule ofRollout(
            String id,
            String name,
            RuleNode condition,
            List<WeightedVariation> rolloutDistributions
    ) {
        return new TargetingRule(id, name, null, condition, null, rolloutDistributions);
    }
}
