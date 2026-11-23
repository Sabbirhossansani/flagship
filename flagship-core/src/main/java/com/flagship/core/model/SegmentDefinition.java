package com.flagship.core.model;

import com.flagship.core.rule.RuleNode;

import java.util.Objects;
import java.util.Set;

/**
 * Reusable audience segment definition (FR2, FR10).
 */
public record SegmentDefinition(
        String key,
        String name,
        String description,
        RuleNode condition,
        Set<String> includedUsers,
        Set<String> excludedUsers,
        long version
) {
    public SegmentDefinition {
        Objects.requireNonNull(key, "segment key must not be null");
        includedUsers = includedUsers != null ? Set.copyOf(includedUsers) : Set.of();
        excludedUsers = excludedUsers != null ? Set.copyOf(excludedUsers) : Set.of();
    }

    public static SegmentDefinition of(String key, String name, RuleNode condition) {
        return new SegmentDefinition(key, name, null, condition, Set.of(), Set.of(), 1L);
    }
}
