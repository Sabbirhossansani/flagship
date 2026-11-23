package com.flagship.core.strategy;

import com.flagship.core.rule.RuleOperatorType;

import java.util.List;

/**
 * Strategy interface for evaluating leaf rule conditions (FR2, Mandatory Strategy Pattern).
 */
public interface RuleOperatorStrategy {

    RuleOperatorType supportedType();

    /**
     * Evaluates whether {@code actualValue} from {@link com.flagship.core.model.EvaluationContext}
     * satisfies this operator with respect to {@code expectedValues}.
     *
     * @param actualValue    the user's attribute value (may be null)
     * @param expectedValues the rule's target value(s)
     * @return true if the condition matches, false otherwise
     */
    boolean evaluate(Object actualValue, List<String> expectedValues);
}
