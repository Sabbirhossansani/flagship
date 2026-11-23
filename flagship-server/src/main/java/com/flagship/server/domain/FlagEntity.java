package com.flagship.server.domain;

import com.flagship.core.model.VariationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "flags")
public class FlagEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @Column(name = "env_key", nullable = false, length = 100)
    private String envKey;

    @Column(name = "flag_key", nullable = false, length = 100)
    private String flagKey;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "owner", nullable = false, length = 255)
    private String owner = "unassigned";

    @Enumerated(EnumType.STRING)
    @Column(name = "variation_type", nullable = false, length = 32)
    private VariationType variationType;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "kill_switch", nullable = false)
    private boolean killSwitch = false;

    @Column(name = "variations_json", nullable = false, columnDefinition = "TEXT")
    private String variationsJson = "[]";

    @Column(name = "default_variation_key", nullable = false, length = 100)
    private String defaultVariationKey;

    @Column(name = "off_variation_key", nullable = false, length = 100)
    private String offVariationKey;

    @Column(name = "individual_targets_json", nullable = false, columnDefinition = "TEXT")
    private String individualTargetsJson = "{}";

    @Column(name = "rules_json", nullable = false, columnDefinition = "TEXT")
    private String rulesJson = "[]";

    @Column(name = "rollout_percentage", nullable = false)
    private double rolloutPercentage = 0.0;

    @Column(name = "rollout_variation_key", length = 100)
    private String rolloutVariationKey;

    @Column(name = "percentage_rollout_json", nullable = false, columnDefinition = "TEXT")
    private String percentageRolloutJson = "[]";

    @Column(name = "version", nullable = false)
    private long version = 1L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_evaluated_at")
    private Instant lastEvaluatedAt;

    @Column(name = "full_rollout_since")
    private Instant fullRolloutSince;

    public FlagEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ProjectEntity getProject() {
        return project;
    }

    public void setProject(ProjectEntity project) {
        this.project = project;
    }

    public String getEnvKey() {
        return envKey;
    }

    public void setEnvKey(String envKey) {
        this.envKey = envKey;
    }

    public String getFlagKey() {
        return flagKey;
    }

    public void setFlagKey(String flagKey) {
        this.flagKey = flagKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public VariationType getVariationType() {
        return variationType;
    }

    public void setVariationType(VariationType variationType) {
        this.variationType = variationType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isKillSwitch() {
        return killSwitch;
    }

    public void setKillSwitch(boolean killSwitch) {
        this.killSwitch = killSwitch;
    }

    public String getVariationsJson() {
        return variationsJson;
    }

    public void setVariationsJson(String variationsJson) {
        this.variationsJson = variationsJson;
    }

    public String getDefaultVariationKey() {
        return defaultVariationKey;
    }

    public void setDefaultVariationKey(String defaultVariationKey) {
        this.defaultVariationKey = defaultVariationKey;
    }

    public String getOffVariationKey() {
        return offVariationKey;
    }

    public void setOffVariationKey(String offVariationKey) {
        this.offVariationKey = offVariationKey;
    }

    public String getIndividualTargetsJson() {
        return individualTargetsJson;
    }

    public void setIndividualTargetsJson(String individualTargetsJson) {
        this.individualTargetsJson = individualTargetsJson;
    }

    public String getRulesJson() {
        return rulesJson;
    }

    public void setRulesJson(String rulesJson) {
        this.rulesJson = rulesJson;
    }

    public double getRolloutPercentage() {
        return rolloutPercentage;
    }

    public void setRolloutPercentage(double rolloutPercentage) {
        this.rolloutPercentage = rolloutPercentage;
    }

    public String getRolloutVariationKey() {
        return rolloutVariationKey;
    }

    public void setRolloutVariationKey(String rolloutVariationKey) {
        this.rolloutVariationKey = rolloutVariationKey;
    }

    public String getPercentageRolloutJson() {
        return percentageRolloutJson;
    }

    public void setPercentageRolloutJson(String percentageRolloutJson) {
        this.percentageRolloutJson = percentageRolloutJson;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getLastEvaluatedAt() {
        return lastEvaluatedAt;
    }

    public void setLastEvaluatedAt(Instant lastEvaluatedAt) {
        this.lastEvaluatedAt = lastEvaluatedAt;
    }

    public Instant getFullRolloutSince() {
        return fullRolloutSince;
    }

    public void setFullRolloutSince(Instant fullRolloutSince) {
        this.fullRolloutSince = fullRolloutSince;
    }
}
