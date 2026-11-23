package com.flagship.core.evaluation;

import com.flagship.core.model.EnvironmentSnapshot;
import com.flagship.core.model.EvaluationContext;
import com.flagship.core.model.EvaluationResult;
import com.flagship.core.model.FlagDefinition;
import com.flagship.core.model.TargetingRule;
import com.flagship.core.model.Variation;
import com.flagship.core.model.VariationType;
import com.flagship.core.rule.CompositeRuleNode;
import com.flagship.core.rule.LeafRuleNode;
import com.flagship.core.rule.RuleNode;
import com.flagship.core.rule.RuleOperatorType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies QA1 latency (10-rule flag evaluation <= 50 microseconds at p99)
 * and QA1 memory overhead (1,000 flags in snapshot <= 20 MB retained heap).
 */
class Qa1PerformanceBenchmarkTest {

    @Test
    void tenRuleFlagEvaluatesUnder50MicrosecondsAtP99() {
        FlagEvaluationEngine engine = new FlagEvaluationEngine();
        FlagDefinition tenRuleFlag = createTenRuleFlag("perf-flag-10-rules");

        EvaluationContext context = EvaluationContext.builder("perf-user-999")
                .attribute("region", "APAC")
                .attribute("appVersion", "3.4.0")
                .attribute("email", "engineer@flagship.internal")
                .attribute("tier", "enterprise")
                .attribute("signupDate", "2026-04-15T10:00:00Z")
                .evaluationTime(Instant.EPOCH)
                .build();

        // Warmup JIT
        for (int i = 0; i < 20_000; i++) {
            engine.evaluate(tenRuleFlag, context);
        }

        int iterations = 50_000;
        long[] latenciesNanos = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            EvaluationResult res = engine.evaluate(tenRuleFlag, context);
            latenciesNanos[i] = System.nanoTime() - start;
            if (res == null) {
                throw new IllegalStateException();
            }
        }

        Arrays.sort(latenciesNanos);
        double p50Micros = latenciesNanos[(int) (iterations * 0.50)] / 1000.0;
        double p95Micros = latenciesNanos[(int) (iterations * 0.95)] / 1000.0;
        double p99Micros = latenciesNanos[(int) (iterations * 0.99)] / 1000.0;

        System.out.printf("QA1 10-Rule Evaluation Latency -> p50: %.2f us | p95: %.2f us | p99: %.2f us%n",
                p50Micros, p95Micros, p99Micros);

        assertThat(p99Micros)
                .as("QA1 requires 10-rule flag local evaluation p99 <= 50.0 microseconds")
                .isLessThanOrEqualTo(50.0);
    }

    @Test
    void oneThousandFlagsSnapshotUsesUnder20MbHeap() {
        Runtime runtime = Runtime.getRuntime();
        forceGc();
        long baselineUsedBytes = runtime.totalMemory() - runtime.freeMemory();

        Map<String, FlagDefinition> flags = new HashMap<>(1_400);
        for (int i = 0; i < 1_000; i++) {
            String key = "feature-flag-" + i;
            flags.put(key, createTenRuleFlag(key));
        }
        EnvironmentSnapshot snapshot = new EnvironmentSnapshot(
                "flagship-prod",
                "production",
                1L,
                Instant.EPOCH,
                flags,
                Map.of()
        );

        forceGc();
        long afterUsedBytes = runtime.totalMemory() - runtime.freeMemory();
        double deltaMb = Math.max(0.1, (afterUsedBytes - baselineUsedBytes) / (1024.0 * 1024.0));

        System.out.printf("QA1 1,000 Flags Heap Overhead -> %.2f MB (flags loaded: %d)%n",
                deltaMb, snapshot.flags().size());

        assertThat(snapshot.flags()).hasSize(1_000);
        assertThat(deltaMb)
                .as("QA1 requires 1,000 flags heap overhead <= 20.0 MB")
                .isLessThanOrEqualTo(20.0);
    }

    static FlagDefinition createTenRuleFlag(String flagKey) {
        List<TargetingRule> rules = new ArrayList<>(10);
        for (int idx = 1; idx <= 9; idx++) {
            RuleNode tree = CompositeRuleNode.and(
                    LeafRuleNode.of("region", RuleOperatorType.EQUALS, "NON_MATCH_" + idx),
                    CompositeRuleNode.or(
                            LeafRuleNode.of("appVersion", RuleOperatorType.SEMVER_GT, "1.0." + idx),
                            LeafRuleNode.of("email", RuleOperatorType.REGEX, "^.*@domain" + idx + "\\.org$")
                    )
            );
            rules.add(TargetingRule.ofVariation("rule-" + idx, "Rule " + idx, tree, "v-on"));
        }

        // 10th rule matches our benchmark context after traversing all 9 preceding rules
        RuleNode tenthRule = CompositeRuleNode.and(
                LeafRuleNode.of("region", RuleOperatorType.IN, "NA", "EMEA", "APAC"),
                LeafRuleNode.of("appVersion", RuleOperatorType.SEMVER_GT, "2.0.0"),
                LeafRuleNode.of("email", RuleOperatorType.CONTAINS, "@flagship.internal"),
                LeafRuleNode.of("signupDate", RuleOperatorType.DATE_AFTER, "2026-01-01T00:00:00Z")
        );
        rules.add(TargetingRule.ofVariation("rule-10", "Rule 10", tenthRule, "v-on"));

        return FlagDefinition.builder(flagKey, VariationType.BOOLEAN)
                .variation(Variation.ofBoolean("v-on", true))
                .variation(Variation.ofBoolean("v-off", false))
                .defaultVariationKey("v-off")
                .offVariationKey("v-off")
                .rules(rules)
                .rollout(25.0, "v-on")
                .build();
    }

    private static void forceGc() {
        for (int i = 0; i < 3; i++) {
            System.gc();
            try {
                Thread.sleep(25);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
