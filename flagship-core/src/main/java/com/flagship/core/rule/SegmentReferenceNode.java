package com.flagship.core.rule;

import java.util.Objects;

/**
 * Leaf node in the Composite + Visitor rule tree pattern that delegates to a named reusable segment.
 */
public record SegmentReferenceNode(
        String segmentKey
) implements RuleNode {

    public SegmentReferenceNode {
        Objects.requireNonNull(segmentKey, "segmentKey must not be null");
    }

    public static SegmentReferenceNode of(String segmentKey) {
        return new SegmentReferenceNode(segmentKey);
    }

    @Override
    public <R> R accept(RuleVisitor<R> visitor) {
        return visitor.visitSegmentRef(this);
    }
}
