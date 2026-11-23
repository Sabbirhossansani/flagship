package com.flagship.core.strategy;

import com.flagship.core.rule.RuleOperatorType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registry mapping {@link RuleOperatorType} to its concrete {@link RuleOperatorStrategy} (FR2).
 */
public final class OperatorStrategyRegistry {

    private static final OperatorStrategyRegistry DEFAULT_INSTANCE = createDefault();

    private final Map<RuleOperatorType, RuleOperatorStrategy> strategies;

    public OperatorStrategyRegistry(Map<RuleOperatorType, RuleOperatorStrategy> strategies) {
        this.strategies = new EnumMap<>(Objects.requireNonNull(strategies));
    }

    public static OperatorStrategyRegistry defaultRegistry() {
        return DEFAULT_INSTANCE;
    }

    private static OperatorStrategyRegistry createDefault() {
        Map<RuleOperatorType, RuleOperatorStrategy> map = new EnumMap<>(RuleOperatorType.class);
        register(map, new EqualsOperator());
        register(map, new InOperator());
        register(map, new ContainsOperator());
        register(map, new SemverOperator(RuleOperatorType.SEMVER_GT));
        register(map, new SemverOperator(RuleOperatorType.SEMVER_LT));
        register(map, new SemverOperator(RuleOperatorType.SEMVER_EQ));
        register(map, new RegexOperator());
        register(map, new DateOperator(RuleOperatorType.DATE_BEFORE));
        register(map, new DateOperator(RuleOperatorType.DATE_AFTER));
        return new OperatorStrategyRegistry(map);
    }

    private static void register(Map<RuleOperatorType, RuleOperatorStrategy> map, RuleOperatorStrategy strategy) {
        map.put(strategy.supportedType(), strategy);
    }

    public RuleOperatorStrategy getStrategy(RuleOperatorType operatorType) {
        return strategies.get(operatorType);
    }

    public boolean evaluate(RuleOperatorType operatorType, Object actualValue, List<String> expectedValues) {
        RuleOperatorStrategy strategy = strategies.get(operatorType);
        return strategy != null && strategy.evaluate(actualValue, expectedValues);
    }
}
