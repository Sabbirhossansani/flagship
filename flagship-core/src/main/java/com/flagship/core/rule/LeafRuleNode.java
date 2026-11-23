package com.flagship.core.rule;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Leaf node in the Composite + Visitor rule tree pattern.
 * Evaluates a single attribute predicate using a {@link RuleOperatorType} Strategy.
 */
public record LeafRuleNode(
        String attribute,
        RuleOperatorType operator,
        List<String> values
) implements RuleNode {

    public LeafRuleNode {
        Objects.requireNonNull(attribute, "attribute must not be null");
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(values, "values must not be null");
        values = List.copyOf(values);
    }

    public static LeafRuleNode of(String attribute, RuleOperatorType operator, String... values) {
        return new LeafRuleNode(attribute, operator, Arrays.asList(values));
    }

    public static LeafRuleNode of(String attribute, RuleOperatorType operator, List<String> values) {
        return new LeafRuleNode(attribute, operator, values);
    }

    @Override
    public <R> R accept(RuleVisitor<R> visitor) {
        return visitor.visitLeaf(this);
    }
}
