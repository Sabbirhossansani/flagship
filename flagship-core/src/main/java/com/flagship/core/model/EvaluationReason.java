package com.flagship.core.model;

/**
 * Explains which stage of the FR2 Chain of Responsibility produced the evaluation result.
 */
public enum EvaluationReason {
    KILL_SWITCH,
    INDIVIDUAL_TARGET,
    SEGMENT_RULE,
    PERCENTAGE_ROLLOUT,
    DEFAULT_VARIATION,
    FLAG_NOT_FOUND,
    TYPE_MISMATCH,
    ERROR
}
