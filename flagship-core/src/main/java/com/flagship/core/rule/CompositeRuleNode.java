package com.flagship.core.rule;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Composite node in the Composite + Visitor rule tree pattern.
 * Combines child {@link RuleNode} elements using {@link LogicalOperator#AND} or {@link LogicalOperator#OR}.
 */
public record CompositeRuleNode(
        LogicalOperator logicalOperator,
        List<RuleNode> children
) implements RuleNode {

    public CompositeRuleNode {
        Objects.requireNonNull(logicalOperator, "logicalOperator must not be null");
        Objects.requireNonNull(children, "children must not be null");
        children = List.copyOf(children);
    }

    public static CompositeRuleNode and(RuleNode... children) {
        return new CompositeRuleNode(LogicalOperator.AND, Arrays.asList(children));
    }

    public static CompositeRuleNode and(List<RuleNode> children) {
        return new CompositeRuleNode(LogicalOperator.AND, children);
    }

    public static CompositeRuleNode or(RuleNode... children) {
        return new CompositeRuleNode(LogicalOperator.OR, Arrays.asList(children));
    }

    public static CompositeRuleNode or(List<RuleNode> children) {
        return new CompositeRuleNode(LogicalOperator.OR, children);
    }

    @Override
    public <R> R accept(RuleVisitor<R> visitor) {
        return visitor.visitComposite(this);
    }
}
