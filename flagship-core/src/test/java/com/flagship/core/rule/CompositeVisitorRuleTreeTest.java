package com.flagship.core.rule;

import com.flagship.core.model.EvaluationContext;
import com.flagship.core.model.SegmentDefinition;
import com.flagship.core.strategy.OperatorStrategyRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeVisitorRuleTreeTest {

    private final OperatorStrategyRegistry registry = OperatorStrategyRegistry.defaultRegistry();

    @Test
    void nestedAndOrCompositeRuleTreeEvaluatesCorrectly() {
        // Rule: (country IN [US, CA]) AND ((appVersion SEMVER_GT 2.1.0) OR (email CONTAINS @flagship.dev))
        RuleNode tree = CompositeRuleNode.and(
                LeafRuleNode.of("country", RuleOperatorType.IN, "US", "CA"),
                CompositeRuleNode.or(
                        LeafRuleNode.of("appVersion", RuleOperatorType.SEMVER_GT, "2.1.0"),
                        LeafRuleNode.of("email", RuleOperatorType.CONTAINS, "@flagship.dev")
                )
        );

        EvaluationContext matchingViaSemver = EvaluationContext.builder("u-1")
                .attribute("country", "US")
                .attribute("appVersion", "2.2.0")
                .attribute("email", "user@gmail.com")
                .build();

        EvaluationContext matchingViaEmail = EvaluationContext.builder("u-2")
                .attribute("country", "CA")
                .attribute("appVersion", "1.0.0")
                .attribute("email", "dev@flagship.dev")
                .build();

        EvaluationContext nonMatchingCountry = EvaluationContext.builder("u-3")
                .attribute("country", "DE")
                .attribute("appVersion", "3.0.0")
                .attribute("email", "dev@flagship.dev")
                .build();

        assertThat(tree.accept(new EvaluationRuleVisitor(matchingViaSemver, registry, Map.of()))).isTrue();
        assertThat(tree.accept(new EvaluationRuleVisitor(matchingViaEmail, registry, Map.of()))).isTrue();
        assertThat(tree.accept(new EvaluationRuleVisitor(nonMatchingCountry, registry, Map.of()))).isFalse();
    }

    @Test
    void segmentReferenceAndCircularSegmentProtectionWork() {
        SegmentDefinition betaSegment = new SegmentDefinition(
                "beta-testers",
                "Beta Testers",
                "Users on beta plan or explicitly included",
                LeafRuleNode.of("plan", RuleOperatorType.EQUALS, "beta"),
                Set.of("explicit-beta-user"),
                Set.of("blocked-user"),
                1L
        );

        // Circular segments A -> B -> A should not stack overflow
        SegmentDefinition cycleA = SegmentDefinition.of("cycle-a", "Cycle A", SegmentReferenceNode.of("cycle-b"));
        SegmentDefinition cycleB = SegmentDefinition.of("cycle-b", "Cycle B", SegmentReferenceNode.of("cycle-a"));

        Map<String, SegmentDefinition> segments = Map.of(
                "beta-testers", betaSegment,
                "cycle-a", cycleA,
                "cycle-b", cycleB
        );

        RuleNode ruleWithSegment = SegmentReferenceNode.of("beta-testers");

        EvaluationContext includedUser = EvaluationContext.builder("explicit-beta-user").build();
        EvaluationContext blockedUser = EvaluationContext.builder("blocked-user")
                .attribute("plan", "beta")
                .build();
        EvaluationContext conditionMatchUser = EvaluationContext.builder("normal-user")
                .attribute("plan", "beta")
                .build();

        assertThat(ruleWithSegment.accept(new EvaluationRuleVisitor(includedUser, registry, segments))).isTrue();
        assertThat(ruleWithSegment.accept(new EvaluationRuleVisitor(blockedUser, registry, segments))).isFalse();
        assertThat(ruleWithSegment.accept(new EvaluationRuleVisitor(conditionMatchUser, registry, segments))).isTrue();

        // Circular reference terminates safely with false
        assertThat(SegmentReferenceNode.of("cycle-a")
                .accept(new EvaluationRuleVisitor(conditionMatchUser, registry, segments))).isFalse();
    }

    @Test
    void validationAndDescribeVisitorsInspectRuleTrees() {
        RuleNode validTree = CompositeRuleNode.and(
                LeafRuleNode.of("appVersion", RuleOperatorType.SEMVER_GT, "1.5.0"),
                LeafRuleNode.of("email", RuleOperatorType.REGEX, "^.+@example\\.com$")
        );
        assertThat(validTree.accept(new RuleValidationVisitor())).isEmpty();
        assertThat(validTree.accept(new RuleDescribeVisitor()))
                .isEqualTo("((appVersion SEMVER_GT [1.5.0]) AND (email REGEX [^.+@example\\.com$]))");

        RuleNode invalidTree = CompositeRuleNode.or(
                LeafRuleNode.of("appVersion", RuleOperatorType.SEMVER_GT, "bad-semver"),
                LeafRuleNode.of("email", RuleOperatorType.REGEX, "[unclosed"),
                LeafRuleNode.of("signupDate", RuleOperatorType.DATE_AFTER, "not-a-date")
        );
        List<String> errors = invalidTree.accept(new RuleValidationVisitor());
        assertThat(errors).hasSize(3);
    }
}
