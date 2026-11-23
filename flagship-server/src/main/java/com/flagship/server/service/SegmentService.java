package com.flagship.server.service;

import com.flagship.core.rule.RuleNode;
import com.flagship.core.rule.RuleValidationVisitor;
import com.flagship.server.domain.EnvironmentEntity;
import com.flagship.server.domain.EnvironmentRepository;
import com.flagship.server.domain.SegmentEntity;
import com.flagship.server.domain.SegmentRepository;
import com.flagship.server.dto.CreateSegmentRequest;
import com.flagship.server.dto.PageResponse;
import com.flagship.server.dto.RuleNodeDto;
import com.flagship.server.dto.SegmentResponse;
import com.flagship.server.dto.UpdateSegmentRequest;
import com.flagship.server.exception.ResourceConflictException;
import com.flagship.server.exception.ResourceNotFoundException;
import com.flagship.server.exception.RuleValidationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class SegmentService {

    private final ProjectService projectService;
    private final EnvironmentRepository environmentRepository;
    private final SegmentRepository segmentRepository;
    private final DomainJsonCodec jsonCodec;
    private final RuleValidationVisitor validationVisitor;

    public SegmentService(
            ProjectService projectService,
            EnvironmentRepository environmentRepository,
            SegmentRepository segmentRepository,
            DomainJsonCodec jsonCodec,
            RuleValidationVisitor validationVisitor
    ) {
        this.projectService = projectService;
        this.environmentRepository = environmentRepository;
        this.segmentRepository = segmentRepository;
        this.jsonCodec = jsonCodec;
        this.validationVisitor = validationVisitor;
    }

    @Transactional
    public SegmentResponse createSegment(String projectKey, String envKey, CreateSegmentRequest request) {
        EnvironmentEntity env = projectService.requireEnvironmentEntity(projectKey, envKey);
        if (segmentRepository.existsByProject_ProjectKeyAndEnvKeyAndSegmentKey(projectKey, envKey, request.key())) {
            throw new ResourceConflictException(
                    "Segment '" + request.key() + "' already exists in " + projectKey + "/" + envKey
            );
        }
        validateCondition(request.condition());
        validateUserSet("includedUsers", request.includedUsers());
        validateUserSet("excludedUsers", request.excludedUsers());

        Instant now = Instant.now();
        SegmentEntity entity = new SegmentEntity();
        entity.setProject(env.getProject());
        entity.setEnvKey(envKey);
        entity.setSegmentKey(request.key());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setConditionJson(jsonCodec.writeRuleNode(request.condition()));
        entity.setIncludedUsersJson(jsonCodec.writeStringSet(request.includedUsers()));
        entity.setExcludedUsersJson(jsonCodec.writeStringSet(request.excludedUsers()));
        entity.setVersion(1L);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        SegmentEntity saved = segmentRepository.save(entity);
        env.incrementRevision(now);
        environmentRepository.save(env);
        return jsonCodec.toSegmentResponse(projectKey, saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<SegmentResponse> listSegments(String projectKey, String envKey, Pageable pageable) {
        projectService.requireEnvironmentEntity(projectKey, envKey);
        Page<SegmentEntity> page = segmentRepository.findAllByProject_ProjectKeyAndEnvKeyOrderBySegmentKeyAsc(
                projectKey, envKey, pageable
        );
        return PageResponse.from(page, s -> jsonCodec.toSegmentResponse(projectKey, s));
    }

    @Transactional(readOnly = true)
    public SegmentResponse getSegment(String projectKey, String envKey, String segmentKey) {
        projectService.requireEnvironmentEntity(projectKey, envKey);
        SegmentEntity entity = requireSegmentEntity(projectKey, envKey, segmentKey);
        return jsonCodec.toSegmentResponse(projectKey, entity);
    }

    @Transactional
    public SegmentResponse updateSegment(
            String projectKey,
            String envKey,
            String segmentKey,
            UpdateSegmentRequest request
    ) {
        EnvironmentEntity env = projectService.requireEnvironmentEntity(projectKey, envKey);
        SegmentEntity entity = requireSegmentEntity(projectKey, envKey, segmentKey);
        validateCondition(request.condition());
        validateUserSet("includedUsers", request.includedUsers());
        validateUserSet("excludedUsers", request.excludedUsers());

        Instant now = Instant.now();
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setConditionJson(jsonCodec.writeRuleNode(request.condition()));
        entity.setIncludedUsersJson(jsonCodec.writeStringSet(request.includedUsers()));
        entity.setExcludedUsersJson(jsonCodec.writeStringSet(request.excludedUsers()));
        entity.setVersion(entity.getVersion() + 1);
        entity.setUpdatedAt(now);

        SegmentEntity saved = segmentRepository.save(entity);
        env.incrementRevision(now);
        environmentRepository.save(env);
        return jsonCodec.toSegmentResponse(projectKey, saved);
    }

    @Transactional
    public void deleteSegment(String projectKey, String envKey, String segmentKey) {
        EnvironmentEntity env = projectService.requireEnvironmentEntity(projectKey, envKey);
        SegmentEntity entity = requireSegmentEntity(projectKey, envKey, segmentKey);
        segmentRepository.delete(entity);
        env.incrementRevision(Instant.now());
        environmentRepository.save(env);
    }

    public SegmentEntity requireSegmentEntity(String projectKey, String envKey, String segmentKey) {
        return segmentRepository.findByProject_ProjectKeyAndEnvKeyAndSegmentKey(projectKey, envKey, segmentKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Segment '" + segmentKey + "' not found in " + projectKey + "/" + envKey
                ));
    }

    private void validateCondition(RuleNodeDto conditionDto) {
        if (conditionDto == null) {
            return;
        }
        RuleNode node = conditionDto.toDomain();
        List<String> errors = node.accept(validationVisitor);
        if (!errors.isEmpty()) {
            throw new RuleValidationException("Segment condition rule tree failed validation", errors);
        }
    }

    private void validateUserSet(String fieldName, java.util.Set<String> users) {
        if (users == null) {
            return;
        }
        for (String userKey : users) {
            if (userKey == null || userKey.isBlank()) {
                throw new IllegalArgumentException(fieldName + " must not contain null or blank user keys");
            }
        }
    }
}
