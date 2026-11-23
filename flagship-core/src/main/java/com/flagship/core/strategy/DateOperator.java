package com.flagship.core.strategy;

import com.flagship.core.rule.RuleOperatorType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Strategy implementation for {@link RuleOperatorType#DATE_BEFORE} and
 * {@link RuleOperatorType#DATE_AFTER} (FR2).
 */
public final class DateOperator implements RuleOperatorStrategy {

    private static final int MAX_CACHE_SIZE = 1024;
    private static final Map<String, Instant> INSTANT_CACHE = new ConcurrentHashMap<>(64);

    private final RuleOperatorType operatorType;

    public DateOperator(RuleOperatorType operatorType) {
        this.operatorType = Objects.requireNonNull(operatorType);
        if (operatorType != RuleOperatorType.DATE_BEFORE
                && operatorType != RuleOperatorType.DATE_AFTER) {
            throw new IllegalArgumentException("Unsupported date operator: " + operatorType);
        }
    }

    @Override
    public RuleOperatorType supportedType() {
        return operatorType;
    }

    @Override
    public boolean evaluate(Object actualValue, List<String> expectedValues) {
        if (actualValue == null || expectedValues == null || expectedValues.isEmpty()) {
            return false;
        }
        String expectedRaw = expectedValues.get(0);
        if (expectedRaw == null) {
            return false;
        }
        Instant actualInstant = toInstant(actualValue);
        Instant expectedInstant = parseInstantCached(expectedRaw);
        if (actualInstant == null || expectedInstant == null) {
            return false;
        }
        return switch (operatorType) {
            case DATE_BEFORE -> actualInstant.isBefore(expectedInstant);
            case DATE_AFTER -> actualInstant.isAfter(expectedInstant);
            default -> false;
        };
    }

    public static Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (value instanceof ZonedDateTime zdt) {
            return zdt.toInstant();
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt.toInstant(ZoneOffset.UTC);
        }
        if (value instanceof LocalDate ld) {
            return ld.atStartOfDay(ZoneOffset.UTC).toInstant();
        }
        if (value instanceof Date date) {
            return date.toInstant();
        }
        if (value instanceof Number number) {
            return Instant.ofEpochMilli(number.longValue());
        }
        if (value instanceof String str) {
            if (str.isBlank()) {
                return null;
            }
            return tryParseInstant(str.trim());
        }
        return null;
    }

    public static Instant parseInstantCached(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        Instant cached = INSTANT_CACHE.get(trimmed);
        if (cached != null) {
            return cached;
        }
        Instant parsed = tryParseInstant(trimmed);
        if (parsed != null && INSTANT_CACHE.size() < MAX_CACHE_SIZE) {
            INSTANT_CACHE.putIfAbsent(trimmed, parsed);
        }
        return parsed;
    }

    private static Instant tryParseInstant(String trimmed) {
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(trimmed).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return ZonedDateTime.parse(trimmed).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(trimmed).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(trimmed).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
