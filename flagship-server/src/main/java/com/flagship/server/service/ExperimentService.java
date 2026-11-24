package com.flagship.server.service;

import com.flagship.core.model.Variation;
import com.flagship.core.model.WeightedVariation;
import com.flagship.server.domain.*;
import com.flagship.server.dto.*;
import com.flagship.server.exception.ResourceConflictException;
import com.flagship.server.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class ExperimentService {

    private final ExperimentRepository experimentRepository;
    private final ExperimentEventRepository eventRepository;
    private final FlagRepository flagRepository;
    private final ProjectService projectService;
    private final DomainJsonCodec jsonCodec;
    private final ExperimentStatisticsService statisticsService;

    public ExperimentService(
            ExperimentRepository experimentRepository,
            ExperimentEventRepository eventRepository,
            FlagRepository flagRepository,
            ProjectService projectService,
            DomainJsonCodec jsonCodec,
            ExperimentStatisticsService statisticsService
    ) {
        this.experimentRepository = experimentRepository;
        this.eventRepository = eventRepository;
        this.flagRepository = flagRepository;
        this.projectService = projectService;
        this.jsonCodec = jsonCodec;
        this.statisticsService = statisticsService;
    }

    @Transactional
    public void recordEvents(String projectKey, String envKey, List<ExperimentEventDto> events) {
        projectService.requireEnvironmentEntity(projectKey, envKey);
        if (events == null || events.isEmpty()) return;

        Instant now = Instant.now();
        List<ExperimentEventEntity> entities = new ArrayList<>(events.size());

        for (ExperimentEventDto dto : events) {
            ExperimentEventEntity entity = new ExperimentEventEntity();
            entity.setProjectKey(projectKey);
            entity.setEnvKey(envKey);
            entity.setEventType(dto.eventType().toUpperCase());
            entity.setFlagKey(dto.flagKey());
            entity.setVariationKey(dto.variationKey());
            entity.setMetricName(dto.metricName());
            entity.setUserKey(dto.userKey());
            entity.setEventValue(dto.value() != null ? dto.value() : 1.0);
            entity.setOccurredAt(dto.timestamp() != null ? dto.timestamp() : now);
            entity.setCreatedAt(now);
            entities.add(entity);
        }

        eventRepository.saveAll(entities);
    }

    @Transactional
    public ExperimentResponse createExperiment(String projectKey, String envKey, CreateExperimentRequest request) {
        projectService.requireEnvironmentEntity(projectKey, envKey);
        if (experimentRepository.findByProjectKeyAndEnvKeyAndExperimentKey(projectKey, envKey, request.key()).isPresent()) {
            throw new ResourceConflictException("Experiment '" + request.key() + "' already exists in " + projectKey + "/" + envKey);
        }

        // Verify flag exists
        FlagEntity flag = flagRepository.findByProject_ProjectKeyAndEnvKeyAndFlagKey(projectKey, envKey, request.flagKey())
                .orElseThrow(() -> new ResourceNotFoundException("Flag '" + request.flagKey() + "' not found"));

        Instant now = Instant.now();
        ExperimentEntity entity = new ExperimentEntity();
        entity.setProjectKey(projectKey);
        entity.setEnvKey(envKey);
        entity.setExperimentKey(request.key());
        entity.setName(request.name());
        entity.setFlagKey(request.flagKey());
        entity.setMetricName(request.metricName());
        entity.setBaselineVariationKey(request.baselineVariationKey());
        entity.setStatus(ExperimentStatus.RUNNING);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        ExperimentEntity saved = experimentRepository.save(entity);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExperimentResponse> listExperiments(String projectKey, String envKey, Pageable pageable) {
        Page<ExperimentEntity> page = experimentRepository.findAllByProjectKeyAndEnvKeyOrderByCreatedAtDesc(projectKey, envKey, pageable);
        return PageResponse.from(page, this::toResponse);
    }

    @Transactional(readOnly = true)
    public ExperimentResultsResponse getExperimentResults(
            String projectKey,
            String envKey,
            String flagKey,
            String metricName,
            String baselineVariationKey
    ) {
        projectService.requireEnvironmentEntity(projectKey, envKey);
        FlagEntity flag = flagRepository.findByProject_ProjectKeyAndEnvKeyAndFlagKey(projectKey, envKey, flagKey)
                .orElseThrow(() -> new ResourceNotFoundException("Flag '" + flagKey + "' not found"));

        List<Variation> variations = jsonCodec.readVariations(flag.getVariationsJson());
        List<String> variationKeys = variations.stream().map(Variation::key).toList();

        String baseline = (baselineVariationKey != null && !baselineVariationKey.isBlank())
                ? baselineVariationKey
                : flag.getDefaultVariationKey();

        // 1. Fetch exposures per variation
        List<Object[]> rawExposures = eventRepository.countDistinctExposuresByVariation(projectKey, envKey, flagKey);
        Map<String, Long> observedExposures = new HashMap<>();
        long totalExposures = 0;
        for (Object[] row : rawExposures) {
            String vKey = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            observedExposures.put(vKey, count);
            totalExposures += count;
        }

        // 2. Fetch conversions per variation
        List<Object[]> rawConversions = eventRepository.countDistinctConversionsByVariation(projectKey, envKey, flagKey, metricName);
        Map<String, Long> conversions = new HashMap<>();
        long totalConversions = 0;
        for (Object[] row : rawConversions) {
            String vKey = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            conversions.put(vKey, count);
            totalConversions += count;
        }

        // 3. Expected weights for SRM test (from rollout or equal weights)
        Map<String, Double> expectedWeights = new HashMap<>();
        List<WeightedVariation> weightedVariations = jsonCodec.readWeightedVariations(flag.getPercentageRolloutJson());
        if (weightedVariations != null && !weightedVariations.isEmpty()) {
            for (WeightedVariation wv : weightedVariations) {
                expectedWeights.put(wv.variationKey(), (double) wv.weightBuckets());
            }
        } else {
            for (String vk : variationKeys) {
                expectedWeights.put(vk, 1.0);
            }
        }

        // 4. SRM Chi-Square Goodness of Fit Test
        var srmResult = statisticsService.evaluateSrm(variationKeys, observedExposures, expectedWeights);

        // 5. Two-proportion z-tests for significance vs baseline
        Map<String, VariationStatsDto> variationStats = statisticsService.computeVariationStats(
                variationKeys,
                baseline,
                observedExposures,
                conversions
        );

        return new ExperimentResultsResponse(
                projectKey,
                envKey,
                flagKey,
                metricName,
                baseline,
                totalExposures,
                totalConversions,
                srmResult.chiSquare(),
                srmResult.pValue(),
                srmResult.hasSrm(),
                srmResult.warningMessage(),
                variationStats
        );
    }

    private ExperimentResponse toResponse(ExperimentEntity e) {
        return new ExperimentResponse(
                e.getId(),
                e.getProjectKey(),
                e.getEnvKey(),
                e.getExperimentKey(),
                e.getName(),
                e.getFlagKey(),
                e.getMetricName(),
                e.getBaselineVariationKey(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
