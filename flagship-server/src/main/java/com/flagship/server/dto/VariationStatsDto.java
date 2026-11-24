package com.flagship.server.dto;

public record VariationStatsDto(
        String variationKey,
        long exposures,
        long conversions,
        double conversionRate,
        Double relativeUplift,
        Double zScore,
        Double pValue,
        Boolean statisticallySignificant,
        Double confidenceIntervalLower,
        Double confidenceIntervalUpper
) {
}
