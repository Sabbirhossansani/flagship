package com.flagship.core.rule;

import com.flagship.core.strategy.DateOperator;
import com.flagship.core.strategy.RegexOperator;
import com.flagship.core.strategy.SemanticVersion;

import java.util.ArrayList;
import java.util.List;

/**
 * Concrete Visitor in the Composite + Visitor pattern that validates a {@link RuleNode} tree
 * and returns a list of structural or semantic validation errors.
 */
public final class RuleValidationVisitor implements RuleVisitor<List<String>> {

    @Override
    public List<String> visitComposite(CompositeRuleNode node) {
        List<String> errors = new ArrayList<>();
        if (node.children().isEmpty()) {
            errors.add("CompositeRuleNode (" + node.logicalOperator() + ") must have at least one child node");
        }
        for (RuleNode child : node.children()) {
            if (child == null) {
                errors.add("CompositeRuleNode contains a null child node");
            } else {
                errors.addAll(child.accept(this));
            }
        }
        return errors;
    }

    @Override
    public List<String> visitLeaf(LeafRuleNode node) {
        List<String> errors = new ArrayList<>();
        if (node.attribute() == null || node.attribute().isBlank()) {
            errors.add("LeafRuleNode attribute must not be blank");
        }
        if (node.values() == null || node.values().isEmpty()) {
            errors.add("LeafRuleNode for attribute '" + node.attribute() + "' must specify at least one expected value");
            return errors;
        }
        String firstValue = node.values().get(0);
        switch (node.operator()) {
            case SEMVER_GT, SEMVER_LT, SEMVER_EQ -> {
                if (SemanticVersion.tryParse(firstValue) == null) {
                    errors.add("Invalid semantic version '" + firstValue + "' for operator " + node.operator());
                }
            }
            case REGEX -> {
                if (RegexOperator.compileCached(firstValue) == null) {
                    errors.add("Invalid regular expression '" + firstValue + "' for operator REGEX");
                }
            }
            case DATE_BEFORE, DATE_AFTER -> {
                if (DateOperator.parseInstantCached(firstValue) == null) {
                    errors.add("Invalid ISO-8601 date/instant '" + firstValue + "' for operator " + node.operator());
                }
            }
            default -> {
                // EQUALS, IN, CONTAINS allow any non-null string representation
            }
        }
        return errors;
    }

    @Override
    public List<String> visitSegmentRef(SegmentReferenceNode node) {
        List<String> errors = new ArrayList<>();
        if (node.segmentKey() == null || node.segmentKey().isBlank()) {
            errors.add("SegmentReferenceNode segmentKey must not be blank");
        }
        return errors;
    }
}
