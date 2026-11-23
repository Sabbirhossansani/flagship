package com.flagship.server.dto;

import com.flagship.core.model.Variation;
import com.flagship.core.model.VariationType;
import com.flagship.core.model.WeightedVariation;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record FlagResponse(
        String projectKey,
        String environmentKey,
        String key,
        String name,
        String description,
        String owner,
        VariationType type,
        boolean enabled,
        boolean killSwitch,
        List<Variation> variations,
        String defaultVariationKey,
        String offVariationKey,
        Map<String, String> individualTargets,
        List<TargetingRuleDto> rules,
        double rolloutPercentage,
        String rolloutVariationKey,
        List<WeightedVariation> percentageRollout,
        long version,
        boolean stale,
        Instant createdAt,
        Instant updatedAt,
        Instant lastEvaluatedAt,
        Instant fullRolloutSince
) {
}
