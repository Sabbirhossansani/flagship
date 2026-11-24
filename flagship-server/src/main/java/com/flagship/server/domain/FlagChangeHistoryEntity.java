package com.flagship.server.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "flag_change_history",
        indexes = {
                @Index(name = "idx_change_history_entity", columnList = "project_key, env_key, entity_type, entity_key")
        }
)
public class FlagChangeHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_key", nullable = false, length = 100)
    private String projectKey;

    @Column(name = "env_key", nullable = false, length = 100)
    private String envKey;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType; // FLAG or SEGMENT

    @Column(name = "entity_key", nullable = false, length = 100)
    private String entityKey;

    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "author", nullable = false, length = 150)
    private String author;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "action", nullable = false, length = 50)
    private String action; // CREATE, UPDATE, KILL_SWITCH, ROLLBACK, DELETE

    @Column(name = "before_json", columnDefinition = "TEXT")
    private String beforeJson;

    @Column(name = "after_json", columnDefinition = "TEXT")
    private String afterJson;

    @Column(name = "diff_summary", columnDefinition = "TEXT")
    private String diffSummary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public FlagChangeHistoryEntity() {
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

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityKey() {
        return entityKey;
    }

    public void setEntityKey(String entityKey) {
        this.entityKey = entityKey;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getBeforeJson() {
        return beforeJson;
    }

    public void setBeforeJson(String beforeJson) {
        this.beforeJson = beforeJson;
    }

    public String getAfterJson() {
        return afterJson;
    }

    public void setAfterJson(String afterJson) {
        this.afterJson = afterJson;
    }

    public String getDiffSummary() {
        return diffSummary;
    }

    public void setDiffSummary(String diffSummary) {
        this.diffSummary = diffSummary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
