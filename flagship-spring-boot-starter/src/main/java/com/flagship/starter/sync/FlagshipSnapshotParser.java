package com.flagship.starter.sync;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flagship.core.model.EnvironmentSnapshot;
import com.flagship.core.model.FlagDefinition;
import com.flagship.core.model.SegmentDefinition;
import com.flagship.core.model.TargetingRule;
import com.flagship.core.model.Variation;
import com.flagship.core.model.VariationType;
import com.flagship.core.model.WeightedVariation;
import com.flagship.core.rule.CompositeRuleNode;
import com.flagship.core.rule.LeafRuleNode;
import com.flagship.core.rule.LogicalOperator;
import com.flagship.core.rule.RuleNode;
import com.flagship.core.rule.RuleOperatorType;
import com.flagship.core.rule.SegmentReferenceNode;

import java.time.Instant;
import java.util.*;

/**
 * Robust JSON serializer and deserializer for {@link EnvironmentSnapshot} and FlagShip domain structures (FR5, FR6).
 * Handles decoupling between pure domain models and JSON representations.
 */
public final class FlagshipSnapshotParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private FlagshipSnapshotParser() {
    }

    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }

    public static String serialize(EnvironmentSnapshot snapshot) throws Exception {
        return OBJECT_MAPPER.writeValueAsString(snapshot);
    }

    public static EnvironmentSnapshot parse(String json, String defaultProjectKey, String defaultEnvKey) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(json);
        return parse(root, defaultProjectKey, defaultEnvKey);
    }

    public static EnvironmentSnapshot parse(JsonNode root, String defaultProjectKey, String defaultEnvKey) {
        String projectKey = root.path("projectKey").asText(defaultProjectKey != null ? defaultProjectKey : "default");
        String envKey = root.path("environmentKey").asText(defaultEnvKey != null ? defaultEnvKey : "production");
        long revision = root.path("revision").asLong(1L);

        Instant generatedAt = Instant.now();
        if (root.has("generatedAt") && !root.path("generatedAt").isNull()) {
            try {
                generatedAt = Instant.parse(root.path("generatedAt").asText());
            } catch (Exception ignored) {
            }
        }

        Map<String, FlagDefinition> flags = new LinkedHashMap<>();
        JsonNode flagsNode = root.path("flags");
        if (flagsNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = flagsNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                FlagDefinition fd = parseFlagDefinition(entry.getValue(), entry.getKey());
                if (fd != null) {
                    flags.put(fd.getKey(), fd);
                }
            }
        }

        Map<String, SegmentDefinition> segments = new LinkedHashMap<>();
        JsonNode segmentsNode = root.path("segments");
        if (segmentsNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = segmentsNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                SegmentDefinition sd = parseSegmentDefinition(entry.getValue(), entry.getKey());
                if (sd != null) {
                    segments.put(sd.key(), sd);
                }
            }
        }

        return new EnvironmentSnapshot(projectKey, envKey, revision, generatedAt, flags, segments);
    }

    public static FlagDefinition parseFlagDefinition(JsonNode node, String fallbackKey) {
        if (node == null || node.isNull()) return null;

        String key = node.path("key").asText(fallbackKey);
        if (key == null || key.isBlank()) return null;

        String name = node.path("name").asText(key);
        String description = node.path("description").isNull() ? null : node.path("description").asText(null);
        String owner = node.path("owner").asText("unassigned");

        VariationType type = VariationType.BOOLEAN;
        if (node.has("type") && !node.path("type").isNull()) {
            try {
                type = VariationType.valueOf(node.path("type").asText().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        boolean enabled = node.path("enabled").asBoolean(true);
        boolean killSwitch = node.path("killSwitch").asBoolean(false);

        List<Variation> variations = new ArrayList<>();
        JsonNode varArray = node.path("variations");
        if (varArray.isArray()) {
            for (JsonNode v : varArray) {
                String vk = v.path("key").asText();
                Object val = parseValue(v.path("value"), type);
                String desc = v.path("description").isNull() ? null : v.path("description").asText(null);
                variations.add(new Variation(vk, type, val, desc));
            }
        }

        if (variations.isEmpty()) {
            if (type == VariationType.BOOLEAN) {
                variations.add(new Variation("true", VariationType.BOOLEAN, true, "True"));
                variations.add(new Variation("false", VariationType.BOOLEAN, false, "False"));
            } else {
                variations.add(new Variation("default", type, "default", "Default"));
            }
        }

        String defaultVarKey = node.path("defaultVariationKey").asText(variations.getFirst().key());
        String offVarKey = node.path("offVariationKey").asText(defaultVarKey);

        Map<String, String> individualTargets = new LinkedHashMap<>();
        JsonNode targetsNode = node.path("individualTargets");
        if (targetsNode.isObject()) {
            targetsNode.fields().forEachRemaining(entry -> individualTargets.put(entry.getKey(), entry.getValue().asText()));
        }

        List<TargetingRule> rules = new ArrayList<>();
        JsonNode rulesArray = node.path("rules");
        if (rulesArray.isArray()) {
            for (JsonNode rNode : rulesArray) {
                TargetingRule tr = parseTargetingRule(rNode);
                if (tr != null) rules.add(tr);
            }
        }

        double rolloutPercentage = node.path("rolloutPercentage").asDouble(0.0);
        String rolloutVarKey = node.path("rolloutVariationKey").isNull() ? null : node.path("rolloutVariationKey").asText(null);

        List<WeightedVariation> weightedVariations = new ArrayList<>();
        JsonNode pctArray = node.path("percentageRollout");
        if (pctArray.isArray()) {
            for (JsonNode pv : pctArray) {
                weightedVariations.add(new WeightedVariation(
                        pv.path("variationKey").asText(),
                        pv.path("weightBuckets").asInt(0)
                ));
            }
        }

        long version = node.path("version").asLong(1L);

        Instant updatedAt = Instant.EPOCH;
        if (node.has("updatedAt") && !node.path("updatedAt").isNull()) {
            try {
                updatedAt = Instant.parse(node.path("updatedAt").asText());
            } catch (Exception ignored) {
            }
        }

        Instant lastEvaluatedAt = null;
        if (node.has("lastEvaluatedAt") && !node.path("lastEvaluatedAt").isNull()) {
            try {
                lastEvaluatedAt = Instant.parse(node.path("lastEvaluatedAt").asText());
            } catch (Exception ignored) {
            }
        }

        Instant fullRolloutSince = null;
        if (node.has("fullRolloutSince") && !node.path("fullRolloutSince").isNull()) {
            try {
                fullRolloutSince = Instant.parse(node.path("fullRolloutSince").asText());
            } catch (Exception ignored) {
            }
        }

        return FlagDefinition.builder(key, type)
                .name(name)
                .description(description)
                .owner(owner)
                .enabled(enabled)
                .killSwitch(killSwitch)
                .variations(variations)
                .defaultVariationKey(defaultVarKey)
                .offVariationKey(offVarKey)
                .individualTargets(individualTargets)
                .rules(rules)
                .rolloutPercentage(rolloutPercentage)
                .rolloutVariationKey(rolloutVarKey)
                .percentageRollout(weightedVariations)
                .version(version)
                .updatedAt(updatedAt)
                .lastEvaluatedAt(lastEvaluatedAt)
                .fullRolloutSince(fullRolloutSince)
                .build();
    }

    public static TargetingRule parseTargetingRule(JsonNode node) {
        if (node == null || node.isNull()) return null;
        String id = node.path("id").asText(UUID.randomUUID().toString());
        String name = node.path("name").asText(id);
        String segmentKey = node.path("segmentKey").isNull() ? null : node.path("segmentKey").asText(null);
        RuleNode condition = parseRuleNode(node.path("condition"));
        String variationKey = node.path("variationKey").isNull() ? null : node.path("variationKey").asText(null);

        List<WeightedVariation> weighted = new ArrayList<>();
        JsonNode distArray = node.path("rolloutDistributions");
        if (distArray.isArray()) {
            for (JsonNode d : distArray) {
                weighted.add(new WeightedVariation(
                        d.path("variationKey").asText(),
                        d.path("weightBuckets").asInt(0)
                ));
            }
        }

        return new TargetingRule(id, name, segmentKey, condition, variationKey, weighted);
    }

    public static SegmentDefinition parseSegmentDefinition(JsonNode node, String fallbackKey) {
        if (node == null || node.isNull()) return null;
        String key = node.path("key").asText(fallbackKey);
        String name = node.path("name").asText(key);
        String description = node.path("description").isNull() ? null : node.path("description").asText(null);

        RuleNode condition = parseRuleNode(node.path("condition"));

        Set<String> inc = new LinkedHashSet<>();
        JsonNode incArray = node.path("includedUsers");
        if (incArray.isArray()) {
            incArray.forEach(u -> inc.add(u.asText()));
        }

        Set<String> exc = new LinkedHashSet<>();
        JsonNode excArray = node.path("excludedUsers");
        if (excArray.isArray()) {
            excArray.forEach(u -> exc.add(u.asText()));
        }

        long version = node.path("version").asLong(1L);

        return new SegmentDefinition(key, name, description, condition, inc, exc, version);
    }

    public static RuleNode parseRuleNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;

        if (node.has("segmentKey") && !node.get("segmentKey").isNull()) {
            return new SegmentReferenceNode(node.get("segmentKey").asText());
        }

        if (node.has("logicalOperator") || (node.has("children") && node.has("operator"))) {
            String opStr = node.has("logicalOperator")
                    ? node.get("logicalOperator").asText()
                    : node.get("operator").asText();
            try {
                LogicalOperator op = LogicalOperator.valueOf(opStr.toUpperCase());
                List<RuleNode> children = new ArrayList<>();
                JsonNode cArray = node.get("children");
                if (cArray != null && cArray.isArray()) {
                    for (JsonNode c : cArray) {
                        RuleNode child = parseRuleNode(c);
                        if (child != null) children.add(child);
                    }
                }
                return new CompositeRuleNode(op, children);
            } catch (Exception ignored) {
            }
        }

        if (node.has("attribute") && node.has("operator")) {
            String attr = node.get("attribute").asText();
            try {
                RuleOperatorType op = RuleOperatorType.valueOf(node.get("operator").asText().toUpperCase());
                List<String> values = new ArrayList<>();
                JsonNode vArray = node.get("values");
                if (vArray != null && vArray.isArray()) {
                    for (JsonNode v : vArray) {
                        values.add(v.asText());
                    }
                }
                return new LeafRuleNode(attr, op, values);
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private static Object parseValue(JsonNode node, VariationType type) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        return switch (type) {
            case BOOLEAN -> node.asBoolean();
            case NUMBER -> node.asDouble();
            case STRING, JSON -> node.isTextual() ? node.asText() : node.toString();
        };
    }
}
