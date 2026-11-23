package com.flagship.core.evaluation;

import com.flagship.core.model.EvaluationContext;
import com.flagship.core.model.EvaluationResult;
import com.flagship.core.model.FlagDefinition;
import com.flagship.core.model.Variation;
import com.flagship.core.model.VariationType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA4 Verification Test:
 * 1,000,000 user keys test proving starter local evaluation == server preview evaluation
 * AND 10% -> 20% gradual rollout ramp monotonicity (zero user regressions).
 */
class Qa4DeterminismAndMonotonicityTest {

    private static final int TOTAL_USERS = 1_000_000;

    @Test
    void oneMillionUsersProveStarterEqualsServerPreviewAnd10To20PercentRampMonotonicity() {
        FlagEvaluationEngine starterEngine = new FlagEvaluationEngine();
        FlagEvaluationEngine serverPreviewEngine = new FlagEvaluationEngine();

        FlagDefinition flagAt10Percent = FlagDefinition.builder("gradual-search-v3", VariationType.BOOLEAN)
                .variation(Variation.ofBoolean("on", true))
                .variation(Variation.ofBoolean("off", false))
                .defaultVariationKey("off")
                .offVariationKey("off")
                .rollout(10.0, "on")
                .build();

        FlagDefinition flagAt20Percent = flagAt10Percent.toBuilder()
                .rollout(20.0, "on")
                .build();

        int includedAt10Count = 0;
        int includedAt20Count = 0;
        int monotonicityViolations = 0;
        int starterServerMismatches = 0;

        Instant fixedTime = Instant.parse("2026-06-01T00:00:00Z");

        for (int i = 0; i < TOTAL_USERS; i++) {
            String userKey = "user-" + i;
            EvaluationContext ctx = EvaluationContext.builder(userKey)
                    .evaluationTime(fixedTime)
                    .build();

            EvaluationResult starter10 = starterEngine.evaluate(flagAt10Percent, ctx);
            EvaluationResult server10 = serverPreviewEngine.evaluate(flagAt10Percent, ctx);

            if (!starter10.variationKey().equals(server10.variationKey())
                    || starter10.bucket() != server10.bucket()
                    || starter10.reason() != server10.reason()) {
                starterServerMismatches++;
            }

            EvaluationResult starter20 = starterEngine.evaluate(flagAt20Percent, ctx);
            EvaluationResult server20 = serverPreviewEngine.evaluate(flagAt20Percent, ctx);

            if (!starter20.variationKey().equals(server20.variationKey())
                    || starter20.bucket() != server20.bucket()
                    || starter20.reason() != server20.reason()) {
                starterServerMismatches++;
            }

            boolean in10 = starter10.asBoolean(false);
            boolean in20 = starter20.asBoolean(false);

            if (in10) {
                includedAt10Count++;
                if (!in20) {
                    monotonicityViolations++;
                }
            }
            if (in20) {
                includedAt20Count++;
            }
        }

        System.out.printf(
                "QA4 1,000,000 Users -> 10%% Cohort: %d | 20%% Cohort: %d | Mismatches: %d | Monotonicity Violations: %d%n",
                includedAt10Count, includedAt20Count, starterServerMismatches, monotonicityViolations
        );

        assertThat(starterServerMismatches)
                .as("Starter evaluation and server preview must match 100%% across all 1,000,000 user keys")
                .isZero();

        assertThat(monotonicityViolations)
                .as("10%% -> 20%% rollout ramp must be strictly monotonic with zero dropped users")
                .isZero();

        assertThat(includedAt10Count).isBetween(98_000, 102_000);
        assertThat(includedAt20Count).isBetween(197_500, 202_500);
        assertThat(includedAt20Count).isGreaterThan(includedAt10Count);
    }
}
