package com.flagship.core.evaluation;

import com.flagship.core.model.EnvironmentDefinition;
import com.flagship.core.model.EnvironmentSnapshot;
import com.flagship.core.model.EvaluationContext;
import com.flagship.core.model.EvaluationReason;
import com.flagship.core.model.EvaluationResult;
import com.flagship.core.model.FlagDefinition;
import com.flagship.core.model.ProjectDefinition;
import com.flagship.core.model.ProjectRole;
import com.flagship.core.model.TargetingRule;
import com.flagship.core.model.Variation;
import com.flagship.core.model.VariationType;
import com.flagship.core.model.WeightedVariation;
import com.flagship.core.rule.LeafRuleNode;
import com.flagship.core.rule.RuleOperatorType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationChainOrderTest {

    private final FlagEvaluationEngine engine = new FlagEvaluationEngine();

    @Test
    void chainStructureMatchesStrictFr2HandlerOrder() {
        EvaluationHandler h1 = engine.getPipelineHead();
        EvaluationHandler h2 = h1.getNext();
        EvaluationHandler h3 = h2.getNext();
        EvaluationHandler h4 = h3.getNext();
        EvaluationHandler h5 = h4.getNext();

        assertThat(h1).isInstanceOf(KillSwitchHandler.class);
        assertThat(h2).isInstanceOf(UserTargetHandler.class);
        assertThat(h3).isInstanceOf(SegmentRuleHandler.class);
        assertThat(h4).isInstanceOf(RolloutHandler.class);
        assertThat(h5).isInstanceOf(DefaultHandler.class);
        assertThat(h5.getNext()).isNull();
    }

    @Test
    void evaluationFollowsStrictFr2PrecedenceOrder() {
        FlagDefinition baseFlag = FlagDefinition.builder("checkout-v2", VariationType.STRING)
                .variation(Variation.ofString("off", "legacy-checkout"))
                .variation(Variation.ofString("vip", "vip-checkout"))
                .variation(Variation.ofString("segment", "eu-checkout"))
                .variation(Variation.ofString("rollout", "new-checkout"))
                .variation(Variation.ofString("default", "standard-checkout"))
                .defaultVariationKey("default")
                .offVariationKey("off")
                .targetUser("vip-user-1", "vip")
                .rule(TargetingRule.ofVariation(
                        "rule-eu",
                        "EU Users",
                        LeafRuleNode.of("region", RuleOperatorType.EQUALS, "EU"),
                        "segment"
                ))
                .rollout(100.0, "rollout")
                .build();

        EvaluationContext vipContextInEu = EvaluationContext.builder("vip-user-1")
                .attribute("region", "EU")
                .build();

        // 1. Kill Switch overrides Individual Target, Segment Rule, Rollout, and Default
        FlagDefinition killedFlag = baseFlag.toBuilder().killSwitch(true).build();
        EvaluationResult killResult = engine.evaluate(killedFlag, vipContextInEu);
        assertThat(killResult.reason()).isEqualTo(EvaluationReason.KILL_SWITCH);
        assertThat(killResult.variationKey()).isEqualTo("off");
        assertThat(killResult.asString("")).isEqualTo("legacy-checkout");

        // 2. Individual User Target overrides Segment Rule, Rollout, and Default
        EvaluationResult targetResult = engine.evaluate(baseFlag, vipContextInEu);
        assertThat(targetResult.reason()).isEqualTo(EvaluationReason.INDIVIDUAL_TARGET);
        assertThat(targetResult.variationKey()).isEqualTo("vip");

        // 3. Segment Rule overrides Percentage Rollout and Default
        EvaluationContext euRegularUser = EvaluationContext.builder("regular-user-1")
                .attribute("region", "EU")
                .build();
        EvaluationResult segmentResult = engine.evaluate(baseFlag, euRegularUser);
        assertThat(segmentResult.reason()).isEqualTo(EvaluationReason.SEGMENT_RULE);
        assertThat(segmentResult.matchedRuleId()).isEqualTo("rule-eu");
        assertThat(segmentResult.variationKey()).isEqualTo("segment");

        // 4. Percentage Rollout overrides Default when no prior rule matches
        EvaluationContext usRegularUser = EvaluationContext.builder("regular-user-1")
                .attribute("region", "US")
                .build();
        EvaluationResult rolloutResult = engine.evaluate(baseFlag, usRegularUser);
        assertThat(rolloutResult.reason()).isEqualTo(EvaluationReason.PERCENTAGE_ROLLOUT);
        assertThat(rolloutResult.variationKey()).isEqualTo("rollout");
        assertThat(rolloutResult.bucket()).isBetween(0, 99_999);

        // 5. Default Variation when rollout is 0% (or userKey is omitted)
        FlagDefinition noRolloutFlag = baseFlag.toBuilder().rollout(0.0, "rollout").build();
        EvaluationResult defaultResult = engine.evaluate(noRolloutFlag, usRegularUser);
        assertThat(defaultResult.reason()).isEqualTo(EvaluationReason.DEFAULT_VARIATION);
        assertThat(defaultResult.variationKey()).isEqualTo("default");
    }

    @Test
    void supportsAllFourVariationTypesAndSnapshotLookup() {
        FlagDefinition jsonFlag = FlagDefinition.builder("ui-theme-config", VariationType.JSON)
                .variation(Variation.ofJson("dark", "{\"theme\":\"dark\",\"contrast\":1.2}"))
                .defaultVariationKey("dark")
                .build();

        FlagDefinition numberFlag = FlagDefinition.builder("rate-limit-rps", VariationType.NUMBER)
                .variation(Variation.ofNumber("standard", 250))
                .defaultVariationKey("standard")
                .build();

        EnvironmentSnapshot snapshot = new EnvironmentSnapshot(
                "core-project",
                "production",
                10L,
                Instant.now(),
                Map.of(
                        jsonFlag.getKey(), jsonFlag,
                        numberFlag.getKey(), numberFlag
                ),
                Map.of()
        );

        EvaluationContext ctx = EvaluationContext.ofUser("user-42");

        EvaluationResult jsonRes = engine.evaluate(snapshot, "ui-theme-config", ctx, VariationType.JSON, "{}");
        assertThat(jsonRes.asJsonString("{}")).contains("\"theme\":\"dark\"");

        EvaluationResult numRes = engine.evaluate(snapshot, "rate-limit-rps", ctx, VariationType.NUMBER, 100);
        assertThat(numRes.asLong(0)).isEqualTo(250L);

        EvaluationResult missingRes = engine.evaluate(snapshot, "non-existent", ctx, VariationType.BOOLEAN, false);
        assertThat(missingRes.reason()).isEqualTo(EvaluationReason.FLAG_NOT_FOUND);
        assertThat(missingRes.asBoolean(true)).isFalse();

        EvaluationResult mismatchRes = engine.evaluate(snapshot, "rate-limit-rps", ctx, VariationType.BOOLEAN, false);
        assertThat(mismatchRes.reason()).isEqualTo(EvaluationReason.TYPE_MISMATCH);
        assertThat(mismatchRes.type()).isEqualTo(VariationType.BOOLEAN);
        assertThat(mismatchRes.asBoolean(true)).isFalse();
    }

    @Test
    void detectsStaleFlagsPerFr9Specification() {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");

        // Newly created flag with default builder timestamps should not falsely report as stale
        FlagDefinition newlyBuiltFlag = FlagDefinition.builder("fresh-flag", VariationType.BOOLEAN)
                .variation(Variation.ofBoolean("on", true))
                .defaultVariationKey("on")
                .build();
        assertThat(newlyBuiltFlag.isStale(now)).isFalse();

        FlagDefinition activeFlag = FlagDefinition.builder("active-flag", VariationType.BOOLEAN)
                .variation(Variation.ofBoolean("on", true))
                .defaultVariationKey("on")
                .owner("payments-team")
                .lastEvaluatedAt(now.minus(Duration.ofDays(5)))
                .fullRolloutSince(now.minus(Duration.ofDays(3)))
                .build();

        FlagDefinition unevaluated30DaysFlag = activeFlag.toBuilder()
                .lastEvaluatedAt(now.minus(Duration.ofDays(31)))
                .build();

        FlagDefinition fullRollout14DaysFlag = activeFlag.toBuilder()
                .fullRolloutSince(now.minus(Duration.ofDays(14)))
                .build();

        // Dialed back to 50% rollout or disabled should not be flagged as 100%-rollout stale
        FlagDefinition dialedBackFlag = fullRollout14DaysFlag.toBuilder()
                .rollout(50.0, "on")
                .build();

        assertThat(activeFlag.isStale(now)).isFalse();
        assertThat(unevaluated30DaysFlag.isStale(now)).isTrue();
        assertThat(fullRollout14DaysFlag.isStale(now)).isTrue();
        assertThat(dialedBackFlag.isStale(now)).isFalse();
    }

    @Test
    void validatesProjectEnvironmentsRbacAndWeightedRolloutBounds() {
        ProjectDefinition project = ProjectDefinition.of("commerce", "Commerce Platform", "Core project");
        assertThat(project.environments())
                .extracting(EnvironmentDefinition::key)
                .containsExactly("development", "staging", "production");
        assertThat(project.environments().get(2).requiresApproval()).isTrue();

        assertThat(ProjectRole.VIEWER.canEdit()).isFalse();
        assertThat(ProjectRole.EDITOR.canEdit()).isTrue();
        assertThat(ProjectRole.EDITOR.canApprove()).isFalse();
        assertThat(ProjectRole.APPROVER.canApprove()).isTrue();
        assertThat(ProjectRole.ADMIN.isAdmin()).isTrue();

        assertThatThrownBy(() -> FlagDefinition.builder("over-100", VariationType.STRING)
                .variation(Variation.ofString("a", "A"))
                .variation(Variation.ofString("b", "B"))
                .defaultVariationKey("a")
                .percentageRollout(List.of(
                        WeightedVariation.ofPercentage("a", 60.0),
                        WeightedVariation.ofPercentage("b", 50.0)
                ))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed 100000");
    }
}
