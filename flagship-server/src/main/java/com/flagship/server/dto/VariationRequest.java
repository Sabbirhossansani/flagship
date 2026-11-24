package com.flagship.server.dto;

import com.flagship.core.model.Variation;
import com.flagship.core.model.VariationType;

/**
 * Request DTO for a flag variation in {@link CreateFlagRequest} and {@link UpdateFlagRequest}.
 * Allows {@code type} to be omitted in JSON when it matches the parent flag's {@link VariationType}.
 */
public record VariationRequest(
        String key,
        VariationType type,
        Object value,
        String description
) {
    public VariationRequest(String key, Object value) {
        this(key, null, value, null);
    }

    public static VariationRequest from(Variation variation) {
        return new VariationRequest(
                variation.key(),
                variation.type(),
                variation.value(),
                variation.description()
        );
    }
}
