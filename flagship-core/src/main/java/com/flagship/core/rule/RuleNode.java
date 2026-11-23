package com.flagship.core.rule;

/**
 * Component interface in the Composite + Visitor pattern for nested rule trees (FR2).
 */
public sealed interface RuleNode permits CompositeRuleNode, LeafRuleNode, SegmentReferenceNode {

    /**
     * Accepts a {@link RuleVisitor} to traverse or evaluate this node.
     *
     * @param visitor the rule visitor
     * @param <R>     the return type of the visitor operation
     * @return the result produced by the visitor
     */
    <R> R accept(RuleVisitor<R> visitor);
}
