package com.flagship.core.strategy;

import com.flagship.core.rule.RuleOperatorType;

import java.util.Collection;
import java.util.List;

/**
 * Strategy implementation for {@link RuleOperatorType#CONTAINS} (FR2).
 */
public final class ContainsOperator implements RuleOperatorStrategy {

    @Override
    public RuleOperatorType supportedType() {
        return RuleOperatorType.CONTAINS;
    }

    @Override
    public boolean evaluate(Object actualValue, List<String> expectedValues) {
        if (actualValue == null || expectedValues == null || expectedValues.isEmpty()) {
            return false;
        }
        String needle = expectedValues.get(0);
        if (needle == null) {
            return false;
        }
        if (actualValue instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null && needle.equals(String.valueOf(item))) {
                    return true;
                }
            }
            return false;
        }
        return String.valueOf(actualValue).contains(needle);
    }
}
