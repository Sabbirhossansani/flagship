package com.flagship.server.dto;

import java.util.Map;

public record ExperimentResultsResponse(
        String projectKey,
        String envKey,
        String flagKey,
        String metricName,
        String baselineVariationKey,
        long totalExposures,
        long totalConversions,
        double chiSquareStat,
        double srmPValue,
        boolean hasSrm,
        String srmWarning,
        Map<String, VariationStatsDto> variations
) {
}
