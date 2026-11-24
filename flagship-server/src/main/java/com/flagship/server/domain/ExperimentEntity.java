package com.flagship.server.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "experiments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_experiments_key", columnNames = {"project_key", "env_key", "experiment_key"})
        },
        indexes = {
                @Index(name = "idx_experiments_env", columnList = "project_key, env_key")
        }
)
public class ExperimentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_key", nullable = false, length = 100)
    private String projectKey;

    @Column(name = "env_key", nullable = false, length = 100)
    private String envKey;

    @Column(name = "experiment_key", nullable = false, length = 100)
    private String experimentKey;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "flag_key", nullable = false, length = 100)
    private String flagKey;

    @Column(name = "metric_name", nullable = false, length = 100)
    private String metricName;

    @Column(name = "baseline_variation_key", nullable = false, length = 100)
    private String baselineVariationKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ExperimentStatus status = ExperimentStatus.RUNNING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ExperimentEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getEnvKey() {
        return envKey;
    }

    public void setEnvKey(String envKey) {
        this.envKey = envKey;
    }

    public String getExperimentKey() {
        return experimentKey;
    }

    public void setExperimentKey(String experimentKey) {
        this.experimentKey = experimentKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFlagKey() {
        return flagKey;
    }

    public void setFlagKey(String flagKey) {
        this.flagKey = flagKey;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public String getBaselineVariationKey() {
        return baselineVariationKey;
    }

    public void setBaselineVariationKey(String baselineVariationKey) {
        this.baselineVariationKey = baselineVariationKey;
    }

    public ExperimentStatus getStatus() {
        return status;
    }

    public void setStatus(ExperimentStatus status) {
        this.status = status;
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
}
