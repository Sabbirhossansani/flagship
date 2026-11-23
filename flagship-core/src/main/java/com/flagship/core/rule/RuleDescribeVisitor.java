package com.flagship.core.rule;

import java.util.stream.Collectors;

/**
 * Concrete Visitor in the Composite + Visitor pattern that renders a {@link RuleNode} tree
 * into a human-readable boolean expression for diffs and audit history (FR7).
 */
public final class RuleDescribeVisitor implements RuleVisitor<String> {

    @Override
    public String visitComposite(CompositeRuleNode node) {
        String delimiter = " " + node.logicalOperator().name() + " ";
        return node.children().stream()
                .map(child -> child.accept(this))
                .collect(Collectors.joining(delimiter, "(", ")"));
    }

    @Override
    public String visitLeaf(LeafRuleNode node) {
        return "(" + node.attribute() + " " + node.operator().name() + " " + node.values() + ")";
    }

    @Override
    public String visitSegmentRef(SegmentReferenceNode node) {
        return "(SEGMENT[" + node.segmentKey() + "])";
    }
}
