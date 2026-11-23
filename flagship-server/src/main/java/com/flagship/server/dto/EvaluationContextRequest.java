package com.flagship.server.dto;

import com.flagship.core.model.EvaluationContext;
import com.flagship.core.model.VariationType;

import java.time.Instant;
import java.util.Map;

public record EvaluationContextRequest(
        String userKey,
        Map<String, Object> attributes,
        VariationType expectedType,
        Object fallbackValue,
        Instant evaluationTime
) {
    public EvaluationContext toEvaluationContext() {
        return EvaluationContext.builder()
                .userKey(userKey)
                .attributes(attributes)
                .evaluationTime(evaluationTime)
                .build();
    }
}
