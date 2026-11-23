package com.flagship.core.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Core feature flag domain model for a specific environment (FR1, FR2, FR3, FR7, FR9).
 * Pre-indexes variations for O(1) lookup and minimal heap overhead (QA1).
 */
public final class FlagDefinition {

    private final String key;
    private final String name;
    private final String description;
    private final String owner;
    private final VariationType type;
    private final boolean enabled;
    private final boolean killSwitch;
    private final List<Variation> variations;
    private final Map<String, Variation> variationIndex;
    private final String defaultVariationKey;
    private final String offVariationKey;
    private final Map<String, String> individualTargets;
    private final List<TargetingRule> rules;
    private final double rolloutPercentage;
    private final String rolloutVariationKey;
    private final List<WeightedVariation> percentageRollout;
    private final long version;
    private final Instant updatedAt;
    private final Instant lastEvaluatedAt;
    private final Instant fullRolloutSince;

    private FlagDefinition(Builder builder) {
        this.key = Objects.requireNonNull(builder.key, "flag key must not be null");
        this.name = builder.name != null ? builder.name : builder.key;
        this.description = builder.description;
        this.owner = builder.owner != null ? builder.owner : "unassigned";
        this.type = Objects.requireNonNull(builder.type, "flag type must not be null");
        this.enabled = builder.enabled;
        this.killSwitch = builder.killSwitch;
        this.variations = List.copyOf(builder.variations);

        Map<String, Variation> index = new HashMap<>(Math.max(4, builder.variations.size() * 2));
        for (Variation variation : this.variations) {
            index.put(variation.key(), variation);
        }
        this.variationIndex = Map.copyOf(index);

        this.defaultVariationKey = Objects.requireNonNull(builder.defaultVariationKey,
                "defaultVariationKey must not be null");
        this.offVariationKey = builder.offVariationKey != null
                ? builder.offVariationKey
                : builder.defaultVariationKey;
        this.individualTargets = Map.copyOf(builder.individualTargets);
        this.rules = List.copyOf(builder.rules);
        if (builder.rolloutPercentage < 0.0 || builder.rolloutPercentage > 100.0) {
            throw new IllegalArgumentException("rolloutPercentage must be between 0.0 and 100.0");
        }
        this.rolloutPercentage = builder.rolloutPercentage;
        this.rolloutVariationKey = builder.rolloutVariationKey;
        this.percentageRollout = List.copyOf(builder.percentageRollout);
        WeightedVariation.validateTotalWeight(this.percentageRollout);
        this.version = builder.version;
        this.updatedAt = builder.updatedAt != null ? builder.updatedAt : Instant.EPOCH;
        this.lastEvaluatedAt = builder.lastEvaluatedAt;
        this.fullRolloutSince = builder.fullRolloutSince;
    }

    public static Builder builder(String key, VariationType type) {
        return new Builder(key, type);
    }

