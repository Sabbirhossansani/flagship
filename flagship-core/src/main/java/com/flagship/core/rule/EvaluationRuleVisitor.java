package com.flagship.core.rule;

import com.flagship.core.model.EvaluationContext;
import com.flagship.core.model.SegmentDefinition;
import com.flagship.core.strategy.OperatorStrategyRegistry;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Concrete Visitor in the Composite + Visitor pattern that evaluates a {@link RuleNode} tree
 * against an {@link EvaluationContext} using the configured {@link OperatorStrategyRegistry} (FR2).
 */
public final class EvaluationRuleVisitor implements RuleVisitor<Boolean> {

    private final EvaluationContext context;
    private final OperatorStrategyRegistry operatorRegistry;
    private final Map<String, SegmentDefinition> segments;
    private Set<String> visitedSegments;

    public EvaluationRuleVisitor(
            EvaluationContext context,
            OperatorStrategyRegistry operatorRegistry,
            Map<String, SegmentDefinition> segments
    ) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.operatorRegistry = Objects.requireNonNull(operatorRegistry, "operatorRegistry must not be null");
        this.segments = segments != null ? segments : Map.of();
    }

    @Override
    public Boolean visitComposite(CompositeRuleNode node) {
        List<RuleNode> children = node.children();
        if (children.isEmpty()) {
            return Boolean.FALSE;
        }
        if (node.logicalOperator() == LogicalOperator.AND) {
            for (int i = 0, size = children.size(); i < size; i++) {
                if (!Boolean.TRUE.equals(children.get(i).accept(this))) {
                    return Boolean.FALSE;
                }
            }
            return Boolean.TRUE;
        } else {
            for (int i = 0, size = children.size(); i < size; i++) {
                if (Boolean.TRUE.equals(children.get(i).accept(this))) {
                    return Boolean.TRUE;
                }
            }
            return Boolean.FALSE;
        }
    }

    @Override
    public Boolean visitLeaf(LeafRuleNode node) {
        Object actualValue = context.getAttribute(node.attribute());
        return operatorRegistry.evaluate(node.operator(), actualValue, node.values());
    }

    @Override
    public Boolean visitSegmentRef(SegmentReferenceNode node) {
        return matchesSegment(node.segmentKey());
    }

    public boolean matchesSegment(String segmentKey) {
        if (segmentKey == null) {
            return false;
        }
        SegmentDefinition segment = segments.get(segmentKey);
        if (segment == null) {
            return false;
        }
        String userKey = context.getUserKey();
        if (userKey != null && !userKey.isEmpty()) {
            if (segment.excludedUsers().contains(userKey)) {
                return false;
            }
            if (segment.includedUsers().contains(userKey)) {
                return true;
            }
        }
        RuleNode condition = segment.condition();
        if (condition == null) {
            return false;
        }
        if (visitedSegments == null) {
            visitedSegments = new HashSet<>(4);
        }
        if (!visitedSegments.add(segmentKey)) {
            // Break circular segment reference safely
            return false;
        }
        try {
            return Boolean.TRUE.equals(condition.accept(this));
        } finally {
            visitedSegments.remove(segmentKey);
        }
    }
}
