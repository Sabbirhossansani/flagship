package com.flagship.server.dto;

import com.flagship.core.model.TargetingRule;
import com.flagship.core.model.WeightedVariation;
import com.flagship.core.rule.RuleDescribeVisitor;
import com.flagship.core.rule.RuleNode;

import java.util.List;
import java.util.UUID;

/**
 * DTO for a feature flag targeting rule (FR2).
 */
public record TargetingRuleDto(
        String id,
        String name,
        String segmentKey,
        RuleNodeDto condition,
        String conditionDescription,
        String variationKey,
        List<WeightedVariation> rolloutDistributions
) {
    public TargetingRule toDomain() {
        String ruleId = (id != null && !id.isBlank()) ? id : UUID.randomUUID().toString();
        RuleNode domainCondition = condition != null ? condition.toDomain() : null;
        if (rolloutDistributions != null) {
            for (WeightedVariation wv : rolloutDistributions) {
                if (wv == null) {
                    throw new IllegalArgumentException("rolloutDistributions must not contain null elements");
                }
            }
        }
        return new TargetingRule(
                ruleId,
                name != null ? name : ruleId,
                segmentKey,
                domainCondition,
                variationKey,
                rolloutDistributions != null ? rolloutDistributions : List.of()
        );
    }

    public static TargetingRuleDto fromDomain(TargetingRule rule, RuleDescribeVisitor describeVisitor) {
        if (rule == null) {
            return null;
        }
        String description = (rule.condition() != null && describeVisitor != null)
                ? rule.condition().accept(describeVisitor)
                : null;
        return new TargetingRuleDto(
                rule.id(),
                rule.name(),
                rule.segmentKey(),
                RuleNodeDto.fromDomain(rule.condition()),
                description,
                rule.variationKey(),
                rule.rolloutDistributions()
        );
    }
}
