package com.flagship.server.service;

import com.flagship.core.evaluation.FlagEvaluationEngine;
import com.flagship.core.model.EnvironmentSnapshot;
import com.flagship.core.model.EvaluationContext;
import com.flagship.core.model.EvaluationResult;
import com.flagship.core.model.FlagDefinition;
import com.flagship.core.model.SegmentDefinition;
import com.flagship.server.domain.EnvironmentEntity;
import com.flagship.server.domain.FlagEntity;
import com.flagship.server.domain.FlagRepository;
import com.flagship.server.domain.SegmentEntity;
import com.flagship.server.domain.SegmentRepository;
import com.flagship.server.dto.EnvironmentSnapshotResponse;
import com.flagship.server.dto.EvaluationContextRequest;
import com.flagship.server.dto.FlagResponse;
import com.flagship.server.dto.SegmentResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates flags for server-side preview and builds environment snapshots using {@link FlagEvaluationEngine} (FR2, FR3).
 */
@Service
public class EvaluationPreviewService {

    private final ProjectService projectService;
    private final FlagRepository flagRepository;
    private final SegmentRepository segmentRepository;
    private final DomainJsonCodec jsonCodec;
    private final FlagEvaluationEngine evaluationEngine;
    private final MeterRegistry meterRegistry;

    @Autowired
    public EvaluationPreviewService(
            ProjectService projectService,
            FlagRepository flagRepository,
            SegmentRepository segmentRepository,
            DomainJsonCodec jsonCodec,
            FlagEvaluationEngine evaluationEngine,
            @Autowired(required = false) MeterRegistry meterRegistry
    ) {
        this.projectService = projectService;
        this.flagRepository = flagRepository;
        this.segmentRepository = segmentRepository;
        this.jsonCodec = jsonCodec;
        this.evaluationEngine = evaluationEngine;
        this.meterRegistry = meterRegistry;
    }

    @Transactional(readOnly = true)
    public EnvironmentSnapshotResponse getEnvironmentSnapshot(String projectKey, String envKey) {
        EnvironmentEntity env = projectService.requireEnvironmentEntity(projectKey, envKey);
        List<FlagEntity> flagEntities = flagRepository.findAllByProject_ProjectKeyAndEnvKeyOrderByFlagKeyAsc(
                projectKey, envKey
        );
        List<SegmentEntity> segmentEntities = segmentRepository.findAllByProject_ProjectKeyAndEnvKeyOrderBySegmentKeyAsc(
                projectKey, envKey
        );

        Map<String, FlagResponse> flags = new LinkedHashMap<>();
        for (FlagEntity f : flagEntities) {
            flags.put(f.getFlagKey(), jsonCodec.toFlagResponse(projectKey, f));
        }

        Map<String, SegmentResponse> segments = new LinkedHashMap<>();
        for (SegmentEntity s : segmentEntities) {
            segments.put(s.getSegmentKey(), jsonCodec.toSegmentResponse(projectKey, s));
        }

        return new EnvironmentSnapshotResponse(
                projectKey,
                envKey,
                env.getRevision(),
                Instant.now(),
                flags,
                segments
        );
    }

    @Transactional(readOnly = true)
    public EnvironmentSnapshot buildCoreSnapshot(String projectKey, String envKey) {
        EnvironmentEntity env = projectService.requireEnvironmentEntity(projectKey, envKey);
        List<FlagEntity> flagEntities = flagRepository.findAllByProject_ProjectKeyAndEnvKeyOrderByFlagKeyAsc(
                projectKey, envKey
        );
        List<SegmentEntity> segmentEntities = segmentRepository.findAllByProject_ProjectKeyAndEnvKeyOrderBySegmentKeyAsc(
                projectKey, envKey
        );

        Map<String, FlagDefinition> flags = new LinkedHashMap<>();
        for (FlagEntity f : flagEntities) {
            flags.put(f.getFlagKey(), jsonCodec.toFlagDefinition(f));
        }

        Map<String, SegmentDefinition> segments = new LinkedHashMap<>();
        for (SegmentEntity s : segmentEntities) {
            segments.put(s.getSegmentKey(), jsonCodec.toSegmentDefinition(s));
        }

        return new EnvironmentSnapshot(
                projectKey,
                envKey,
                env.getRevision(),
                Instant.now(),
                flags,
                segments
        );
    }

    @Transactional
    public EvaluationResult evaluateFlag(
            String projectKey,
            String envKey,
            String flagKey,
            EvaluationContextRequest request
    ) {
        EnvironmentSnapshot snapshot = buildCoreSnapshot(projectKey, envKey);
        EvaluationContext context = request != null
                ? request.toEvaluationContext()
                : EvaluationContext.builder().build();

        EvaluationResult result = evaluationEngine.evaluate(
                snapshot,
                flagKey,
                context,
                request != null ? request.expectedType() : null,
                request != null ? request.fallbackValue() : null
        );

        flagRepository.findByProject_ProjectKeyAndEnvKeyAndFlagKey(projectKey, envKey, flagKey)
                .ifPresent(entity -> {
                    entity.setLastEvaluatedAt(Instant.now());
                    flagRepository.save(entity);
                });

        recordEvaluationMetric(projectKey, envKey, flagKey, result);
        return result;
    }

    @Transactional
    public Map<String, EvaluationResult> evaluateAllFlags(
            String projectKey,
            String envKey,
            EvaluationContextRequest request
    ) {
        EnvironmentSnapshot snapshot = buildCoreSnapshot(projectKey, envKey);
        EvaluationContext context = request != null
                ? request.toEvaluationContext()
                : EvaluationContext.builder().build();

        Map<String, EvaluationResult> results = new LinkedHashMap<>();
        for (FlagDefinition flag : snapshot.flags().values()) {
            EvaluationResult result = evaluationEngine.evaluate(flag, context, snapshot.segments());
            results.put(flag.getKey(), result);
            recordEvaluationMetric(projectKey, envKey, flag.getKey(), result);
        }

        Instant now = Instant.now();
        List<FlagEntity> entities = flagRepository.findAllByProject_ProjectKeyAndEnvKeyOrderByFlagKeyAsc(
                projectKey, envKey
        );
        for (FlagEntity entity : entities) {
            entity.setLastEvaluatedAt(now);
        }
        flagRepository.saveAll(entities);

        return results;
    }

    private void recordEvaluationMetric(
            String projectKey,
            String envKey,
            String flagKey,
            EvaluationResult result
    ) {
        if (meterRegistry != null && result != null) {
            Counter.builder("flagship.evaluations.total")
                    .tag("project", projectKey)
                    .tag("environment", envKey)
                    .tag("flag", flagKey)
                    .tag("reason", result.reason().name())
                    .tag("variation", result.variationKey() != null ? result.variationKey() : "none")
                    .register(meterRegistry)
                    .increment();
        }
    }
}
