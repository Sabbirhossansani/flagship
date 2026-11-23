package com.flagship.server.dto;

import com.flagship.core.model.WeightedVariation;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record UpdateFlagRequest(
        @NotBlank(message = "Flag name must not be blank")
        @Size(max = 255)
        String name,

        @Size(max = 2000)
        String description,

        @Size(max = 255)
        String owner,

        Boolean enabled,
        Boolean killSwitch,

        @NotEmpty(message = "At least one variation must be defined")
        List<VariationRequest> variations,

        @NotBlank(message = "defaultVariationKey must not be blank")
        String defaultVariationKey,

        String offVariationKey,
        Map<String, String> individualTargets,
        List<TargetingRuleDto> rules,

        @DecimalMin(value = "0.0", message = "rolloutPercentage must be >= 0.0")
        @DecimalMax(value = "100.0", message = "rolloutPercentage must be <= 100.0")
        Double rolloutPercentage,

        String rolloutVariationKey,
        List<WeightedVariation> percentageRollout
) {
}
