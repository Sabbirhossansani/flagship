package com.flagship.core.strategy;

import com.flagship.core.rule.RuleOperatorType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Strategy implementation for {@link RuleOperatorType#REGEX} (FR2).
 * Caches compiled {@link Pattern} instances to achieve sub-microsecond evaluation latency (QA1).
 */
public final class RegexOperator implements RuleOperatorStrategy {

    private static final int MAX_CACHE_SIZE = 1024;
    private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>(64);

    @Override
    public RuleOperatorType supportedType() {
        return RuleOperatorType.REGEX;
    }

    @Override
    public boolean evaluate(Object actualValue, List<String> expectedValues) {
        if (actualValue == null || expectedValues == null || expectedValues.isEmpty()) {
            return false;
        }
        String patternRaw = expectedValues.get(0);
        if (patternRaw == null || patternRaw.isEmpty()) {
            return false;
        }
        Pattern pattern = compileCached(patternRaw);
        if (pattern == null) {
            return false;
        }
        return pattern.matcher(String.valueOf(actualValue)).matches();
    }

    public static Pattern compileCached(String regex) {
        Pattern cached = PATTERN_CACHE.get(regex);
        if (cached != null) {
            return cached;
        }
        try {
            Pattern compiled = Pattern.compile(regex);
            if (PATTERN_CACHE.size() < MAX_CACHE_SIZE) {
                PATTERN_CACHE.putIfAbsent(regex, compiled);
            }
            return compiled;
        } catch (PatternSyntaxException ignored) {
            return null;
        }
    }
}
