package com.flagship.core.evaluation;

import com.flagship.core.model.EvaluationContext;
import com.flagship.core.model.FlagDefinition;
import com.flagship.core.model.SegmentDefinition;

import java.util.Map;
import java.util.Objects;

/**
 * Encapsulates a flag evaluation request flowing through the Chain of Responsibility pipeline (FR2).
 */
public record EvaluationRequest(
        FlagDefinition flag,
        EvaluationContext context,
        Map<String, SegmentDefinition> segments
) {
    public EvaluationRequest {
        Objects.requireNonNull(flag, "flag must not be null");
        context = context != null ? context : EvaluationContext.builder().build();
        segments = segments != null ? segments : Map.of();
    }
}
