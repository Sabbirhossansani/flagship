package com.flagship.core.rule;

/**
 * Visitor interface in the Composite + Visitor pattern for traversing {@link RuleNode} trees.
 *
 * @param <R> return type of the visitor operation (e.g., {@code Boolean} for evaluation,
 *            {@code List<String>} for validation, {@code String} for human-readable description).
 */
public interface RuleVisitor<R> {

    R visitComposite(CompositeRuleNode node);

    R visitLeaf(LeafRuleNode node);

    R visitSegmentRef(SegmentReferenceNode node);
}
