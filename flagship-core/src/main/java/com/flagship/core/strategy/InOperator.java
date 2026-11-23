package com.flagship.core.strategy;

import com.flagship.core.rule.RuleOperatorType;

import java.util.Collection;
import java.util.List;

/**
 * Strategy implementation for {@link RuleOperatorType#IN} (FR2).
 */
public final class InOperator implements RuleOperatorStrategy {

    @Override
    public RuleOperatorType supportedType() {
        return RuleOperatorType.IN;
    }

    @Override
    public boolean evaluate(Object actualValue, List<String> expectedValues) {
        if (actualValue == null || expectedValues == null || expectedValues.isEmpty()) {
            return false;
        }
        if (actualValue instanceof Collection<?> collection) {
            for (Object element : collection) {
                if (element != null && matchesAny(element, expectedValues)) {
                    return true;
                }
            }
            return false;
        }
        return matchesAny(actualValue, expectedValues);
    }

    private static boolean matchesAny(Object candidate, List<String> expectedValues) {
        for (int i = 0, size = expectedValues.size(); i < size; i++) {
            if (EqualsOperator.matchesScalar(candidate, expectedValues.get(i))) {
                return true;
            }
        }
        return false;
    }
}
