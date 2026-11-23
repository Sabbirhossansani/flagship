package com.flagship.core.strategy;

import com.flagship.core.rule.RuleOperatorType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Strategy implementation for {@link RuleOperatorType#EQUALS} (FR2).
 */
public final class EqualsOperator implements RuleOperatorStrategy {

    @Override
    public RuleOperatorType supportedType() {
        return RuleOperatorType.EQUALS;
    }

    @Override
    public boolean evaluate(Object actualValue, List<String> expectedValues) {
        if (actualValue == null || expectedValues == null || expectedValues.isEmpty()) {
            return false;
        }
        return matchesScalar(actualValue, expectedValues.get(0));
    }

    public static boolean matchesScalar(Object actualValue, String expected) {
        if (actualValue == null || expected == null) {
            return false;
        }
        if (actualValue instanceof String actualStr) {
            return actualStr.equals(expected);
        }
        if (actualValue instanceof Number actualNum) {
            try {
                BigDecimal left = new BigDecimal(actualNum.toString());
                BigDecimal right = new BigDecimal(expected.trim());
                return left.compareTo(right) == 0;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        if (actualValue instanceof Boolean actualBool) {
            return actualBool.toString().equalsIgnoreCase(expected.trim());
        }
        return String.valueOf(actualValue).equals(expected);
    }
}