    public Builder toBuilder() {
        return new Builder(this.key, this.type)
                .name(this.name)
                .description(this.description)
                .owner(this.owner)
                .enabled(this.enabled)
                .killSwitch(this.killSwitch)
                .variations(this.variations)
                .defaultVariationKey(this.defaultVariationKey)
                .offVariationKey(this.offVariationKey)
                .individualTargets(this.individualTargets)
                .rules(this.rules)
                .rolloutPercentage(this.rolloutPercentage)
                .rolloutVariationKey(this.rolloutVariationKey)
                .percentageRollout(this.percentageRollout)
                .version(this.version)
                .updatedAt(this.updatedAt)
                .lastEvaluatedAt(this.lastEvaluatedAt)
                .fullRolloutSince(this.fullRolloutSince);
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getOwner() {
        return owner;
    }

    public VariationType getType() {
        return type;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isKillSwitch() {
        return killSwitch;
    }

    public List<Variation> getVariations() {
        return variations;
    }

    public Variation getVariation(String variationKey) {
        return variationKey != null ? variationIndex.get(variationKey) : null;
    }

    public String getDefaultVariationKey() {
        return defaultVariationKey;
    }

    public String getOffVariationKey() {
        return offVariationKey;
    }

    public Map<String, String> getIndividualTargets() {
        return individualTargets;
    }

    public List<TargetingRule> getRules() {
        return rules;
    }

    public double getRolloutPercentage() {
        return rolloutPercentage;
    }

    public String getRolloutVariationKey() {
        return rolloutVariationKey;
    }

    public List<WeightedVariation> getPercentageRollout() {
        return percentageRollout;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getLastEvaluatedAt() {
        return lastEvaluatedAt;
    }

    public Instant getFullRolloutSince() {
        return fullRolloutSince;
    }

    /**
     * FR9 Flag Hygiene check: returns true if the flag has not been evaluated in the past 30 days
     * OR has been 100% rolled out for at least 14 days as of {@code referenceTime}.
     */
    public boolean isStale(Instant referenceTime) {
        Objects.requireNonNull(referenceTime, "referenceTime must not be null");
        if (lastEvaluatedAt == null) {
            if (updatedAt != null
                    && !Instant.EPOCH.equals(updatedAt)
                    && Duration.between(updatedAt, referenceTime).toDays() >= 30) {
                return true;
            }
        } else if (Duration.between(lastEvaluatedAt, referenceTime).toDays() >= 30) {
            return true;
        }
        if (fullRolloutSince != null
                && enabled
                && !killSwitch
                && (rolloutPercentage <= 0.0 || rolloutPercentage >= 100.0)
                && Duration.between(fullRolloutSince, referenceTime).toDays() >= 14) {
            return true;
        }
        return false;
    }

    public static final class Builder {
        private final String key;
        private final VariationType type;
        private String name;
        private String description;
        private String owner = "unassigned";
        private boolean enabled = true;
        private boolean killSwitch = false;
        private final List<Variation> variations = new ArrayList<>();
        private String defaultVariationKey;
        private String offVariationKey;
        private final Map<String, String> individualTargets = new LinkedHashMap<>();
        private final List<TargetingRule> rules = new ArrayList<>();
        private double rolloutPercentage = 0.0;
        private String rolloutVariationKey;
        private final List<WeightedVariation> percentageRollout = new ArrayList<>();
        private long version = 1L;
        private Instant updatedAt = Instant.EPOCH;
        private Instant lastEvaluatedAt;
        private Instant fullRolloutSince;

        private Builder(String key, VariationType type) {
            this.key = key;
            this.type = type;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder owner(String owner) {
            this.owner = owner;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder killSwitch(boolean killSwitch) {
            this.killSwitch = killSwitch;
            return this;
        }

        public Builder variation(Variation variation) {
            this.variations.add(Objects.requireNonNull(variation));
            return this;
        }

        public Builder variations(List<Variation> variations) {
            this.variations.clear();
            if (variations != null) {
                this.variations.addAll(variations);
            }
            return this;
        }

        public Builder defaultVariationKey(String defaultVariationKey) {
            this.defaultVariationKey = defaultVariationKey;
            return this;
        }

        public Builder offVariationKey(String offVariationKey) {
            this.offVariationKey = offVariationKey;
            return this;
        }

        public Builder targetUser(String userKey, String variationKey) {
            this.individualTargets.put(Objects.requireNonNull(userKey), Objects.requireNonNull(variationKey));
            return this;
        }

        public Builder individualTargets(Map<String, String> targets) {
            this.individualTargets.clear();
            if (targets != null) {
                this.individualTargets.putAll(targets);
            }
            return this;
        }

        public Builder rule(TargetingRule rule) {
            this.rules.add(Objects.requireNonNull(rule));
            return this;
        }

        public Builder rules(List<TargetingRule> rules) {
            this.rules.clear();
            if (rules != null) {
                this.rules.addAll(rules);
            }
            return this;
        }

        public Builder rollout(double percentage, String variationKey) {
            this.rolloutPercentage = percentage;
            this.rolloutVariationKey = variationKey;
            return this;
        }

        public Builder rolloutPercentage(double rolloutPercentage) {
            this.rolloutPercentage = rolloutPercentage;
            return this;
        }

        public Builder rolloutVariationKey(String rolloutVariationKey) {
            this.rolloutVariationKey = rolloutVariationKey;
            return this;
        }

        public Builder percentageRollout(List<WeightedVariation> weights) {
            this.percentageRollout.clear();
            if (weights != null) {
                this.percentageRollout.addAll(weights);
            }
            return this;
        }

        public Builder version(long version) {
            this.version = version;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder lastEvaluatedAt(Instant lastEvaluatedAt) {
            this.lastEvaluatedAt = lastEvaluatedAt;
            return this;
        }

        public Builder fullRolloutSince(Instant fullRolloutSince) {
            this.fullRolloutSince = fullRolloutSince;
            return this;
        }

        public FlagDefinition build() {
            return new FlagDefinition(this);
        }
    }
}
