package com.flagship.core.evaluation;

import com.flagship.core.model.EvaluationReason;
import com.flagship.core.model.EvaluationResult;
import com.flagship.core.model.FlagDefinition;
import com.flagship.core.model.Variation;

/**
 * Step 5 of FR2 Chain of Responsibility: Default Variation.
 * Terminal handler returning the environment's default variation when no prior handler matched.
 */
public final class DefaultHandler extends EvaluationHandler {

    @Override
    public EvaluationResult handle(EvaluationRequest request) {
        FlagDefinition flag = request.flag();
        Variation defaultVariation = flag.getVariation(flag.getDefaultVariationKey());
        return EvaluationResult.fromVariation(
                flag,
                defaultVariation,
                EvaluationReason.DEFAULT_VARIATION,
                null,
                EvaluationResult.NO_BUCKET,
                request.context().getEvaluationTime()
        );
    }
}
