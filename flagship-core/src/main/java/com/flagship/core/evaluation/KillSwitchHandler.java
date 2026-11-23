package com.flagship.core.evaluation;

import com.flagship.core.model.EvaluationReason;
import com.flagship.core.model.EvaluationResult;
import com.flagship.core.model.FlagDefinition;
import com.flagship.core.model.Variation;

/**
 * Step 1 of FR2 Chain of Responsibility: Kill Switch evaluation.
 * If the flag has its kill switch active or is disabled, short-circuits immediately
 * and returns the configured off/default variation with {@link EvaluationReason#KILL_SWITCH}.
 */
public final class KillSwitchHandler extends EvaluationHandler {

    @Override
    public EvaluationResult handle(EvaluationRequest request) {
        FlagDefinition flag = request.flag();
        if (flag.isKillSwitch() || !flag.isEnabled()) {
            Variation offVariation = flag.getVariation(flag.getOffVariationKey());
            if (offVariation == null) {
                offVariation = flag.getVariation(flag.getDefaultVariationKey());
            }
            return EvaluationResult.fromVariation(
                    flag,
                    offVariation,
                    EvaluationReason.KILL_SWITCH,
                    null,
                    EvaluationResult.NO_BUCKET,
                    request.context().getEvaluationTime()
            );
        }
        return forwardToNext(request);
    }
}
