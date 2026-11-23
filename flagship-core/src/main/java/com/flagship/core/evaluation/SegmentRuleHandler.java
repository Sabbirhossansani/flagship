package com.flagship.core.evaluation;

import com.flagship.core.model.EvaluationReason;
import com.flagship.core.model.EvaluationResult;
import com.flagship.core.model.FlagDefinition;
import com.flagship.core.model.TargetingRule;
import com.flagship.core.model.Variation;
import com.flagship.core.model.WeightedVariation;
import com.flagship.core.rule.EvaluationRuleVisitor;
import com.flagship.core.rule.RuleNode;
import com.flagship.core.strategy.BucketingStrategy;
import com.flagship.core.strategy.OperatorStrategyRegistry;

import java.util.List;
import java.util.Objects;

/**
 * Step 3 of FR2 Chain of Responsibility: Segment Rules.
 * Evaluates ordered {@link TargetingRule} items using the Composite + Visitor rule engine
 * ({@link EvaluationRuleVisitor}) and {@link OperatorStrategyRegistry}.
 */
public final class SegmentRuleHandler extends EvaluationHandler {

    private final OperatorStrategyRegistry operatorRegistry;
    private final BucketingStrategy bucketingStrategy;

    public SegmentRuleHandler(OperatorStrategyRegistry operatorRegistry, BucketingStrategy bucketingStrategy) {
        this.operatorRegistry = Objects.requireNonNull(operatorRegistry, "operatorRegistry must not be null");
        this.bucketingStrategy = Objects.requireNonNull(bucketingStrategy, "bucketingStrategy must not be null");
    }

    @Override
    public EvaluationResult handle(EvaluationRequest request) {
        FlagDefinition flag = request.flag();
        List<TargetingRule> rules = flag.getRules();
        if (rules.isEmpty()) {
            return forwardToNext(request);
        }

        EvaluationRuleVisitor visitor = new EvaluationRuleVisitor(
                request.context(),
                operatorRegistry,
                request.segments()
        );

        for (int i = 0, size = rules.size(); i < size; i++) {
            TargetingRule rule = rules.get(i);
            if (matchesRule(rule, visitor)) {
                int bucket = EvaluationResult.NO_BUCKET;
                String chosenVariationKey = rule.variationKey();

                List<WeightedVariation> distributions = rule.rolloutDistributions();
                if ((chosenVariationKey == null || chosenVariationKey.isEmpty()) && !distributions.isEmpty()) {
                    bucket = bucketingStrategy.computeBucket(flag.getKey(), request.context().getUserKey());
                    if (bucket >= 0) {
                        chosenVariationKey = RolloutHandler.selectWeightedVariation(distributions, bucket);
                    }
                }

                if (chosenVariationKey != null) {
                    Variation variation = flag.getVariation(chosenVariationKey);
                    if (variation != null) {
                        return EvaluationResult.fromVariation(
                                flag,
                                variation,
                                EvaluationReason.SEGMENT_RULE,
                                rule.id(),
                                bucket,
                                request.context().getEvaluationTime()
                        );
                    }
                }
            }
        }
        return forwardToNext(request);
    }

    private boolean matchesRule(TargetingRule rule, EvaluationRuleVisitor visitor) {
        boolean hasSegment = rule.segmentKey() != null && !rule.segmentKey().isBlank();
        boolean hasCondition = rule.condition() != null;
        if (!hasSegment && !hasCondition) {
            return false;
        }
        if (hasSegment && !visitor.matchesSegment(rule.segmentKey())) {
            return false;
        }
        if (hasCondition) {
            RuleNode condition = rule.condition();
            return Boolean.TRUE.equals(condition.accept(visitor));
        }
        return true;
    }
}
