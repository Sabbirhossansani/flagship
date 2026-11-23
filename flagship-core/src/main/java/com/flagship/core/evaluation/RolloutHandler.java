package com.flagship.core.evaluation;

import com.flagship.core.model.EvaluationReason;
import com.flagship.core.model.EvaluationResult;
import com.flagship.core.model.FlagDefinition;
import com.flagship.core.model.Variation;
import com.flagship.core.model.WeightedVariation;
import com.flagship.core.strategy.BucketingStrategy;

import java.util.List;
import java.util.Objects;

/**
 * Step 4 of FR2 Chain of Responsibility: Percentage Rollout.
 * Uses deterministic hashing ({@link BucketingStrategy}) of {@code flagKey + ":" + userKey}
 * mapped to {@code [0, 99999]} to guarantee sticky gradual rollouts (FR3, QA4).
 */
public final class RolloutHandler extends EvaluationHandler {

    private final BucketingStrategy bucketingStrategy;

    public RolloutHandler(BucketingStrategy bucketingStrategy) {
        this.bucketingStrategy = Objects.requireNonNull(bucketingStrategy, "bucketingStrategy must not be null");
    }

    @Override
    public EvaluationResult handle(EvaluationRequest request) {
        FlagDefinition flag = request.flag();
        List<WeightedVariation> weights = flag.getPercentageRollout();
        double rolloutPercentage = flag.getRolloutPercentage();

        if (weights.isEmpty() && rolloutPercentage <= 0.0) {
            return forwardToNext(request);
        }

        String userKey = request.context().getUserKey();
        int bucket = bucketingStrategy.computeBucket(flag.getKey(), userKey);
        if (bucket < 0) {
            return forwardToNext(request);
        }

        if (!weights.isEmpty()) {
            String selectedKey = selectWeightedVariation(weights, bucket);
            if (selectedKey != null) {
                Variation selected = flag.getVariation(selectedKey);
                if (selected != null) {
                    return EvaluationResult.fromVariation(
                            flag,
                            selected,
                            EvaluationReason.PERCENTAGE_ROLLOUT,
                            null,
                            bucket,
                            request.context().getEvaluationTime()
                    );
                }
            }
            return forwardToNext(request);
        }

        int threshold = (int) Math.round(rolloutPercentage * 1000.0);
        if (bucket < threshold && flag.getRolloutVariationKey() != null) {
            Variation rolloutVariation = flag.getVariation(flag.getRolloutVariationKey());
            if (rolloutVariation != null) {
                return EvaluationResult.fromVariation(
                        flag,
                        rolloutVariation,
                        EvaluationReason.PERCENTAGE_ROLLOUT,
                        null,
                        bucket,
                        request.context().getEvaluationTime()
                );
            }
        }
        return forwardToNext(request);
    }

    static String selectWeightedVariation(List<WeightedVariation> distributions, int bucket) {
        int cumulative = 0;
        for (int i = 0, size = distributions.size(); i < size; i++) {
            WeightedVariation weighted = distributions.get(i);
            cumulative += weighted.weightBuckets();
            if (bucket < cumulative) {
                return weighted.variationKey();
            }
        }
        return null;
    }
}
