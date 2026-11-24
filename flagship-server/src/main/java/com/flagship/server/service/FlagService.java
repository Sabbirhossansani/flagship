package com.flagship.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.core.model.TargetingRule;
import com.flagship.core.model.Variation;
import com.flagship.core.model.VariationType;
import com.flagship.core.model.WeightedVariation;
import com.flagship.core.rule.RuleDescribeVisitor;
import com.flagship.core.rule.RuleValidationVisitor;
import com.flagship.server.domain.EnvironmentEntity;
import com.flagship.server.domain.EnvironmentRepository;
import com.flagship.server.domain.FlagChangeHistoryEntity;
import com.flagship.server.domain.FlagChangeHistoryRepository;
import com.flagship.server.domain.FlagEntity;
import com.flagship.server.domain.FlagRepository;
import com.flagship.server.dto.CreateFlagRequest;
import com.flagship.server.dto.FlagResponse;
import com.flagship.server.dto.KillSwitchRequest;
import com.flagship.server.dto.PageResponse;
import com.flagship.server.dto.TargetingRuleDto;
import com.flagship.server.dto.UpdateFlagRequest;
import com.flagship.server.dto.VariationRequest;
import com.flagship.server.exception.ResourceConflictException;
import com.flagship.server.exception.ResourceNotFoundException;
import com.flagship.server.exception.RuleValidationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class FlagService {

    private final ProjectService projectService;
    private final EnvironmentRepository environmentRepository;
    private final FlagRepository flagRepository;
    private final DomainJsonCodec jsonCodec;
    private final RuleValidationVisitor validationVisitor;
    private final RuleDescribeVisitor describeVisitor;
    private final SseBroadcasterService sseBroadcasterService;
    private final FlagChangeHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    public FlagService(
            ProjectService projectService,
            EnvironmentRepository environmentRepository,
            FlagRepository flagRepository,
            DomainJsonCodec jsonCodec,
            RuleValidationVisitor validationVisitor,
            RuleDescribeVisitor describeVisitor,
            SseBroadcasterService sseBroadcasterService,
            FlagChangeHistoryRepository historyRepository,
            ObjectMapper objectMapper
    ) {
        this.projectService = projectService;
        this.environmentRepository = environmentRepository;
        this.flagRepository = flagRepository;
        this.jsonCodec = jsonCodec;
        this.validationVisitor = validationVisitor;
        this.describeVisitor = describeVisitor;
        this.sseBroadcasterService = sseBroadcasterService;
        this.historyRepository = historyRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FlagResponse createFlag(String projectKey, String envKey, CreateFlagRequest request) {
        EnvironmentEntity env = projectService.requireEnvironmentEntity(projectKey, envKey);
        if (flagRepository.existsByProject_ProjectKeyAndEnvKeyAndFlagKey(projectKey, envKey, request.key())) {
            throw new ResourceConflictException(
                    "Flag '" + request.key() + "' already exists in " + projectKey + "/" + envKey
            );
        }

        List<Variation> normalizedVariations = normalizeAndValidateVariations(request.type(), request.variations());
        Set<String> variationKeys = collectVariationKeys(normalizedVariations);
        String defaultVarKey = request.defaultVariationKey();
        String offVarKey = (request.offVariationKey() != null && !request.offVariationKey().isBlank())
                ? request.offVariationKey()
                : defaultVarKey;

        validateVariationReferences(
                variationKeys,
                defaultVarKey,
                offVarKey,
                request.individualTargets(),
                request.rules(),
                request.rolloutPercentage(),
                request.rolloutVariationKey(),
                request.percentageRollout()
        );

        Instant now = Instant.now();
        boolean enabled = request.enabled() == null || request.enabled();
        boolean killSwitch = request.killSwitch() != null && request.killSwitch();
        double rolloutPct = request.rolloutPercentage() != null ? request.rolloutPercentage() : 0.0;

        FlagEntity entity = new FlagEntity();
        entity.setProject(env.getProject());
        entity.setEnvKey(envKey);
        entity.setFlagKey(request.key());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setOwner(request.owner() != null && !request.owner().isBlank() ? request.owner() : "unassigned");
        entity.setVariationType(request.type());
        entity.setEnabled(enabled);
        entity.setKillSwitch(killSwitch);
        entity.setVariationsJson(jsonCodec.writeVariations(normalizedVariations));
        entity.setDefaultVariationKey(defaultVarKey);
        entity.setOffVariationKey(offVarKey);
        entity.setIndividualTargetsJson(jsonCodec.writeStringMap(request.individualTargets()));
        entity.setRulesJson(jsonCodec.writeRules(normalizeRuleDtos(request.rules())));
        entity.setRolloutPercentage(rolloutPct);
        entity.setRolloutVariationKey(request.rolloutVariationKey());
        entity.setPercentageRolloutJson(jsonCodec.writeWeightedVariations(request.percentageRollout()));
        entity.setVersion(1L);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        if (enabled && !killSwitch && rolloutPct >= 100.0) {
            entity.setFullRolloutSince(now);
        }

        FlagEntity saved = flagRepository.save(entity);
        env.incrementRevision(now);
        environmentRepository.save(env);
        FlagResponse response = jsonCodec.toFlagResponse(projectKey, saved);
        recordAudit(projectKey, envKey, saved.getFlagKey(), saved.getVersion(), saved.getOwner(), "Flag created", "CREATE", null, response, "Created flag");
        sseBroadcasterService.broadcastFlagUpdate(projectKey, envKey, "CREATE", response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<FlagResponse> listFlags(
            String projectKey,
            String envKey,
            boolean staleOnly,
            Pageable pageable
    ) {
        projectService.requireEnvironmentEntity(projectKey, envKey);
        if (!staleOnly) {
            Page<FlagEntity> page = flagRepository.findAllByProject_ProjectKeyAndEnvKeyOrderByFlagKeyAsc(
                    projectKey, envKey, pageable
            );
            return PageResponse.from(page, f -> jsonCodec.toFlagResponse(projectKey, f));
        }
        List<FlagResponse> staleFlags = flagRepository.findAllByProject_ProjectKeyAndEnvKeyOrderByFlagKeyAsc(
                        projectKey, envKey
                )
                .stream()
                .map(f -> jsonCodec.toFlagResponse(projectKey, f))
                .filter(FlagResponse::stale)
                .toList();
        return PageResponse.ofList(staleFlags, pageable.getPageNumber(), pageable.getPageSize());
    }

    @Transactional(readOnly = true)
    public FlagResponse getFlag(String projectKey, String envKey, String flagKey) {
        projectService.requireEnvironmentEntity(projectKey, envKey);
        FlagEntity entity = requireFlagEntity(projectKey, envKey, flagKey);
        return jsonCodec.toFlagResponse(projectKey, entity);
    }

    @Transactional
    public FlagResponse updateFlag(
            String projectKey,
            String envKey,
            String flagKey,
            UpdateFlagRequest request
    ) {
        EnvironmentEntity env = projectService.requireEnvironmentEntity(projectKey, envKey);
        FlagEntity entity = requireFlagEntity(projectKey, envKey, flagKey);

        List<Variation> normalizedVariations = normalizeAndValidateVariations(
                entity.getVariationType(),
                request.variations()
        );
        Set<String> variationKeys = collectVariationKeys(normalizedVariations);
        String defaultVarKey = request.defaultVariationKey();
        String offVarKey = (request.offVariationKey() != null && !request.offVariationKey().isBlank())
                ? request.offVariationKey()
                : defaultVarKey;

        validateVariationReferences(
                variationKeys,
                defaultVarKey,
                offVarKey,
                request.individualTargets(),
                request.rules(),
                request.rolloutPercentage(),
                request.rolloutVariationKey(),
                request.percentageRollout()
        );

        Instant now = Instant.now();
        boolean enabled = request.enabled() != null ? request.enabled() : entity.isEnabled();
        boolean killSwitch = request.killSwitch() != null ? request.killSwitch() : entity.isKillSwitch();
        double rolloutPct = request.rolloutPercentage() != null
                ? request.rolloutPercentage()
                : entity.getRolloutPercentage();

        entity.setName(request.name());
        entity.setDescription(request.description());
        if (request.owner() != null && !request.owner().isBlank()) {
            entity.setOwner(request.owner());
        }
        entity.setEnabled(enabled);
        entity.setKillSwitch(killSwitch);
        entity.setVariationsJson(jsonCodec.writeVariations(normalizedVariations));
        entity.setDefaultVariationKey(defaultVarKey);
        entity.setOffVariationKey(offVarKey);
        entity.setIndividualTargetsJson(jsonCodec.writeStringMap(request.individualTargets()));
        entity.setRulesJson(jsonCodec.writeRules(normalizeRuleDtos(request.rules())));
        entity.setRolloutPercentage(rolloutPct);
        entity.setRolloutVariationKey(request.rolloutVariationKey());
        entity.setPercentageRolloutJson(jsonCodec.writeWeightedVariations(request.percentageRollout()));
        entity.setVersion(entity.getVersion() + 1);
        entity.setUpdatedAt(now);

        if (enabled && !killSwitch && rolloutPct >= 100.0) {
            if (entity.getFullRolloutSince() == null) {
                entity.setFullRolloutSince(now);
            }
        } else {
            entity.setFullRolloutSince(null);
        }

        FlagEntity saved = flagRepository.save(entity);
        env.incrementRevision(now);
        environmentRepository.save(env);
        FlagResponse response = jsonCodec.toFlagResponse(projectKey, saved);
        recordAudit(projectKey, envKey, saved.getFlagKey(), saved.getVersion(), saved.getOwner(), "Flag updated", "UPDATE", null, response, "Updated flag attributes");
        sseBroadcasterService.broadcastFlagUpdate(projectKey, envKey, "UPDATE", response);
        return response;
    }

    @Transactional
    public FlagResponse toggleKillSwitch(
            String projectKey,
            String envKey,
            String flagKey,
            KillSwitchRequest request
    ) {
        if (request == null || request.killSwitch() == null) {
            throw new IllegalArgumentException("killSwitch value must be provided");
        }
        EnvironmentEntity env = projectService.requireEnvironmentEntity(projectKey, envKey);
        FlagEntity entity = requireFlagEntity(projectKey, envKey, flagKey);
        Instant now = Instant.now();
        boolean killSwitch = request.killSwitch();
        entity.setKillSwitch(killSwitch);
        entity.setVersion(entity.getVersion() + 1);
        entity.setUpdatedAt(now);
        if (entity.isEnabled() && !killSwitch && entity.getRolloutPercentage() >= 100.0) {
            if (entity.getFullRolloutSince() == null) {
                entity.setFullRolloutSince(now);
            }
        } else {
            entity.setFullRolloutSince(null);
        }
        FlagEntity saved = flagRepository.save(entity);
        env.incrementRevision(now);
        environmentRepository.save(env);
        FlagResponse response = jsonCodec.toFlagResponse(projectKey, saved);
        recordAudit(projectKey, envKey, saved.getFlagKey(), saved.getVersion(), saved.getOwner(),
                killSwitch ? "Kill switch engaged" : "Kill switch disengaged",
                "KILL_SWITCH", null, response, "Kill switch set to " + killSwitch);
        sseBroadcasterService.broadcastFlagUpdate(projectKey, envKey, "KILL_SWITCH", response);
        return response;
    }

    @Transactional
    public void deleteFlag(String projectKey, String envKey, String flagKey) {
        EnvironmentEntity env = projectService.requireEnvironmentEntity(projectKey, envKey);
        FlagEntity entity = requireFlagEntity(projectKey, envKey, flagKey);
        FlagResponse lastResponse = jsonCodec.toFlagResponse(projectKey, entity);
        flagRepository.delete(entity);
        env.incrementRevision(Instant.now());
        environmentRepository.save(env);
        recordAudit(projectKey, envKey, flagKey, entity.getVersion() + 1, entity.getOwner(),
                "Flag deleted", "DELETE", lastResponse, null, "Deleted flag");
        sseBroadcasterService.broadcastFlagUpdate(projectKey, envKey, "DELETE", lastResponse);
    }

    private void recordAudit(
            String projectKey,
            String envKey,
            String flagKey,
            long version,
            String author,
            String reason,
            String action,
            FlagResponse before,
            FlagResponse after,
            String diffSummary
    ) {
        try {
            FlagChangeHistoryEntity history = new FlagChangeHistoryEntity();
            history.setProjectKey(projectKey);
            history.setEnvKey(envKey);
            history.setEntityType("FLAG");
            history.setEntityKey(flagKey);
            history.setVersion(version);
            history.setAuthor(author != null ? author : "system");
            history.setReason(reason != null ? reason : action);
            history.setAction(action);
            history.setBeforeJson(before != null ? objectMapper.writeValueAsString(before) : null);
            history.setAfterJson(after != null ? objectMapper.writeValueAsString(after) : null);
            history.setDiffSummary(diffSummary != null ? diffSummary : action);
            history.setCreatedAt(Instant.now());
            historyRepository.save(history);
        } catch (Exception ex) {
            // Audit persistence should not prevent flag operations
        }
    }

    public FlagEntity requireFlagEntity(String projectKey, String envKey, String flagKey) {
        return flagRepository.findByProject_ProjectKeyAndEnvKeyAndFlagKey(projectKey, envKey, flagKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Flag '" + flagKey + "' not found in " + projectKey + "/" + envKey
                ));
    }

    private List<Variation> normalizeAndValidateVariations(
            VariationType flagType,
            List<VariationRequest> variations
    ) {
        if (variations == null || variations.isEmpty()) {
            throw new IllegalArgumentException("At least one variation must be provided");
        }
        List<Variation> normalized = new ArrayList<>(variations.size());
        Set<String> seenKeys = new HashSet<>();
        for (VariationRequest v : variations) {
            if (v == null || v.key() == null || v.key().isBlank()) {
                throw new IllegalArgumentException("Variation key must not be blank");
            }
            if (!seenKeys.add(v.key())) {
                throw new IllegalArgumentException("Duplicate variation key '" + v.key() + "'");
            }
            VariationType vType = v.type() != null ? v.type() : flagType;
            if (vType != flagType) {
                throw new IllegalArgumentException(
                        "Variation '" + v.key() + "' has type " + vType + " which does not match flag type " + flagType
                );
            }
            validateVariationValue(v.key(), flagType, v.value());
            normalized.add(new Variation(v.key(), flagType, v.value(), v.description()));
        }
        return normalized;
    }

    private void validateVariationValue(String variationKey, VariationType flagType, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Variation '" + variationKey + "' value must not be null");
        }
        switch (flagType) {
            case BOOLEAN -> {
                if (!(value instanceof Boolean)) {
                    throw new IllegalArgumentException(
                            "Variation '" + variationKey + "' of type BOOLEAN must have a boolean value"
                    );
                }
            }
            case NUMBER -> {
                if (!(value instanceof Number)) {
                    throw new IllegalArgumentException(
                            "Variation '" + variationKey + "' of type NUMBER must have a numeric value"
                    );
                }
            }
            case STRING -> {
                if (!(value instanceof String)) {
                    throw new IllegalArgumentException(
                            "Variation '" + variationKey + "' of type STRING must have a string value"
                    );
                }
            }
            case JSON -> {
                if (!(value instanceof String || value instanceof Map<?, ?> || value instanceof List<?>)) {
                    throw new IllegalArgumentException(
                            "Variation '" + variationKey + "' of type JSON must have a JSON string, object, or array value"
                    );
                }
            }
        }
    }

    private Set<String> collectVariationKeys(List<Variation> variations) {
        Set<String> keys = new HashSet<>();
        for (Variation v : variations) {
            keys.add(v.key());
        }
        return keys;
    }

    private List<TargetingRuleDto> normalizeRuleDtos(List<TargetingRuleDto> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        return rules.stream()
                .map(dto -> TargetingRuleDto.fromDomain(dto.toDomain(), describeVisitor))
                .toList();
    }

    private void validateVariationReferences(
            Set<String> variationKeys,
            String defaultVariationKey,
            String offVariationKey,
            Map<String, String> individualTargets,
            List<TargetingRuleDto> rules,
            Double rolloutPercentage,
            String rolloutVariationKey,
            List<WeightedVariation> percentageRollout
    ) {
        if (!variationKeys.contains(defaultVariationKey)) {
            throw new IllegalArgumentException(
                    "defaultVariationKey '" + defaultVariationKey + "' does not exist in flag variations"
            );
        }
        if (!variationKeys.contains(offVariationKey)) {
            throw new IllegalArgumentException(
                    "offVariationKey '" + offVariationKey + "' does not exist in flag variations"
            );
        }
        if (individualTargets != null) {
            for (Map.Entry<String, String> entry : individualTargets.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    throw new IllegalArgumentException("Individual target user key must not be blank");
                }
                if (!variationKeys.contains(entry.getValue())) {
                    throw new IllegalArgumentException(
                            "Individual target user '" + entry.getKey() + "' references unknown variation '"
                                    + entry.getValue() + "'"
                    );
                }
            }
        }
        if (rules != null) {
            List<String> allRuleErrors = new ArrayList<>();
            for (TargetingRuleDto ruleDto : rules) {
                if (ruleDto == null) {
                    throw new IllegalArgumentException("Targeting rule entry must not be null");
                }
                TargetingRule domainRule = ruleDto.toDomain();
                boolean hasSegment = domainRule.segmentKey() != null && !domainRule.segmentKey().isBlank();
                boolean hasCondition = domainRule.condition() != null;
                if (!hasSegment && !hasCondition) {
                    throw new IllegalArgumentException(
                            "Targeting rule '" + domainRule.name() + "' must specify at least a segmentKey or a condition"
                    );
                }
                if (hasCondition) {
                    allRuleErrors.addAll(domainRule.condition().accept(validationVisitor));
                }
                boolean hasVariation = domainRule.variationKey() != null && !domainRule.variationKey().isBlank();
                boolean hasRollout = !domainRule.rolloutDistributions().isEmpty();
                if (!hasVariation && !hasRollout) {
                    throw new IllegalArgumentException(
                            "Targeting rule '" + domainRule.name() + "' must specify either variationKey or rolloutDistributions"
                    );
                }
                if (hasVariation && !variationKeys.contains(domainRule.variationKey())) {
                    throw new IllegalArgumentException(
                            "Targeting rule '" + domainRule.name() + "' references unknown variation '"
                                    + domainRule.variationKey() + "'"
                    );
                }
                for (WeightedVariation wv : domainRule.rolloutDistributions()) {
                    if (!variationKeys.contains(wv.variationKey())) {
                        throw new IllegalArgumentException(
                                "Targeting rule '" + domainRule.name() + "' rollout references unknown variation '"
                                        + wv.variationKey() + "'"
                        );
                    }
                }
            }
            if (!allRuleErrors.isEmpty()) {
                throw new RuleValidationException("Flag targeting rules failed validation", allRuleErrors);
            }
        }
        if (rolloutPercentage != null && (rolloutPercentage < 0.0 || rolloutPercentage > 100.0)) {
            throw new IllegalArgumentException("rolloutPercentage must be between 0.0 and 100.0");
        }
        if (rolloutVariationKey != null && !rolloutVariationKey.isBlank() && !variationKeys.contains(rolloutVariationKey)) {
            throw new IllegalArgumentException(
                    "rolloutVariationKey '" + rolloutVariationKey + "' does not exist in flag variations"
            );
        }
        if (percentageRollout != null && !percentageRollout.isEmpty()) {
            for (WeightedVariation wv : percentageRollout) {
                if (wv == null) {
                    throw new IllegalArgumentException("percentageRollout must not contain null elements");
                }
            }
            WeightedVariation.validateTotalWeight(percentageRollout);
            for (WeightedVariation wv : percentageRollout) {
                if (!variationKeys.contains(wv.variationKey())) {
                    throw new IllegalArgumentException(
                            "percentageRollout references unknown variation '" + wv.variationKey() + "'"
                    );
                }
            }
        } else if (rolloutPercentage != null && rolloutPercentage > 0.0
                && (rolloutVariationKey == null || rolloutVariationKey.isBlank())) {
            throw new IllegalArgumentException(
                    "rolloutVariationKey is required when rolloutPercentage > 0.0 and percentageRollout is empty"
            );
        }
    }
}
