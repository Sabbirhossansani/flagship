package com.flagship.core.rule;

/**
 * Supported segment targeting operators (FR2).
 */
public enum RuleOperatorType {
    EQUALS,
    IN,
    CONTAINS,
    SEMVER_GT,
    SEMVER_LT,
    SEMVER_EQ,
    REGEX,
    DATE_BEFORE,
    DATE_AFTER
}
