package com.flagship.core.evaluation;

import com.flagship.core.model.EvaluationReason;
import com.flagship.core.model.EvaluationResult;
import com.flagship.core.model.FlagDefinition;
import com.flagship.core.model.Variation;

/**
 * Step 2 of FR2 Chain of Responsibility: Individual User Targets.
 * Checks if {@code context.getUserKey()} is explicitly targeted in {@code flag.getIndividualTargets()}.
 */
public final class UserTargetHandler extends EvaluationHandler {

    @Override
    public EvaluationResult handle(EvaluationRequest request) {
        String userKey = request.context().getUserKey();
        if (userKey != null && !userKey.isEmpty()) {
            FlagDefinition flag = request.flag();
            String targetVariationKey = flag.getIndividualTargets().get(userKey);
            if (targetVariationKey != null) {
                Variation targetVariation = flag.getVariation(targetVariationKey);
                if (targetVariation != null) {
                    return EvaluationResult.fromVariation(
                            flag,
                            targetVariation,
                            EvaluationReason.INDIVIDUAL_TARGET,
                            null,
                            EvaluationResult.NO_BUCKET,
                            request.context().getEvaluationTime()
                    );
                }
            }
        }
        return forwardToNext(request);
    }
}
