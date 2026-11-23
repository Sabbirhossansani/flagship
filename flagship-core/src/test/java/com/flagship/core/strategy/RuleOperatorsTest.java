package com.flagship.core.strategy;

import com.flagship.core.rule.RuleOperatorType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleOperatorsTest {

    private final OperatorStrategyRegistry registry = OperatorStrategyRegistry.defaultRegistry();

    @Test
    void equalsOperatorMatchesStringsNumbersAndBooleans() {
        assertThat(registry.evaluate(RuleOperatorType.EQUALS, "US", List.of("US"))).isTrue();
        assertThat(registry.evaluate(RuleOperatorType.EQUALS, "CA", List.of("US"))).isFalse();
        assertThat(registry.evaluate(RuleOperatorType.EQUALS, 42, List.of("42"))).isTrue();
        assertThat(registry.evaluate(RuleOperatorType.EQUALS, 42.0, List.of("42.00"))).isTrue();
        assertThat(registry.evaluate(RuleOperatorType.EQUALS, true, List.of("true"))).isTrue();
        assertThat(registry.evaluate(RuleOperatorType.EQUALS, null, List.of("US"))).isFalse();
        assertThat(registry.evaluate(RuleOperatorType.EQUALS, "US", List.of())).isFalse();
    }

    @Test
    void inOperatorMatchesScalarAndCollectionAttributes() {
        assertThat(registry.evaluate(RuleOperatorType.IN, "CA", List.of("US", "CA", "GB"))).isTrue();
        assertThat(registry.evaluate(RuleOperatorType.IN, "FR", List.of("US", "CA", "GB"))).isFalse();
        assertThat(registry.evaluate(RuleOperatorType.IN, 42.0, List.of("42", "100"))).isTrue();
        assertThat(registry.evaluate(RuleOperatorType.IN, true, List.of("TRUE", "YES"))).isTrue();
        assertThat(registry.evaluate(RuleOperatorType.IN, List.of("beta", "vip"), List.of("vip", "staff"))).isTrue();
        assertThat(registry.evaluate(RuleOperatorType.IN, List.of(10.0, 20.0), List.of("20", "30"))).isTrue();
        assertThat(registry.evaluate(RuleOperatorType.IN, List.of("free"), List.of("vip", "staff"))).isFalse();
        assertThat(registry.evaluate(RuleOperatorType.IN, null, List.of("US"))).isFalse();
    }

    @Test
    void containsOperatorMatchesSubstringAndCollectionElement() {
        assertThat(registry.evaluate(RuleOperatorType.CONTAINS, "alice@corp.example.com", List.of("@corp.example.com"))).isTrue();
        assertThat(registry.evaluate(RuleOperatorType.CONTAINS, "alice@gmail.com", List.of("@corp.example.com"))).isFalse();
        assertThat(registry.evaluate(RuleOperatorType.CONTAINS, List.of("dark-mode", "ai-assist"), List.of("ai-assist"))).isTrue();
        assertThat(registry.evaluate(RuleOperatorType.CONTAINS, null, List.of("test"))).isFalse();
    }

    @Test
    void semverOperatorsCompareVersionsProperly() {
        assertThat(registry.evaluate(RuleOperatorType.SEMVER_GT, "2.10.0", List.of("2.2.9"))).isTrue();
        assertThat(registry.evaluate(RuleOperatorType.SEMVER_GT, "v1.2.1", List.of("1.2.0"))).isTrue();
        assertThat(registry.evaluate(RuleOperatorType.SEMVER_LT, "1.9.9", List.of("2.0.0"))).isTrue();
        assertThat(registry.evaluate(RuleOperatorType.SEMVER_EQ, "3.1.4+build123", List.of("3.1.4"))).isTrue();

        // Pre-release has lower precedence than normal release per SemVer 2.0.0 §11
        assertThat(registry.evaluate(RuleOperatorType.SEMVER_LT, "2.0.0-alpha.1", List.of("2.0.0"))).isTrue();
        assertThat(registry.evaluate(RuleOperatorType.SEMVER_GT, "2.0.0", List.of("2.0.0-rc.1"))).isTrue();
        assertThat(registry.evaluate(RuleOperatorType.SEMVER_GT, "1.0.0-alpha.10", List.of("1.0.0-alpha.2"))).isTrue();
        assertThat(registry.evaluate(RuleOperatorType.SEMVER_LT, "1.0.0-2", List.of("1.0.0-10"))).isTrue();
        assertThat(registry.evaluate(RuleOperatorType.SEMVER_LT, "1.0.0-99", List.of("1.0.0-alpha"))).isTrue();
        assertThat(registry.evaluate(RuleOperatorType.SEMVER_LT, "1.0.0-alpha", List.of("1.0.0-alpha.1"))).isTrue();

        // Malformed versions safely evaluate to false without throwing
        assertThat(registry.evaluate(RuleOperatorType.SEMVER_GT, "not-a-semver", List.of("1.0.0"))).isFalse();
        assertThat(registry.evaluate(RuleOperatorType.SEMVER_GT, "1.0.0", List.of("invalid"))).isFalse();
        assertThat(SemanticVersion.tryParse("1.2.3.")).isNull();
        assertThat(SemanticVersion.tryParse("1.0.0-alpha..1")).isNull();
    }

    @Test
    void regexOperatorMatchesAndHandlesInvalidPatternsGracefully() {
        assertThat(registry.evaluate(RuleOperatorType.REGEX, "user_12345", List.of("^user_[0-9]{5}$"))).isTrue();
        assertThat(registry.evaluate(RuleOperatorType.REGEX, "user_abc", List.of("^user_[0-9]{5}$"))).isFalse();
        assertThat(registry.evaluate(RuleOperatorType.REGEX, "anything", List.of("[unclosed-regex"))).isFalse();
        assertThat(registry.evaluate(RuleOperatorType.REGEX, null, List.of(".*"))).isFalse();
    }

    @Test
    void dateOperatorsCompareIsoDatesInstantsAndEpochMillis() {
        assertThat(registry.evaluate(
                RuleOperatorType.DATE_BEFORE,
                "2026-01-15T00:00:00Z",
                List.of("2026-02-01T00:00:00Z"))).isTrue();

        assertThat(registry.evaluate(
                RuleOperatorType.DATE_AFTER,
                Instant.parse("2026-06-01T12:00:00Z"),
                List.of("2026-05-01T00:00:00Z"))).isTrue();

        assertThat(registry.evaluate(
                RuleOperatorType.DATE_AFTER,
                LocalDate.of(2026, 3, 10),
                List.of("2026-03-01"))).isTrue();

        assertThat(registry.evaluate(
                RuleOperatorType.DATE_AFTER,
                ZonedDateTime.parse("2026-04-01T10:00:00+02:00[Europe/Paris]"),
                List.of("2026-03-01T00:00:00Z"))).isTrue();

        assertThat(registry.evaluate(
                RuleOperatorType.DATE_BEFORE,
                LocalDateTime.of(2026, 1, 10, 12, 0),
                List.of("2026-02-01T00:00:00Z"))).isTrue();

        assertThat(registry.evaluate(
                RuleOperatorType.DATE_AFTER,
                Date.from(Instant.parse("2026-07-01T00:00:00Z")),
                List.of("2026-06-01T00:00:00Z"))).isTrue();

        assertThat(registry.evaluate(
                RuleOperatorType.DATE_BEFORE,
                "not-a-date",
                List.of("2026-03-01T00:00:00Z"))).isFalse();
    }
}
