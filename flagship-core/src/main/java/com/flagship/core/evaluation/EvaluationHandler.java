package com.flagship.core.evaluation;

import com.flagship.core.model.EvaluationResult;

/**
 * Abstract handler in the Chain of Responsibility pattern implementing the FR2 evaluation pipeline.
 */
public abstract class EvaluationHandler {

    private EvaluationHandler next;

    public EvaluationHandler setNext(EvaluationHandler next) {
        this.next = next;
        return next;
    }

    public EvaluationHandler getNext() {
        return next;
    }

    public abstract EvaluationResult handle(EvaluationRequest request);

    protected EvaluationResult forwardToNext(EvaluationRequest request) {
        if (next == null) {
            throw new IllegalStateException("Evaluation chain terminated without producing a result for flag: "
                    + request.flag().getKey());
        }
        return next.handle(request);
    }
}
