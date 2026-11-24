package com.flagship.server.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "experiment_events",
        indexes = {
                @Index(name = "idx_exp_events_query", columnList = "project_key, env_key, flag_key, event_type")
        }
)
public class ExperimentEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_key", nullable = false, length = 100)
    private String projectKey;

    @Column(name = "env_key", nullable = false, length = 100)
    private String envKey;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType; // EXPOSURE or GOAL

    @Column(name = "flag_key", nullable = false, length = 100)
    private String flagKey;

    @Column(name = "variation_key", length = 100)
    private String variationKey;

    @Column(name = "metric_name", length = 100)
    private String metricName;

    @Column(name = "user_key", nullable = false, length = 255)
    private String userKey;

    @Column(name = "event_value", nullable = false)
    private double eventValue = 1.0;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ExperimentEventEntity() {
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

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getFlagKey() {
        return flagKey;
    }

    public void setFlagKey(String flagKey) {
        this.flagKey = flagKey;
    }

    public String getVariationKey() {
        return variationKey;
    }

    public void setVariationKey(String variationKey) {
        this.variationKey = variationKey;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public String getUserKey() {
        return userKey;
    }

    public void setUserKey(String userKey) {
        this.userKey = userKey;
    }

    public double getEventValue() {
        return eventValue;
    }

    public void setEventValue(double eventValue) {
        this.eventValue = eventValue;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
