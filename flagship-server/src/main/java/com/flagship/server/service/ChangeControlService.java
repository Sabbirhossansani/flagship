package com.flagship.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.core.model.TargetingRule;
import com.flagship.core.model.Variation;
import com.flagship.core.model.WeightedVariation;
import com.flagship.server.command.FlagshipCommand;
import com.flagship.server.domain.*;
import com.flagship.server.dto.*;
import com.flagship.server.exception.ResourceNotFoundException;
import com.flagship.server.exception.RuleValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
public class ChangeControlService {

    private static final Logger log = LoggerFactory.getLogger(ChangeControlService.class);

    private final FlagRepository flagRepository;
    private final EnvironmentRepository environmentRepository;
    private final FlagChangeHistoryRepository historyRepository;
    private final ChangeRequestRepository changeRequestRepository;
    private final ScheduledFlagChangeRepository scheduledChangeRepository;
    private final ProjectService projectService;
    private final DomainJsonCodec jsonCodec;
    private final SseBroadcasterService sseBroadcasterService;
    private final ObjectMapper objectMapper;

    public ChangeControlService(
            FlagRepository flagRepository,
            EnvironmentRepository environmentRepository,
            FlagChangeHistoryRepository historyRepository,
            ChangeRequestRepository changeRequestRepository,
            ScheduledFlagChangeRepository scheduledChangeRepository,
            ProjectService projectService,
            DomainJsonCodec jsonCodec,
            SseBroadcasterService sseBroadcasterService,
            ObjectMapper objectMapper
    ) {
        this.flagRepository = flagRepository;
        this.environmentRepository = environmentRepository;
        this.historyRepository = historyRepository;
        this.changeRequestRepository = changeRequestRepository;
        this.scheduledChangeRepository = scheduledChangeRepository;
        this.projectService = projectService;
        this.jsonCodec = jsonCodec;
        this.sseBroadcasterService = sseBroadcasterService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public <T> T executeCommand(FlagshipCommand<T> command, boolean bypassApprovalCheck) {
        EnvironmentEntity env = projectService.requireEnvironmentEntity(command.getProjectKey(), command.getEnvKey());
        if (!bypassApprovalCheck && env.isRequiresApproval()) {
            throw new RuleValidationException(
                    "Environment '" + command.getEnvKey() + "' requires two-person approval. Submit a change request instead."
            );
        }

        String beforeJson = captureState(command.getProjectKey(), command.getEnvKey(), command.getEntityType(), command.getEntityKey());
        T result = command.execute();
        String afterJson = captureState(command.getProjectKey(), command.getEnvKey(), command.getEntityType(), command.getEntityKey());

        long version = resolveVersion(result);
        String diffSummary = computeDiffSummary(beforeJson, afterJson);

        FlagChangeHistoryEntity history = new FlagChangeHistoryEntity();
        history.setProjectKey(command.getProjectKey());
        history.setEnvKey(command.getEnvKey());
        history.setEntityType(command.getEntityType());
        history.setEntityKey(command.getEntityKey());
        history.setVersion(version);
        history.setAuthor(command.getAuthor() != null ? command.getAuthor() : "system");
        history.setReason(command.getReason() != null ? command.getReason() : command.getAction());
        history.setAction(command.getAction());
        history.setBeforeJson(beforeJson);
        history.setAfterJson(afterJson);
        history.setDiffSummary(diffSummary);
        history.setCreatedAt(Instant.now());

        historyRepository.save(history);

        if (result instanceof FlagResponse flagResponse) {
            sseBroadcasterService.broadcastFlagUpdate(command.getProjectKey(), command.getEnvKey(), command.getAction(), flagResponse);
        } else if (result instanceof SegmentResponse segmentResponse) {
            sseBroadcasterService.broadcastSegmentUpdate(command.getProjectKey(), command.getEnvKey(), command.getAction(), segmentResponse);
        }

        return result;
    }

    @Transactional(readOnly = true)
    public PageResponse<ChangeHistoryResponse> getFlagHistory(
            String projectKey,
            String envKey,
            String flagKey,
            Pageable pageable
    ) {
        Page<FlagChangeHistoryEntity> page = historyRepository
                .findAllByProjectKeyAndEnvKeyAndEntityTypeAndEntityKeyOrderByVersionDesc(
                        projectKey, envKey, "FLAG", flagKey, pageable
                );
        return PageResponse.from(page, this::toHistoryResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<ChangeHistoryResponse> getAuditHistory(
            String projectKey,
            String envKey,
            Pageable pageable
    ) {
        Page<FlagChangeHistoryEntity> page = historyRepository
                .findAllByProjectKeyAndEnvKeyOrderByCreatedAtDesc(
                        projectKey, envKey, pageable
                );
        return PageResponse.from(page, this::toHistoryResponse);
    }

    @Transactional
    public FlagResponse rollbackFlag(
            String projectKey,
            String envKey,
            String flagKey,
            long targetVersion,
            String author,
            String reason
    ) {
        EnvironmentEntity env = projectService.requireEnvironmentEntity(projectKey, envKey);
        FlagEntity flag = flagRepository.findByProject_ProjectKeyAndEnvKeyAndFlagKey(projectKey, envKey, flagKey)
                .orElseThrow(() -> new ResourceNotFoundException("Flag '" + flagKey + "' not found"));

        FlagChangeHistoryEntity targetHistory = historyRepository
                .findByProjectKeyAndEnvKeyAndEntityTypeAndEntityKeyAndVersion(
                        projectKey, envKey, "FLAG", flagKey, targetVersion
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "History record for flag '" + flagKey + "' at version " + targetVersion + " not found"
                ));

        if (targetHistory.getAfterJson() == null || targetHistory.getAfterJson().isBlank()) {
            throw new RuleValidationException("Target historical version does not contain full state for restoration");
        }

        String beforeJson = captureState(projectKey, envKey, "FLAG", flagKey);

        try {
            JsonNode tree = objectMapper.readTree(targetHistory.getAfterJson());
            if (tree.has("name")) flag.setName(tree.get("name").asText());
            if (tree.has("description")) flag.setDescription(tree.get("description").asText(null));
            if (tree.has("enabled")) flag.setEnabled(tree.get("enabled").asBoolean());
            if (tree.has("killSwitch")) flag.setKillSwitch(tree.get("killSwitch").asBoolean());
            if (tree.has("defaultVariationKey")) flag.setDefaultVariationKey(tree.get("defaultVariationKey").asText());
            if (tree.has("offVariationKey")) flag.setOffVariationKey(tree.get("offVariationKey").asText());
            if (tree.has("rolloutPercentage")) flag.setRolloutPercentage(tree.get("rolloutPercentage").asDouble());
            if (tree.has("rolloutVariationKey")) flag.setRolloutVariationKey(
                    tree.get("rolloutVariationKey").isNull() ? null : tree.get("rolloutVariationKey").asText()
            );

            if (tree.has("variations")) {
                flag.setVariationsJson(tree.get("variations").toString());
            }
            if (tree.has("individualTargets")) {
                flag.setIndividualTargetsJson(tree.get("individualTargets").toString());
            }
            if (tree.has("rules")) {
                flag.setRulesJson(tree.get("rules").toString());
            }
            if (tree.has("percentageRollout")) {
                flag.setPercentageRolloutJson(tree.get("percentageRollout").toString());
            }

            Instant now = Instant.now();
            flag.setVersion(flag.getVersion() + 1);
            flag.setUpdatedAt(now);

            FlagEntity saved = flagRepository.save(flag);
            env.incrementRevision(now);
            environmentRepository.save(env);

            FlagResponse response = jsonCodec.toFlagResponse(projectKey, saved);
            String afterJson = captureState(projectKey, envKey, "FLAG", flagKey);

            FlagChangeHistoryEntity rollbackAudit = new FlagChangeHistoryEntity();
            rollbackAudit.setProjectKey(projectKey);
            rollbackAudit.setEnvKey(envKey);
            rollbackAudit.setEntityType("FLAG");
            rollbackAudit.setEntityKey(flagKey);
            rollbackAudit.setVersion(saved.getVersion());
            rollbackAudit.setAuthor(author != null ? author : "system");
            rollbackAudit.setReason(reason != null ? reason : "Rolled back to version " + targetVersion);
            rollbackAudit.setAction("ROLLBACK");
            rollbackAudit.setBeforeJson(beforeJson);
            rollbackAudit.setAfterJson(afterJson);
            rollbackAudit.setDiffSummary("Rolled back state from version " + targetVersion);
            rollbackAudit.setCreatedAt(now);
            historyRepository.save(rollbackAudit);

            sseBroadcasterService.broadcastFlagUpdate(projectKey, envKey, "ROLLBACK", response);

            return response;
        } catch (Exception ex) {
            log.error("Rollback failed for flag {}/{} at version {}", projectKey, flagKey, targetVersion, ex);
            throw new RuleValidationException("Failed to rollback flag: " + ex.getMessage());
        }
    }

    // --- Two-Person Approval Workflows ---

    @Transactional
    public ChangeRequestResponse createChangeRequest(
            String projectKey,
            String envKey,
            CreateChangeRequestDto dto,
            String author
    ) {
        projectService.requireEnvironmentEntity(projectKey, envKey);

        ChangeRequestEntity entity = new ChangeRequestEntity();
        entity.setProjectKey(projectKey);
        entity.setEnvKey(envKey);
        entity.setEntityType(dto.entityType().toUpperCase());
        entity.setEntityKey(dto.entityKey());
        entity.setAction(dto.action().toUpperCase());
        entity.setPayloadJson(dto.payloadJson());
        entity.setAuthor(author != null ? author : "unknown");
        entity.setReason(dto.reason());
        entity.setStatus(ChangeRequestStatus.PENDING);
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        ChangeRequestEntity saved = changeRequestRepository.save(entity);
        return toChangeRequestResponse(saved);
    }

    @Transactional
    public ChangeRequestResponse approveChangeRequest(
            String projectKey,
            String envKey,
            Long requestId,
            String approver
    ) {
        ChangeRequestEntity req = changeRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Change request #" + requestId + " not found"));

        if (!req.getProjectKey().equals(projectKey) || !req.getEnvKey().equals(envKey)) {
            throw new ResourceNotFoundException("Change request #" + requestId + " not in " + projectKey + "/" + envKey);
        }

        if (req.getStatus() != ChangeRequestStatus.PENDING) {
            throw new RuleValidationException("Change request #" + requestId + " is not pending (status: " + req.getStatus() + ")");
        }

        // Two-person rule enforcement:
        if (req.getAuthor() != null && req.getAuthor().equalsIgnoreCase(approver)) {
            throw new RuleValidationException(
                    "Two-person approval violated: Author '" + req.getAuthor() + "' cannot approve their own change request."
            );
        }

        applyChangeRequestPayload(req, approver);

        Instant now = Instant.now();
        req.setStatus(ChangeRequestStatus.APPROVED);
        req.setApprovedBy(approver);
        req.setApprovedAt(now);
        req.setUpdatedAt(now);

        return toChangeRequestResponse(changeRequestRepository.save(req));
    }

    @Transactional
    public ChangeRequestResponse rejectChangeRequest(
            String projectKey,
            String envKey,
            Long requestId,
            String rejector,
            String rejectionReason
    ) {
        ChangeRequestEntity req = changeRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Change request #" + requestId + " not found"));

        if (!req.getProjectKey().equals(projectKey) || !req.getEnvKey().equals(envKey)) {
            throw new ResourceNotFoundException("Change request #" + requestId + " not in " + projectKey + "/" + envKey);
        }

        if (req.getStatus() != ChangeRequestStatus.PENDING) {
            throw new RuleValidationException("Change request #" + requestId + " is not pending (status: " + req.getStatus() + ")");
        }

        Instant now = Instant.now();
        req.setStatus(ChangeRequestStatus.REJECTED);
        req.setApprovedBy(rejector);
        req.setRejectionReason(rejectionReason);
        req.setUpdatedAt(now);

        return toChangeRequestResponse(changeRequestRepository.save(req));
    }

    @Transactional(readOnly = true)
    public PageResponse<ChangeRequestResponse> listChangeRequests(
            String projectKey,
            String envKey,
            ChangeRequestStatus status,
            Pageable pageable
    ) {
        Page<ChangeRequestEntity> page = (status == null)
                ? changeRequestRepository.findAllByProjectKeyAndEnvKeyOrderByCreatedAtDesc(projectKey, envKey, pageable)
                : changeRequestRepository.findAllByProjectKeyAndEnvKeyAndStatusOrderByCreatedAtDesc(projectKey, envKey, status, pageable);

        return PageResponse.from(page, this::toChangeRequestResponse);
    }

    private void applyChangeRequestPayload(ChangeRequestEntity req, String approver) {
        try {
            if ("FLAG".equalsIgnoreCase(req.getEntityType())) {
                FlagEntity flag = flagRepository.findByProject_ProjectKeyAndEnvKeyAndFlagKey(
                        req.getProjectKey(), req.getEnvKey(), req.getEntityKey()
                ).orElseThrow(() -> new ResourceNotFoundException("Flag '" + req.getEntityKey() + "' not found"));

                String beforeJson = captureState(req.getProjectKey(), req.getEnvKey(), "FLAG", req.getEntityKey());
                Instant now = Instant.now();

                if ("DELETE".equalsIgnoreCase(req.getAction())) {
                    flagRepository.delete(flag);
                    EnvironmentEntity env = projectService.requireEnvironmentEntity(req.getProjectKey(), req.getEnvKey());
                    env.incrementRevision(now);
                    environmentRepository.save(env);

                    FlagChangeHistoryEntity history = new FlagChangeHistoryEntity();
                    history.setProjectKey(req.getProjectKey());
                    history.setEnvKey(req.getEnvKey());
                    history.setEntityType("FLAG");
                    history.setEntityKey(req.getEntityKey());
                    history.setVersion(flag.getVersion() + 1);
                    history.setAuthor(approver + " (on behalf of " + req.getAuthor() + ")");
                    history.setReason(req.getReason());
                    history.setAction("DELETE_APPROVED");
                    history.setBeforeJson(beforeJson);
                    history.setAfterJson(null);
                    history.setDiffSummary("Approved deletion of flag '" + req.getEntityKey() + "' by " + approver);
                    history.setCreatedAt(now);
                    historyRepository.save(history);

                    FlagResponse resp = jsonCodec.toFlagResponse(req.getProjectKey(), flag);
                    sseBroadcasterService.broadcastFlagUpdate(req.getProjectKey(), req.getEnvKey(), "DELETE", resp);
                    return;
                }

                if ("UPDATE".equalsIgnoreCase(req.getAction())) {
                    UpdateFlagRequest updateReq = objectMapper.readValue(req.getPayloadJson(), UpdateFlagRequest.class);
                    if (updateReq.name() != null) flag.setName(updateReq.name());
                    if (updateReq.description() != null) flag.setDescription(updateReq.description());
                    if (updateReq.owner() != null && !updateReq.owner().isBlank()) flag.setOwner(updateReq.owner());
                    if (updateReq.enabled() != null) flag.setEnabled(updateReq.enabled());
                    if (updateReq.killSwitch() != null) flag.setKillSwitch(updateReq.killSwitch());
                    if (updateReq.defaultVariationKey() != null) flag.setDefaultVariationKey(updateReq.defaultVariationKey());
                    if (updateReq.offVariationKey() != null) flag.setOffVariationKey(updateReq.offVariationKey());

                    if (updateReq.variations() != null && !updateReq.variations().isEmpty()) {
                        List<Variation> vars = updateReq.variations().stream()
                                .map(v -> new Variation(v.key(), flag.getVariationType(), v.value(), v.description()))
                                .toList();
                        flag.setVariationsJson(jsonCodec.writeVariations(vars));
                    }
                    if (updateReq.individualTargets() != null) {
                        flag.setIndividualTargetsJson(jsonCodec.writeStringMap(updateReq.individualTargets()));
                    }
                    if (updateReq.rules() != null) {
                        flag.setRulesJson(jsonCodec.writeRules(updateReq.rules()));
                    }
                    if (updateReq.rolloutPercentage() != null) flag.setRolloutPercentage(updateReq.rolloutPercentage());
                    if (updateReq.rolloutVariationKey() != null) flag.setRolloutVariationKey(updateReq.rolloutVariationKey());
                    if (updateReq.percentageRollout() != null) {
                        flag.setPercentageRolloutJson(jsonCodec.writeWeightedVariations(updateReq.percentageRollout()));
                    }

                    if (flag.isEnabled() && !flag.isKillSwitch() && flag.getRolloutPercentage() >= 100.0) {
                        if (flag.getFullRolloutSince() == null) {
                            flag.setFullRolloutSince(now);
                        }
                    } else {
                        flag.setFullRolloutSince(null);
                    }
                } else if ("KILL_SWITCH".equalsIgnoreCase(req.getAction())) {
                    KillSwitchRequest ks = objectMapper.readValue(req.getPayloadJson(), KillSwitchRequest.class);
                    flag.setKillSwitch(ks.killSwitch());
                    if (ks.killSwitch()) {
                        flag.setFullRolloutSince(null);
                    } else if (flag.isEnabled() && flag.getRolloutPercentage() >= 100.0) {
                        if (flag.getFullRolloutSince() == null) {
                            flag.setFullRolloutSince(now);
                        }
                    }
                }

                flag.setVersion(flag.getVersion() + 1);
                flag.setUpdatedAt(now);
                FlagEntity saved = flagRepository.save(flag);

                EnvironmentEntity env = projectService.requireEnvironmentEntity(req.getProjectKey(), req.getEnvKey());
                env.incrementRevision(now);
                environmentRepository.save(env);

                FlagResponse resp = jsonCodec.toFlagResponse(req.getProjectKey(), saved);
                String afterJson = captureState(req.getProjectKey(), req.getEnvKey(), "FLAG", req.getEntityKey());

                FlagChangeHistoryEntity history = new FlagChangeHistoryEntity();
                history.setProjectKey(req.getProjectKey());
                history.setEnvKey(req.getEnvKey());
                history.setEntityType("FLAG");
                history.setEntityKey(req.getEntityKey());
                history.setVersion(saved.getVersion());
                history.setAuthor(approver + " (on behalf of " + req.getAuthor() + ")");
                history.setReason(req.getReason());
                history.setAction(req.getAction() + "_APPROVED");
                history.setBeforeJson(beforeJson);
                history.setAfterJson(afterJson);
                history.setDiffSummary("Approved change request #" + req.getId() + " by " + approver);
                history.setCreatedAt(now);
                historyRepository.save(history);

                sseBroadcasterService.broadcastFlagUpdate(req.getProjectKey(), req.getEnvKey(), req.getAction(), resp);
            }
        } catch (Exception ex) {
            log.error("Failed to apply change request payload for #{}", req.getId(), ex);
            throw new RuleValidationException("Could not apply change request payload: " + ex.getMessage());
        }
    }

    private String captureState(String projectKey, String envKey, String entityType, String entityKey) {
        if ("FLAG".equalsIgnoreCase(entityType)) {
            return flagRepository.findByProject_ProjectKeyAndEnvKeyAndFlagKey(projectKey, envKey, entityKey)
                    .map(flag -> {
                        try {
                            return objectMapper.writeValueAsString(jsonCodec.toFlagResponse(projectKey, flag));
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .orElse(null);
        }
        return null;
    }

    private long resolveVersion(Object result) {
        if (result instanceof FlagResponse fr) {
            return fr.version();
        } else if (result instanceof SegmentResponse sr) {
            return sr.version();
        }
        return 1L;
    }

    private String computeDiffSummary(String beforeJson, String afterJson) {
        if (beforeJson == null) return "Entity created";
        if (afterJson == null) return "Entity deleted";

        try {
            JsonNode bNode = objectMapper.readTree(beforeJson);
            JsonNode aNode = objectMapper.readTree(afterJson);
            List<String> changes = new ArrayList<>();

            Iterator<String> fieldNames = aNode.fieldNames();
            while (fieldNames.hasNext()) {
                String field = fieldNames.next();
                JsonNode bVal = bNode.get(field);
                JsonNode aVal = aNode.get(field);
                if (bVal == null || !bVal.equals(aVal)) {
                    changes.add(field + ": " + (bVal == null ? "null" : bVal.asText()) + " -> " + aVal.asText());
                }
            }
            return changes.isEmpty() ? "No semantic changes" : String.join(", ", changes);
        } catch (Exception e) {
            return "Updated attributes";
        }
    }

    private ChangeHistoryResponse toHistoryResponse(FlagChangeHistoryEntity e) {
        return new ChangeHistoryResponse(
                e.getId(),
                e.getProjectKey(),
                e.getEnvKey(),
                e.getEntityType(),
                e.getEntityKey(),
                e.getVersion(),
                e.getAuthor(),
                e.getReason(),
                e.getAction(),
                e.getBeforeJson(),
                e.getAfterJson(),
                e.getDiffSummary(),
                e.getCreatedAt()
        );
    }

    private ChangeRequestResponse toChangeRequestResponse(ChangeRequestEntity e) {
        return new ChangeRequestResponse(
                e.getId(),
                e.getProjectKey(),
                e.getEnvKey(),
                e.getEntityType(),
                e.getEntityKey(),
                e.getAction(),
                e.getPayloadJson(),
                e.getAuthor(),
                e.getReason(),
                e.getStatus(),
                e.getApprovedBy(),
                e.getApprovedAt(),
                e.getRejectionReason(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
