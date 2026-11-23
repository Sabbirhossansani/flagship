package com.flagship.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flagship.core.model.FlagDefinition;
import com.flagship.core.model.SegmentDefinition;
import com.flagship.core.model.TargetingRule;
import com.flagship.core.model.Variation;
import com.flagship.core.model.WeightedVariation;
import com.flagship.core.rule.RuleDescribeVisitor;
import com.flagship.core.rule.RuleNode;
import com.flagship.server.domain.FlagEntity;
import com.flagship.server.domain.SegmentEntity;
import com.flagship.server.dto.FlagResponse;
import com.flagship.server.dto.RuleNodeDto;
import com.flagship.server.dto.SegmentResponse;
import com.flagship.server.dto.TargetingRuleDto;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Thread-safe JSON codec for converting between JPA JSON columns, REST DTOs, and {@code flagship-core} domain objects.
 */
@Component
public class DomainJsonCodec {

    private static final TypeReference<List<Variation>> VARIATION_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<WeightedVariation>> WEIGHTED_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<TargetingRuleDto>> RULE_DTO_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Set<String>> STRING_SET_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> STRING_MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper mapper;
    private final RuleDescribeVisitor describeVisitor;

    public DomainJsonCodec() {
        this(new RuleDescribeVisitor());
    }

    public DomainJsonCodec(RuleDescribeVisitor describeVisitor) {
        this.describeVisitor = describeVisitor != null ? describeVisitor : new RuleDescribeVisitor();
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public String writeRuleNode(RuleNodeDto dto) {
        if (dto == null) {
            return null;
        }
        return writeJson(dto);
    }

    public RuleNodeDto readRuleNodeDto(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return readJson(json, RuleNodeDto.class);
    }

    public RuleNode readRuleNode(String json) {
        RuleNodeDto dto = readRuleNodeDto(json);
        return dto != null ? dto.toDomain() : null;
    }

    public String writeStringSet(Set<String> set) {
        return writeJson(set != null ? set : Set.of());
    }

    public Set<String> readStringSet(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        Set<String> parsed = readJson(json, STRING_SET_TYPE);
        if (parsed == null) {
            return Set.of();
        }
        Set<String> cleaned = new LinkedHashSet<>();
        for (String item : parsed) {
            if (item != null && !item.isBlank()) {
                cleaned.add(item);
            }
        }
        return cleaned;
    }

    public String writeStringMap(Map<String, String> map) {
        return writeJson(map != null ? map : Map.of());
    }

    public Map<String, String> readStringMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        Map<String, String> parsed = readJson(json, STRING_MAP_TYPE);
        return parsed != null ? new LinkedHashMap<>(parsed) : Map.of();
    }

    public String writeVariations(List<Variation> variations) {
        return writeJson(variations != null ? variations : List.of());
    }

    public List<Variation> readVariations(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<Variation> parsed = readJson(json, VARIATION_LIST_TYPE);
        return parsed != null ? List.copyOf(parsed) : List.of();
    }

    public String writeWeightedVariations(List<WeightedVariation> weights) {
        return writeJson(weights != null ? weights : List.of());
    }

    public List<WeightedVariation> readWeightedVariations(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<WeightedVariation> parsed = readJson(json, WEIGHTED_LIST_TYPE);
        return parsed != null ? List.copyOf(parsed) : List.of();
    }

    public String writeRules(List<TargetingRuleDto> rules) {
        return writeJson(rules != null ? rules : List.of());
    }

    public List<TargetingRuleDto> readRuleDtos(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<TargetingRuleDto> parsed = readJson(json, RULE_DTO_LIST_TYPE);
        if (parsed == null) {
            return List.of();
        }
        return parsed.stream()
                .map(dto -> TargetingRuleDto.fromDomain(dto.toDomain(), describeVisitor))
                .toList();
    }

    public List<TargetingRule> readDomainRules(String json) {
        return readRuleDtos(json).stream()
                .map(TargetingRuleDto::toDomain)
                .toList();
    }

    public SegmentDefinition toSegmentDefinition(SegmentEntity entity) {
        return new SegmentDefinition(
                entity.getSegmentKey(),
                entity.getName(),
                entity.getDescription(),
                readRuleNode(entity.getConditionJson()),
                readStringSet(entity.getIncludedUsersJson()),
                readStringSet(entity.getExcludedUsersJson()),
                entity.getVersion()
        );
    }

    public SegmentResponse toSegmentResponse(String projectKey, SegmentEntity entity) {
        RuleNode domainNode = readRuleNode(entity.getConditionJson());
        String conditionDescription = domainNode != null ? domainNode.accept(describeVisitor) : null;
        return new SegmentResponse(
                projectKey,
                entity.getEnvKey(),
                entity.getSegmentKey(),
                entity.getName(),
                entity.getDescription(),
                RuleNodeDto.fromDomain(domainNode),
                conditionDescription,
                readStringSet(entity.getIncludedUsersJson()),
                readStringSet(entity.getExcludedUsersJson()),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public FlagDefinition toFlagDefinition(FlagEntity entity) {
        return FlagDefinition.builder(entity.getFlagKey(), entity.getVariationType())
                .name(entity.getName())
                .description(entity.getDescription())
                .owner(entity.getOwner())
                .enabled(entity.isEnabled())
                .killSwitch(entity.isKillSwitch())
                .variations(readVariations(entity.getVariationsJson()))
                .defaultVariationKey(entity.getDefaultVariationKey())
                .offVariationKey(entity.getOffVariationKey())
                .individualTargets(readStringMap(entity.getIndividualTargetsJson()))
                .rules(readDomainRules(entity.getRulesJson()))
                .rolloutPercentage(entity.getRolloutPercentage())
                .rolloutVariationKey(entity.getRolloutVariationKey())
                .percentageRollout(readWeightedVariations(entity.getPercentageRolloutJson()))
                .version(entity.getVersion())
                .updatedAt(entity.getUpdatedAt())
                .lastEvaluatedAt(entity.getLastEvaluatedAt())
                .fullRolloutSince(entity.getFullRolloutSince())
                .build();
    }

    public FlagResponse toFlagResponse(String projectKey, FlagEntity entity) {
        FlagDefinition domain = toFlagDefinition(entity);
        List<TargetingRuleDto> ruleDtos = domain.getRules().stream()
                .map(r -> TargetingRuleDto.fromDomain(r, describeVisitor))
                .toList();
        return new FlagResponse(
                projectKey,
                entity.getEnvKey(),
                domain.getKey(),
                domain.getName(),
                domain.getDescription(),
                domain.getOwner(),
                domain.getType(),
                domain.isEnabled(),
                domain.isKillSwitch(),
                domain.getVariations(),
                domain.getDefaultVariationKey(),
                domain.getOffVariationKey(),
                domain.getIndividualTargets(),
                ruleDtos,
                domain.getRolloutPercentage(),
                domain.getRolloutVariationKey(),
                domain.getPercentageRollout(),
                domain.getVersion(),
                domain.isStale(Instant.now()),
                entity.getCreatedAt(),
                domain.getUpdatedAt(),
                domain.getLastEvaluatedAt(),
                domain.getFullRolloutSince()
        );
    }

    public <T> String writeJson(T value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize value to JSON: " + e.getMessage(), e);
        }
    }

    public <T> T readJson(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize JSON payload: " + e.getMessage(), e);
        }
    }

    public <T> T readJson(String json, TypeReference<T> typeRef) {
        try {
            return mapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize JSON payload: " + e.getMessage(), e);
        }
    }
}
